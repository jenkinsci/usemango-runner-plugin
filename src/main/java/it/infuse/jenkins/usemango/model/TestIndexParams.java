package it.infuse.jenkins.usemango.model;

import java.util.ArrayList;

public class TestIndexParams {

	private String projectId;
	private String tags;
	private String testName;
	private String testStatus;
	private String assignedTo;

	/**
	 * @return the projectId
	 */
	public String getProjectId() {
		return projectId;
	}

	/**
	 * @param projectId the projectId to set
	 */
	public void setProjectId(String projectId) {
		this.projectId = projectId;
	}

	/**
	 * @return the tags
	 */
	public String getTags() {
		return tags;
	}

	/**
	 * @param tags the tags to set
	 */
	public void addTags(String tags) { this.tags = tags; }

	/**
	 * @return the testName
	 */
	public String getTestName() {
		return testName;
	}

	/**
	 * @param testName the testName to set
	 */
	public void setTestName(String testName) {
		this.testName = testName;
	}

	/**
	 * @return the testStatus
	 */
	public String getTestStatus() {
		return testStatus;
	}

	/**
	 * @param testStatus the testStatus to set
	 */
	public void setTestStatus(String testStatus) {
		this.testStatus = testStatus;
	}

	/**
	 * @return the assignedTo
	 */
	public String getAssignedTo() {
		return assignedTo;
	}

	/**
	 * @param assignedTo the assignedTo to set
	 */
	public void setAssignedTo(String assignedTo) {
		this.assignedTo = assignedTo;
	}

}
