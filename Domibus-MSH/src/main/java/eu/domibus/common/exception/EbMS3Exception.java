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

package eu.domibus.common.exception;

import eu.domibus.common.ErrorCode;
import eu.domibus.common.MSHRole;
import eu.domibus.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.Description;
import eu.domibus.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.Error;
import org.apache.commons.lang.StringUtils;

import javax.xml.ws.WebFault;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * @author Christian Koch, Stefan Mueller
 *         This is the implementation of a ebMS3 Error Message
 */
@WebFault(name = "ebMS3Error")
public class EbMS3Exception extends Exception {

    /**
     * Default locale for error messages
     */
    public static final Locale DEFAULT_LOCALE = Locale.ENGLISH;
    /**
     * Default ResourceBundle name for error messages
     */
    public static final String RESOURCE_BUNDLE_NAME = "messages.ebms3.codes.MessagesBundle";
    /**
     * Default ResourceBundle for error messages
     */
    public static final ResourceBundle DEFAULT_MESSAGES = ResourceBundle.getBundle(EbMS3Exception.RESOURCE_BUNDLE_NAME, EbMS3Exception.DEFAULT_LOCALE);

    private final ErrorCode.EbMS3ErrorCode errorCode;
    /**
     * "This OPTIONAL attribute provides a short description of the error that can be reported in a log, in order to facilitate readability."
     * (OASIS ebXML Messaging Services Version 3.0: Part 1, Core Features, 1 October 2007)
     */
    private String errorDetail;
    private String refToMessageId;
    private MSHRole mshRole;
    private boolean recoverable = true;
    private String signalMessageId;

    /**
     * "This OPTIONAL element provides a narrative description of the error in the language
     * defined by the xml:lang attribute. The content of this element is left to
     * implementation-specific decisions."
     * (OASIS ebXML Messaging Services Version 3.0: Part 1, Core Features, 1 October 2007)
     */
    //private final String description;
    public EbMS3Exception(final ErrorCode.EbMS3ErrorCode errorCode, final String errorDetail, final String refToMessageId, final Throwable cause) {
        super(cause);
        this.errorCode = errorCode;
        this.errorDetail = errorDetail;
        this.refToMessageId = refToMessageId;
    }

    public boolean isRecoverable() {
        return this.recoverable;
    }

    public void setRecoverable(final boolean recoverable) {
        this.recoverable = recoverable;
    }

    public Description getDescription() {
        return this.getDescription(EbMS3Exception.DEFAULT_MESSAGES);
    }

    public Description getDescription(final ResourceBundle bundle) {
        final Description description = new Description();
        description.setValue(bundle.getString(this.errorCode.getCode().name()));
        description.setLang(bundle.getLocale().getLanguage());

        return description;
    }

    public Description getDescription(final Locale locale) {
        return this.getDescription(ResourceBundle.getBundle(EbMS3Exception.RESOURCE_BUNDLE_NAME, locale));
    }

    public String getErrorDetail() {
        return StringUtils.abbreviate(this.errorDetail, 255);
    }

    public void setErrorDetail(final String errorDetail) {
        this.errorDetail = errorDetail;
    }

    public String getOrigin() {
        return this.errorCode.getCode().getOrigin();
    }

    public String getErrorCode() {
        return this.errorCode.getCode().getErrorCode().name();
    }

    public ErrorCode getErrorCodeObject() {
        return this.errorCode.getCode().getErrorCode();
    }

    public String getShortDescription() {
        return this.errorCode.getShortDescription();
    }

    public String getSeverity() {
        return this.errorCode.getSeverity();
    }

    public String getCategory() {
        return this.errorCode.getCategory().name();
    }

    public Error getFaultInfo() {

        final Error ebMS3Error = new Error();

        ebMS3Error.setOrigin(this.errorCode.getCode().getOrigin());
        ebMS3Error.setErrorCode(this.errorCode.getCode().getErrorCode().getErrorCodeName());
        ebMS3Error.setSeverity(this.errorCode.getSeverity());
        ebMS3Error.setErrorDetail((this.errorDetail != null ? getErrorDetail() : ""));
        ebMS3Error.setCategory(this.errorCode.getCategory().name());
        ebMS3Error.setRefToMessageInError(this.refToMessageId);
        ebMS3Error.setShortDescription(this.getShortDescription());
        ebMS3Error.setDescription(this.getDescription());


        return ebMS3Error;
    }

    public String getRefToMessageId() {
        return this.refToMessageId;
    }

    public void setRefToMessageId(final String refToMessageId) {
        this.refToMessageId = refToMessageId;
    }

    public MSHRole getMshRole() {
        return this.mshRole;
    }

    public void setMshRole(final MSHRole mshRole) {
        this.mshRole = mshRole;
    }

    public String getSignalMessageId() {
        return this.signalMessageId;
    }

    public void setSignalMessageId(final String signalMessageId) {
        this.signalMessageId = signalMessageId;
    }


}

