package eu.domibus;

import eu.domibus.common.dao.PModeProvider;
import eu.domibus.common.model.configuration.*;
import eu.domibus.messaging.XmlProcessingException;
import eu.domibus.web.controller.AdminGUIController;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;


/**
 * This JUNIT implements the Test cases: UploadPMode - 01, UploadPMode - 02, UploadPMode - 03.
 *
 * @author martifp
 */
@Transactional
public class UploadPModeIT extends AbstractIT {

    private static final String BLUE_2_RED_SERVICE1_ACTION1_PMODE_KEY = "blue_gw:red_gw:testService1:tc1Action:agreement1110:pushTestcase1tc1Action";

    private static final String PREFIX_MPC_URI = "http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/";

    private static boolean initialized;
    @Autowired
    AdminGUIController adminGui;
    @Autowired
    PModeProvider pModeDao;
    @Autowired()
    @Qualifier("jaxbContextConfig")
    private JAXBContext jaxbContext;

    @Before
    public void before() {
        if (!initialized) {
            initialized = true;
        }
    }


    /**
     * Tests that the PMODE is correctly saved in the DB.
     *
     * @throws IOException
     * @throws XmlProcessingException
     */
    @Test
    public void testSavePModeOk() throws IOException, XmlProcessingException {

        try {
            File pModeFile = new File("src/test/resources/SamplePModes/domibus-configuration-blue_gw.xml");
            FileInputStream fis = new FileInputStream(pModeFile);
            //MultipartFile pModeContent = new MockMultipartFile("domibus-configuration-blue_gw", pModeFile.getName(), "text/xml", IOUtils.toByteArray(fis));
            //String response = adminGui.uploadFileHandler(pModeContent);
            pModeDao.updatePModes(IOUtils.toByteArray(fis));
            //Assert.assertEquals("You successfully uploaded the PMode file.", response);
        } catch (IOException ioEx) {
            System.out.println("File reading error: " + ioEx.getMessage());
            throw ioEx;
        } catch (XmlProcessingException xpEx) {
            System.out.println("XML error: " + xpEx.getMessage());
            throw xpEx;
        }
    }

