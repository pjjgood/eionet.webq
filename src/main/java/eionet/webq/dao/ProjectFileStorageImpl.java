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
package eionet.webq.dao;

import eionet.webq.dto.ProjectEntry;
import eionet.webq.dto.ProjectFile;
import eionet.webq.dto.ProjectFileType;
import eionet.webq.dto.util.ProjectFileInfo;
import org.hibernate.Session;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.Collection;

import static org.hibernate.criterion.Restrictions.eq;

/**
 * ProjectFileStorage implementation.
 */
@Repository
@Transactional
public class ProjectFileStorageImpl extends AbstractDao<ProjectFile> implements WebFormStorage, ProjectFileStorage {

    @Override
    public int save(final ProjectFile projectFile, final ProjectEntry project) {
        projectFile.setProjectId(project.getId());
        getCurrentSession().save(projectFile);
        return projectFile.getId();
    }

    @Override
    public ProjectFile findById(int id) {
        return (ProjectFile) getCurrentSession().byId(getDtoClass()).load(id);
    }

    @Override
    public void update(final ProjectFile projectFile, ProjectEntry projectEntry) {
        if (projectFile.getProjectId() == projectEntry.getId()) {
            if (ProjectFileInfo.fileIsEmpty(projectFile)) {
                updateWithoutChangingContent(projectFile);
            } else {
                fullUpdate(projectFile);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<ProjectFile> findAllFilesFor(ProjectEntry project) {
        return getCriteria().add(eq("projectId", project.getId())).addOrder(Order.asc("id")).list();
    }

    @Override
    public void remove(final ProjectEntry projectEntry, final int... fileIds) {
        for (int fileId : fileIds) {
            getCurrentSession().createQuery("delete from ProjectFile where id=:id").setInteger("id", fileId).executeUpdate();
        }
    }

    @Override
    public ProjectFile findByNameAndProject(String name, ProjectEntry projectEntry) {
        return (ProjectFile) getCriteria()
                .add(Restrictions.and(eq("projectId", projectEntry.getId()), eq("file.name", name))).uniqueResult();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<ProjectFile> getAllActiveWebForms() {
        return getCriteria().add(
                Restrictions.and(eq("fileType", ProjectFileType.WEBFORM), eq("active", true), eq("mainForm", true),
                        Restrictions.isNotNull("xmlSchema"))).list();
    }

    @Override
    Class<ProjectFile> getDtoClass() {
        return ProjectFile.class;
    }

    /**
     * Updates all fields.
     *
     * @param projectFile project file
     */
    private void fullUpdate(ProjectFile projectFile) {
        Session currentSession = getCurrentSession();
        projectFile.setUpdated(new Timestamp(System.currentTimeMillis()));
        currentSession.merge(projectFile);
        currentSession.flush();
    }

    /**
     * Performs update without changing file content data.
     *
     * @param projectFile project file
     */
    private void updateWithoutChangingContent(ProjectFile projectFile) {
        getCurrentSession().createQuery("UPDATE ProjectFile SET title=:title, xmlSchema=:xmlSchema, "
                + " description=:description, userName=:userName, "
                + " active=:active, mainForm=:mainForm, remoteFileUrl=:remoteFileUrl, "
                + " newXmlFileName=:newXmlFileName, emptyInstanceUrl=:emptyInstanceUrl, updated=CURRENT_TIMESTAMP() "
                + " WHERE id=:id").setProperties(projectFile).executeUpdate();
    }
}
