package it.infuse.jenkins.usemango;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import hudson.Launcher.ProcStarter;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Node;
import hudson.model.Queue.Executable;
import hudson.model.Queue.Task;
import hudson.util.ArgumentListBuilder;
import it.infuse.jenkins.usemango.model.TestIndexItem;
import jenkins.model.queue.AsynchronousExecution;

public class UseMangoTestExecutor implements Executable {

	private final static String encoding = "UTF-8";
	
	private final Node node;
	private final Task parentTask;
	private final FilePath workspace;
	private final BuildListener listener;
	private final TestIndexItem test;
	private final String command;

    public UseMangoTestExecutor(Node node, Task parentTask, FilePath workspace, BuildListener listener, 
    		TestIndexItem test, String command) {
    	this.node = node;
    	this.parentTask = parentTask;
    	this.workspace = workspace;
        this.listener = listener;
        this.test = test;
        this.command = command;
    }

    @Override
    public Task getParent() {
        return parentTask;
    }

    @Override
    public void run() throws AsynchronousExecution {
    	listener.getLogger().println("\nExecuting test: "+test.getName());
    	
    	ArgumentListBuilder args = new ArgumentListBuilder();
    	args.addTokenized(command);
    	
    	Launcher launcher;
    	if(node.getChannel() != null)
    		launcher = new Launcher.RemoteLauncher(listener, node.getChannel(), false); // must be 'usemango' node
    	else 
    		launcher = new Launcher.LocalLauncher(listener); // default to local, must be Windows OS
    	
    	ProcStarter starter = launcher.new ProcStarter();
    	try {
    		
    		// send test output to byte stream
    		ByteArrayOutputStream out = new ByteArrayOutputStream();
    		starter = starter.cmds(args).stdout(out);
    		
    		 // run command
			int exitCode = launcher.launch(starter).join();
			
			// write to file on workspace
			if (workspace != null) workspace.child(test.getId()).write(out.toString(encoding), encoding);
			
			// Write outcome to listener
			if(exitCode == 0) listener.getLogger().println("Test successful");
            else listener.error("Test failed with exit code: "+exitCode);
			
		} catch (IOException | InterruptedException e) {
			if (workspace != null) {
				try {
					workspace.child(test.getId()).write(e.getMessage(), encoding);
				} catch (IOException | InterruptedException e1) {
					listener.error("Error writing test result to workspace: "+e1.getMessage());
				}
			}
			throw new RuntimeException(e);
		}
    }

    @Override
    public long getEstimatedDuration() {
        return -1;
    }

}
