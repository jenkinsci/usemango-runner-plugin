package it.infuse.jenkins.usemango;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import org.apache.commons.io.IOUtils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Launcher.ProcStarter;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Executor;
import hudson.model.Node;
import hudson.model.Queue.Executable;
import hudson.model.Queue.Task;
import hudson.util.ArgumentListBuilder;
import it.infuse.jenkins.usemango.model.TestIndexItem;
import it.infuse.jenkins.usemango.util.ProjectUtils;

public class UseMangoTestExecutor implements Executable {

	private final Task task;
	private final FilePath workspace;
	private final BuildListener listener;
	private final TestIndexItem test;
	private final String useMangoUrl;
	private final String projectId;
	private final StandardUsernamePasswordCredentials credentials;

    public UseMangoTestExecutor(Task task, FilePath workspace, BuildListener listener,
    		TestIndexItem test, String useMangoUrl, String projectId,
            StandardUsernamePasswordCredentials credentials){
    	this.task = task;
    	this.workspace = workspace;
        this.listener = listener;
        this.test = test;
        this.useMangoUrl = useMangoUrl;
        this.projectId = projectId;
        this.credentials = credentials;
    }

    @Override
    public Task getParent() {
        return task;
    }

    @SuppressFBWarnings(value="NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
	@Override
    public synchronized void run() {

    	Node currentNode = Executor.of(this).getOwner().getNode();

    	if(currentNode != null) {

        	String operatingSystem = null;
    		try {
    			operatingSystem = currentNode.toComputer().getEnvironment().get("OS");
    		} catch (IOException | InterruptedException e) {
    			listener.error("Unable to determine OS for node '"+currentNode.getNodeName()+"', task stopped.");
    			e.printStackTrace(listener.getLogger());
    		}

    		if(operatingSystem != null && operatingSystem.toLowerCase().contains("windows")) {

    			String motorPath = getMotorPath(currentNode);
				ArgumentListBuilder args = getUMCommandArgs(motorPath);

	    		listener.getLogger().println("START: Executing test '"+test.getName()+"' on Windows node "+currentNode.getNodeName());

		    	Launcher launcher = currentNode.createLauncher(listener);
		    	ProcStarter starter = launcher.new ProcStarter();
		    	ByteArrayOutputStream out = null;
		    	try {

		    		// send test output to byte stream
		    		out = new ByteArrayOutputStream();
		    		starter = starter.cmds(args).stdout(out);

		    		 // run command
					int exitCode = launcher.launch(starter).join();

					String stdout = out.toString(StandardCharsets.UTF_8.name());

					// write byte stream to workspace (log)
					ProjectUtils.createLogFile(workspace, test.getId(), stdout, listener);

					// write outcome to listener (console)
					if(exitCode == 0) {
						test.setPassed(true);
						listener.getLogger().println("PASS: Test '"+test.getName()+"' passed");
					}
		            else {
		            	test.setPassed(false);
		            	listener.getLogger().println("FAIL: Test '"+test.getName()+"' failed");
		            }

		            String logsPath = stdout.substring(0, stdout.lastIndexOf("\\run.log"));
		            logsPath = logsPath.substring(logsPath.lastIndexOf("\n") + 1);

                    FilePath junitPath = new FilePath(currentNode.getChannel(), logsPath);
                    if (junitPath.exists()) {
                        String junit = IOUtils.toString(junitPath.child("\\junit.xml").read(), StandardCharsets.UTF_8.name());

                        // write result to workspace (junit)
                        workspace.child(ProjectUtils.RESULTS_DIR).
                                child(ProjectUtils.getJUnitFileName(test.getId())).write(junit, StandardCharsets.UTF_8.name());

                        listener.getLogger().println("STOP: Outcome saved to workspace for test '" + test.getName() + "'");
                    } else {
                        throw new IOException("useMango Junit log file not found at path '" + logsPath);
                    }

				} catch (IOException | IllegalArgumentException | InterruptedException | NullPointerException  e) {
					if (workspace != null) {
						ProjectUtils.createLogFile(workspace, test.getId(), e.getMessage(), listener);
					}
					listener.error(e.getMessage());
				}
		    	finally {
					try {
						if(out != null) out.close();
					} catch (IOException e) {
						listener.error(e.getMessage());
					}
		    	}
	    	}
	    	else {
	    		test.setPassed(false);
	    		String failureMessage = "Failed to execute test: Node '"+currentNode.getDisplayName()+"' does not have Windows OS.";
	    		ProjectUtils.createLogFile(workspace, test.getId(), failureMessage, listener);
	    		listener.error(failureMessage);
	    	}
    	}
    	else {
    		test.setPassed(false);
    		String failureMessage = "Failed to execute test: Node is null.";
    		ProjectUtils.createLogFile(workspace, test.getId(), failureMessage, listener);
    		listener.error(failureMessage);
    	}
    }

    @Override
    public long getEstimatedDuration() {
        return 60000l; // 1 minute
    }

    private ArgumentListBuilder getUMCommandArgs(String motorPath) {
    	ArgumentListBuilder args = new ArgumentListBuilder();
    	args.addTokenized(motorPath);
    	args.addTokenized(" -s \""+useMangoUrl+"\"");
		args.addTokenized(" -p \""+projectId+"\"");
		args.addTokenized(" --testname \""+test.getName()+"\"");
		args.addTokenized(" -e \""+credentials.getUsername()+"\"");
		args.addTokenized(" -a ");
		args.addMasked(credentials.getPassword().getPlainText());
		return args;
    }

    private String getMotorPath(Node node){
    	try {
    		//User's home directory
			String userHome = Objects.requireNonNull(node.toComputer()).getSystemProperties().get("user.home").toString();
			String umApp = userHome + "\\AppData\\Roaming\\useMango\\app";
			File app;

			//Selecting app branch - dev or public
			List<File> appBranches = Arrays.asList(Objects.requireNonNull(new File(umApp).listFiles(File::isDirectory)));
			if(appBranches.stream().anyMatch(b -> b.getName().equalsIgnoreCase("dev"))){
				app = appBranches.stream().filter(b -> b.getName().equalsIgnoreCase("dev")).findFirst().get();
			}
			else if (appBranches.stream().anyMatch(b -> b.getName().equalsIgnoreCase("qa"))) {
				app = appBranches.stream().filter(b -> b.getName().equalsIgnoreCase("qa")).findFirst().get();
			}
			else {
				app = appBranches.stream().filter(b -> b.getName().equalsIgnoreCase("public")).findFirst().get();
			}

			//Selecting app version, selecting the highest
			List<File> appVersions = Arrays.asList(Objects.requireNonNull(app.listFiles(File::isDirectory)));
			appVersions.sort((o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));

			app = appVersions.get(appVersions.size() - 1);
			return app.getAbsolutePath() + "\\MangoMotor.exe";
		}
		catch (InterruptedException | IOException | NullPointerException e) {
			throw new RuntimeException(e);
		}
	}

}
