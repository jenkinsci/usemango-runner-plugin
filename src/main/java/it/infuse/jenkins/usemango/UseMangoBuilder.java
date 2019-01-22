package it.infuse.jenkins.usemango;

import java.io.IOException;
import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.servlet.ServletException;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Label;
import hudson.model.Result;
import hudson.model.User;
import hudson.security.ACL;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import it.infuse.jenkins.usemango.exception.UseMangoException;
import it.infuse.jenkins.usemango.model.Project;
import it.infuse.jenkins.usemango.model.TestIndexItem;
import it.infuse.jenkins.usemango.model.TestIndexParams;
import it.infuse.jenkins.usemango.model.TestIndexResponse;
import it.infuse.jenkins.usemango.util.APIUtils;
import it.infuse.jenkins.usemango.util.ProjectUtils;
import jenkins.model.Jenkins;

public class UseMangoBuilder extends Builder implements BuildStep {

	private static StandardUsernamePasswordCredentials credentials;
	private static String useMangoUrl;
	
	private String useSlaveNodes;
	private String nodeLabel;
	private String projectId;
	private String folderName;
	private String testName;
	private String testStatus;
	private String assignedTo;
	
	@DataBoundConstructor
	public UseMangoBuilder(String useSlaveNodes, String nodeLabel, String projectId, String folderName, String testName, 
			String testStatus, String assignedTo) {
		this.useSlaveNodes = useSlaveNodes;
		this.nodeLabel = nodeLabel;
		this.projectId = projectId;
		this.folderName = folderName;
		this.testName = testName;
		this.testStatus = testStatus;
		this.assignedTo = assignedTo;
	}
	
	@SuppressFBWarnings(value="NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
	@Override
	public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) 
			throws InterruptedException, IOException {
		
		if(ProjectUtils.hasCorrectPermissions(User.current())) { // check user allowed to build this job
			
			prepareWorkspace(build.getWorkspace());
			try {
				loadUseMangoCredentials();
			} catch (UseMangoException e) {
				throw new RuntimeException(e.getMessage());
			}
			
			boolean useSlaves = Boolean.valueOf(useSlaveNodes);
			if(!useSlaves || StringUtils.isBlank(nodeLabel)) {
				listener.getLogger().println("Not using labelled nodes, or no label defined.");
				nodeLabel = "master"; // default to 'master'
			}
			Label label = Label.get(nodeLabel);
			
			if(label != null && label.getNodes() != null && label.getNodes().size() > 0) {
				listener.getLogger().println("Executing tests on '"+nodeLabel+"' node(s)");
			}
			else throw new RuntimeException("No '"+nodeLabel+"' nodes available to run tests");
			
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			TestIndexParams params = new TestIndexParams(); 
			params.setAssignedTo(this.assignedTo);
			params.setFolderName(this.folderName);
			params.setProjectId(this.projectId);
			params.setTestName(this.testName);
			params.setTestStatus(this.testStatus);
			listener.getLogger().println("TestIndex API parameters:\n"+gson.toJson(params));
			
			TestIndexResponse indexes = null;
			try {
				indexes = getTestIndexes(params);
			} catch (UseMangoException e) {
				throw new RuntimeException(e);
			}
				
			if(Objects.requireNonNull(indexes).size() > 0) {
			
				build.getWorkspace().child(ProjectUtils.LOG_DIR).mkdirs();
				build.getWorkspace().child(ProjectUtils.RESULTS_DIR).mkdirs();
				
				listener.getLogger().println(indexes.getItems().size() + " tests retrieved:\n"+gson.toJson(indexes.getItems()));
				
				List<String> testIds = new ArrayList<String>();
				indexes.getItems().forEach((test) -> {
					try {
						testIds.add(test.getId());
						Jenkins.getInstance().getQueue().schedule2(new UseMangoTestTask(nodeLabel, build, listener, test, 
			            		getUseMangoCommand(this.projectId, test.getName())), Jenkins.getInstance().getQuietPeriod());
		            } catch(Exception e) {
		            	listener.error("Error executing test: "+test.getName());
		            	throw new RuntimeException(e);
		            }
				});
				
				String testId;
				while(!testIds.isEmpty()) { // keep alive until all test tasks are done
					testId = null;
					for(String tempId : testIds) {
						if(build.getWorkspace().child(ProjectUtils.LOG_DIR).
								child(ProjectUtils.getLogFileName(tempId)).exists()) {
							testId = tempId;
						}
					}
					if(testId != null) testIds.remove(testId);
					Thread.sleep(1000);
				}
				
				boolean testSuitePassed = true;
				for(TestIndexItem test : indexes.getItems()) {
					if(!test.isPassed()) testSuitePassed = false;
				}
				
				if(!testSuitePassed) build.setResult(Result.FAILURE); // set job to failure if a test failed
				
				listener.getLogger().println("\nTest execution complete.\n\nThank you for using useMango :-)\n");
			
			}
			else {
				listener.getLogger().println("No tests retrieved from useMango account, please check settings and try again.");
			}
			return true;
			
		}
		else {
			listener.error("Jenkins user '"+User.current()+"' does not have permissions to configure and build this Job - please contact your system administrator, or update the users' security settings.");
			return false;
		}
	}
	
