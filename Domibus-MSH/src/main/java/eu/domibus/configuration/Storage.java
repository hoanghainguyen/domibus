package eu.domibus.configuration;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.persistence.Transient;
import javax.xml.bind.annotation.XmlTransient;
import java.io.File;
import java.util.Properties;

/**
 * Created by idragusa on 5/12/16.
 */
@Component
public class Storage {

    private static final String ATTACHMENT_STORAGE_LOCATION = "domibus.attachment.storage.location";
    private static final Log LOG = LogFactory.getLog(Storage.class);

    public static File storageDirectory = null;

    @Transient
    @XmlTransient
    @Autowired
    @Qualifier("domibusProperties")
    private Properties domibusProperties;

    @PostConstruct
    public void initFileSystemStorage() {
        final String location = domibusProperties.getProperty(ATTACHMENT_STORAGE_LOCATION);
        if (location != null && !location.isEmpty()) {
            if(storageDirectory == null) {
                storageDirectory = new File(location);
                if (!storageDirectory.exists()) {
                    throw new IllegalArgumentException("The configured storage location " + storageDirectory.getAbsolutePath() + " does not exist");
                }
            }
        } else {
            LOG.warn("No file system storage defined. This is fine for small attachments but might lead to database issues when processing large payloads");
            storageDirectory = null;
        }
    }
}
