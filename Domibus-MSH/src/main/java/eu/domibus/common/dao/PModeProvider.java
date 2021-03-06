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

package eu.domibus.common.dao;

import eu.domibus.clustering.Command;
import eu.domibus.common.ErrorCode;
import eu.domibus.common.exception.EbMS3Exception;
import eu.domibus.common.model.configuration.*;
import eu.domibus.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.AgreementRef;
import eu.domibus.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.PartyId;
import eu.domibus.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.UserMessage;
import eu.domibus.messaging.XmlProcessingException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.MessageCreator;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.List;

/**
 * @author Christian Koch, Stefan Mueller
 */
public abstract class PModeProvider {

    private static final Log LOG = LogFactory.getLog(PModeProvider.class);

    private static final String EBMS3_TEST_ACTION = "http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/test";
    private static final String EBMS3_TEST_SERVICE = "http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/service"; //TODO: move to appropriate classes

    public static final String SCHEMAS_DIR = "/schemas/";

    protected static final String OPTIONAL_AND_EMPTY = "OAE";

    @Autowired
    protected ConfigurationDAO configurationDAO;

    @PersistenceContext
    protected EntityManager entityManager;

    @Autowired()
    @Qualifier("jaxbContextConfig")
    private JAXBContext jaxbContext;

    @Qualifier("jmsTemplateCommand")
    @Autowired
    private JmsOperations jmsOperations;

    public abstract void init();

    public abstract void refresh();

    @Transactional(propagation = Propagation.REQUIRED)
    public void updatePModes(final byte[] bytes) throws XmlProcessingException {
        try {
            /*SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            String filePath = System.getProperty("domibus.config.location") + SCHEMAS_DIR;
            Schema schema = sf.newSchema(new File(filePath + "domibus-pmode.xsd"));*/
            Unmarshaller unmarshaller = this.jaxbContext.createUnmarshaller();
            /*unmarshaller.setSchema(schema); see JIRA 807
            unmarshaller.setEventHandler(new XmlValidationEventHandler());*/
            final Configuration configuration = (Configuration) unmarshaller.unmarshal(new ByteArrayInputStream(bytes));
            if (configuration != null) {
                this.configurationDAO.updateConfiguration(configuration);
                LOG.info("Configuration successfully updated");
            }
            //} catch (JAXBException | SAXException xmlEx) {
        } catch (JAXBException xmlEx) {
            if (LOG.isDebugEnabled()) {
                LOG.error("Xml not correctly processed: ", xmlEx);
            } else {
                LOG.error("Xml not correctly processed: ", xmlEx.getCause());
            }
            throw new XmlProcessingException(xmlEx.getCause().getMessage());
        }
        // Sends a message into the topic queue in order to refresh all the singleton instances of the PModeProvider.
        jmsOperations.send(new ReloadPmodeMessageCreator());
    }


    @Transactional(propagation = Propagation.REQUIRED, noRollbackFor = IllegalStateException.class)
    public String findPModeKeyForUserMessage(final UserMessage userMessage) throws EbMS3Exception {

        final String agreementName;
        final String senderParty;
        final String receiverParty;
        final String service;
        final String action;
        final String leg;

        try {
            agreementName = this.findAgreement(userMessage.getCollaborationInfo().getAgreementRef());
            senderParty = this.findPartyName(userMessage.getPartyInfo().getFrom().getPartyId());
            receiverParty = this.findPartyName(userMessage.getPartyInfo().getTo().getPartyId());
            service = this.findServiceName(userMessage.getCollaborationInfo().getService());
            action = this.findActionName(userMessage.getCollaborationInfo().getAction());
            leg = this.findLegName(agreementName, senderParty, receiverParty, service, action);

            if ((action.equals(PModeProvider.EBMS3_TEST_ACTION) && (!service.equals(PModeProvider.EBMS3_TEST_SERVICE)))) {
                throw new EbMS3Exception(ErrorCode.EbMS3ErrorCode.EBMS_0004, "ebMS3 Test Service: " + PModeProvider.EBMS3_TEST_SERVICE + " and ebMS3 Test Action: " + PModeProvider.EBMS3_TEST_ACTION + " can only be used together [CORE] 5.2.2.9", userMessage.getMessageInfo().getMessageId(), null);
            }

            return senderParty + ":" + receiverParty + ":" + service + ":" + action + ":" + agreementName + ":" + leg;

        } catch (final EbMS3Exception e) {
            e.setRefToMessageId(userMessage.getMessageInfo().getMessageId());
            throw e;
        }
    }


    class ReloadPmodeMessageCreator implements MessageCreator {
        @Override
        public Message createMessage(Session session) throws JMSException {
            Message m = session.createMessage();
            m.setStringProperty(Command.COMMAND, Command.RELOAD_PMODE);
            return m;
        }
    }

    public abstract List<String> getMpcList();

    public abstract List<String> getMpcURIList();

    protected abstract String findLegName(String agreementRef, String senderParty, String receiverParty, String service, String action) throws EbMS3Exception;

    protected abstract String findActionName(String action) throws EbMS3Exception;

    protected abstract String findServiceName(eu.domibus.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.Service service) throws EbMS3Exception;

    protected abstract String findPartyName(Collection<PartyId> partyId) throws EbMS3Exception;

    protected abstract String findAgreement(AgreementRef agreementRef) throws EbMS3Exception;

    public abstract Party getSenderParty(String pModeKey);

    public abstract Party getReceiverParty(String pModeKey);

    public abstract Service getService(String pModeKey);

    public abstract Action getAction(String pModeKey);

    public abstract Agreement getAgreement(String pModeKey);

    public abstract LegConfiguration getLegConfiguration(String pModeKey);

    public abstract boolean isMpcExistant(String mpc);

    public abstract int getRetentionDownloadedByMpcName(String mpcName);

    public abstract int getRetentionDownloadedByMpcURI(final String mpcURI);

    public abstract int getRetentionUndownloadedByMpcName(String mpcName);

    public abstract int getRetentionUndownloadedByMpcURI(final String mpcURI);

    public ConfigurationDAO getConfigurationDAO() {
        return configurationDAO;
    }

    public void setConfigurationDAO(final ConfigurationDAO configurationDAO) {
        this.configurationDAO = configurationDAO;
    }

    protected String getSenderPartyNameFromPModeKey(final String pModeKey) {
        return pModeKey.split(":")[0];
    }

    protected String getReceiverPartyNameFromPModeKey(final String pModeKey) {
        return pModeKey.split(":")[1];
    }

    protected String getServiceNameFromPModeKey(final String pModeKey) {
        return pModeKey.split(":")[2];
    }

    protected String getActionNameFromPModeKey(final String pModeKey) {
        return pModeKey.split(":")[3];
    }

    protected String getAgreementRefNameFromPModeKey(final String pModeKey) {
        return pModeKey.split(":")[4];
    }

    protected String getLegConfigurationNameFromPModeKey(final String pModeKey) {
        return pModeKey.split(":")[5];
    }

}
