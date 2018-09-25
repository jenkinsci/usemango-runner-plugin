package it.infuse.jenkins.usemango.util;

import java.io.IOException;
import java.net.HttpCookie;
import java.util.List;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.GenericData;

import it.infuse.jenkins.usemango.exception.UseMangoException;
import it.infuse.jenkins.usemango.model.TestIndexParams;
import it.infuse.jenkins.usemango.model.TestIndexResponse;

public class APIUtils {

	static HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    static JsonFactory JSON_FACTORY = new JacksonFactory();
    
    final static String ENDPOINT_SESSION 	= "/v1.5/session";
    final static String ENDPOINT_TESTINDEX 	= "/v1.5/projects/%s/testindex";
    
	public static HttpCookie getSessionCookie(String useMangoUrl, String email, String password) throws UseMangoException, IOException {
		HttpRequestFactory requestFactory = HTTP_TRANSPORT.createRequestFactory();
		GenericUrl url = new GenericUrl(useMangoUrl);
		url.setRawPath(ENDPOINT_SESSION);
		GenericData data = new GenericData();
		data.put("email", email);
		data.put("password", password);
		data.put("executionOnly", true);
		JsonHttpContent httpContent = new JsonHttpContent(new JacksonFactory(), data);
		HttpRequest request = requestFactory.buildPostRequest(url, httpContent);
		HttpResponse response = request.execute();
		List<HttpCookie> cookies = HttpCookie.parse(response.getHeaders().getFirstHeaderStringValue("Set-Cookie"));
		return cookies
				.stream()
				.filter(c -> c.getName().contains("Identity"))
				.findFirst()
				.orElseThrow(() -> new UseMangoException("Auth cookie not found in /session response"));
	}
	
	public static TestIndexResponse getTestIndex(String useMangoUrl, TestIndexParams params, HttpCookie authCookie) throws IOException {
		HttpRequestFactory requestFactory = HTTP_TRANSPORT.createRequestFactory(
				(HttpRequest request) -> {request.setParser(new JsonObjectParser(JSON_FACTORY));
        });
	    GenericUrl url = new GenericUrl(useMangoUrl);
		url.setRawPath(String.format(ENDPOINT_TESTINDEX, params.getProjectId()));
		url.set("folder", params.getFolderName());
		url.set("filter", params.getTestName());
		url.set("status", params.getTestStatus());
		url.set("assignee", params.getAssignedTo());
		HttpRequest request = requestFactory.buildGetRequest(url);
		HttpHeaders headers = new HttpHeaders();
		headers.setCookie(authCookie.toString());
		request.setHeaders(headers);
		return request.execute().parseAs(TestIndexResponse.class);
	}
	
}