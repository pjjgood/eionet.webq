package eionet.webq.web.controller;

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

import eionet.webq.dao.ProjectFolders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;


/**
 * Spring controller to manage projects.
 *
 * @see Controller
 */
@Controller
@RequestMapping("projects")
public class ProjectsController {
    /**
     * Access to project folders.
     */
    @Autowired
    private ProjectFolders projectFolders;

    /**
     * All projects handler.
     *
     * @param model model attributes holder
     * @return view name
     */
    @RequestMapping("/")
    public String allProjects(Model model) {
        model.addAttribute("allProjects", projectFolders.getAllFolders());
        return "projects";
    }
}
