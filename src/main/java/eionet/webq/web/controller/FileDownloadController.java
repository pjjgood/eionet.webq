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

import eionet.webq.dto.UploadedXmlFile;
import eionet.webq.service.ConversionService;
import eionet.webq.service.UploadedXmlFileService;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Spring controller for WebQ file download.
 */
@Controller
@RequestMapping("download")
public class FileDownloadController {
    /**
     * Service for getting file content from storage.
     */
    @Autowired
    private UploadedXmlFileService uploadedXmlFileService;
    /**
     * File conversion service.
     */
    @Autowired
    private ConversionService conversionService;

    /**
     * Download uploaded file action.
     *
     * @param fileId requested file id
     * @param response http response to write file
     */
    @RequestMapping(value = "/user_file")
    public void downloadUserFile(@RequestParam int fileId, HttpServletResponse response) {
        UploadedXmlFile file = uploadedXmlFileService.getById(fileId);
        response.setContentType(MediaType.APPLICATION_XML_VALUE);
        response.addHeader("Content-Disposition", "attachment;filename=" + file.getName());
        writeToResponse(response, file.getContent());
    }

    /**
     * Performs conversion of specified {@link UploadedXmlFile} to specific format.
     * Format is defined by conversionId.
     * @param fileId file id, which will be loaded and converted
     * @param conversionId id of conversion to be used
     * @param response object where conversion result will be written
     */
    @RequestMapping("/convert")
    public void convertXmlFile(@RequestParam int fileId, @RequestParam int conversionId, HttpServletResponse response) {
        UploadedXmlFile fileContent = uploadedXmlFileService.getById(fileId);
        writeToResponse(response, conversionService.convert(fileContent, conversionId));
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