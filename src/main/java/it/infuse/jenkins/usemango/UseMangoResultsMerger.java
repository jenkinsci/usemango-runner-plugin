package it.infuse.jenkins.usemango;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import it.infuse.jenkins.usemango.exception.UseMangoException;
import it.infuse.jenkins.usemango.util.JUnitMerger;
import it.infuse.jenkins.usemango.util.ProjectUtils;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

public class UseMangoResultsMerger extends Notifier {

    private String destinationFolder;
    private String filename;

    @DataBoundConstructor
    public  UseMangoResultsMerger(String destinationFolder, String filename){
        this.destinationFolder = destinationFolder;
        this.filename = filename;
    }

    public String getDestinationFolder () {
        return destinationFolder;
    }

    public String getFilename() {
        return filename;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

        Function<String, Boolean> logErrorAndReturn = msg -> {
            listener.error(msg);
            return false;
        };
        PrintStream jobLogger = listener.getLogger();

        if(!ProjectUtils.hasCorrectPermissions(User.current())){
            String msg = "Jenkins user '"+User.current()+"' does not have permissions to configure and build this Job - please contact your system administrator, or update the users' security settings.";
            return logErrorAndReturn.apply(msg);
        }

        FilePath workspace = build.getWorkspace();
        if (workspace == null) {
            String msg = "Failed to combine useMango test results, unable to access the jobs's workspace.";
            return logErrorAndReturn.apply(msg);
        }

        FilePath resultsDir = workspace.child(ProjectUtils.RESULTS_DIR);
        if (!resultsDir.exists()) {
            String msg = "Failed to combine useMango test results, directory containing the result files does not exist.";
            return logErrorAndReturn.apply(msg);
        }

        List<FilePath> resultFiles = resultsDir.list(new SuffixFileFilter(".xml"));
        if (resultFiles.size() < 1) {
            jobLogger.println("useMango test results weren't combined, results directory doesn't contain any files.\n");
            return true;
        }

        String mergedXML;
        try {
            mergedXML = JUnitMerger.merge(resultFiles);
        }
        catch (UseMangoException e) {
            listener.error(e.getMessage());
            return false;
        }

        String combinedFile = destinationFolder + "/" + filename;
        workspace.child(combinedFile).write(mergedXML, StandardCharsets.UTF_8.name());
        jobLogger.println("Finished combining useMango test results. File can be found at '" + combinedFile + "'");
        return true;
    }

    @Extension
    public final static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        public FormValidation doCheckDestinationFolder(@AncestorInPath AbstractProject project,
            @QueryParameter String destinationFolder) throws IOException, ServletException
        {
            if (project == null || !project.hasPermission(Item.WORKSPACE)) return FormValidation.ok();

            FilePath workspace = project.getSomeWorkspace();
            if (workspace == null) return FormValidation.ok();

            FormValidation validation = workspace.validateFileMask(destinationFolder, false, false);
            if (validation.kind == FormValidation.Kind.WARNING) {
                return FormValidation.warning(validation.getMessage() + ". Any missing directories will be created at the time of execution.");
            }
            return validation;
        }

        public FormValidation doCheckFilename(@QueryParameter String filename)
                throws IOException, ServletException
        {
            if (StringUtils.isNotBlank(filename)) {
                return FormValidation.ok();
            }
            return FormValidation.error("Must specify a destination file name");
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return FreeStyleProject.class.isAssignableFrom(jobType);
        }

        @Override
        public String getDisplayName() {
            return "Merge useMango test results";
        }
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }
}
