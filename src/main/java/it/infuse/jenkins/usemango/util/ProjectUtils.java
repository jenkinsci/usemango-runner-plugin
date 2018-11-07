package it.infuse.jenkins.usemango.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;

import hudson.FilePath;
import hudson.model.BuildListener;
import hudson.model.Job;
import hudson.model.User;

public class ProjectUtils {

	public static final String LOG_DIR = "logs";
	public static final String RESULTS_DIR = "results";
	
	public static String getLogFileName(String testId) {
		return "test_"+testId+".log";
	}
	
	public static String getJUnitFileName(String testId) {
		return "test_"+testId+"_junit.xml";
	}

	public static String getJenkinsTestTaskName(String testName, String buildName, int buildNumber) {
		return (buildName+" ["+formatTestName(testName)+"] #"+buildNumber).toLowerCase();
	}
	
	public static String formatTestName(String testName) {
		if(StringUtils.isNoneBlank(testName)) {
			testName = testName.trim().toLowerCase();
			if(testName.length() > 12) {
				return (testName.substring(0, 12)+"...").toLowerCase();
			}
			else return testName.toLowerCase();
		}
		else return null;
	}
	
	public static void createLogFile(FilePath workspace, String testId, String logMessage, BuildListener listener) {
		try {
			workspace.child(ProjectUtils.LOG_DIR).child(ProjectUtils.getLogFileName(testId)).
				write(logMessage, StandardCharsets.UTF_8.name());
		} catch (IOException | InterruptedException e) {
			listener.error("Error writing test log to workspace: "+e.getMessage());
			e.printStackTrace(listener.getLogger());
		}
	}
	
	public static boolean hasCorrectPermissions(User user) {
		if(user != null && user.hasPermission(Job.CONFIGURE) && user.hasPermission(Job.BUILD)) {
			return true;
		}
		else return false;
	}
	
}