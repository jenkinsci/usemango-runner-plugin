package it.infuse.jenkins.usemango;

import hudson.Extension;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;

import java.util.ArrayList;
import java.util.List;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundSetter;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

@Extension
public class UseMangoConfiguration extends GlobalConfiguration {

	private String location;
    private String credentialsId;
	
    public static UseMangoConfiguration get() {
        return GlobalConfiguration.all().get(UseMangoConfiguration.class);
    }

    public UseMangoConfiguration() {
        load();
    }

	/**
	 * @return the credentialsId
	 */
	public String getCredentialsId() {
		return credentialsId;
	}

	/**
	 * @param credentialsId the credentialsId to set
	 */
	public void setCredentialsId(String credentialsId) {
		this.credentialsId = credentialsId;
		save();
	}
	
	/**
	 * Populates credentials select box.
	 * @param context Jenkins context
	 * @return the populated select box
	 */
	@SuppressWarnings("deprecation")
	public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Jenkins context) {
        if (context == null || !context.hasPermission(Item.CONFIGURE))
            return new StandardListBoxModel();

        List<DomainRequirement> domainRequirements = new ArrayList<DomainRequirement>();
        return new StandardListBoxModel().withEmptySelection().withMatching(
                CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class)),
                CredentialsProvider.lookupCredentials(StandardCredentials.class, context, ACL.SYSTEM,domainRequirements));
    }
}
