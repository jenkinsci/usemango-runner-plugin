package it.infuse.jenkins.usemango;

import java.io.IOException;

import hudson.Launcher.ProcStarter;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Queue.Executable;
import hudson.model.Queue.Task;
import hudson.util.ArgumentListBuilder;
import it.infuse.jenkins.usemango.model.TestIndexItem;
import jenkins.model.queue.AsynchronousExecution;

public class UseMangoTestExecutor implements Executable {

	private final AbstractBuild<?,?> build;
	private final Launcher launcher;
	private final BuildListener listener;
	private final TestIndexItem item;
	private final ArgumentListBuilder command;

    public UseMangoTestExecutor(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener, 
    		TestIndexItem item, ArgumentListBuilder command) {
    	this.build = build;
    	this.launcher = launcher;
        this.listener = listener;
        this.item = item;
        this.command = command;
    }

    @Override
    public Task getParent() {
        return build.getParent();
    }

    @Override
    public void run() throws AsynchronousExecution {
    	listener.getLogger().println("Executing test: "+item.getName());
    	ProcStarter starter = launcher.new ProcStarter();
    	starter = starter.cmds(command).stdout(listener);
    	try {
    		starter = starter.pwd(build.getWorkspace()).envs(build.getEnvironment(listener));
			Proc proc = launcher.launch(starter);
			int exitCode = proc.join();
            if(exitCode == 0) listener.getLogger().println("Test finished successfully");
            else listener.error("Test failed with exit code: "+exitCode);
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e.getMessage());
		}
    }

    @Override
    public long getEstimatedDuration() {
        return build.getEstimatedDuration();
    }

}
