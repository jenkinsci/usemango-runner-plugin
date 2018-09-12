package it.infuse.jenkins.usemango;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

public class UseMangoNotifier extends Notifier {

    private final String credentialsId;
    private final StandardUsernamePasswordCredentials credentials;

    @DataBoundConstructor
    public UseMangoNotifier(String credentialsId) {
        this.credentialsId = credentialsId;

       List<StandardUsernamePasswordCredentials> credentialList = CredentialsProvider.lookupCredentials(
    		   StandardUsernamePasswordCredentials.class, Jenkins.getInstance(), ACL.SYSTEM,
                Collections.<DomainRequirement> emptyList());
       StandardUsernamePasswordCredentials credentials = CredentialsMatchers.firstOrNull(credentialList,
                CredentialsMatchers.allOf(CredentialsMatchers.withId(credentialsId)));

        this.credentials = credentials;
    }

    /**
     * @return the credentialsId
     */
    public String getCredentialsId() {
        return credentialsId;
    }

	@Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
		// TODO:
//		String username = credentials.getUsername();
//    	Secret password = credentials.getPassword();
        return true;
    }

    @Extension
    public final static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        public DescriptorImpl() {
            super(UseMangoNotifier.class);
            load();
        }

        @Override
        public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType) {
            return Boolean.TRUE;
        }

        @Override
        public String getDisplayName() {
            return "useMango Execution Settings";
        }
        
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
    
    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

}