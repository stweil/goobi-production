/*
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */

package org.kitodo.selenium;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.util.List;

import org.apache.commons.lang.SystemUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.kitodo.data.database.beans.Client;
import org.kitodo.data.database.beans.Docket;
import org.kitodo.data.database.beans.LdapGroup;
import org.kitodo.data.database.beans.Project;
import org.kitodo.data.database.beans.Ruleset;
import org.kitodo.data.database.beans.Template;
import org.kitodo.data.database.beans.User;
import org.kitodo.data.database.beans.UserGroup;
import org.kitodo.data.database.beans.Workflow;
import org.kitodo.selenium.testframework.BaseTestSelenium;
import org.kitodo.selenium.testframework.Browser;
import org.kitodo.selenium.testframework.Pages;
import org.kitodo.selenium.testframework.enums.TabIndex;
import org.kitodo.selenium.testframework.generators.LdapGroupGenerator;
import org.kitodo.selenium.testframework.generators.ProjectGenerator;
import org.kitodo.selenium.testframework.generators.UserGenerator;
import org.kitodo.selenium.testframework.pages.ProcessesPage;
import org.kitodo.selenium.testframework.pages.ProjectsPage;
import org.kitodo.selenium.testframework.pages.UserGroupEditPage;
import org.kitodo.selenium.testframework.pages.UsersPage;
import org.kitodo.services.ServiceManager;

public class AddingST extends BaseTestSelenium {

    private ServiceManager serviceManager = new ServiceManager();

    private static ProcessesPage processesPage;
    private static ProjectsPage projectsPage;
    private static UsersPage usersPage;
    private static UserGroupEditPage userGroupEditPage;

    @BeforeClass
    public static void setup() throws Exception {
        processesPage = Pages.getProcessesPage();
        projectsPage = Pages.getProjectsPage();
        usersPage = Pages.getUsersPage();
        userGroupEditPage = Pages.getUserGroupEditPage();
    }

    @Before
    public void login() throws Exception {
        Pages.getLoginPage().goTo().performLoginAsAdmin();
    }

    @After
    public void logout() throws Exception {
        Pages.getTopNavigation().logout();
        if (Browser.isAlertPresent()) {
            Browser.getDriver().switchTo().alert().accept();
        }
    }

    @Test
    public void addBatchTest() throws Exception {
        processesPage.createNewBatch();
        await().untilAsserted(() -> assertEquals("Batch was inserted!", 1,
            serviceManager.getBatchService().getByQuery("FROM Batch WHERE title = 'SeleniumBatch'").size()));
    }

    @Test
    public void addProjectTest() throws Exception {
        Project project = ProjectGenerator.generateProject();
        projectsPage.createNewProject();
        assertEquals("Header for create new project is incorrect", "Neues Projekt",
            Pages.getProjectEditPage().getHeaderText());

        Pages.getProjectEditPage().insertProjectData(project).save();
        assertTrue("Redirection after save was not successful", projectsPage.isAt());

        boolean projectAvailable = Pages.getProjectsPage().getProjectsTitles().contains(project.getTitle());
        assertTrue("Created Project was not listed at projects table!", projectAvailable);
    }

    @Test
    public void addTemplateTest() throws Exception {
        Template template = new Template();
        template.setTitle("MockTemplate");
        projectsPage.createNewTemplate();
        assertEquals("Header for create new template is incorrect", "Neue Produktionsvorlage",
            Pages.getTemplateEditPage().getHeaderText());

        Pages.getTemplateEditPage().insertTemplateData(template).save();
        boolean templateAvailable = projectsPage.getTemplateTitles().contains(template.getTitle());
        assertTrue("Created Template was not listed at templates table!", templateAvailable);
    }

    @Ignore("buttons invisible for tests")
    @Test
    public void addProcessTest() throws Exception {
        assumeTrue(!SystemUtils.IS_OS_WINDOWS && !SystemUtils.IS_OS_MAC);

        projectsPage.createNewProcess();
        assertEquals("Header for create new process is incorrect", "createNewProcess",
            Pages.getProcessFromTemplatePage().getHeaderText());

        String generatedTitle = Pages.getProcessFromTemplatePage().createProcess();
        boolean processAvailable = processesPage.getProcessTitles().contains(generatedTitle);
        assertTrue("Created Process was not listed at processes table!", processAvailable);
    }

    @Test
    public void addWorkflowTest() throws Exception {
        Workflow workflow = new Workflow();
        workflow.setFileName("testWorkflow");
        projectsPage.createNewWorkflow();
        assertEquals("Header for create new workflow is incorrect", "Neuen Workflow anlegen",
            Pages.getWorkflowEditPage().getHeaderText());

        Pages.getWorkflowEditPage().insertWorkflowData(workflow).save();
        assertTrue("Redirection after save was not successful", projectsPage.isAt());

        List<String> workflowTitles = projectsPage.getWorkflowTitles();
        boolean workflowAvailable = workflowTitles.contains("Process_1");
        assertTrue("Created Workflow was not listed at workflows table!", workflowAvailable);
    }

    @Test
    public void addDocketTest() throws Exception {
        Docket docket = new Docket();
        docket.setTitle("MockDocket");
        projectsPage.createNewDocket();
        assertEquals("Header for create new docket is incorrect", "Neuen Laufzettel anlegen",
            Pages.getDocketEditPage().getHeaderText());

        Pages.getDocketEditPage().insertDocketData(docket).save();
        assertTrue("Redirection after save was not successful", projectsPage.isAt());

        List<String> docketTitles = projectsPage.getDocketTitles();
        boolean docketAvailable = docketTitles.contains(docket.getTitle());
        assertTrue("Created Docket was not listed at dockets table!", docketAvailable);
    }

