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

import eu.domibus.common.ErrorResult;
import eu.domibus.common.MessageStatus;
import eu.domibus.messaging.MessageNotFoundException;
import eu.domibus.messaging.MessagingProcessingException;
import eu.domibus.plugin.transformer.MessageRetrievalTransformer;
import eu.domibus.plugin.transformer.MessageSubmissionTransformer;

import java.util.Collection;
import java.util.List;

/**
 * Definition of a backend integration plugin. Direct implementation of this interface is NOT RECOMMENDED.
 * Implementations should extend eu.domibus.plugin.AbstractBackendConnector instead.
 *
 * @author Christian Koch, Stefan Mueller
 */
public interface BackendConnector<U, T> {


    /**
     * @return the MessageSubmissionTransformer matching the intended backend submission DTO
     */
    MessageSubmissionTransformer<U> getMessageSubmissionTransformer();

    /**
     * @return MessageRetrievalTransformer matching the intended backend retrieval DTO
     */
    MessageRetrievalTransformer<T> getMessageRetrievalTransformer();


    /**
     * Submits a message in the native format to the Domibus MSH
     *
     * @param message the message to be sent
     * @return the Domibus-internal Id of the message. This Id is used for all further reference to the submitted message.
     * @throws MessagingProcessingException If the message was rejected by the Domibus MSH
     */
    String submit(final U message) throws MessagingProcessingException;

    /**
     * provides the message with the corresponding messageId. A target object (i.e. an instance of javax.jms.Message)
     * can be provided. This is necessary in case the DTO for transfer to the backend is constructed by a
     * factory (i.e. a JMS session).
     *
     * @param messageId the messageId of the message to retrieve
     * @param target    the target object to be filled.
     * @return the message object with the given messageId
     */
    T downloadMessage(final String messageId, final T target) throws MessageNotFoundException;

    /**
     * provides a list of messageIds which have not been downloaded yet. Only available for Mode.PULL plugins
     *
     * @return a list of messages that have not been downloaded yet.
     * @throws UnsupportedOperationException when called from a plugin using Mode.PUSH
     */
    Collection<String> listPendingMessages();

    /**
     * Returns message status {@link eu.domibus.common.MessageStatus} for message with messageid
     *
     * @param messageId id of the message the status is requested for
     * @return the message status {@link eu.domibus.common.MessageStatus}
     */
    MessageStatus getMessageStatus(final String messageId);

    /**
     * Returns List {@link java.util.List} of error logs {@link ErrorResult} for message with messageid
     *
     * @param messageId id of the message the errors are requested for
     * @return the list of error log entries {@link java.util.List< ErrorResult >}
     */
    List<ErrorResult> getErrorsForMessage(final String messageId);

    /**
     * Delivers the message with the associated messageId to the backend application. Plugins working in Mode.PUSH mode
     * MUST OVERRIDE this method. For plugins working in Mode.PULL this method never gets called. The message can be
     * retrieved by a subclass by calling super.downloadMessage(messageId, target)
     *
     * @param messageId
     */
    void deliverMessage(final String messageId);

    /**
     * The name of the plugin instance. To allow for multiple deployments of the same plugin this value should be externalized.
     *
     * @return the name of the plugin
     */
    String getName();

    /**
     * This method gets called when an incoming message associated with a Mode.PUSH plugin and an associated
     * PMode[1].errorHandling.Report.ProcessErrorNotifyConsumer=true is rejected by the MSH. The error details
     * are provided by #getErrorsForMessage
     *
     * @param messageId the Id of the failed message
     * @param ednpoint  the endpoint that tried to send the message or null if unknown
     */
    void messageReceiveFailed(String messageId, String ednpoint);

    /**
     * This method gets called when an outgoing message associated with a Mode.PUSH plugin and an associated
     * PMode[1].errorHandling.Report.ProcessErrorNotifyProducer=true has finally failed to be delivered. The error details
     * are provided by #getErrorsForMessage. This is only called for messages that have no rerty attempts left.
     *
     * @param messageId the Id of the failed message
     */
    void messageSendFailed(String messageId);

    /**
     * This method gets called when an outgoing message associated with a Mode.PUSH plugin has been successfully sent
     * to the intended receiving MSH
     *
     * @param messageId the Id of the successful message
     */
    void messageSendSuccess(String messageId);

    /**
     * Describes the behaviour of the plugin regarding message delivery to the backend application
     */
    enum Mode {
        /**
         * Messages and notifications are actively pushed to the backend application (i.e. via a JMS queue)
         */
        PUSH,
        /**
         * Messages and notifications are actively pulled by the backend application (i.e. via a webservice)
         */
        PULL;
    }
}
