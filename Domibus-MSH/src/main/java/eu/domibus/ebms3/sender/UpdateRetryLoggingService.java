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

import eu.domibus.common.MSHRole;
import eu.domibus.common.MessageStatus;
import eu.domibus.common.NotificationStatus;
import eu.domibus.common.dao.MessageLogDao;
import eu.domibus.common.dao.MessagingDao;
import eu.domibus.common.model.configuration.LegConfiguration;
import eu.domibus.common.model.logging.MessageLogEntry;
import eu.domibus.ebms3.receiver.BackendNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UpdateRetryLoggingService {

    @Autowired
    private BackendNotificationService backendNotificationService;

    @Autowired
    private MessageLogDao messageLogDao;

    @Autowired
    private MessagingDao messagingDao;

    /**
     * This method is responsible for the handling of retries for a given message
     *
     * @param messageId        id of the message that needs to be retried
     * @param legConfiguration processing information for the message
     */
    public void updateRetryLogging(final String messageId, final LegConfiguration legConfiguration) {
        final MessageLogEntry messageLogEntry = this.messageLogDao.findByMessageId(messageId, MSHRole.SENDING);
        //messageLogEntry.setMessageStatus(MessageStatus.SEND_ATTEMPT_FAILED); //This is not stored in the database
        if (messageLogEntry.getSendAttempts() < messageLogEntry.getSendAttemptsMax() //check that there are attempts left
                && (messageLogEntry.getReceived().getTime() + legConfiguration.getReceptionAwareness().getRetryTimeout() * 60000) > System.currentTimeMillis()) {// chek that there is time left
            messageLogEntry.setSendAttempts(messageLogEntry.getSendAttempts() + 1);
            if (legConfiguration.getReceptionAwareness() != null) {
                messageLogEntry.setNextAttempt(legConfiguration.getReceptionAwareness().getStrategy().getAlgorithm().compute(messageLogEntry.getNextAttempt(), messageLogEntry.getSendAttemptsMax(), legConfiguration.getReceptionAwareness().getRetryTimeout()));
                messageLogEntry.setMessageStatus(MessageStatus.WAITING_FOR_RETRY);
                messageLogDao.update(messageLogEntry);
            }

        } else { // mark message as ultimately failed if max retries reached
            if (NotificationStatus.REQUIRED.equals(messageLogEntry.getNotificationStatus())) {
                backendNotificationService.notifyOfSendFailure(messageId);
                messagingDao.delete(messageId, MessageStatus.SEND_FAILURE, NotificationStatus.NOTIFIED);
            } else {
                messagingDao.delete(messageId, MessageStatus.SEND_FAILURE);
            }
        }
    }
}
