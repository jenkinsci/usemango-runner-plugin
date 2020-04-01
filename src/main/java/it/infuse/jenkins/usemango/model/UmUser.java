package it.infuse.jenkins.usemango.model;

import com.google.api.client.json.GenericJson;
import com.google.api.client.util.Key;

public class UmUser extends GenericJson {

    @Key("Id")
    private String Id;

    @Key("Name")
    private String Name;

    @Key("Email")
    private String Email;

    @Key("Title")
    private String Title;

    @Key("IsAdministrator")
    private boolean IsAdmin;

    /**
     * @return the id
     */
    public String getId() {
        return Id;
    }

    /**
     * @return the name
     */
    public String getName() {
        return Name;
    }

    /**
     * @return the email
     */
    public String getEmail() {
        return Email;
    }

    /**
     * @return the title
     */
    public String getTitle() {
        return Title;
    }

    /**
     * @return the isAdmin
     */
    public boolean isAdmin() {
        return IsAdmin;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof UmUser)) {
            return false;
        }
        return super.equals(o) && ((UmUser) o).Id.equalsIgnoreCase(Id);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
