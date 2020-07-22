package it.infuse.jenkins.usemango;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.model.BuildListener;
import hudson.model.Executor;
import hudson.model.Node;
import hudson.model.Queue.Executable;
import hudson.model.Queue.Task;
import hudson.remoting.VirtualChannel;
import hudson.util.ArgumentListBuilder;
import it.infuse.jenkins.usemango.model.ExecutableTest;
import it.infuse.jenkins.usemango.model.Scenario;
import it.infuse.jenkins.usemango.util.ProjectUtils;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

public class UseMangoTestExecutor implements Executable {

	private final Task task;
	private final FilePath workspace;
	private final BuildListener listener;
	private final ExecutableTest test;
	private final String projectId;
	private final StandardUsernamePasswordCredentials credentials;

    public UseMangoTestExecutor(Task task, FilePath workspace, BuildListener listener,
			ExecutableTest test, String projectId,
            StandardUsernamePasswordCredentials credentials){
    	this.task = task;
    	this.workspace = workspace;
        this.listener = listener;
        this.test = test;
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
                ByteArrayOutputStream out = null;
		    	try {
                    String umAppData;
                    //User's home directory
                    String userHome = Objects.requireNonNull(currentNode.toComputer()).getSystemProperties().get("user.home").toString();
                    umAppData = userHome + "\\AppData\\Roaming\\useMango";

                    String motorPath = getMotorPath(umAppData, currentNode.getChannel());
                    ArgumentListBuilder args = getUMCommandArgs(motorPath);

                    listener.getLogger().println("START: Executing test '"+test.getName()+"' on Windows node "+currentNode.getNodeName());

                    Launcher launcher = currentNode.createLauncher(listener);
                    ProcStarter starter = launcher.new ProcStarter();

		    		// send test output to byte stream
		    		out = new ByteArrayOutputStream();
		    		starter = starter.cmds(args).stdout(out);

		    		 // run command
					int exitCode = launcher.launch(starter).join();

					String stdout = out.toString(StandardCharsets.UTF_8.name());

					// write outcome to listener (console)
					if(exitCode == 0) {
						test.setPassed(true);
						listener.getLogger().println("PASS: Test '"+test.getName()+"' passed");
					}
		            else {
		            	test.setPassed(false);
		            	listener.getLogger().println("FAIL: Test '"+test.getName()+"' failed");
		            }

		            String logsPath = umAppData + "\\Logs";
					FilePath junitFile = new FilePath(currentNode.getChannel(), logsPath + "\\junit.xml");

					if (!junitFile.exists()) {
						throw new IOException("useMango Junit log file not found at path '" + junitFile);
					}

					String junit = IOUtils.toString(junitFile.read(), StandardCharsets.UTF_8.name());

					//Setting executionId
					String subText = "runId=\"";
					String exId = junit.substring(junit.indexOf(subText) + subText.length());
					exId = exId.substring(0, exId.indexOf("\""));
					test.setRunId(exId);

					// write byte stream to workspace (log)
					ProjectUtils.createLogFile(workspace, test, stdout, listener);

					// write result to workspace (junit)
					workspace.child(ProjectUtils.RESULTS_DIR).
							child(ProjectUtils.getJUnitFileName(test)).write(junit, StandardCharsets.UTF_8.name());

					listener.getLogger().println("STOP: Outcome saved to workspace for test '" + test.getName() + "'");
				}
		    	catch (IOException | IllegalArgumentException | InterruptedException | NullPointerException e) {
					if (workspace != null) {
						ProjectUtils.createLogFile(workspace, test, e.getMessage(), listener);
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
	    		ProjectUtils.createLogFile(workspace, test, failureMessage, listener);
	    		listener.error(failureMessage);
	    	}
    	}
    	else {
    		test.setPassed(false);
    		String failureMessage = "Failed to execute test: Node is null.";
    		ProjectUtils.createLogFile(workspace, test, failureMessage, listener);
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
		args.addTokenized(" -p \""+projectId+"\"");
		args.addTokenized(" -i \""+test.getId()+"\"");

		Scenario scenario = test.getScenario();
		if (scenario != null) {
			args.addTokenized(" -s \""+scenario.getId()+"\"");
		}

		args.addTokenized(" -e \""+credentials.getUsername()+"\"");
		args.addTokenized(" -a ");
		args.addMasked(credentials.getPassword().getPlainText());
		return args;
    }


    private String getMotorPath(String umAppData, VirtualChannel channel){
    	try {
    		String umAppDirectory = umAppData + "\\app";

			//Selecting app branch - dev, qa or public
			List<FilePath> appBranches = new FilePath(channel, umAppDirectory).listDirectories();
			if(appBranches.stream().anyMatch(b -> b.getName().equalsIgnoreCase("dev"))){
				umAppDirectory += "\\dev";
			}
			else if (appBranches.stream().anyMatch(b -> b.getName().equalsIgnoreCase("qa"))) {
				umAppDirectory += "\\qa";
			}
			else {
				umAppDirectory += "\\public";
			}

			//Selecting app version, selecting the highest
			List<FilePath> appVersions = new FilePath(channel, umAppDirectory).listDirectories();
			appVersions.sort((o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));
			FilePath app = appVersions.get(appVersions.size() - 1);

			String appPath = app.toURI().toString().replace("file:/", "");
			return appPath + "MangoMotor.exe";
		}
		catch (NullPointerException | IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
}
