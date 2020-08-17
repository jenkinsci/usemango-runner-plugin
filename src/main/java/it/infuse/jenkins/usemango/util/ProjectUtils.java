package it.infuse.jenkins.usemango.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import it.infuse.jenkins.usemango.model.ExecutableTest;
import org.apache.commons.lang3.StringUtils;

import hudson.FilePath;
import hudson.model.BuildListener;
import hudson.model.Job;
import hudson.model.User;

public class ProjectUtils {

	public static final String LOG_DIR = "logs";
	public static final String RESULTS_DIR = "results";
	
	public static String getLogFileName(ExecutableTest test) {
		return getFileName(".log", test);
	}
	
	public static String getJUnitFileName(ExecutableTest test) {
		return getFileName("_junit.xml", test);
	}

	public static String getJenkinsTestTaskName(ExecutableTest test, String buildName, int buildNumber) {
		String testName = test.getName();
		if (test.getScenario() != null){
			testName += "_" + test.getScenario().getName();
		}
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
	
	public static void createLogFile(FilePath workspace, ExecutableTest test, String logMessage, BuildListener listener) {
		try {
			workspace.child(ProjectUtils.LOG_DIR).child(ProjectUtils.getLogFileName(test)).
				write(logMessage, StandardCharsets.UTF_8.name());
		} catch (IOException | InterruptedException e) {
			listener.error("Error writing test log to workspace: "+e.getMessage());
			e.printStackTrace(listener.getLogger());
		}
	}
	
	public static boolean hasCorrectPermissions(User user) {
		return user != null && user.hasPermission(Job.CONFIGURE) && user.hasPermission(Job.BUILD);
	}

	private static String getFileName(String extension, ExecutableTest test){
		String fileName = test.getId();
		if (test.getScenario() != null){
			fileName += "_" + test.getScenario().getId();
		}
		return fileName + extension;
	}
}