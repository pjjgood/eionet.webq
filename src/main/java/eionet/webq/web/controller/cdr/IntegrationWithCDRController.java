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

import eionet.webq.converter.CdrRequestConverter;
import eionet.webq.dao.orm.ProjectFile;
import eionet.webq.dao.orm.UserFile;
import eionet.webq.dto.CdrRequest;
import eionet.webq.service.CDREnvelopeService;
import eionet.webq.service.FileNotAvailableException;
import eionet.webq.service.UserFileService;
import eionet.webq.service.WebFormService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static eionet.webq.service.CDREnvelopeService.XmlFile;

/**
 * Provides integration options with CDR.
 */
@Controller
public class IntegrationWithCDRController {

    /**
     * Converts request to CdrRequest.
     */
    @Autowired
    private CdrRequestConverter converter;
    /**
     * CDR envelope service.
     */
    @Autowired
    private CDREnvelopeService envelopeService;
    /**
     * Operations with web forms.
     */
    @Autowired
    private WebFormService webFormService;
    /**
     * User file service.
     */
    @Autowired
    private UserFileService userFileService;


    /**
     * Deliver with WebForms.
     *
     * @param request parameters of this action
     * @param model model
     * @return view name
     * @throws eionet.webq.service.FileNotAvailableException if one redirect to xform remote file not found.
     */
    @RequestMapping("/WebQMenu")
    public String webQMenu(HttpServletRequest request, Model model) throws FileNotAvailableException {
        CdrRequest parameters = converter.convert(request);
        MultiValueMap<String, XmlFile> xmlFiles = envelopeService.getXmlFiles(parameters);
        Collection<String> requiredSchemas =
                StringUtils.isNotEmpty(parameters.getSchema()) ? Arrays.asList(parameters.getSchema()) : xmlFiles.keySet();
        Collection<ProjectFile> webForms = webFormService.findWebFormsForSchemas(requiredSchemas);

        if (hasOnlyOneFileAndWebFormForSameSchema(xmlFiles, webForms, parameters)) {
            return redirectToEditWebForm(request, xmlFiles, webForms);
        }
        if (oneWebFormAndNoFilesButNewFileCreationIsAllowed(xmlFiles, webForms, parameters)) {
            return "redirect:/startWebform?formId=" + webForms.iterator().next().getId();
        }
        model.addAttribute("parameters", parameters);
        model.addAttribute("xmlFiles", xmlFiles);
        model.addAttribute("availableWebForms", webForms);
        return "deliver_menu";
    }

    /**
     * WebQEdit request handler.
     *
     * @param request current request
     * @return view name
     * @throws FileNotAvailableException if remote file not available.
     */
    @RequestMapping("/WebQEdit")
    public String webQEdit(HttpServletRequest request) throws FileNotAvailableException {
        CdrRequest parameters = converter.convert(request);
        String schema = parameters.getSchema();
        if (StringUtils.isEmpty(schema)) {
            throw new IllegalArgumentException("schema parameter is required");
        }

        Collection<ProjectFile> webFormsForSchemas = webFormService.findWebFormsForSchemas(Arrays.asList(schema));
        if (webFormsForSchemas.isEmpty()) {
            throw new IllegalArgumentException("no web forms for '" + schema + "' schema found");
        }
        String instanceUrl = parameters.getInstanceUrl();
        String fileName = instanceUrl.substring(instanceUrl.lastIndexOf("/") + 1);
        return editFile(webFormsForSchemas.iterator().next(), fileName, instanceUrl, request);
    }

    /**
     * Edit envelope file with web form.
     *
     * @param formId web form id
     * @param fileName file name
     * @param remoteFileUrl remote file url
     * @param request current request
     * @return view name
     * @throws eionet.webq.service.FileNotAvailableException if remote file not available
     */
    @RequestMapping("/cdr/edit/file")
    public String editWithWebForm(@RequestParam int formId, @RequestParam String fileName,
                                  @RequestParam String remoteFileUrl, HttpServletRequest request) throws FileNotAvailableException {
        return editFile(webFormService.findActiveWebFormById(formId), fileName, remoteFileUrl, request);
    }

    /**
     * Saves new user file to db and returns redirect url to web form edit.
     *
     * @param webForm web form to be used for edit.
     * @param fileName new file name
     * @param remoteFileUrl remote file url
     * @param request current request
     * @return redirect url
     * @throws FileNotAvailableException if remote file not available
     */
    private String editFile(ProjectFile webForm, String fileName, String remoteFileUrl, HttpServletRequest request)
            throws FileNotAvailableException {
        UserFile userFile = new UserFile();
        userFile.setName(fileName);
        userFile.setXmlSchema(webForm.getXmlSchema());

        int fileId = userFileService.saveWithContentFromRemoteLocation(userFile, remoteFileUrl);
        return "redirect:/xform/?formId=" + webForm.getId() + "&fileId=" + fileId + "&base_uri=" + request.getContextPath();
    }

    /**
     * Check whether there is only 1 file and 1 schema available and their xml schemas match.
     *
     * @param xmlFiles xml files
     * @param webForms web forms
     * @param parameters request parameters
     * @return true iff there are only 1 file and schema with equal xml schema
     */
    private boolean hasOnlyOneFileAndWebFormForSameSchema(MultiValueMap<String, XmlFile> xmlFiles,
            Collection<ProjectFile> webForms, CdrRequest parameters) {
        if (webForms.size() == 1 && xmlFiles.size() == 1 && !parameters.isNewFormCreationAllowed()) {
            List<XmlFile> filesForSchema = xmlFiles.get(webForms.iterator().next().getXmlSchema());
            if (filesForSchema != null && filesForSchema.size() == 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check whether there is no files and only one form available. Adding new files must be allowed.
     *
     * @param xmlFiles xml files
     * @param webForms web forms
     * @param parameters request parameters
     * @return true iff only one form, no files and creation of new files allowed.
     */
    private boolean oneWebFormAndNoFilesButNewFileCreationIsAllowed(MultiValueMap<String, XmlFile> xmlFiles,
            Collection<ProjectFile> webForms, CdrRequest parameters) {
        return webForms.size() == 1 && xmlFiles.size() == 0 && parameters.isNewFormCreationAllowed();
    }

    /**
     * Redirects to edit form.
     *
     * @param request current request
     * @param xmlFiles xml files
     * @param webForms web forms
     * @return redirect string
     * @throws FileNotAvailableException if remote file not available
     */
    private String redirectToEditWebForm(HttpServletRequest request, MultiValueMap<String, XmlFile> xmlFiles,
            Collection<ProjectFile> webForms) throws FileNotAvailableException {
        ProjectFile onlyOneAvailableForm = webForms.iterator().next();
        XmlFile onlyOneAvailableFile = xmlFiles.getFirst(onlyOneAvailableForm.getXmlSchema());
        return editWithWebForm(onlyOneAvailableForm.getId(), onlyOneAvailableFile.getTitle(), onlyOneAvailableFile.getFullName(),
                request);
    }
}
