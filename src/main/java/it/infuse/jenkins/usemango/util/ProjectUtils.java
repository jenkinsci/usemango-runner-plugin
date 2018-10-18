package it.infuse.jenkins.usemango.util;

public class ProjectUtils {

	public static final String LOG_DIR = "logs";
	public static final String RESULTS_DIR = "results";
	
	public static String getLogFileName(String testId) {
		return "test_"+testId+".log";
	}
	
	public static String getJUnitFileName(String testId) {
		return "test_"+testId+"_junit.xml";
	}

}
