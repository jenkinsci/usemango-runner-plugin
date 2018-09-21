package it.infuse.jenkins.usemango;

import hudson.model.FreeStyleProject;

import org.junit.Ignore;
import org.junit.Rule;
//import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class UseMangoBuilderTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    final String projectId = "TestProject";
    final String folderName = "TestFolder";
    final String testName = "TestFile";
    final String testStatus = "TestStatus";
    final String assignedTo = "TestAssignee";

    @Ignore
    public void testConfigRoundtrip() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        project.getBuildersList().add(new UseMangoBuilder(projectId, testName, folderName, testStatus, assignedTo));
        project = jenkins.configRoundtrip(project);
        jenkins.assertEqualDataBoundBeans(new UseMangoBuilder(projectId, testName, folderName, testStatus, assignedTo), 
        		project.getBuildersList().get(0));
    }

}