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

package eu.domibus.common.model.org.oasis_open.docs.ebxml_bp.ebbp_signals_2_0;

import org.w3c.dom.Element;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;


/**
 * <p>Java class for SPKIDataType complex type.
 * <p/>
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p/>
 * <pre>
 * &lt;complexType name="SPKIDataType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence maxOccurs="unbounded">
 *         &lt;element name="SPKISexp" type="{http://www.w3.org/2001/XMLSchema}base64Binary"/>
 *         &lt;any processContents='lax' namespace='##other' minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "SPKIDataType", namespace = "http://www.w3.org/2000/09/xmldsig#", propOrder = {
        "spkiSexpAndAny"
})
public class SPKIDataType {

    @XmlElementRef(name = "SPKISexp", namespace = "http://www.w3.org/2000/09/xmldsig#", type = JAXBElement.class)
    @XmlAnyElement(lax = true)
    protected List<Object> spkiSexpAndAny;

    /**
     * Gets the value of the spkiSexpAndAny property.
     * <p/>
     * <p/>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the spkiSexpAndAny property.
     * <p/>
     * <p/>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getSPKISexpAndAny().add(newItem);
     * </pre>
     * <p/>
     * <p/>
     * <p/>
     * Objects of the following type(s) are allowed in the list
     * {@link Object }
     * {@link Element }
     * {@link JAXBElement }{@code <}{@link byte[]}{@code >}
     */
    public List<Object> getSPKISexpAndAny() {
        if (this.spkiSexpAndAny == null) {
            this.spkiSexpAndAny = new ArrayList<Object>();
        }
        return this.spkiSexpAndAny;
    }

}
