/*
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * The Original Code is Web Questionnaires 2
 *
 * The Initial Owner of the Original Code is European Environment
 * Agency. Portions created by TripleDev are Copyright
 * (C) European Environment Agency.  All Rights Reserved.
 *
 * Contributor(s):
 *        Anton Dmitrijev
 */
package eionet.webq.web.controller.cdr;

import eionet.webq.dao.UserFileStorage;
import eionet.webq.dao.orm.ProjectEntry;
import eionet.webq.dao.orm.ProjectFile;
import eionet.webq.dao.orm.ProjectFileType;
import eionet.webq.dao.orm.UserFile;
import eionet.webq.dto.CdrRequest;
import eionet.webq.service.ProjectFileService;
import eionet.webq.web.AbstractContextControllerTests;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestOperations;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static eionet.webq.service.CDREnvelopeService.XmlFile;
import static java.util.Collections.singletonMap;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 */
@RunWith(SpringJUnit4ClassRunner.class)
@Transactional
public class IntegrationWithCDRControllerIntegrationTest extends AbstractContextControllerTests {
    @Autowired
    private XmlRpcClient xmlRpcClient;
    @Autowired
    private ProjectFileService projectFileService;
    @Autowired
    private RestOperations restOperations;
    @Autowired
    private UserFileStorage userFileStorage;
    @Value("#{ws['webq1.url']}")
    private String webQFallBackUrl;

    private static final String ENVELOPE_URL = "http://cdr.envelope.eu";
    private static final String XML_SCHEMA = "cdr-specific-schema";

    @Before
    public void setUp() throws Exception {
        Mockito.reset(xmlRpcClient);
    }

    @Test
    public void menuReturnsViewName() throws Exception {
        saveAvailableWebFormWithSchema(XML_SCHEMA);

        requestWebQMenu().andExpect(view().name("deliver_menu"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void expectXmlFilesSetForMenu() throws Exception {
        saveAvailableWebFormWithSchema(XML_SCHEMA);
        saveAvailableWebFormWithSchema(XML_SCHEMA);
        rpcClientWillReturnFileForSchema(XML_SCHEMA);

        MultiValueMap<String, XmlFile> files = (MultiValueMap<String, XmlFile>) requestToWebQMenuAndGetModelAttribute("xmlFiles");

        assertThat(files.size(), equalTo(1));
        assertTrue(files.containsKey(XML_SCHEMA));
    }

    @Test
    public void parametersAreAccessibleViaModelForMenu() throws Exception {
        saveAvailableWebFormWithSchema(XML_SCHEMA);
        CdrRequest parameters = (CdrRequest) requestToWebQMenuAndGetModelAttribute("parameters");

        assertThat(parameters.getEnvelopeUrl(), equalTo(ENVELOPE_URL));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void availableWebFormsAreAccessibleViaModelForMenu() throws Exception {
        rpcClientWillReturnFileForSchema(XML_SCHEMA);
        saveAvailableWebFormWithSchema(XML_SCHEMA);
        saveAvailableWebFormWithSchema(XML_SCHEMA);

        Collection<ProjectFile> webForms = (Collection<ProjectFile>) requestToWebQMenuAndGetModelAttribute("availableWebForms");

        assertThat(webForms.size(), equalTo(2));
    }

    @Test
    public void editCdrFileWillSaveFileLocallyAndRedirectToXFormsEngine() throws Exception {
        MockHttpSession session = new MockHttpSession();
        byte[] fileContent = "file-content".getBytes();
        when(restOperations.getForEntity(anyString(), any(Class.class)))
                .thenReturn(new ResponseEntity<byte[]>(fileContent, HttpStatus.OK));

        String fileName = "file.xml";
        int formId = saveAvailableWebFormWithSchema(XML_SCHEMA);
        CdrRequest request = new CdrRequest();
        request.setNewFileName(fileName);
        request.setAdditionalParametersAsQueryString("");
        session.setAttribute(IntegrationWithCDRController.LATEST_CDR_REQUEST, request);

        MvcResult mvcResult = mvc().perform(post("/cdr/edit/file").param("formId", String.valueOf(formId)).param("fileName", fileName)
                .param("remoteFileUrl", "http://remote-file.url").session(session))
                .andExpect(status().isFound()).andReturn();

        String redirectedUrl = mvcResult.getResponse().getRedirectedUrl();

        UserFile file = userFileStorage.findFile(extractFileIdFromXFormRedirectUrl(redirectedUrl), session.getId());
        assertNull(file.getContent());
        assertThat(file.getName(), equalTo(fileName));
        assertThat(file.getXmlSchema(), equalTo(XML_SCHEMA));
    }

    @Test
    public void ifOnlyOneFileAndWebFormAvailableDoRedirectToEdit() throws Exception {
        rpcClientWillReturnFileForSchema(XML_SCHEMA);
        saveAvailableWebFormWithSchema(XML_SCHEMA);

        MvcResult mvcResult =
                mvc().perform(post("/WebQMenu").param("envelope", ENVELOPE_URL)).andExpect(status().isFound()).andReturn();

        assertTrue(mvcResult.getResponse().getRedirectedUrl().startsWith("/xform"));
    }

    @Test
    public void webQMenu_ifNoWebFormsAvailable_SendRedirectToWebQ1() throws Exception {
        mvc().perform(post("/WebQMenu").param("envelope", ENVELOPE_URL))
                .andExpect(status().is(HttpStatus.MOVED_PERMANENTLY.value()))
                .andExpect(header().string("Location", containsString(webQFallBackUrl)));
    }

    @Test
    public void webQEdit_IfNoWebFormsAvailable_SendRedirectToWebQ1() throws Exception {
        mvc().perform(post("/WebQEdit"))
                .andExpect(status().is(HttpStatus.MOVED_PERMANENTLY.value()))
                .andExpect(header().string("Location", containsString(webQFallBackUrl)));
    }

    private int saveAvailableWebFormWithSchema(String xmlSchema) {
        ProjectFile file = new ProjectFile();
        file.setXmlSchema(xmlSchema);
        file.setActive(true);
        file.setMainForm(true);
        file.setTitle("web form");
        file.setFileContent("content".getBytes());
        file.setFileType(ProjectFileType.WEBFORM);
        projectFileService.saveOrUpdate(file, new ProjectEntry());
        return file.getId();
    }

    private void rpcClientWillReturnFileForSchema(String xmlSchema) throws XmlRpcException {
        when(xmlRpcClient.execute(any(XmlRpcClientConfig.class), anyString(), anyList()))
                .thenReturn(singletonMap(xmlSchema, new Object[]{new Object[]{"file.url", "file name"}}));
    }

    private Object requestToWebQMenuAndGetModelAttribute(String attributeName) throws Exception {
        return requestWebQMenu().andReturn().getModelAndView().getModel().get(attributeName);
    }

    private ResultActions requestWebQMenu() throws Exception {
        return request(post("/WebQMenu").param("envelope", ENVELOPE_URL));
    }

    private int extractFileIdFromXFormRedirectUrl(String redirectUrl) {
        String redirectUrlRegex = "/xform/\\?formId=\\d+&instance=.*&fileId=(\\d+)&base_uri=";
        Matcher matcher = Pattern.compile(redirectUrlRegex).matcher(redirectUrl);

        assertTrue(matcher.find());
        return Integer.valueOf(matcher.group(1));
    }
}
