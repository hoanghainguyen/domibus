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
import eu.domibus.messaging.PModeMismatchException;
import eu.domibus.plugin.exception.TransformationException;
import eu.domibus.plugin.handler.MessageRetriever;
import eu.domibus.plugin.handler.MessageSubmitter;
import eu.domibus.plugin.transformer.MessageRetrievalTransformer;
import eu.domibus.plugin.transformer.MessageSubmissionTransformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Base class for implementing plugins
 *
 * @author Christian Koch, Stefan Mueller
 */
public abstract class AbstractBackendConnector<U, T> implements BackendConnector<U, T> {

    private final String name;
    @Autowired
    protected MessageRetriever<Submission> messageRetriever;
    @Autowired
    protected MessageSubmitter<Submission> messageSubmitter;
    private MessageLister lister;

    public AbstractBackendConnector(final String name) {
        this.name = name;
    }

    public void setLister(final MessageLister lister) {
        this.lister = lister;
    }

    public abstract MessageSubmissionTransformer<U> getMessageSubmissionTransformer();

    public abstract MessageRetrievalTransformer<T> getMessageRetrievalTransformer();

    @Override
    @Transactional(noRollbackFor = {IllegalArgumentException.class, IllegalStateException.class})
    public final String submit(final U message) throws MessagingProcessingException {
        try {
            return this.messageSubmitter.submit(this.getMessageSubmissionTransformer().transformToSubmission(message), this.getName());
        } catch (IllegalArgumentException iaEx) {
            throw new TransformationException(iaEx);
        } catch (IllegalStateException ise) {
            throw new PModeMismatchException(ise);
        }
    }


    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public final T downloadMessage(final String messageId, final T target) throws MessageNotFoundException {
        lister.removeFromPending(messageId);
        return this.getMessageRetrievalTransformer().transformFromSubmission(this.messageRetriever.downloadMessage(messageId), target);
    }


    @Override
    public final Collection<String> listPendingMessages() {
        return lister.listPendingMessages();
    }


    @Override
    public final MessageStatus getMessageStatus(final String messageId) {
        return this.messageRetriever.getMessageStatus(messageId);
    }


    @Override
    public final List<ErrorResult> getErrorsForMessage(final String messageId) {
        return new ArrayList<>(this.messageRetriever.getErrorsForMessage(messageId));
    }


    @Override
    public void deliverMessage(final String messageId) {
        throw new UnsupportedOperationException("Plugins using " + Mode.PUSH.name() + " must implement this method");
    }

    @Override
    public void messageSendSuccess(String messageId) {
        throw new UnsupportedOperationException("Plugins using " + Mode.PUSH.name() + " must implement this method");
    }

    @Override
    public String getName() {
        return name;
    }
}
