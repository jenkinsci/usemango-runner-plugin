package it.infuse.jenkins.usemango.model;

import java.util.List;

import com.google.api.client.json.GenericJson;
import com.google.api.client.util.Key;

public class TestIndexResponse extends GenericJson {

	@Key("Items")
	private List<TestIndexItem> items;
	@Key("FullCount")
	private int fullCount;
	@Key("Offset")
	private int offset;
	@Key("Info")
	private TestIndexInfo info;

	/**
	 * @return the items
	 */
	public List<TestIndexItem> getItems() {
		return items;
	}

	/**
	 * @return the fullCount
	 */
	public int getFullCount() {
		return fullCount;
	}

	/**
	 * @return the offset
	 */
	public int getOffset() {
		return offset;
	}

	/**
	 * @return the info
	 */
	public TestIndexInfo getInfo() {
		return info;
	}

}
