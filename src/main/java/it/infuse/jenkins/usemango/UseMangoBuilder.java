package it.infuse.jenkins.usemango;

import java.io.IOException;
import java.net.HttpCookie;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.List;

import javax.servlet.ServletException;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import it.infuse.jenkins.usemango.model.TestIndexParams;
import it.infuse.jenkins.usemango.model.TestIndexResponse;
import it.infuse.jenkins.usemango.util.APIUtils;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;

public class UseMangoBuilder extends Builder implements SimpleBuildStep {

	private String projectId;
	private String folderName;
	private String testName;
	private String testStatus;
	private String assignedTo;
	
	@DataBoundConstructor
	public UseMangoBuilder(String projectId, String folderName, String testName, String testStatus, String assignedTo) {
		this.projectId = projectId;
		this.folderName = folderName;
		this.testName = testName;
		this.testStatus = testStatus;
		this.assignedTo = assignedTo;
	}
	
	@Override
	public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
			throws InterruptedException, IOException {
		
		StandardUsernamePasswordCredentials credentials = getCredentials(UseMangoConfiguration.get().getCredentialsId());
		String useMangoUrl = UseMangoConfiguration.get().getLocation();
		
		if (credentials == null) {
            listener.error("Invalid credentials: Please set credentials in useMango global configuration");
            throw new RuntimeException("Invalid credentials");
        }
		else if(StringUtils.isBlank(useMangoUrl)) {
			listener.error("Invalid location: Please set location in useMango global configuration");
            throw new RuntimeException("Invalid location");
		}
		else {
			listener.getLogger().println("Location: "+useMangoUrl);
			listener.getLogger().println("Username: "+credentials.getUsername());
			listener.getLogger().println("Password: "+credentials.getPassword());
		}
		
	}
	
    @Symbol("greet")
    @Extension
    public final static class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public FormValidation doCheckProjectId(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a Project ID");
            return FormValidation.ok();
        }

    	public FormValidation doValidateSettings(
    			@QueryParameter("projectId") final String projectId,
    			@QueryParameter("folderName") final String folderName,
    			@QueryParameter("testName") final String testName,
    	        @QueryParameter("testStatus") final String testStatus,
    	        @QueryParameter("assignedTo") final String assignedTo) throws IOException, ServletException {
    		try {
    			StandardUsernamePasswordCredentials credentials = getCredentials(UseMangoConfiguration.get().getCredentialsId());
    			HttpCookie cookie = APIUtils.getSessionCookie(credentials.getUsername(), credentials.getPassword().getPlainText());
    			TestIndexParams params = new TestIndexParams(); 
    			params.setAssignedTo(assignedTo);
    			params.setFolderName(folderName);
    			params.setProjectId(projectId);
    			params.setTestName(testName);
    			params.setTestStatus(testStatus);
    			TestIndexResponse indexes = APIUtils.getTestIndex(params, cookie);
    			if(indexes != null && indexes.getItems() != null && indexes.getItems().size() > 0) {
    				return FormValidation.okWithMarkup("Check account: <font color=\"green\">Pass</font><br/>"
    						+"Check settings: <font color=\"green\">Pass ("+indexes.getItems().size()
    						+" tests found)</font>");
    			}
    			else {
    				return FormValidation.warningWithMarkup("Check account: Pass<br/>"
    						+"Check settings: Fail, no tests found. Please check settings match account and try again");
    			}
    		} catch (Exception e) {
    	        return FormValidation.error("Validation error: "+e.getMessage());
    	    }
    	}
    	
        @SuppressWarnings("rawtypes")
		@Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Run useMango tests";
        }

    }
	
	private static StandardUsernamePasswordCredentials getCredentials(String credentialsId) {
		List<StandardUsernamePasswordCredentials> credentailsList = CredentialsProvider.lookupCredentials(
				StandardUsernamePasswordCredentials.class, Jenkins.getInstance(), ACL.SYSTEM,
					Collections.<DomainRequirement> emptyList());
		return CredentialsMatchers.firstOrNull(credentailsList,
                CredentialsMatchers.allOf(CredentialsMatchers.withId(credentialsId)));
	}

	/**
	 * @return the projectId
	 */
	public String getProjectId() {
		return projectId;
	}

	/**
	 * @return the folderName
	 */
	public String getFolderName() {
		return folderName;
	}

	/**
	 * @return the testName
	 */
	public String getTestName() {
		return testName;
	}

	/**
	 * @return the testStatus
	 */
	public String getTestStatus() {
		return testStatus;
	}

	/**
	 * @return the assignedTo
	 */
	public String getAssignedTo() {
		return assignedTo;
	}
}
