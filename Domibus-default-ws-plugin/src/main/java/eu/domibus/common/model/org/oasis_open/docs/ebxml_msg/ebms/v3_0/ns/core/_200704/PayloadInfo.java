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

package eu.domibus.common.model.org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.util.HashSet;
import java.util.Set;

/**
 * Each PayloadInfo element identifies payload data associated with the message. The purpose of the
 * PayloadInfo is:
 * • to make it easier to extract particular payload parts associated with this ebMS Message,
 * • and to allow an application to determine whether it can process these payload parts, without
 * having to parse them.
 *
 * @author Christian Koch
 * @version 1.0
 * @since 3.0
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PayloadInfo", propOrder = "partInfo")

public class PayloadInfo {

    @XmlElement(name = "PartInfo", required = true)

    protected Set<PartInfo> partInfo;

    /**
     * This element occurs zero or more times. The PartInfo element is used to reference a MIME
     * attachment, an XML element within the SOAP Body, or another resource which may be obtained
     * by resolving a URL, according to the value of the href attribute
     * <p/>
     * This accessor method returns a reference to the live list, not a
     * snapshot. Therefore any modification you make to the returned list will
     * be present inside the JAXB object. This is why there is not a
     * <CODE>set</CODE> method for the partInfo property.
     * <p/>
     * <p/>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPartInfo().add(newItem);
     * </pre>
     * <p/>
     * <p/>
     * <p/>
     * Objects of the following type(s) are allowed in the list
     * {@link PartInfo }
     */

    //TODO: support payloadreference?
    public Set<PartInfo> getPartInfo() {
        if (this.partInfo == null) {
            this.partInfo = new HashSet<>();
        }
        return this.partInfo;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof PayloadInfo)) return false;

        final PayloadInfo that = (PayloadInfo) o;

        return this.partInfo.equals(that.partInfo);

    }

    @Override
    public int hashCode() {
        return this.partInfo.hashCode();
    }
}