    @Symbol("useMango")
    @Extension
    public final static class DescriptorImpl extends BuildStepDescriptor<Builder> {
    	
    	public ListBoxModel doFillProjectIdItems() {
    		ListBoxModel items = new ListBoxModel();
    		items.add(""); // blank top option
    		try {
    			List<Project> projects = getProjects();
    			if(projects != null) {
    				projects.forEach(project->{
    					items.add(project.getName());
    				});
    			}
    		}
    		catch(IOException | UseMangoException e) {
    			e.printStackTrace(System.out);
    		}
    		return items;
    	}
        
        public ListBoxModel doFillTestStatusItems() {
    		ListBoxModel items = new ListBoxModel();
    		items.add(""); // blank top option
    		items.add("Design");
    		items.add("Ready");
    		items.add("Repair");
    		items.add("Revalidate");
    		items.add("Review");
    		return items;
    	}
    	
        @POST
        public FormValidation doCheckNodeLabel(@QueryParameter String nodeLabel) 
        		throws IOException, ServletException {
        	
        	if(StringUtils.isNotBlank(nodeLabel)) {
        		Label label = Label.get(nodeLabel);
        		if(label != null && label.getNodes() != null && label.getNodes().size() > 0) {
        			return FormValidation.ok();
        		}
        		else return FormValidation.warning("No matching nodes found, please try again.");
        	}
        	else return FormValidation.ok();
        }
        
        @POST
        public FormValidation doCheckProjectId(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0) return FormValidation.error("Please set a Project ID");
            return FormValidation.ok();
        }

