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

package eu.domibus.ebms3.receiver;

import eu.domibus.common.NotificationType;
import eu.domibus.common.dao.MessageLogDao;
import eu.domibus.common.exception.ConfigurationException;
import eu.domibus.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.UserMessage;
import eu.domibus.messaging.NotifyMessageCreator;
import eu.domibus.messaging.ReceiveFailedMessageCreator;
import eu.domibus.plugin.NotificationListener;
import eu.domibus.plugin.routing.*;
import eu.domibus.plugin.routing.dao.BackendFilterDao;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.jms.core.JmsOperations;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.jms.Queue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Christian Koch, Stefan Mueller
 */
@Service("backendNotificationService")
public class BackendNotificationService {

    private static final Log LOG = LogFactory.getLog(BackendNotificationService.class);

    @Qualifier("jmsTemplateNotify")
    @Autowired
    private JmsOperations jmsOperations;

    @Autowired
    private BackendFilterDao backendFilterDao;

    @Autowired
    private RoutingService routingService;

    @Autowired
    private MessageLogDao messageLogDao;


    private List<NotificationListener> notificationListenerServices;

    @Resource(name = "routingCriteriaFactories")
    private List<CriteriaFactory> routingCriteriaFactories;

    @Autowired
    @Qualifier("unknownReceiverQueue")
    private Queue unknownReceiverQueue;

    @Autowired
    private ApplicationContext applicationContext;

    private Map<String, IRoutingCriteria> criteriaMap;


    @PostConstruct
    public void init() {
        Map notificationListenerBeanMap = applicationContext.getBeansOfType(NotificationListener.class);
        if(notificationListenerBeanMap.isEmpty()) {
            throw new ConfigurationException("No Plugin available! Please configure at least one backend plugin in order to run domibus");
        } else {
           notificationListenerServices = new ArrayList<NotificationListener>(notificationListenerBeanMap.values());
        }

        criteriaMap = new HashMap<>();
        for (final CriteriaFactory routingCriteriaFactory : routingCriteriaFactories) {
            criteriaMap.put(routingCriteriaFactory.getName(), routingCriteriaFactory.getInstance());
        }
    }

    public void notifyOfIncoming(final UserMessage userMessage, NotificationType notificationType) {
        List<BackendFilter> backendFilter = backendFilterDao.findAll();
        if (backendFilter.isEmpty()) { // There is no saved backendfilter configuration. Most likely the backends have not been configured yet
            backendFilter = routingService.getBackendFilters();
            if (backendFilter.isEmpty()) {
                LOG.error("There are no backend plugins deployed on this server");
            }
            if (backendFilter.size() > 1) { //There is more than one unconfigured backend available. For security reasons we cannot send the message just to the first one
                LOG.error("There are multiple unconfigured backend plugins available. Please set up the configuration using the \"Message filter\" pannel of the administrative GUI.");
                backendFilter.clear(); // empty the list so its handled in the desired way.
            }
            //If there is only one backend deployed we send it to that as this is most likely the intent
        }
        for (final BackendFilter filter : backendFilter) {
            boolean matches = true;
            for (final RoutingCriteria routingCriteria : filter.getRoutingCriterias()) {
                final IRoutingCriteria criteria = criteriaMap.get(routingCriteria.getName());
                matches = criteria.matches(userMessage, routingCriteria.getExpression());
                if (!matches) {
                    break;
                }
            }
            if (matches) {
                LOG.info("Notify backend " + filter.getBackendName() + " of messageId " + userMessage.getMessageInfo().getMessageId());
                notify(userMessage.getMessageInfo().getMessageId(), filter.getBackendName(), notificationType);
                return;
            }
        }
        LOG.error("No backend responsible for message [" + userMessage.getMessageInfo().getMessageId() + "] found. Sending notification to [" + unknownReceiverQueue + "]");
        jmsOperations.send(unknownReceiverQueue, new NotifyMessageCreator(userMessage.getMessageInfo().getMessageId(), NotificationType.MESSAGE_RECEIVED));
    }

    private void notify(final String messageId, final String backendName, final NotificationType notificationType) {
        for (final NotificationListener notificationListenerService : notificationListenerServices) {
            if (notificationListenerService.getBackendName().equals(backendName)) {
                jmsOperations.send(notificationListenerService.getBackendNotificationQueue(), new NotifyMessageCreator(messageId, notificationType));
            }
        }
    }

    public void notifyOfSendFailure(final String messageId) {
        final String backendName = messageLogDao.findBackendForMessageId(messageId);
        notify(messageId, backendName, NotificationType.MESSAGE_SEND_FAILURE);

    }

    public void notifyOfSendSuccess(final String messageId) {
        final String backendName = messageLogDao.findBackendForMessageId(messageId);
        notify(messageId, backendName, NotificationType.MESSAGE_SEND_SUCCESS);
    }

    public void notifyOfReceiveFailure(final String messageId, String endpoint) {
        final String backendName = messageLogDao.findBackendForMessageId(messageId);
        for (final NotificationListener notificationListenerService : notificationListenerServices) {
            if (notificationListenerService.getBackendName().equals(backendName)) {
                jmsOperations.send(notificationListenerService.getBackendNotificationQueue(), new ReceiveFailedMessageCreator(messageId, endpoint));
            }
        }
    }

    public List<NotificationListener> getNotificationListenerServices() {
        return notificationListenerServices;
    }
}
