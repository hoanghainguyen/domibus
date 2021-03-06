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

package eu.domibus.plugin.ws;

import eu.domibus.AbstractIT;
import eu.domibus.common.ErrorResult;
import eu.domibus.plugin.webService.MessageErrorsRequest;
import eu.domibus.plugin.webService.generated.BackendInterface;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;


/**
 * This JUNIT implements the Test cases Get Message Errors-01 and Get Message Errors-02.
 *
 * @author martifp
 */
public class GetMessageErrorsIT extends AbstractIT {

    private static boolean initialized;
    @Autowired
    BackendInterface backendWebService;

    @Before
    public void before() {
        if (!initialized) {
            insertDataset("getMessageErrorsDataset.sql");
            initialized = true;
        }
    }

    /**
     * Tests that the list of errors is not empty for a certain message.
     */
    @Test
    public void testGetMessageErrorsOk() {
        String messageId = "9008713e-1912-460c-97b3-40ec12a29f49@domibus.eu";
        MessageErrorsRequest messageErrorsRequest = createMessageErrorsRequest(messageId);
        Collection<? extends ErrorResult> response = backendWebService.getMessageErrors(messageErrorsRequest);
        Assert.assertFalse(response.isEmpty());
    }

    /**
     * Tests that the list of errors is empty for a certain message since there were no errors in the transaction.
     */
    @Test
    public void testGetEmptyMessageErrorsList() {

        String messageId = "2809cef6-240f-4792-bec1-7cb300a34679@domibus.eu";
        MessageErrorsRequest messageErrorsRequest = createMessageErrorsRequest(messageId);
        Collection<? extends ErrorResult> response = backendWebService.getMessageErrors(messageErrorsRequest);
        Assert.assertTrue(response.isEmpty());
    }

    private MessageErrorsRequest createMessageErrorsRequest(final String messageId) {

        MessageErrorsRequest messageErrorsRequest = new MessageErrorsRequest();
        messageErrorsRequest.setMessageID(messageId);

        return messageErrorsRequest;
    }
}