    /**
     * Tests that the PMode is not saved in the DB because there is a wrong configuration.
     */
    @Test
    public void testSavePModeNOk() throws IOException {


        try {
            File wrongPmode = new File("src/test/resources/SamplePModes/wrong-domibus-configuration.xml");
            FileInputStream fis = new FileInputStream(wrongPmode);
            MultipartFile pModeContent = new MockMultipartFile("wrong-domibus-configuration", wrongPmode.getName(), "text/xml", IOUtils.toByteArray(fis));
            String response = adminGui.uploadFileHandler(pModeContent);
            Assert.assertTrue(response.contains("Failed to upload the PMode file due to"));
        } catch (IOException ioEx) {
            System.out.println("Error: " + ioEx.getMessage());
            throw ioEx;
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    private Configuration testUpdatePModes(final byte[] bytes) throws JAXBException {
        final Configuration configuration = (Configuration) this.jaxbContext.createUnmarshaller().unmarshal(new ByteArrayInputStream(bytes));
        pModeDao.getConfigurationDAO().updateConfiguration(configuration);
        return configuration;
    }

    /**
     * Tests that a subset of the PMODE file content (given a fixed pModeKey) is correctly stored in the DB.
     *
     * PMODE Key  = Initiator Party: Responder Party: Service name: Action name: Agreement: Test case name
     */
    @Test
    public void testVerifyPModeContent() throws IOException, JAXBException {

        try {
            File pModeFile = new File("src/test/resources/SamplePModes/domibus-configuration-blue_gw.xml");
            FileInputStream fis = new FileInputStream(pModeFile);
            Configuration configuration = testUpdatePModes(IOUtils.toByteArray(fis));
            // Starts to check that the content of the XML file has actually been saved!
            Party receiverParty = pModeDao.getReceiverParty(BLUE_2_RED_SERVICE1_ACTION1_PMODE_KEY);
            Party senderParty = pModeDao.getSenderParty(BLUE_2_RED_SERVICE1_ACTION1_PMODE_KEY);
            List<String> parties = new ArrayList<>();
            parties.add(receiverParty.getName());
            parties.add(senderParty.getName());

            boolean partyFound = false;
            Iterator<Party> partyIterator = configuration.getBusinessProcesses().getParties().iterator();
            while (!partyFound && partyIterator.hasNext()) {
                Party party = partyIterator.next();
                partyFound = parties.contains(party.getName());
            }
            Assert.assertTrue(partyFound);

            Action savedAction = pModeDao.getAction(BLUE_2_RED_SERVICE1_ACTION1_PMODE_KEY);
            boolean actionFound = false;
            Iterator<Action> actionIterator = configuration.getBusinessProcesses().getActions().iterator();
            while (!actionFound && actionIterator.hasNext()) {
                Action action = actionIterator.next();
                if (action.getName().equals(savedAction.getName())) {
                    actionFound = true;
                }
            }
            Assert.assertTrue(actionFound);

            Service savedService = pModeDao.getService(BLUE_2_RED_SERVICE1_ACTION1_PMODE_KEY);
            boolean serviceFound = false;
            Iterator<Service> serviceIterator = configuration.getBusinessProcesses().getServices().iterator();
            while (!serviceFound && serviceIterator.hasNext()) {
                Service service = serviceIterator.next();
                if (service.getName().equals(savedService.getName())) {
                    serviceFound = true;
                }
            }
            Assert.assertTrue(serviceFound);

            LegConfiguration savedLegConf = pModeDao.getLegConfiguration(BLUE_2_RED_SERVICE1_ACTION1_PMODE_KEY);
            boolean legConfFound = false;
            Iterator<LegConfiguration> legConfIterator = configuration.getBusinessProcesses().getLegConfigurations().iterator();
            while (!legConfFound && legConfIterator.hasNext()) {
                LegConfiguration legConf = legConfIterator.next();
                if (legConf.getName().equals(savedLegConf.getName())) {
                    legConfFound = true;
                }
            }
            Assert.assertTrue(legConfFound);

            Agreement savedAgreement = pModeDao.getAgreement(BLUE_2_RED_SERVICE1_ACTION1_PMODE_KEY);
            boolean agreementFound = false;
            Iterator<Agreement> agreementIterator = configuration.getBusinessProcesses().getAgreements().iterator();
            while (!agreementFound && agreementIterator.hasNext()) {
                Agreement agreement = agreementIterator.next();
                if (agreement.getName().equals(savedAgreement.getName())) {
                    agreementFound = true;
                }
            }
            Assert.assertTrue(agreementFound);

            List<String> mpcNames = pModeDao.getMpcList();
            Map<String, Mpc> savedMpcs = new HashMap<>();
            for (String mpcName : mpcNames) {
                Mpc mpc = new Mpc();
                mpc.setName(mpcName);
                mpc.setQualifiedName(PREFIX_MPC_URI + mpcName);
                mpc.setDefault(true);
                mpc.setEnabled(true);
                mpc.setRetentionDownloaded(pModeDao.getRetentionDownloadedByMpcURI(mpc.getQualifiedName()));
                mpc.setRetentionUndownloaded(pModeDao.getRetentionUndownloadedByMpcURI(mpc.getQualifiedName()));
                savedMpcs.put(mpcName, mpc);
            }

            for (Mpc mpc : configuration.getMpcs()) {
                Mpc savedMpc = savedMpcs.get(mpc.getName());
                Assert.assertNotNull(savedMpc);
                // Assert.assertEquals(mpc, savedMpc); This strangely seeems to work only with Strings!
                //Assert.assertTrue(mpc.equals(savedMpc)); equals and hashcode are based on hibernate Ids !
                Assert.assertEquals(mpc.getName(), savedMpc.getName());
                Assert.assertEquals(mpc.getQualifiedName(), savedMpc.getQualifiedName());
                Assert.assertEquals(mpc.getRetentionDownloaded(), savedMpc.getRetentionDownloaded());
                Assert.assertEquals(mpc.getRetentionUndownloaded(), savedMpc.getRetentionUndownloaded());
            }

        } catch (IOException ioEx) {
            System.out.println("Error: " + ioEx.getMessage());
            throw ioEx;
        } catch (JAXBException jEx) {
            System.out.println("JAXB error: " + jEx.getMessage());
            throw jEx;
        }
    }


}
