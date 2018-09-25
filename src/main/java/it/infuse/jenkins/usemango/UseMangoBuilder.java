package it.infuse.jenkins.usemango;

import java.io.File;
import java.io.IOException;
import java.net.HttpCookie;
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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import it.infuse.jenkins.usemango.exception.UseMangoException;
import it.infuse.jenkins.usemango.model.TestIndexParams;
import it.infuse.jenkins.usemango.model.TestIndexResponse;
import it.infuse.jenkins.usemango.util.APIUtils;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;

public class UseMangoBuilder extends Builder implements SimpleBuildStep {

	private static StandardUsernamePasswordCredentials credentials;
	private static String useMangoUrl;
	
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
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		TestIndexParams params = new TestIndexParams(); 
		params.setAssignedTo(this.assignedTo);
		params.setFolderName(this.folderName);
		params.setProjectId(this.projectId);
		params.setTestName(this.testName);
		params.setTestStatus(this.testStatus);
		listener.getLogger().println("TestIndex API parameters:\n"+gson.toJson(params));
		try {
			TestIndexResponse indexes = getTestIndexes(params);
			listener.getLogger().println("Tests retrieved:\n"+gson.toJson(indexes.getItems()));
			indexes.getItems().forEach((item) -> {
				
				listener.getLogger().println("Executing test: "+item.getName());
				ArgumentListBuilder command = new ArgumentListBuilder();
	            StringBuffer sb = new StringBuffer("\""+File.separator+"Program Files (x86)");
	            sb.append(File.separator+"Infuse Consulting");
	            sb.append(File.separator+"useMango");
	            sb.append(File.separator+"App");
	            sb.append(File.separator+"MangoMotor.exe\"");
	            sb.append(" -s \""+useMangoUrl+"\"");
	            sb.append(" -p \""+this.projectId+"\"");
	            sb.append(" --testname \""+item.getName()+"\"");
	            sb.append(" -e \""+credentials.getUsername()+"\"");
	            sb.append(" -a \""+credentials.getPassword().getPlainText()+"\"");
	            command.addTokenized(sb.toString());
	            
//	            Label labelToFind = Label.get("usemango");
//	            labelToFind.getNodes().forEach((node)-> {
//	            	listener.getLogger().println("Node used: "+node.getDisplayName());
//	            	Computer computer = node.toComputer();
//	            	if(computer != null && computer.isOnline() && computer.isAcceptingTasks()) {
//		            	Launcher umLanucher = node.createLauncher(listener);
		            	ProcStarter umStarter = launcher.new ProcStarter();
		            	umStarter = umStarter.cmds(command).stdout(listener);
		            	try {
		            		umStarter = umStarter.pwd(workspace).envs(run.getEnvironment(listener));
							Proc proc = launcher.launch(umStarter);
							int exitCode = proc.join();
				            if(exitCode == 0) listener.getLogger().println("Test finished successfully");
				            else listener.error("Test failed with exit code: "+exitCode);
						} catch (IOException | InterruptedException e) {
							throw new RuntimeException(e.getMessage());
						}
//	            	}
//	            });
	            
			});
		} catch (UseMangoException e) {
			throw new RuntimeException(e.getMessage());
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
    			TestIndexParams params = new TestIndexParams(); 
    			params.setAssignedTo(assignedTo);
    			params.setFolderName(folderName);
    			params.setProjectId(projectId);
    			params.setTestName(testName);
    			params.setTestStatus(testStatus);
    			TestIndexResponse indexes = getTestIndexes(params);
    			if(indexes != null && indexes.getItems() != null && !indexes.getItems().isEmpty()) {
    				return FormValidation.okWithMarkup("Checking account... done.<br/>Validating settings... done.<br/>"
    						+"Result: <font color=\"green\">Success, "+indexes.getItems().size()+" test(s) found!</font>");
    			} 
    			else {
    				return FormValidation.warningWithMarkup("Checking account... done.<br/>Validating settings... done.<br/>"
    						+"Result: Warning, no tests found. Please check settings exist in account and try again.");
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
	
	private static TestIndexResponse getTestIndexes(TestIndexParams params) throws IOException, UseMangoException {
		useMangoUrl = UseMangoConfiguration.get().getLocation();
		credentials = getCredentials(UseMangoConfiguration.get().getCredentialsId());
		
		if(StringUtils.isBlank(useMangoUrl) || !useMangoUrl.startsWith("http")) 
			throw new UseMangoException("Invalid useMango url in global configuration");
		if(credentials == null) 
			throw new UseMangoException("Invalid useMango credentials in global configuration");
		
		HttpCookie cookie = APIUtils.getSessionCookie(useMangoUrl, credentials.getUsername(), credentials.getPassword().getPlainText());
		return APIUtils.getTestIndex(useMangoUrl, params, cookie);
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