    	public FormValidation doValidateSettings(
    			@QueryParameter("projectId") final String projectId,
    			@QueryParameter("folderName") final String folderName,
    			@QueryParameter("testName") final String testName,
    	        @QueryParameter("testStatus") final String testStatus,
    	        @QueryParameter("assignedTo") final String assignedTo) throws IOException, ServletException {
    		
    		if(!ProjectUtils.hasCorrectPermissions(User.current())) {
    			return FormValidation.error("Jenkins user '"+User.current()+"' does not have permissions to configure and build this Job - please contact your system administrator, or update the users' security settings.");
    		}
    		else if(StringUtils.isBlank(projectId)) {
    			return FormValidation.error("Please complete mandatory Project ID field above");
    		}
    		else {
	    		try {
	    			TestIndexParams params = new TestIndexParams(); 
	    			params.setAssignedTo(assignedTo);
	    			params.setFolderName(folderName);
	    			params.setProjectId(projectId);
	    			params.setTestName(testName);
	    			params.setTestStatus(testStatus);
	    			TestIndexResponse indexes = getTestIndexes(params);
	    			
	    			StringBuilder sb = new StringBuilder();
    				sb.append("Connecting to <a href=\""+useMangoUrl+"\" target=\"_blank\">"+useMangoUrl+"</a>... done.<br/>");
    				sb.append("Validating account "+credentials.getUsername()+"... done.<br/><br/>");
	    			
	    			if(indexes != null && indexes.getItems() != null && !indexes.getItems().isEmpty()) {
	    				int size = indexes.getItems().size();
	    				
	    				StringBuilder resultsHtml = new StringBuilder("<div style=\"max-height:300px;overflow-y:scroll;padding-top:5px;\">");
	    				resultsHtml.append("<table width=\"100%\" border=\"0\" cellspacing=\"6\" cellpadding=\"6\" style=\"border:1px solid rgba(0, 0, 0, 0.1);width:100%;background-color:#eee;\">");
	    				resultsHtml.append("<tr style=\"background-color:rgba(0, 0, 0, 0.1);\" align=\"left\"><th>Name</th><th>Folder</th><th>Status</th><th>Assigned To</th></tr>");
	    				indexes.getItems().forEach((item)->{
	    					resultsHtml.append("<tr><td>"+item.getName()+"</td><td>"+item.getFolder()+"</td><td>"+item.getStatus()+"</td><td>"+item.getAssignee()+"</td></tr>");
	    				});
	    				resultsHtml.append("</table>");
	    				resultsHtml.append("</div>");
	    				
	    				sb.append("<b>Test"+((size>1)?"s":"")+" matched in account ("+size+"):</b><br/>");
	    				
	    				return FormValidation.okWithMarkup(sb.toString()+resultsHtml);
	    			} 
	    			else {
	    				return FormValidation.warningWithMarkup(sb.toString()+"No tests matched in account. Please check settings above and try again.");
	    			}
	    		} catch (UseMangoException e) {
	    	        return FormValidation.error("Validation error: "+e.getMessage());
	    	    }
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
	
	private static void loadUseMangoCredentials() throws UseMangoException {
		useMangoUrl = UseMangoConfiguration.get().getLocation();
		credentials = getCredentials(UseMangoConfiguration.get().getCredentialsId());
		
		if(StringUtils.isBlank(useMangoUrl) || !useMangoUrl.startsWith("http")) 
			throw new UseMangoException("Invalid useMango url in global configuration (<a href=\"/configure\">view config</a>)");
		if(credentials == null) 
			throw new UseMangoException("Invalid useMango credentials in global configuration (<a href=\"/configure\">view config</a>)");
	}
	
	private static TestIndexResponse getTestIndexes(TestIndexParams params) throws IOException, UseMangoException {
		loadUseMangoCredentials();
		if(params == null) throw new UseMangoException("Test parameters are null, please check useMango build step in job");
		if(credentials == null) throw new UseMangoException("Credentials are null, please check useMango global config");
		String idToken = APIUtils.getSessionCookie(useMangoUrl, credentials.getUsername(), credentials.getPassword().getPlainText());
		return APIUtils.getTestIndex(useMangoUrl, params, idToken);
	}
	
	private static List<Project> getProjects() throws IOException, UseMangoException {
		loadUseMangoCredentials();
		String idToken = APIUtils.getSessionCookie(useMangoUrl, credentials.getUsername(), credentials.getPassword().getPlainText());
		return APIUtils.getProjects(useMangoUrl, idToken);
	}
	
	private static String getUseMangoCommand(String projectId, String testName) {
		StringBuffer sb = new StringBuffer("\"C:\\Program Files (x86)\\Infuse Consulting\\useMango\\App\\MangoMotor.exe\"");
		sb.append(" -s \""+useMangoUrl+"\"");
		sb.append(" -p \""+projectId+"\"");
		sb.append(" -e \""+credentials.getUsername()+"\"");
		sb.append(" -a \""+credentials.getPassword().getPlainText()+"\"");
		sb.append(" --testname \""+testName+"\"");
		return sb.toString();
	}
	
	private static void prepareWorkspace(FilePath workspace) throws IOException, InterruptedException {
		if(workspace.child(ProjectUtils.LOG_DIR).exists()) {
			workspace.child(ProjectUtils.LOG_DIR).deleteContents();
			workspace.child(ProjectUtils.LOG_DIR).deleteRecursive();
		}
		if(workspace.child(ProjectUtils.RESULTS_DIR).exists()) {
			workspace.child(ProjectUtils.RESULTS_DIR).deleteContents();
			workspace.child(ProjectUtils.RESULTS_DIR).deleteRecursive();
		}
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

	/**
	 * @return the nodeLabel
	 */
	public String getNodeLabel() {
		return nodeLabel;
	}

	/**
	 * @return the useSlaveNodes
	 */
	public String getUseSlaveNodes() {
		return useSlaveNodes;
	}
	
}
