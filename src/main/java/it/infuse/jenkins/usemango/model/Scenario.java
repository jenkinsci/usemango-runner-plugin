package it.infuse.jenkins.usemango.model;

import com.google.api.client.json.GenericJson;
import com.google.api.client.util.Key;

public class Scenario extends GenericJson {

    @Key("Id")
    private int id;

    @Key("Name")
    private String name;

    /**
     * @return the name
     */
    public int getId() {
        return id;
    }
    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }
    /**
     * @param id the name to set
     */
    public void setId(int id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Scenario)) {
            return false;
        }
        return super.equals(o) && ((Scenario) o).id == id;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
