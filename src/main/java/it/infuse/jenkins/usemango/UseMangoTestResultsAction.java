package it.infuse.jenkins.usemango;

import hudson.Util;
import hudson.model.Run;
import it.infuse.jenkins.usemango.model.ExecutableTest;
import it.infuse.jenkins.usemango.model.Scenario;
import it.infuse.jenkins.usemango.util.StringUtils;
import jenkins.model.RunAction2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UseMangoTestResultsAction implements RunAction2 {

    private transient Run run;
    private List<TestResult> tests;
    private String errorMessage;
    private final String serverLink;

    public UseMangoTestResultsAction(String serverLink) {
        this.serverLink = serverLink;
        this.tests = new ArrayList<>();
    }

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

    public String getErrorMessage() { return this.errorMessage; }

    public void addTestResults(List<ExecutableTest> executedTests){
        Map<String, List<ExecutableTest>> counting = executedTests.stream().collect(
                Collectors.groupingBy(ExecutableTest::getId));

        counting.forEach((id, testScenarios) -> {
            if (testScenarios.size() > 1) {
                boolean testPassed = true;
                List<TestResult> scenarioResults = new ArrayList<>();
                for (ExecutableTest exeScenario: testScenarios) {
                    if (!exeScenario.isPassed()) { testPassed = false; }
                    Scenario scenario = exeScenario.getScenario();
                    TestResult result = new TestResult(scenario.getName(), exeScenario.isPassed(), getReportLink(exeScenario.getRunId()), null);
                    scenarioResults.add(result);
                }
                this.tests.add(new TestResult(testScenarios.get(0).getName(), testPassed, null, scenarioResults));
            }
            else {
                ExecutableTest first = testScenarios.get(0);
                TestResult result = new TestResult(first.getName(), first.isPassed(), getReportLink(first.getRunId()), null);
                this.tests.add(result);
            }
        });
    }

    public void setBuildException(String errorMessage){
        this.errorMessage = Util.escape(errorMessage);
    }

    private String getReportLink(String runId) {
        if (!StringUtils.isBlank(runId)) {
            return serverLink + "/report.html?runId=" + runId;
        }
        else {
            return null;
        }
    }

    public static class TestResult {
        public final String name;
        public final String result;
        public final String reportLink;
        public final List<TestResult> scenarios;

        public TestResult(String name, Boolean passed, String reportLink, List<TestResult> scenarios){
            this.name = Util.escape(name);
            this.result = passed ? "Passed" : "Failed";
            this.reportLink = Util.escape(reportLink);
            this.scenarios = scenarios;
        }
    }
}

