package it.infuse.jenkins.usemango.model;

import java.util.List;

import com.google.api.client.json.GenericJson;
import com.google.api.client.util.Key;

public class TestIndexResponse extends GenericJson {

	@Key("Items")
	private List<TestIndexItem> items;

	/**
	 * @return the items
	 */
	public List<TestIndexItem> getItems() {
		return items;
	}

}
