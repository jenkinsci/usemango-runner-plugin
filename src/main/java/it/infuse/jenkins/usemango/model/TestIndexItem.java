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
	@Key("Assignee")
	private String assignee;
	@Key("LastModified")
	private String lastModified;
	@Key("HasScenarios")
	private boolean hasScenarios;
	@Key("Tags")
	private List<String> tags;

	// Default constructor required by JSON class
	public TestIndexItem() { }
	public TestIndexItem(String id, String name, String status, String assignee,
							 String lastModified, boolean hasScenarios, List<String> tags) {
		this.id = id;
		this.name = name;
		this.status = status;
		this.assignee = assignee;
		this.lastModified = lastModified;
		this.hasScenarios = hasScenarios;
		this.tags = tags;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getStatus() {
		return status;
	}

	public String getAssignee() {
		return assignee;
	}

	public String getLastModified() {
		return lastModified;
	}

	public boolean getHasScenarios() {
		return hasScenarios;
	}

	public List<String> getTags() {
		return tags;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof TestIndexItem)) {
			return false;
		}
		return super.equals(o) && ((TestIndexItem) o).id.equalsIgnoreCase(id);
	}

	@Override
	public int hashCode() {
		return super.hashCode();
	}
}