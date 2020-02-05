package it.infuse.jenkins.usemango;

import hudson.model.Run;
import it.infuse.jenkins.usemango.model.TestIndexItem;
import jenkins.model.RunAction2;

import java.util.ArrayList;
import java.util.List;

public class UseMangoTestResultsAction implements RunAction2 {

    private transient Run run;
    private List<TestResult> tests;
    private String errorMessage;

    public UseMangoTestResultsAction() { }

    @Override
    public String getIconFileName() { return "graph.png"; }

    @Override
    public String getDisplayName() {
        return "useMango Test Results";
    }

    @Override
    public String getUrlName() {
        return "useMangoResults";
    }

    @Override
    public void onAttached(Run<?, ?> run) { this.run = run; }

    @Override
    public void onLoad(Run<?, ?> run) {
        this.run = run;
    }

    public Run getRun() {
        return run;
    }

    public List<TestResult> getTests() { return this.tests; }

    public String getErrorMessage() { return this.errorMessage; };

    public void addTestResults(List<TestIndexItem> tests, String link){
        this.tests = new ArrayList<TestResult>();
        for (TestIndexItem test: tests) {
            this.tests.add(new TestResult(test.getName(), test.isPassed(), link));
        }
    }

    public void setBuildException(String errorMessage){
        this.errorMessage = errorMessage;
    }

    public static class TestResult {
        public final String name;
        public final String result;
        public final String reportLink;

        public TestResult(String name, Boolean passed, String reportLink){
            this.name = name;
            this.result = passed ? "Passed" : "Failed";
            this.reportLink = reportLink;
        }
    }
}

