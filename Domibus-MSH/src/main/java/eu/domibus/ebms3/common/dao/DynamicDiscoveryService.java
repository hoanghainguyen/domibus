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

package eu.domibus.ebms3.common.dao;

import eu.domibus.common.exception.ConfigurationException;
import no.difi.vefa.edelivery.lookup.LookupClient;
import no.difi.vefa.edelivery.lookup.LookupClientBuilder;
import no.difi.vefa.edelivery.lookup.api.LookupException;
import no.difi.vefa.edelivery.lookup.locator.BusdoxLocator;
import no.difi.vefa.edelivery.lookup.model.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Properties;

/**
 * @author Christian Koch, Stefan Mueller
 */
@Service
public class DynamicDiscoveryService {

    public static final String SMLZONE_KEY = "domibus.smlzone";
    private static final Log LOG = LogFactory.getLog(DynamicDiscoveryService.class);
    @Resource(name = "domibusProperties")
    private Properties domibusProperties;

    public Endpoint lookupInformation(final String receiverId, final String receiverIdType, final String documentId, final String processId, final String processIdType) {

        final String smlInfo = domibusProperties.getProperty(SMLZONE_KEY);
        if (smlInfo == null) {
            throw new ConfigurationException("SML Zone missing. Configure in domibus-configuration.xml");
        }

        final LookupClient smpClient = LookupClientBuilder.newInstance()
                .locator(new BusdoxLocator(smlInfo))
                .build();
        try {
            final ParticipantIdentifier participantIdentifier = new ParticipantIdentifier(receiverId, receiverIdType);
            final DocumentIdentifier documentIdentifier = new DocumentIdentifier(documentId);

            final ProcessIdentifier processIdentifier = new ProcessIdentifier(processId, processIdType);

            final ServiceMetadata sm = smpClient.getServiceMetadata(participantIdentifier, documentIdentifier);

            final Endpoint endpoint;
            endpoint = sm.getEndpoint(processIdentifier, new TransportProfile("bdxr-transport-ebms3-as4-v1p0"), TransportProfile.AS4);

            if (endpoint == null) {
                throw new ConfigurationException("Receiver does not support reception of " + documentId + " for process " + processId + " using the AS4 Protocol");
            }
            return endpoint;

        } catch (final LookupException e) {
            throw new ConfigurationException("Receiver does not support reception of " + documentId + " for process " + processId + " using the AS4 Protocol", e);
        } catch (final no.difi.vefa.edelivery.lookup.api.SecurityException e) {
            LOG.error(e);
            throw new ConfigurationException("Could not fetch metadata from SMP", e);
        }
    }
}
