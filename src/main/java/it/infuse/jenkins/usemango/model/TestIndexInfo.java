package it.infuse.jenkins.usemango.model;

import com.google.api.client.json.GenericJson;
import com.google.api.client.util.Key;

public class TestIndexInfo extends GenericJson {

	@Key("Next")
	private String next;
	@Key("Previous")
	private String previous;
	@Key("HasNext")
	private boolean hasNext;
	@Key("HasPrevious")
	private boolean hasPrevious;
	
	/**
	 * @return the next
	 */
	public String getNext() {
		return next;
	}
	/**
	 * @return the previous
	 */
	public String getPrevious() {
		return previous;
	}
	/**
	 * @return the hasNext
	 */
	public boolean isHasNext() {
		return hasNext;
	}
	/**
	 * @return the hasPrevious
	 */
	public boolean isHasPrevious() {
		return hasPrevious;
	}
	/**
	 * @param next the next to set
	 */
	public void setNext(String next) {
		this.next = next;
	}
	/**
	 * @param previous the previous to set
	 */
	public void setPrevious(String previous) {
		this.previous = previous;
	}
	/**
	 * @param hasNext the hasNext to set
	 */
	public void setHasNext(boolean hasNext) {
		this.hasNext = hasNext;
	}
	/**
	 * @param hasPrevious the hasPrevious to set
	 */
	public void setHasPrevious(boolean hasPrevious) {
		this.hasPrevious = hasPrevious;
	}
	
}