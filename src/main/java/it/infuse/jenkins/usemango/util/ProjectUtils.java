package it.infuse.jenkins.usemango.util;

import org.apache.commons.lang3.StringUtils;

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
			if(testName.length() > 15) {
				return (testName.substring(0, 15)+"...").toLowerCase();
			}
			else return testName.toLowerCase();
		}
		else return null;
	}
	
}