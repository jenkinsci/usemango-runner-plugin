package it.infuse.jenkins.usemango;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;

import it.infuse.jenkins.usemango.UseMangoConfiguration;

import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class UseMangoConfigurationTest {

    @Rule
    public RestartableJenkinsRule rr = new RestartableJenkinsRule();

    /**
     * Tries to exercise enough code paths to catch common mistakes:
     * <ul>
     * <li>missing {@code load}
     * <li>missing {@code save}
     * <li>misnamed or absent getter/setter
     * <li>misnamed {@code textbox}
     * </ul>
     */
    @Test
    public void uiAndStorage() {
        rr.then(r -> {
            assertNull("not set initially", UseMangoConfiguration.get().getLocation());
            HtmlForm config = r.createWebClient().goTo("configure").getFormByName("config");
            HtmlTextInput textbox = config.getInputByName("_.location");
            textbox.setText("hello");
            r.submit(config);
            assertEquals("global config page let us edit it", "hello", UseMangoConfiguration.get().getLocation());
        });
        rr.then(r -> {
            assertEquals("still there after restart of Jenkins", "hello", UseMangoConfiguration.get().getLocation());
        });
    }

}
