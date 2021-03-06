package eu.domibus.clustering;

import eu.domibus.common.dao.PModeProvider;
import eu.domibus.wss4j.common.crypto.TrustStoreService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import java.util.Collection;


/**
 * Created by kochc01 on 02.03.2016.
 */

@Service(value = "controllerListenerService")
public class ControllerListenerService implements MessageListener {

    private static final Log LOG = LogFactory.getLog(ControllerListenerService.class);

    @Autowired
    private PModeProvider pModeProvider;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private TrustStoreService trustStoreService;

    @Override
    @Transactional
    public void onMessage(Message message) {
        String command = null;
        try {
            command = message.getStringProperty(Command.COMMAND);
        } catch (JMSException e) {
            LOG.error("Could not parse command", e);
            return;
        }
        if (command == null) {
            LOG.error("Received null command");
            return;
        }
        switch (command) {
            case Command.RELOAD_PMODE:
                pModeProvider.refresh();
                trustStoreService.refreshTrustStore();
                break;
            case Command.EVICT_CACHES:
                Collection<String> cacheNames = cacheManager.getCacheNames();
                for (String cacheName : cacheNames) {
                    cacheManager.getCache(cacheName).clear();
                }
                break;
            case Command.RELOAD_TRUSTSTORE:
                trustStoreService.refreshTrustStore();
                break;
            default:
                LOG.error("Unknown command received: " + command);
        }
    }
}
