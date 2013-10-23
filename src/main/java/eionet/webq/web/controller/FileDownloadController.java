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
package eionet.webq.web.controller;

import eionet.webq.dao.MergeModules;
import eionet.webq.dao.orm.MergeModule;
import eionet.webq.dao.orm.ProjectFile;
import eionet.webq.dao.orm.UserFile;
import eionet.webq.service.ConversionService;
import eionet.webq.service.ProjectFileService;
import eionet.webq.service.ProjectService;
import eionet.webq.service.UserFileMergeService;
import eionet.webq.service.UserFileService;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Spring controller for WebQ file download.
 */
@Controller
@RequestMapping("download")
public class FileDownloadController {
    /**
     * Service for getting user file content from storage.
     */
    @Autowired
    private UserFileService userFileService;
    /**
     * Service for getting user file content from storage.
     */
    @Autowired
    private ProjectService projectService;
    /**
     * Service for getting project file content from storage.
     */
    @Autowired
    private ProjectFileService projectFileService;
    /**
     * File conversion service.
     */
    @Autowired
    private ConversionService conversionService;
    /**
     * Merge modules repository.
     */
    @Autowired
    private MergeModules mergeModules;
    /**
     * User files merge service.
     */
    @Autowired
    private UserFileMergeService mergeService;

    /**
     * Download uploaded file action.
     *
     * @param fileId requested file id
     * @param response http response to write file
     */
    @RequestMapping(value = "/user_file")
    @Transactional
    public void downloadUserFile(@RequestParam int fileId, HttpServletResponse response) {
        UserFile file = userFileService.download(fileId);
        addXmlFileHeaders(response, file.getName());
        writeToResponse(response, file.getContent());
    }

    /**
     * Download uploaded file action.
     *
     * @param projectId project id for file download
     * @param fileName requested file name
     * @param response http response to write file
     */
    @RequestMapping(value = "/project/{projectId}/file/{fileName:.*}")
    @Transactional
    public void downloadProjectFile(@PathVariable String projectId, @PathVariable String fileName, HttpServletResponse response) {
        ProjectFile projectFile = projectFileService.fileContentBy(fileName, projectService.getByProjectId(projectId));
        addXmlFileHeaders(response, encodeAsUrl(fileName));
        writeToResponse(response, projectFile.getFileContent());
    }

    /**
     * Allows to download merge module file.
     *
     * @param moduleName module name.
     * @param response http response to write file
     */
    @RequestMapping("/merge/file/{moduleName:.*}")
    @Transactional
    public void downloadMergeFile(@PathVariable String moduleName, HttpServletResponse response) {
        MergeModule module = mergeModules.findByFileName(moduleName);
        addXmlFileHeaders(response, encodeAsUrl(module.getXslFile().getName()));
        writeToResponse(response, module.getXslFile().getContent().getFileContent());
    }

    /**
     * Merge selected files.
     *
     * @param selectedUserFile list of selected files ids
     * @param mergeModule module used for merge
     * @param response http response
     * @throws TransformerException if transformation fails.
     * @throws IOException if content operations fail.
     */
    @RequestMapping("/merge/files")
    @Transactional
    public void mergeFiles(@RequestParam(required = false) List<Integer> selectedUserFile,
                           @RequestParam int mergeModule, HttpServletResponse response) throws TransformerException, IOException {
        if (selectedUserFile != null && !selectedUserFile.isEmpty()) {
            if (selectedUserFile.size() == 1) {
                downloadUserFile(selectedUserFile.get(0), response);
                return;
            }

            ArrayList<UserFile> userFiles = new ArrayList<UserFile>();
            for (Integer id : selectedUserFile) {
                userFiles.add(userFileService.getById(id));
            }
            //TODO if user files contains null -> fail

            byte[] mergeResult = mergeService.mergeFiles(userFiles, mergeModules.findById(mergeModule));

            addXmlFileHeaders(response, encodeAsUrl("merged_files.xml"));
            writeToResponse(response, mergeResult);
        }
    }

    /**
     * Performs conversion of specified {@link eionet.webq.dao.orm.UserFile} to specific format.
     * Format is defined by conversionId.
     * @param fileId file id or xsl name, which will be used to convert file
     * @param conversionId id of conversion to be used
     * @param response object where conversion result will be written
     */
    @RequestMapping("/convert")
    @Transactional
    public void convertXmlFile(@RequestParam int fileId, @RequestParam String conversionId, HttpServletResponse response) {
        UserFile fileContent = userFileService.getById(fileId);
        ResponseEntity<byte[]> convert = conversionService.convert(fileContent, conversionId);
        HttpHeaders headers = convert.getHeaders();
        setContentType(response, headers.getContentType());
        setContentDisposition(response, headers.getFirst("Content-Disposition"));
        writeToResponse(response, convert.getBody());
    }

    /**
     * Sets headers required to xml file download.
     *
     * @param response http response
     * @param fileName file name
     */
    private void addXmlFileHeaders(HttpServletResponse response, String fileName) {
        setContentType(response, MediaType.APPLICATION_XML);
        setContentDisposition(response, "attachment;filename=" + fileName);
    }

    /**
     * Sets content disposition to response.
     *
     * @param response {@link HttpServletResponse}
     * @param contentDisposition content disposition header value.
     */
    private void setContentDisposition(HttpServletResponse response, String contentDisposition) {
        if (contentDisposition != null) {
            response.setHeader("Content-Disposition", contentDisposition);
        }
    }

    /**
     * Sets content type header to response.
     *
     * @param response http response
     * @param contentType content type
     */
    private void setContentType(HttpServletResponse response, MediaType contentType) {
        if (contentType != null) {
            response.setContentType(contentType.toString());
        }
    }

    /**
     * {@link URLEncoder#encode(String, String)} with default value.
     *
     * @param path string to encode, also default value
     * @return encoded string or default value.
     */
    private String encodeAsUrl(String path) {
        try {
            return URLEncoder.encode(path, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return path;
        }
    }

    /**
     * Writes specified content to http response.
     * @param response http response
     * @param data content to be written to response
     */
    private void writeToResponse(HttpServletResponse response, byte[] data) {
        ServletOutputStream output = null;
        try {
            response.setContentLength(data.length);

            output = response.getOutputStream();
            IOUtils.write(data, output);
            output.flush();
        } catch (IOException e) {
            throw new RuntimeException("Unable to write response", e);
        } finally {
            IOUtils.closeQuietly(output);
        }
    }
}
