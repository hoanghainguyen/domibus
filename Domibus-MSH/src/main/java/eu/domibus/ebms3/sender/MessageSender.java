/*
 * Copyright 2015 e-CODEX Project
 *
 * Licensed under the EUPL, Version 1.1 or – as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the
 * Licence.
 * You may obtain a copy of the Licence at:
 * http://ec.europa.eu/idabc/eupl5
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the Licence is
 * distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.domibus.ebms3.sender;

import eu.domibus.common.ErrorCode;
import eu.domibus.common.MSHRole;
import eu.domibus.common.dao.ErrorLogDao;
import eu.domibus.common.dao.MessageLogDao;
import eu.domibus.common.dao.MessagingDao;
import eu.domibus.common.dao.PModeProvider;
import eu.domibus.common.exception.EbMS3Exception;
import eu.domibus.common.model.configuration.LegConfiguration;
import eu.domibus.common.model.logging.ErrorLogEntry;
import eu.domibus.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.UserMessage;
import eu.domibus.ebms3.common.DelayedDispatchMessageCreator;
import eu.domibus.ebms3.receiver.BackendNotificationService;
import eu.domibus.messaging.MessageConstants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.interceptor.Fault;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jms.core.JmsOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.xml.soap.SOAPMessage;
import javax.xml.ws.soap.SOAPFaultException;

//import eu.domibus.ebms3.pmode.PModeFinder;
//import eu.domibus.ebms3.pmode.model.PMode;

/**
 * This class is responsible for the handling of outgoing messages.
 *
 * @author Christian Koch, Stefan Mueller
 * @since 3.0
 */
@Service(value = "messageSenderService")
public class MessageSender implements MessageListener {
    private static final Log LOG = LogFactory.getLog(MessageSender.class);

    private final String UNRECOVERABLE_ERROR_RETRY = "domibus.dispatch.ebms.error.unrecoverable.retry";

    @Autowired
    @Qualifier("jmsTemplateDispatch")
    private JmsOperations jmsOperationsDispatch;

    @Autowired
    private ErrorLogDao errorLogDao;

    @Autowired
    private MessagingDao messagingDao;

    @Autowired
    private MessageLogDao messageLogDao;

    @Autowired
    private PModeProvider pModeProvider;

    @Autowired
    private MSHDispatcher mshDispatcher;

    @Autowired
    private EbMS3MessageBuilder messageBuilder;

    @Autowired
    private ReliabilityChecker reliabilityChecker;

    @Autowired
    private EbmsErrorChecker ebmsErrorChecker;

    @Autowired
    private BackendNotificationService backendNotificationService;

    @Autowired
    private UpdateRetryLoggingService updateRetryLoggingService;


    private void sendUserMessage(final String messageId) {
        ReliabilityChecker.CheckResult reliabilityCheckSuccessful = ReliabilityChecker.CheckResult.FAIL;
        // Assuming that everything goes fine
        EbmsErrorChecker.CheckResult errorCheckResult = EbmsErrorChecker.CheckResult.OK;

        LegConfiguration legConfiguration = null;
        final String pModeKey;

        final UserMessage userMessage = this.messagingDao.findUserMessageByMessageId(messageId);
        try {
            pModeKey = this.pModeProvider.findPModeKeyForUserMessage(userMessage);
            legConfiguration = this.pModeProvider.getLegConfiguration(pModeKey);

            MessageSender.LOG.debug("PMode found : " + pModeKey);
            final SOAPMessage soapMessage = this.messageBuilder.buildSOAPMessage(userMessage, legConfiguration);
            final SOAPMessage response = this.mshDispatcher.dispatch(soapMessage, pModeKey);
            errorCheckResult = this.ebmsErrorChecker.check(response);
            if (EbmsErrorChecker.CheckResult.MARSHALL_ERROR.equals(errorCheckResult)) {
                EbMS3Exception e = new EbMS3Exception(ErrorCode.EbMS3ErrorCode.EBMS_0004, "Problem occured during marshalling", messageId, null);
                e.setMshRole(MSHRole.SENDING);
                throw e;
            }
            reliabilityCheckSuccessful = this.reliabilityChecker.check(soapMessage, response, pModeKey);
        } catch (final SOAPFaultException soapFEx) {
            if (soapFEx.getCause() instanceof Fault && soapFEx.getCause().getCause() instanceof EbMS3Exception) {
                this.handleEbms3Exception((EbMS3Exception) soapFEx.getCause().getCause(), messageId);
            } else MessageSender.LOG.warn("Error for message with ID [" + messageId + "]", soapFEx);

        } catch (final EbMS3Exception e) {
            this.handleEbms3Exception(e, messageId);
        } finally {
            switch (reliabilityCheckSuccessful) {
                case OK:
                    switch (errorCheckResult) {
                        case OK:
                            this.messageLogDao.setMessageAsAck(messageId);
                            break;
                        case WARNING:
                            this.messageLogDao.setMessageAsAckWithWarnings(messageId);
                            break;
                        default:
                            assert false;
                    }
                    backendNotificationService.notifyOfSendSuccess(messageId);
                    messageLogDao.setAsNotified(messageId);
                    messagingDao.clearPayloadData(messageId);
                    break;
                case WAITING_FOR_CALLBACK:
                    this.messageLogDao.setMessageAsWaitingForReceipt(messageId);
                    break;
                case FAIL:
                    updateRetryLoggingService.updateRetryLogging(messageId, legConfiguration);
            }
        }
    }

    /**
     * This method is responsible for the ebMS3 error handling (creation of errorlogs and marking message as sent)
     *
     * @param exceptionToHandle the exception {@link eu.domibus.common.exception.EbMS3Exception} that needs to be handled
     * @param messageId         id of the message the exception belongs to
     */
    private void handleEbms3Exception(final EbMS3Exception exceptionToHandle, final String messageId) {
        exceptionToHandle.setRefToMessageId(messageId);
        if (!exceptionToHandle.isRecoverable() && !Boolean.parseBoolean(System.getProperty(UNRECOVERABLE_ERROR_RETRY))) {
            messageLogDao.setMessageAsAck(messageId);
        }

        exceptionToHandle.setMshRole(MSHRole.SENDING);
        MessageSender.LOG.error("Error for message with ID [" + messageId + "]", exceptionToHandle);
        this.errorLogDao.create(new ErrorLogEntry(exceptionToHandle));
        //TODO: notify backends of error
    }





    @Transactional(propagation = Propagation.REQUIRED)
    public void onMessage(final Message message) {
        Long delay = null;
        String messageId = null;
        try {
            messageId = message.getStringProperty(MessageConstants.MESSAGE_ID);
            delay = message.getLongProperty(MessageConstants.DELAY);
            if (delay > 0) {
                jmsOperationsDispatch.send(new DelayedDispatchMessageCreator(messageId, message.getStringProperty(MessageConstants.ENDPOINT), delay));
                return;
            }
        } catch (final NumberFormatException nfe) {
            //This is ok, no delay has been set
        } catch (final JMSException e) {
            LOG.error("", e);
        }
        sendUserMessage(messageId);
    }
}