    @Test
    public void addRulesetTest() throws Exception {
        Ruleset ruleset = new Ruleset();
        ruleset.setTitle("MockRuleset");
        projectsPage.createNewRuleset();
        assertEquals("Header for create new ruleset is incorrect", "Neuen Regelsatz anlegen",
            Pages.getRulesetEditPage().getHeaderText());

        Pages.getRulesetEditPage().insertRulesetData(ruleset).save();
        assertTrue("Redirection after save was not successful", projectsPage.isAt());

        List<String> rulesetTitles = projectsPage.getRulesetTitles();
        boolean rulesetAvailable = rulesetTitles.contains(ruleset.getTitle());
        assertTrue("Created Ruleset was not listed at rulesets table!", rulesetAvailable);
    }

    @Test
    public void addUserTest() throws Exception {
        User user = UserGenerator.generateUser();
        usersPage.createNewUser();
        assertEquals("Header for create new user is incorrect", "Neuen Benutzer anlegen",
            Pages.getUserEditPage().getHeaderText());

        Pages.getUserEditPage().insertUserData(user).switchToTabByIndex(TabIndex.USER_USER_GROUPS.getIndex());
        Pages.getUserEditPage().addUserToUserGroup(serviceManager.getUserGroupService().getById(2).getTitle());
        Pages.getUserEditPage().switchToTabByIndex(TabIndex.USER_CLIENT_LIST.getIndex());
        Pages.getUserEditPage().addUserToClient(serviceManager.getClientService().getById(1).getName());
        Pages.getUserEditPage().addUserToClient(serviceManager.getClientService().getById(2).getName()).save();
        assertTrue("Redirection after save was not successful", Pages.getUsersPage().isAt());

        Pages.getTopNavigation().logout();
        Pages.getLoginPage().performLogin(user);
        Pages.getTopNavigation().acceptClientSelection();
        assertEquals(serviceManager.getClientService().getById(1).getName(),
            Pages.getTopNavigation().getSessionClient());
    }

    @Test
    public void addLdapGroupTest() throws Exception {
        LdapGroup ldapGroup = LdapGroupGenerator.generateLdapGroup();
        usersPage.createNewLdapGroup();
        assertEquals("Header for create new LDAP group is incorrect", "Neue LDAP-Gruppe anlegen",
            Pages.getLdapGroupEditPage().getHeaderText());

        Pages.getLdapGroupEditPage().insertLdapGroupData(ldapGroup).save();
        assertTrue("Redirection after save was not successful", usersPage.isAt());

        boolean ldapGroupAvailable = usersPage.getLdapGroupNames().contains(ldapGroup.getTitle());
        assertTrue("Created ldap group was not listed at ldap group table!", ldapGroupAvailable);

        LdapGroup actualLdapGroup = usersPage.editLdapGroup(ldapGroup.getTitle()).readLdapGroup();
        assertEquals("Saved ldap group is giving wrong data at edit page!", ldapGroup, actualLdapGroup);
    }

    @Test
    public void addClientTest() throws Exception {
        Client client = new Client();
        client.setName("MockClient");
        usersPage.createNewClient();
        assertEquals("Header for create new client is incorrect", "Neuen Mandanten anlegen",
            Pages.getClientEditPage().getHeaderText());

        Pages.getClientEditPage().insertClientData(client).save();
        assertTrue("Redirection after save was not successful", usersPage.isAt());

        boolean clientAvailable = usersPage.getClientNames().contains(client.getName());
        assertTrue("Created Client was not listed at clients table!", clientAvailable);
    }

    @Test
    public void addUserGroupTest() throws Exception {
        UserGroup userGroup = new UserGroup();
        userGroup.setTitle("MockUserGroup");

        usersPage.createNewUserGroup();
        assertEquals("Header for create new user group is incorrect", "Neue Benutzergruppe anlegen",
                userGroupEditPage.getHeaderText());

        userGroupEditPage.setUserGroupTitle(userGroup.getTitle()).assignAllGlobalAuthorities()
                .assignAllClientAuthorities().assignAllProjectAuthorities();
        userGroupEditPage.save();
        assertTrue("Redirection after save was not successful", usersPage.isAt());
        List<String> userGroupTitles = usersPage.getUserGroupTitles();
        assertTrue("New user group was not saved", userGroupTitles.contains(userGroup.getTitle()));

        int availableGlobalAuthorities = serviceManager.getAuthorityService().getAllAssignableGlobal().size();
        int assignedGlobalAuthorities = usersPage.editUserGroup(userGroup.getTitle())
                .countAssignedGlobalAuthorities();
        assertEquals("Assigned authorities of the new user group were not saved!", availableGlobalAuthorities,
                assignedGlobalAuthorities);
        String actualTitle = Pages.getUserGroupEditPage().getUserGroupTitle();
        assertEquals("New Name of user group was not saved", userGroup.getTitle(), actualTitle);

        int availableClientAuthorities = serviceManager.getAuthorityService().getAllAssignableToClients().size();
        int assignedClientAuthorities = usersPage.editUserGroup(userGroup.getTitle())
                .countAssignedClientAuthorities();
        assertEquals("Assigned client authorities of the new user group were not saved!", availableClientAuthorities,
            assignedClientAuthorities);

        int availableProjectAuthorities = serviceManager.getAuthorityService().getAllAssignableToProjects().size();
        int assignedProjectAuthorities = userGroupEditPage.countAssignedProjectAuthorities();
        assertEquals("Assigned project authorities of the new user group were not saved!", availableProjectAuthorities,
                assignedProjectAuthorities);
        actualTitle = userGroupEditPage.getUserGroupTitle();
        assertEquals("New Name of user group was not saved", userGroup.getTitle(), actualTitle);
    }
}
