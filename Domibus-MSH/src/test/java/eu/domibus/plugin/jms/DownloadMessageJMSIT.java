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

package eu.domibus.plugin.jms;


import eu.domibus.AbstractIT;
import eu.domibus.plugin.BackendConnector;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;

/**
 * This JUNIT implements the Test cases Download Message-03 and Download Message-04.
 * It uses the JMS backend connector.
 *
 * @author martifp
 */
public class DownloadMessageJMSIT extends AbstractIT {

    private static boolean initialized;

    @Autowired
    private ConnectionFactory xaJmsConnectionFactory;

    @Autowired
    @Qualifier("backendJms")
    BackendConnector backendJms;

    @Before
    public void before() {

        if (!initialized) {
            insertDataset("downloadMessage.sql");
            initialized = true;
        }
    }

    /**
     * Negative test: the message is not found in the JMS queue and a specific exception is returned.
     *
     * @throws RuntimeException
     */
    @Test(expected = RuntimeException.class)
    public void testDownloadMessageInvalidId() throws RuntimeException {

        // Prepare the request to the backend
        String messageId = "invalid@e-delivery.eu";

        backendJms.deliverMessage(messageId);

        Assert.fail("DownloadMessageFault was expected but was not raised");
    }

    /**
     * Tests that a message is found in the JMS queue and pushed to the business queue.
     *
     * @throws RuntimeException
     * @throws JMSException
     */
    @Test
    @Transactional(propagation = Propagation.REQUIRED)
    public void testDownloadMessageOk() throws Exception {

        String messageId = "2809cef6-240f-4792-bec1-7cb300a34679@domibus.eu";

        javax.jms.Connection connection = xaJmsConnectionFactory.createConnection("domibus", "changeit");
        connection.start();
        pushQueueMessage(messageId, connection, JMS_NOT_QUEUE_NAME);

        backendJms.deliverMessage(messageId);

        Message message = popQueueMessage(connection, JMS_BACKEND_OUT_QUEUE_NAME);

        connection.close();

        Assert.assertNotNull(message);
    }

    /**
     * Tests that a message is found in the JMS queue and pushed to the business queue.
     *
     * @throws RuntimeException
     * @throws JMSException
     */
    @Test
    @Transactional(propagation = Propagation.REQUIRED)
    public void testDownloadMessageOkWithTimeout() throws Exception {

        String messageId = "2809cef6-240f-4792-bec1-7cb300a34679@domibus.eu";

        javax.jms.Connection connection = xaJmsConnectionFactory.createConnection("domibus", "changeit");
        connection.start();
        pushQueueMessage(messageId, connection, JMS_NOT_QUEUE_NAME);

        backendJms.deliverMessage(messageId);

        Message message = popQueueMessageWithTimeout(connection, JMS_BACKEND_OUT_QUEUE_NAME, 2000);

        connection.close();

        Assert.assertNotNull(message);
    }


}
