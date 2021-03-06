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

package eu.domibus.plugin.handler;

/**
 * @author Christian Koch, Stefan Mueller
 * @Since 3.0
 */

import eu.domibus.common.*;
import eu.domibus.common.dao.ErrorLogDao;
import eu.domibus.common.dao.MessageLogDao;
import eu.domibus.common.dao.MessagingDao;
import eu.domibus.common.dao.PModeProvider;
import eu.domibus.common.exception.EbMS3Exception;
import eu.domibus.common.exception.MessagingExceptionFactory;
import eu.domibus.common.model.MessageType;
import eu.domibus.common.model.configuration.Configuration;
import eu.domibus.common.model.configuration.LegConfiguration;
import eu.domibus.common.model.configuration.Mpc;
import eu.domibus.common.model.configuration.Party;
import eu.domibus.common.model.logging.ErrorLogEntry;
import eu.domibus.common.model.logging.MessageLogEntry;
import eu.domibus.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.MessageInfo;
import eu.domibus.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.Messaging;
import eu.domibus.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.ObjectFactory;
import eu.domibus.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.UserMessage;
import eu.domibus.common.validators.PayloadProfileValidator;
import eu.domibus.common.validators.PropertyProfileValidator;
import eu.domibus.ebms3.common.CompressionService;
import eu.domibus.ebms3.common.DispatchMessageCreator;
import eu.domibus.ebms3.common.MessageIdGenerator;
import eu.domibus.messaging.DuplicateMessageException;
import eu.domibus.messaging.MessageNotFoundException;
import eu.domibus.messaging.MessagingProcessingException;
import eu.domibus.messaging.PModeMismatchException;
import eu.domibus.plugin.Submission;
import eu.domibus.plugin.transformer.impl.SubmissionAS4Transformer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jms.core.JmsOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.jms.Queue;
import javax.persistence.NoResultException;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class DatabaseMessageHandler implements MessageSubmitter<Submission>, MessageRetriever<Submission> {

    private static final Log LOG = LogFactory.getLog(DatabaseMessageHandler.class);

    private final ObjectFactory objectFactory = new ObjectFactory();

    private final eu.domibus.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.ObjectFactory ebMS3Of = new eu.domibus.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.ObjectFactory();

    @Resource(name = "jmsTemplateDispatch")
    private JmsOperations jmsOperations;

    @Autowired
    @Qualifier("sendMessageQueue")
    private Queue sendMessageQueue;

    @Autowired
    private CompressionService compressionService;
    @Autowired
    private SubmissionAS4Transformer transformer;
    @Autowired
    private MessagingDao messagingDao;
    @Autowired
    private MessageLogDao messageLogDao;
    @Autowired
    private ErrorLogDao errorLogDao;
    @Autowired
    private PModeProvider pModeProvider;
    @Autowired
    private MessageIdGenerator messageIdGenerator;
    @Autowired
    private PayloadProfileValidator payloadProfileValidator;
    @Autowired
    private PropertyProfileValidator propertyProfileValidator;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Submission downloadMessage(final String messageId) throws MessageNotFoundException {
        DatabaseMessageHandler.LOG.info("looking for message with id: " + messageId);
        final MessageLogEntry messageLogEntry;
        final UserMessage userMessage;
        try {
            userMessage = this.messagingDao.findUserMessageByMessageId(messageId);
            messageLogEntry = this.messageLogDao.findByMessageId(messageId, MSHRole.RECEIVING);
            if (messageLogEntry == null) {
                throw new MessageNotFoundException("Message with id [" + messageId + "] was not found");
            }
        } catch (final NoResultException nrEx) {
            DatabaseMessageHandler.LOG.debug("Message with id [" + messageId + "] was not found", nrEx);
            throw new MessageNotFoundException("Message with id [" + messageId + "] was not found");
        }

        messageLogEntry.setDeleted(new Date());
        this.messageLogDao.update(messageLogEntry);
        if (0 == pModeProvider.getRetentionDownloadedByMpcURI(messageLogEntry.getMpc())) {
            messagingDao.delete(messageId);
        }
        return this.transformer.transformFromMessaging(userMessage);
    }


    @Override
    public MessageStatus getMessageStatus(final String messageId) {
        return this.messageLogDao.getMessageStatus(messageId);
    }

    @Override
    public List<? extends ErrorResult> getErrorsForMessage(final String messageId) {
        return this.errorLogDao.getErrorsForMessage(messageId);
    }


    @Override
    @Transactional
    public String submit(final Submission messageData, final String backendName) throws MessagingProcessingException {
        try {
            final UserMessage m = this.transformer.transformFromSubmission(messageData);
            final MessageInfo messageInfo = m.getMessageInfo();
            if (messageInfo == null) {
                m.setMessageInfo(this.objectFactory.createMessageInfo());
            }
            String messageId = m.getMessageInfo().getMessageId();
            if (messageId == null || m.getMessageInfo().getMessageId().trim().isEmpty()) {
                messageId = messageIdGenerator.generateMessageId();
                m.getMessageInfo().setMessageId(messageId);
            } else if (messageId.length() > 255) {
                throw new EbMS3Exception(ErrorCode.EbMS3ErrorCode.EBMS_0008, "MessageId value is too long (over 255 characters)", null, null);
            }
            String refToMessageId = m.getMessageInfo().getRefToMessageId();
            if (refToMessageId != null && refToMessageId.length() > 255) {
                throw new EbMS3Exception(ErrorCode.EbMS3ErrorCode.EBMS_0008, "RefToMessageId value is too long (over 255 characters)", refToMessageId, null);
            }
            //check if the messageId is unique. This should only fail if the ID is set from the outside
            if (!MessageStatus.NOT_FOUND.equals(messageLogDao.getMessageStatus(messageId))) {
                throw new DuplicateMessageException("Message with id:" + messageId + "already exists. Message identifiers must be unique");
            }

            final String pmodeKey;
            final Messaging message = this.ebMS3Of.createMessaging();
            message.setUserMessage(m);
            try {
                pmodeKey = this.pModeProvider.findPModeKeyForUserMessage(m);
            } catch (IllegalStateException e) { //if no pmodes are configured
                LOG.debug(e);
                throw new PModeMismatchException("PMode could not be found. Are PModes configured in the database?");
            }

            final Party from = pModeProvider.getSenderParty(pmodeKey);
            final Party to = pModeProvider.getReceiverParty(pmodeKey);
            // Verifies that the initiator and responder party are not the same.
            if (from.getName().equals(to.getName())) {
                throw new EbMS3Exception(ErrorCode.EbMS3ErrorCode.EBMS_0010, "The initiator party's name is the same as the responder party's one[" + from.getName() + "]", null, null);
            }
            // Verifies that the message is not for the current gateway.
            Configuration config = pModeProvider.getConfigurationDAO().read();
            if (config.getParty().equals(to)) {
                throw new EbMS3Exception(ErrorCode.EbMS3ErrorCode.EBMS_0010, "It is forbidden to submit a message to the sender gateway[" + to.getName() + "]", null, null);
            }

            final LegConfiguration legConfiguration = this.pModeProvider.getLegConfiguration(pmodeKey);
            final Map<Party, Mpc> mpcMap = legConfiguration.getPartyMpcMap();
            String mpc = Mpc.DEFAULT_MPC;
            if (legConfiguration.getDefaultMpc() != null) {
                mpc = legConfiguration.getDefaultMpc().getQualifiedName();
            }
            if (mpcMap != null && mpcMap.containsKey(to)) {
                mpc = mpcMap.get(to).getQualifiedName();
            }
            m.setMpc(mpc);
            this.payloadProfileValidator.validate(message, pmodeKey);
            this.propertyProfileValidator.validate(message, pmodeKey);
            int sendAttemptsMax = 1;

            if (legConfiguration.getReceptionAwareness() != null) {
                sendAttemptsMax = legConfiguration.getReceptionAwareness().getRetryCount();
            }

            try {
                final boolean compressed = this.compressionService.handleCompression(m, legConfiguration);
                LOG.debug("Compression for message with id: " + m.getMessageInfo().getMessageId() + " applied: " + compressed);
            } catch (final EbMS3Exception e) {
                this.errorLogDao.create(new ErrorLogEntry(e));
                throw e;
            }

            //We do not create MessageIds for SignalMessages, as those should never be submitted via the backend
            this.messagingDao.create(message);
            final MessageLogEntry messageLogEntry = new MessageLogEntry();
            messageLogEntry.setMessageId(m.getMessageInfo().getMessageId());
            messageLogEntry.setMshRole(MSHRole.SENDING);
            messageLogEntry.setReceived(new Date());
            messageLogEntry.setMessageType(MessageType.USER_MESSAGE);
            messageLogEntry.setSendAttempts(0);
            messageLogEntry.setSendAttemptsMax(sendAttemptsMax);
            messageLogEntry.setNextAttempt(messageLogEntry.getReceived());
            messageLogEntry.setNotificationStatus(legConfiguration.getErrorHandling().isBusinessErrorNotifyProducer() ? NotificationStatus.REQUIRED : NotificationStatus.NOT_REQUIRED);
            messageLogEntry.setMessageStatus(MessageStatus.READY_TO_SEND);
            messageLogEntry.setMpc(message.getUserMessage().getMpc());
            messageLogEntry.setEndpoint(to.getEndpoint());
            messageLogEntry.setBackend(backendName);
            this.jmsOperations.send(sendMessageQueue, new DispatchMessageCreator(messageId, to.getEndpoint()));
            messageLogEntry.setMessageStatus(MessageStatus.SEND_ENQUEUED);
            this.messageLogDao.create(messageLogEntry);

            return m.getMessageInfo().getMessageId();

        } catch (final EbMS3Exception e) {
            LOG.error("Error submitting to backendName :" + backendName, e);
            throw MessagingExceptionFactory.transform(e);
        }
    }

}
