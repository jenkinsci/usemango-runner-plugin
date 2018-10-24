package it.infuse.jenkins.usemango;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import org.acegisecurity.AccessDeniedException;

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
import it.infuse.jenkins.usemango.model.TestIndexItem;
import it.infuse.jenkins.usemango.util.ProjectUtils;

public class UseMangoTestTask extends AbstractQueueTask implements AccessControlled {

	private final String nodeLabel;
	private final AbstractBuild<?,?> build;
	private final BuildListener listener;
	private final TestIndexItem test;
	private final String command;

    public UseMangoTestTask(String nodeLabel, AbstractBuild<?,?> build, BuildListener listener, 
    		TestIndexItem test, String command) {
    	this.build = build;
        this.listener = listener;
        this.test = test;
        this.command = command;
        this.nodeLabel = nodeLabel;
    }

    public String getName() {
    	return ProjectUtils.getJenkinsTestTaskName(test.getName(), build.getParent().getName(), build.number);
    }

    public String getFullDisplayName() {
        return this.getName();
    }

    public String getUrl() {
        return build.getUrl();
    }

    public String getDisplayName() {
        return this.getName();
    }

    @Override
    public Label getAssignedLabel() {
        return Label.get(this.nodeLabel);
    }

    @Override
    public Node getLastBuiltOn() {
        return build.getParent().getLastBuiltOn();
    }

    @Override
    public long getEstimatedDuration() {
        return build.getEstimatedDuration();
    }

	@Override
	public Collection<? extends SubTask> getSubTasks() {
		ArrayList<SubTask> tasks = new ArrayList<SubTask>();
		tasks.add(this);
		return tasks;
	}

	@Override
	public void checkPermission(Permission p) throws AccessDeniedException {
		build.checkPermission(p);
	}

	@Override
	public ACL getACL() {
		return build.getProject().getACL();
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
    	return new UseMangoTestExecutor(this, build.getWorkspace(), listener, test, command);
    }
}
