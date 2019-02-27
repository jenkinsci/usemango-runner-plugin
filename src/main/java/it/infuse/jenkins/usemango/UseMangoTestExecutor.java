package it.infuse.jenkins.usemango;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
	private final String command;

    public UseMangoTestExecutor(Task task, FilePath workspace, BuildListener listener,
    		TestIndexItem test, String command) {
    	this.task = task;
    	this.workspace = workspace;
        this.listener = listener;
        this.test = test;
        this.command = command;
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

	    		listener.getLogger().println("START: Executing test '"+test.getName()+"' on Windows node "+currentNode.getNodeName());

	    		String[] parts = command.split(" --password ");
	    		String cmd = parts[0].concat(" -a ");

				ArgumentListBuilder args = new ArgumentListBuilder();
				args.addTokenized(cmd);
				args.addMasked(parts[1]);

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

}
