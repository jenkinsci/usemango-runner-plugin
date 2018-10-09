package it.infuse.jenkins.usemango;

import java.io.IOException;
import java.net.HttpCookie;
import java.util.ArrayList;
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
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Label;
import hudson.model.Node;
import hudson.security.ACL;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import it.infuse.jenkins.usemango.enums.UseMangoNodeLabel;
import it.infuse.jenkins.usemango.exception.UseMangoException;
import it.infuse.jenkins.usemango.model.TestIndexParams;
import it.infuse.jenkins.usemango.model.TestIndexResponse;
import it.infuse.jenkins.usemango.util.APIUtils;
import jenkins.model.Jenkins;

public class UseMangoBuilder extends Builder implements BuildStep {

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
	public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) 
			throws InterruptedException, IOException {
		
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
			
		if(indexes != null && indexes.getItems() != null && indexes.getItems().size() > 0) {
		
			listener.getLogger().println(indexes.getItems().size() + " tests retrieved:\n"+gson.toJson(indexes.getItems()));
			
			// Copy immutable Set to non-immutable List for processing
			List<Node> nodes = new ArrayList<Node>(Label.get(UseMangoNodeLabel.USEMANGO.toString()).getNodes());
			if(nodes == null || nodes.size() < 1) {
				throw new RuntimeException("No 'usemango' nodes configured, unable to execute tests");
			}
			
			List<String> testIds = new ArrayList<String>();
			indexes.getItems().forEach((test) -> {
				try {
					testIds.add(test.getId());
					Node node = getNode(nodes);
					listener.getLogger().println("Allocating node '"+node.getNodeName()+"' to run test: "+test.getId());
		            Jenkins.getInstance().getQueue().schedule2(new UseMangoTestTask(node, build, listener, test, 
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
					if(testIds != null && build != null && build.getWorkspace() != null &&  
							build.getWorkspace().child(tempId).exists()) {
						testId = tempId;
					}
				}
				if(testId != null) testIds.remove(testId);
				Thread.sleep(1000);
			}
			
			listener.getLogger().println("\n\nTest execution complete.");
			listener.getLogger().println("\nThanks for using useMango :-)\n");
		
		}
		else {
			listener.getLogger().println("No tests retrieved from useMango account, please check settings and try again.");
		}
		
		return true;
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
    				int size = indexes.getItems().size();
    				
    				StringBuilder resultsHtml = new StringBuilder("<div style=\"padding-top:5px;\">");
    				resultsHtml.append("<table border=\"1\" cellspacing=\"0\" cellpadding=\"0\">");
    				resultsHtml.append("<tr><th>Name</th><th>Folder</th><th>Status</th><th>Assigned To</th></tr>");
    				indexes.getItems().forEach((item)->{
    					resultsHtml.append("<tr><td>"+item.getName()+"</td><td>"+item.getFolder()+"</td><td>"+item.getStatus()+"</td><td>"+item.getAssignee()+"</td></tr>");
    				});
    				resultsHtml.append("</table>");
    				resultsHtml.append("</div>");
    				
    				return FormValidation.okWithMarkup("Checking account... done.<br/>Validating settings... done.<br/><br/>"
    						+"<b><font color=\"green\">Success, "+size+" test"+((size>1)?"s":"")+" found:</font></b><br/>"
    						+resultsHtml);
    			} 
    			else {
    				return FormValidation.warningWithMarkup("Checking account... done.<br/>Validating settings... done.<br/>"
    						+"Warning, no tests found. Please check settings exist in account and try again.");
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
	
	private static String getUseMangoCommand(String projectId, String testName) {
		StringBuffer sb = new StringBuffer("\"C:\\Program Files (x86)\\Infuse Consulting\\useMango\\App\\MangoMotor.exe\"");
		sb.append(" -s \""+useMangoUrl+"\"");
		sb.append(" -p \""+projectId+"\"");
		sb.append(" -e \""+credentials.getUsername()+"\"");
		sb.append(" -a \""+credentials.getPassword().getPlainText()+"\"");
		sb.append(" --testname \""+testName+"\"");
		return sb.toString();
	}
	
	private Node getNode(List<Node> nodes) {
		Node nodeToUse = null;
		if(nodes == null || nodes.isEmpty()) {
			nodes = new ArrayList<Node>(Label.get(UseMangoNodeLabel.USEMANGO.toString()).getNodes());
		}
		for(Node tempNode : nodes) {
			nodeToUse = tempNode;
			break;
		}
		nodes.remove(nodeToUse);
		return nodeToUse;
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
