package it.infuse.jenkins.usemango;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Util;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.security.ACL;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import it.infuse.jenkins.usemango.exception.UseMangoException;
import it.infuse.jenkins.usemango.model.Project;
import it.infuse.jenkins.usemango.model.*;
import it.infuse.jenkins.usemango.util.APIUtils;
import it.infuse.jenkins.usemango.util.AuthUtil;
import it.infuse.jenkins.usemango.util.Log;
import it.infuse.jenkins.usemango.util.ProjectUtils;
import jenkins.model.Jenkins;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import javax.servlet.ServletException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class UseMangoBuilder extends Builder implements BuildStep {

	private static StandardUsernamePasswordCredentials credentials;
	private static String ID_TOKEN = null;
	private static String REFRESH_TOKEN = null;

	private String useSlaveNodes;
	private String nodeLabel;
	private String projectId;
	private String tags;
	private String testName;
	private String testStatus;
	private String assignedTo;

	@DataBoundConstructor
	public UseMangoBuilder(String useSlaveNodes, String nodeLabel, String projectId, String tags, String testName,
			String testStatus, String assignedTo) {
		this.useSlaveNodes = useSlaveNodes;
		this.nodeLabel = nodeLabel;
		this.projectId = projectId;
		this.tags = tags;
		this.testName = testName;
		this.testStatus = testStatus;
		this.assignedTo = assignedTo;
	}
	
	@SuppressFBWarnings(value="NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
	@Override
	public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener)
			throws InterruptedException, IOException{

		List<UseMangoTestTask> testTasks = new ArrayList<UseMangoTestTask>();
		UseMangoTestResultsAction umTestResultsAction = new UseMangoTestResultsAction(APIUtils.getTestAppUrl());

		try {
			if(!ProjectUtils.hasCorrectPermissions(User.current())){
				String msg = "Jenkins user '"+User.current()+"' does not have permissions to configure and build this Job - please contact your system administrator, or update the users' security settings.";
				umTestResultsAction.setBuildException(msg);
				listener.error(msg);
				return false;
			}

			prepareWorkspace(build.getWorkspace());

			boolean useSlaves = Boolean.parseBoolean(useSlaveNodes);
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
			params.addTags(this.tags);
			params.setProjectId(this.projectId);
			params.setTestName(this.testName);
			params.setTestStatus(this.testStatus);
			listener.getLogger().println("TestIndex API parameters:\n"+gson.toJson(params));

			TestIndexResponse indexes = null;
			indexes = getTestIndexes(params);

			if(indexes != null && indexes.getItems() != null && indexes.getItems().size() > 0) {

				build.getWorkspace().child(ProjectUtils.LOG_DIR).mkdirs();
				build.getWorkspace().child(ProjectUtils.RESULTS_DIR).mkdirs();

				listener.getLogger().println(indexes.getItems().size() + " tests retrieved:\n"+gson.toJson(indexes.getItems()));

				List<String> logList = new ArrayList<>();
				List<ExecutableTest> executions = new ArrayList<>();
				BiConsumer<TestIndexItem, Scenario> queueTest = (indexItem, scenario) -> {
					ExecutableTest exeTest = new ExecutableTest(indexItem, scenario);
					UseMangoTestTask testTask = new UseMangoTestTask(nodeLabel, build, listener, exeTest, projectId, credentials);
					executions.add(exeTest);
					testTasks.add(testTask);
					Jenkins.getInstance().getQueue().schedule2(testTask, Jenkins.getInstance().getQuietPeriod());
					logList.add(ProjectUtils.getLogFileName(exeTest));
				};
				indexes.getItems().forEach((test) -> {
					try {
						if (test.getHasScenarios()){
							List<Scenario> scenarios = getTestScenarios(this.projectId, test.getId());
							scenarios.forEach((scenario) -> {
								queueTest.accept(test, scenario);
							});
						}
						else {
							queueTest.accept(test, null);
						}
					} catch(Exception e) {
						listener.error("Error executing test: "+test.getName());
						throw new RuntimeException(e);
					}
				});

				while(!logList.isEmpty()) { // keep alive until all test tasks are done
					String logFileName = null;
					for(String item : logList) {
						if(build.getWorkspace().child(ProjectUtils.LOG_DIR).
								child(item).exists()) {
							logFileName = item;
						}
					}
					if(logFileName != null) logList.remove(logFileName);
					Thread.sleep(1000);
				}

				boolean testSuitePassed = true;
				for(ExecutableTest test : executions) {
					if (!test.isPassed()) {
						testSuitePassed = false;
						break;
					}
				}

				if(!testSuitePassed) build.setResult(Result.FAILURE); // set job to failure if a test failed

				listener.getLogger().println("\nTest execution complete.\n\nThank you for using useMango :-)\n");

				umTestResultsAction.addTestResults(executions);
			}
			else {
				String msg = "No tests retrieved from useMango account, please check settings and try again.";
				umTestResultsAction.setBuildException(msg);
				listener.getLogger().println(msg);
			}
			return true;
		}
		catch (RuntimeException | UseMangoException e) {
			umTestResultsAction.setBuildException(e.getMessage());
			throw new RuntimeException(e);
		}
		catch (InterruptedException e) {
			for (UseMangoTestTask task: testTasks) {
				Jenkins.getInstance().getQueue().cancel(task);
			}
			umTestResultsAction.setBuildException("Build was aborted, all useMango tests were cancelled.");
			throw e;
		}
		finally {
			build.addAction(umTestResultsAction);
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

		public ListBoxModel doFillAssignedToItems() {
			ListBoxModel items = new ListBoxModel();
			items.add("Anybody");
			items.add("Nobody");
			try {
				List<UmUser> users = getUsers();
				if(users != null) {
					users.forEach(user->{
						items.add(user.getName() + " (" + user.getEmail() + ")");
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

            List<String> projectTags = new ArrayList<>();
			try {
				projectTags = getProjectTags(value);
			}
			catch(IOException | UseMangoException e) {
				e.printStackTrace(System.out);
			}

			if(projectTags != null && !projectTags.isEmpty()) {
				StringBuilder resultsHtml = new StringBuilder("<label><strong>Available tags:</strong></label>");
				resultsHtml.append("<div id=\"tempTagsContainer\"style=\"max-height:150px;overflow-y:scroll;padding-top:5px;padding-bottom:5px;\">");
				resultsHtml.append("<ul>");
				projectTags.forEach((item)->{
					String tag = Util.escape(item);
					resultsHtml.append("<li>"+tag+"</li>");
				});
				resultsHtml.append("</ul>");
				resultsHtml.append("</div>");
				return FormValidation.okWithMarkup(resultsHtml.toString());
			}
			else {
				return FormValidation.warningWithMarkup("Project doesn't have any test tags.");
			}
        }

    	public FormValidation doValidateSettings(
    			@QueryParameter("projectId") final String projectId,
    			@QueryParameter("tags") final String tags,
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
	    		    List<UmUser> users = getUsers();
	    		    String userId = assignedTo;
	    			if (!userId.contains("Anybody") && !userId.contains("Nobody")){
	    			    String userEmail = StringUtils.substringBetween(userId, "(", ")");
	    			    userId = users.stream().filter(u -> u.getEmail().equals(userEmail)).collect(Collectors.toList()).get(0).getId();
	    			}
	    			TestIndexParams params = new TestIndexParams();
	    			params.setAssignedTo(userId);
	    			params.addTags(tags);
	    			params.setProjectId(projectId);
	    			params.setTestName(testName);
	    			params.setTestStatus(testStatus);
	    			TestIndexResponse indexes = getTestIndexes(params);

					String umURL = Util.escape(APIUtils.getTestServiceUrl());
					String userName = Util.escape(credentials.getUsername());

	    			StringBuilder sb = new StringBuilder();
					sb.append("Connecting to <a href=\""+umURL+"\" target=\"_blank\">"+umURL+"</a>... done.<br/>");
					sb.append("Validating account "+userName+"... done.<br/><br/>");
	    			
	    			if(indexes != null && indexes.getItems() != null && !indexes.getItems().isEmpty()) {
	    				int size = indexes.getItems().size();
	    				
	    				StringBuilder resultsHtml = new StringBuilder("<div style=\"max-height:300px;overflow-y:scroll;padding-top:5px;\">");
	    				resultsHtml.append("<table width=\"100%\" border=\"0\" cellspacing=\"6\" cellpadding=\"6\" style=\"border:1px solid rgba(0, 0, 0, 0.1);width:100%;background-color:#eee;\">");
	    				resultsHtml.append("<tr style=\"background-color:rgba(0, 0, 0, 0.1);\" align=\"left\"><th>Name</th><th>Tags</th><th>Status</th><th>Assigned To</th><th>Scenarios</th></tr>");
						indexes.getItems().forEach((item)-> {
							String name = Util.escape(item.getName());
							List<String> tagsArray = item.getTags().stream().map(t -> Util.escape(t)).collect(Collectors.toList());
							String testTags = String.join(", ", tagsArray);
							String status = Util.escape(item.getStatus());
							String assignee = Util.escape(item.getAssignee());
							if (!item.getAssignee().isEmpty()) {
								List<UmUser> foundUsers = users.stream().filter(u -> u.getId().equals(item.getAssignee())).collect(Collectors.toList());
								if (foundUsers.size() > 0) {
									assignee = Util.escape(foundUsers.get(0).getName() + " (" + foundUsers.get(0).getEmail() + ")");
								}
							}
							String scenarioCount = "0";
							if (item.getHasScenarios()) {
								try {
									scenarioCount = String.valueOf(getTestScenarios(projectId, item.getId()).size());
								}
								catch (IOException | UseMangoException e){
									scenarioCount = "Failed to load scenarios for test: " + e.getMessage();
								}
							}
							resultsHtml.append("<tr><td>" + name + "</td><td>" + testTags + "</td><td>" + status + "</td><td>" + assignee + "</td><td>" + scenarioCount + "</td></tr>");
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
		List<StandardUsernamePasswordCredentials> credentialsList = CredentialsProvider.lookupCredentials(
				StandardUsernamePasswordCredentials.class, Jenkins.getInstance(), ACL.SYSTEM,
					Collections.<DomainRequirement> emptyList());
		return CredentialsMatchers.firstOrNull(credentialsList,
                CredentialsMatchers.allOf(CredentialsMatchers.withId(credentialsId)));
	}
	
	private static void loadUseMangoCredentials() throws UseMangoException {
		credentials = getCredentials(UseMangoConfiguration.get().getCredentialsId());

		if(credentials == null) 
			throw new UseMangoException("Invalid useMango credentials in global configuration (<a href=\"/configure\">view config</a>)");
	}

	private static void getTokens() throws UseMangoException {
		loadUseMangoCredentials();
		if(credentials == null) throw new UseMangoException("Credentials are null, please check useMango global config");
		String[] tokens = AuthUtil.getAuthTokens(credentials.getUsername(), credentials.getPassword().getPlainText());
		ID_TOKEN = tokens[0];
		REFRESH_TOKEN = tokens[1];
	}

	private static void refreshIdToken() throws UseMangoException{
		try{
			String[] tokens = AuthUtil.refreshAuthTokens(REFRESH_TOKEN);
			ID_TOKEN = tokens[0];
			REFRESH_TOKEN = tokens[1];
		} catch (UseMangoException e){
			ID_TOKEN = null;
			REFRESH_TOKEN = null;
			// Only handling the expired refresh token exception here other exceptions thrown will be related to other issues
			String msg = e.getMessage();
			Log.severe("Refreshing auth tokens failed: '" + msg + "'");
			if (msg.contains("Expired refresh token") || msg.contains("Missing refresh token")){
				getTokens();
			}
		}
	}

	private static boolean isTokenExpired(){
		String base64EncodedBody = ID_TOKEN.split("\\.")[1];
		Base64 base64Url = new Base64(true);
		String body = new String(base64Url.decode(base64EncodedBody), StandardCharsets.UTF_8);
		JsonObject jsonBody = new JsonParser().parse(body).getAsJsonObject();
		long expiry = jsonBody.get("exp").getAsLong();
		Date expiring = new Date(expiry * 1000);
		Date now = new Date();
		return expiring.before(now);
	}

	private static void checkTokenExistsAndValid() throws UseMangoException {
		if(ID_TOKEN == null){
			getTokens();
		}
		else if(isTokenExpired()){
			refreshIdToken();
		}
	}

	private static TestIndexResponse getTestIndexes(TestIndexParams params) throws IOException, UseMangoException {
		checkTokenExistsAndValid();
		if(params == null) throw new UseMangoException("Test parameters are null, please check useMango build step in job");
		return APIUtils.getTestIndex(params, ID_TOKEN);
	}

	private static List<Scenario> getTestScenarios(String projectId, String testId) throws  IOException, UseMangoException {
		checkTokenExistsAndValid();
		return APIUtils.getScenarios(ID_TOKEN, projectId, testId);
	}

	
	private static List<Project> getProjects() throws IOException, UseMangoException {
		checkTokenExistsAndValid();
		return APIUtils.getProjects(ID_TOKEN);
	}

	private static List<String> getProjectTags(String projectId) throws IOException, UseMangoException{
		checkTokenExistsAndValid();
		return APIUtils.getProjectTags(ID_TOKEN, projectId);
	}

	private static List<UmUser> getUsers() throws IOException, UseMangoException {
		checkTokenExistsAndValid();
		return APIUtils.getUsers(ID_TOKEN);
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
	 * @return the tags
	 */
	public String getTags() {
		return tags;
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
