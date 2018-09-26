package it.infuse.jenkins.usemango;

import java.io.IOException;
import java.util.Collection;

import org.acegisecurity.AccessDeniedException;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.ResourceList;
import hudson.model.Queue.Executable;
import hudson.model.queue.AbstractQueueTask;
import hudson.model.queue.SubTask;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.util.ArgumentListBuilder;
import it.infuse.jenkins.usemango.model.TestIndexItem;

public class UseMangoTestTask extends AbstractQueueTask implements AccessControlled {

	private final AbstractBuild<?,?> build;
	private final Launcher launcher;
	private final BuildListener listener;
	private final TestIndexItem item;
	private final ArgumentListBuilder command;

    public UseMangoTestTask(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener, 
    		TestIndexItem item, ArgumentListBuilder command) {
    	this.build = build;
    	this.launcher = launcher;
        this.listener = listener;
        this.item = item;
        this.command = command;
    }

    public String getName() {
        return "test_"+item.getId();
    }

    public String getFullDisplayName() {
        return build.getDisplayName()+"_"+getName();
    }

    public String getUrl() {
        return build.getUrl();
    }

    public String getDisplayName() {
        return getFullDisplayName();
    }

    @Override
    public Label getAssignedLabel() {
        return build.getProject().getAssignedLabel();
    }

    @Override
    public Node getLastBuiltOn() {
        return build.getBuiltOn();
    }

    @Override
    public long getEstimatedDuration() {
        return build.getEstimatedDuration();
    }

	@Override
	public Collection<? extends SubTask> getSubTasks() {
		return build.getProject().getSubTasks();
	}

	@Override
	public void checkPermission(Permission p) throws AccessDeniedException {
		build.checkPermission(p);
	}

	@Override
	public ACL getACL() {
		return build.getACL();
	}

	@Override
	public boolean hasPermission(Permission p) {
		return build.getProject().hasPermission(p);
	}

	@Override
	public void checkAbortPermission() {
		build.getProject().checkAbortPermission();
	}

	@Override
	public String getWhyBlocked() {
		return build.getProject().getWhyBlocked();
	}

	@Override
	public boolean hasAbortPermission() {
		return build.getProject().hasAbortPermission();
	}

	@Override
	public boolean isBuildBlocked() {
		return build.getProject().isBuildBlocked();
	}

	@Override
	public ResourceList getResourceList() {
		return build.getProject().getResourceList();
	}
	
    public Executable createExecutable() throws IOException {
        return new UseMangoTestExecutor(build, launcher, listener, item, command);
    }
}
