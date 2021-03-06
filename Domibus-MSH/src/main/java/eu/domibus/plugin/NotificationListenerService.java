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

package eu.domibus.plugin;

import eu.domibus.common.NotificationType;
import eu.domibus.common.exception.ConfigurationException;
import eu.domibus.messaging.MessageConstants;
import eu.domibus.messaging.MessageNotFoundException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jms.annotation.JmsListenerConfigurer;
import org.springframework.jms.config.JmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerEndpointRegistrar;
import org.springframework.jms.config.SimpleJmsListenerEndpoint;
import org.springframework.jms.core.BrowserCallback;
import org.springframework.jms.core.JmsOperations;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.jms.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;

/**
 * @author Christian Koch, Stefan Mueller
 */

public class NotificationListenerService implements MessageListener, JmsListenerConfigurer, MessageLister, eu.domibus.plugin.NotificationListener {

    private static final Log LOG = LogFactory.getLog(NotificationListenerService.class);

    @Autowired
    @Qualifier(value = "jmsTemplateNotify")
    private JmsOperations jmsOperations;

    @Autowired
    @Qualifier("internalJmsListenerContainerFactory")
    private JmsListenerContainerFactory jmsListenerContainerFactory;

    private Queue backendNotificationQueue;
    private BackendConnector.Mode mode;
    private BackendConnector backendConnector;

    public NotificationListenerService(final Queue queue, final BackendConnector.Mode mode) {
        backendNotificationQueue = queue;
        this.mode = mode;
    }

    public void setBackendConnector(final BackendConnector backendConnector) {
        this.backendConnector = backendConnector;
    }

    @Transactional
    public void onMessage(final Message message) {
        try {
            final String messageId = message.getStringProperty(MessageConstants.MESSAGE_ID);
            final NotificationType notificationType = NotificationType.valueOf(message.getStringProperty(MessageConstants.NOTIFICATION_TYPE));
            switch (notificationType) {
                case MESSAGE_RECEIVED:
                    backendConnector.deliverMessage(messageId);
                    break;
                case MESSAGE_SEND_FAILURE:
                    backendConnector.messageSendFailed(messageId);
                    break;
                case MESSAGE_SEND_SUCCESS:
                    backendConnector.messageSendSuccess(messageId);
                    break;
                case MESSAGE_RECEIVED_FAILURE:
                    backendConnector.messageReceiveFailed(messageId, message.getStringProperty(MessageConstants.ENDPOINT));

            }


        } catch (final JMSException e) {
            LOG.error("", e);
        }
    }


    public final Collection<String> listPendingMessages() {
        return getQueueElements(NotificationType.MESSAGE_RECEIVED);
    }

    public final Collection<String> listSendFailureMessages() {
        return getQueueElements(NotificationType.MESSAGE_SEND_FAILURE);
    }

    public final Collection<String> listReceiveFailureMessages() {
        return getQueueElements(NotificationType.MESSAGE_RECEIVED_FAILURE);
    }

    private Collection<String> getQueueElements(final NotificationType notificationType) {
        if (this.mode == BackendConnector.Mode.PUSH) {
            throw new UnsupportedOperationException("this method is only available for clients using Mode.PULL");
        }
        final Collection<String> result = browseQueue(notificationType);
        return result;
    }

    private Collection<String> browseQueue(final NotificationType notificationType) {
        final Collection<String> result = new ArrayList<>();
        jmsOperations.browse(backendNotificationQueue, new BrowserCallback<Void>() {
            @Override
            public Void doInJms(final Session session, final QueueBrowser browser) throws JMSException {
                final Enumeration browserEnumeration = browser.getEnumeration();
                while (browserEnumeration.hasMoreElements()) {
                    final Message message = (Message) browserEnumeration.nextElement();
                    if (notificationType.name().equals(message.getStringProperty(MessageConstants.NOTIFICATION_TYPE))) {
                        result.add(message.getStringProperty(MessageConstants.MESSAGE_ID));
                    }
                }
                return null;
            }
        });
        return result;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void removeFromPending(final String messageId) throws MessageNotFoundException {
        if (this.mode == BackendConnector.Mode.PUSH) {
            LOG.debug("No messages will be removed because this a PUSH consumer");
            return;
        }
        final String selector = MessageConstants.MESSAGE_ID + "= '" + messageId + "'";

        final Boolean received = jmsOperations.browseSelected(backendNotificationQueue, selector, new BrowserCallback<Boolean>() {
            @Override
            public Boolean doInJms(final Session session, final QueueBrowser browser) throws JMSException {
                return browser.getEnumeration().hasMoreElements();
            }
        });
        if (!received) {
            throw new MessageNotFoundException("No message with ID " + messageId + " pending for download");
        }
        jmsOperations.receiveSelected(getBackendNotificationQueue(), selector);
    }


    @Override
    public void configureJmsListeners(final JmsListenerEndpointRegistrar registrar) {

        if (this.mode == BackendConnector.Mode.PUSH) {
            final SimpleJmsListenerEndpoint endpoint = new SimpleJmsListenerEndpoint();
            endpoint.setId(getBackendName());
            final Queue pushQueue = backendNotificationQueue;
            if (pushQueue == null) {
                throw new ConfigurationException("No notification queue found for " + getBackendName());
            } else {
                try {
                    endpoint.setDestination(getQueueName(pushQueue));
                } catch (final JMSException e) {
                    LOG.error("Problem with predefined queue.", e);
                }
            }
            endpoint.setMessageListener(this);
            registrar.registerEndpoint(endpoint, jmsListenerContainerFactory);
        }
    }

    @Override
    public String getBackendName() {
        return backendConnector.getName();
    }

    @Override
    public Queue getBackendNotificationQueue() {
        return backendNotificationQueue;
    }

    protected String getQueueName(Queue queue) throws JMSException {
        return queue.getQueueName();
    }
}
