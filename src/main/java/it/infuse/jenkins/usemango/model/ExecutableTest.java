package it.infuse.jenkins.usemango.model;

public class ExecutableTest extends TestIndexItem {

    private String runId;
    private final Scenario scenario;
    private boolean passed = false;

    public ExecutableTest(TestIndexItem test, Scenario scenario)
    {
        super(test.getId(), test.getName(), test.getStatus(), test.getAssignee(),
                test.getLastModified(), test.getHasScenarios(), test.getTags());
        this.scenario = scenario;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public Scenario getScenario() { return  scenario; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ExecutableTest)) {
            return false;
        }
        return super.equals(o) && ((ExecutableTest) o).runId.equalsIgnoreCase(runId);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}