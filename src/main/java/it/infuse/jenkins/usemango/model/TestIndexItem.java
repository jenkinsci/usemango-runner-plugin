package it.infuse.jenkins.usemango.model;

import com.google.api.client.json.GenericJson;
import com.google.api.client.util.Key;

import java.util.List;

public class TestIndexItem extends GenericJson {

	@Key("Id")
	private String id;
	@Key("Name")
	private String name;
	@Key("Status")
	private String status;
	@Key("Tags")
	private List<String> tags;
	@Key("Assignee")
	private String assignee;
	@Key("LastModified")
	private String lastModified; 
	private boolean passed = false;
	private String runId;
	
	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}
	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}
	/**
	 * @return the status
	 */
	public String getStatus() {
		return status;
	}
	/**
	 * @return the tags
	 */
	public List<String> getTags() {
		return tags;
	}
	/**
	 * @return the assignee
	 */
	public String getAssignee() {
		return assignee;
	}
	/**
	 * @return the lastModified
	 */
	public String getLastModified() {
		return lastModified;
	}
	/**
	 * @return the passed
	 */
	public boolean isPassed() {
		return passed;
	}
	/**
	 * @return the runId
	 */
	public String getRunId() {
		return runId;
	}
	/**
	 * @param passed the passed to set
	 */
	public void setPassed(boolean passed) {
		this.passed = passed;
	}
	/**
	 * @param runId - test execution id
	 */
	public void setRunId(String runId) {
		this.runId = runId;
	}
}