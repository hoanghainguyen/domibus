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

package eu.domibus.wss4j.common.crypto;

import eu.domibus.clustering.Command;
import eu.domibus.common.exception.ConfigurationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.MessageCreator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Properties;

/**
 * @author Christian Koch, Stefan Mueller, Federico Martini
 */
@Service(value = "trustStoreService")
@Scope(value = "singleton")
public class TrustStoreService {

    private static final Log LOG = LogFactory.getLog(TrustStoreService.class);

    @Resource(name = "trustStoreProperties")
    private Properties trustStoreProperties;

    private KeyStore trustStore;

    @Qualifier("jmsTemplateCommand")
    @Autowired
    private JmsOperations jmsOperations;

    private Merlin crypto;

    public synchronized KeyStore getTrustStore() {
        if (trustStore == null) {
            try {
                initTrustStore();
            } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
                LOG.error("Error while initializing trustStore", e);
            }
        }
        return trustStore;
    }

    /**
     * Adds the certificate to the trustStore
     *
     * @param certificate the certificate to add
     * @param alias       the certifictae alias
     * @param overwrite   if {@value true} existing entries will be replaced
     * @return {@value true} if added, else {@value false}
     */
    public boolean addCertificate(final X509Certificate certificate, final String alias, final boolean overwrite) {
        boolean containsAlias = false;
        try {
            containsAlias = getTrustStore().containsAlias(alias);
        } catch (final KeyStoreException e) {
            throw new RuntimeException("This should never happen", e);
        }
        if (containsAlias && !overwrite) {
            return false;
        }
        try {
            if (containsAlias) {
                getTrustStore().deleteEntry(alias);
            }
            getTrustStore().setCertificateEntry(alias, certificate);

            return true;
        } catch (final KeyStoreException e) {
            throw new ConfigurationException(e);
        }
    }

    private void initTrustStore() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {

        final KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        String trustStoreFilename = trustStoreProperties.getProperty("org.apache.ws.security.crypto.merlin.trustStore.file");
        String trustStorePassword = trustStoreProperties.getProperty("org.apache.ws.security.crypto.merlin.trustStore.password");
        ks.load(new FileInputStream(trustStoreFilename), trustStorePassword.toCharArray());
        trustStore = ks;
        LOG.info("TrustStore successfully loaded");
    }

    public void refreshTrustStore() {
        try {
            initTrustStore();
            // After startup and before the first message is sent the crypto is not initialized yet, so there is no need to refresh the trustStore in it!
            if (crypto != null) {
                crypto.setTrustStore(trustStore);
            }
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException ex) {
            if (LOG.isDebugEnabled()) {
                LOG.warn("Failed to reload certificates due to: " + ex);
            } else {
                LOG.warn("Failed to reload certificates due to: " + ex.getCause());
            }
        }
    }

    // Saves the reference to the Merlin object in order to be able to refresh it afterwards whenever is needed!
    void setCrypto(Merlin crypto) {
        this.crypto = crypto;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateTrustStore() {
        // Sends a message into the topic queue in order to refresh all the singleton instances of the TrustStoreService.
        jmsOperations.send(new ReloadTrustStoreMessageCreator());
    }

    class ReloadTrustStoreMessageCreator implements MessageCreator {
        @Override
        public Message createMessage(Session session) throws JMSException {
            Message m = session.createMessage();
            m.setStringProperty(Command.COMMAND, Command.RELOAD_TRUSTSTORE);
            return m;
        }
    }


}
