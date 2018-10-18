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
	    		
				ArgumentListBuilder args = new ArgumentListBuilder();
				args.addTokenized(command);
	    	
		    	Launcher launcher = currentNode.createLauncher(listener);
		    	ProcStarter starter = launcher.new ProcStarter();
		    	ByteArrayOutputStream out = null;
		    	try {
		    		
		    		// send test output to byte stream
		    		out = new ByteArrayOutputStream();
		    		starter = starter.cmds(args).stdout(out);
		    		
		    		 // run command
					int exitCode = launcher.launch(starter).join();
					
					// write byte stream to workspace (log)
					workspace.child(ProjectUtils.LOG_DIR).child(ProjectUtils.getLogFileName(test.getId())).
						write(out.toString(StandardCharsets.UTF_8.name()), StandardCharsets.UTF_8.name());
					
					// write outcome to listener (console)
					if(exitCode == 0) {
						test.setPassed(true);
						listener.getLogger().println("PASS: Test '"+test.getName()+"' passed");
					}
		            else {
		            	test.setPassed(false);
		            	listener.getLogger().println("FAIL: Test '"+test.getName()+"' failed");
		            }
					
					// write result to workspace (junit xml)
					String junit = IOUtils.toString(currentNode.getRootPath().
							child("..\\programdata\\usemango\\logs\\junit.xml").read(), StandardCharsets.UTF_8.name());
					
					workspace.child(ProjectUtils.RESULTS_DIR).
						child(ProjectUtils.getJUnitFileName(test.getId())).write(junit, StandardCharsets.UTF_8.name());
					
					listener.getLogger().println("STOP: Outcome saved to workspace for test '"+test.getName()+"'");
					
				} catch (IOException | InterruptedException e) {
					if (workspace != null) {
						try {
							workspace.child(test.getId()).write(e.getMessage(), StandardCharsets.UTF_8.name());
						} catch (IOException | InterruptedException e1) {
							listener.error("Error writing test result to workspace: "+e1.getMessage());
						}
					}
					throw new RuntimeException(e);
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
	    		listener.error("Error executing task for test '"+test.getName()+"', node does not have Windows OS.");
	    	}
    	}
    	else {
    		listener.error("Error executing task for test '"+test.getName()+"', node is null.");
    	}
    }

    @Override
    public long getEstimatedDuration() {
        return 60000l; // 1 minute
    }

}
