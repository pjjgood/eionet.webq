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
package eionet.webq.converter;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import configuration.ApplicationTestContextWithMockSession;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.multipart.MultipartFile;

import eionet.webq.dto.UploadedXmlFile;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {ApplicationTestContextWithMockSession.class})
public class MultipartFileConverterTest {
    @Autowired
    private MultipartFileConverter fileConverter;
    private final String originalFilename = "file.xml";

    @Test
    public void convertToUploadedFile() throws Exception {
        String schemaLocation = "testSchema";
        String rootAttributesDeclaration = rootAttributesDeclaration(noNamespaceSchemaAttribute(schemaLocation));
        byte[] fileContent = xmlWithRootElementAttributes(rootAttributesDeclaration);
        MultipartFile xmlFileUpload = createMultipartFile(fileContent);

        UploadedXmlFile xmlFile = fileConverter.convert(xmlFileUpload);

        assertThat(xmlFile.getName(), equalTo(originalFilename));
        assertThat(xmlFile.getContent(), equalTo(fileContent));
        assertThat(xmlFile.getXmlSchema(), equalTo(schemaLocation));
        assertThat(xmlFile.getSizeInBytes(), equalTo(xmlFileUpload.getSize()));
    }

    @Test
    public void setXmlSchemaToNullIfUnableToRead() {
        UploadedXmlFile result =
                fileConverter.convert(createMultipartFile(xmlWithRootElementAttributes(noNamespaceSchemaAttribute("foo"))));
        assertNull(result.getXmlSchema());
    }

    @Test
    public void setXmlSchemaWithNamespace() throws Exception {
        String namespace = "namespace";
        String schemaLocation = "testSchema";
        UploadedXmlFile result =
                fileConverter.convert(createMultipartFile(xmlWithRootElementAttributes(rootAttributesDeclaration(schemaAttribute(
                        namespace, schemaLocation)))));

        assertThat(result.getXmlSchema(), equalTo(namespace + " " + schemaLocation));
    }

    private String noNamespaceSchemaAttribute(String schemaLocation) {
        return "xsi:noNamespaceSchemaLocation=\"" + schemaLocation + "\"";
    }

    private String schemaAttribute(String namespace, String schemaLocation) {
        return "xsi:schemaLocation=\"" + namespace + " " + schemaLocation + "\"";
    }

    private String rootAttributesDeclaration(String schemaAttribute) {
        return "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " + schemaAttribute;
    }

    private MultipartFile createMultipartFile(byte[] content) {
        return new MockMultipartFile("xmlFileUpload", originalFilename, MediaType.APPLICATION_XML_VALUE, content);
    }

    private byte[] xmlWithRootElementAttributes(String rootAttributesDeclaration) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<derogations " + rootAttributesDeclaration + " >" +
                "</derogations>";
        return xml.getBytes();
    }
}
