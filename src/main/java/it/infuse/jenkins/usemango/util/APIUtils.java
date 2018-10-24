package it.infuse.jenkins.usemango.util;

import java.io.IOException;
import java.net.HttpCookie;
import java.util.ArrayList;
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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import it.infuse.jenkins.usemango.exception.UseMangoException;
import it.infuse.jenkins.usemango.model.Project;
import it.infuse.jenkins.usemango.model.TestIndexParams;
import it.infuse.jenkins.usemango.model.TestIndexResponse;

public class APIUtils {

	static HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    static JsonFactory JSON_FACTORY = new JacksonFactory();
    
    final static String ENDPOINT_SESSION 	= "/v1.5/session";
    final static String ENDPOINT_PROJECTS 	= "/v1.5/projects";
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
		
		TestIndexResponse response = null;
		while(true) { // handle pagination
		    GenericUrl url = new GenericUrl(useMangoUrl);
			url.setRawPath(String.format(ENDPOINT_TESTINDEX, params.getProjectId()));
			url.set("folder", params.getFolderName());
			url.set("filter", params.getTestName());
			url.set("status", params.getTestStatus());
			url.set("assignee", params.getAssignedTo());
			if(isAnotherPage(response)) url.set("cursor", response.getInfo().getNext());
			HttpRequest request = requestFactory.buildGetRequest(url);
			HttpHeaders headers = new HttpHeaders();
			headers.setCookie(authCookie.toString());
			request.setHeaders(headers);
			if(isAnotherPage(response)) {
				TestIndexResponse tmpResponse = request.execute().parseAs(TestIndexResponse.class);
				response.getItems().addAll(tmpResponse.getItems());
				response.getInfo().setHasNext(tmpResponse.getInfo().isHasNext());
				response.getInfo().setNext(tmpResponse.getInfo().getNext());
			}
			else {
				response = request.execute().parseAs(TestIndexResponse.class);
			}
			if(!response.getInfo().isHasNext()) break; // exit when no more pages
		}
		return response;
	}
	
	@SuppressWarnings("unchecked")
	public static List<Project> getProjects(String useMangoUrl, HttpCookie authCookie) throws IOException {
		HttpRequestFactory requestFactory = HTTP_TRANSPORT.createRequestFactory(
				(HttpRequest request) -> {request.setParser(new JsonObjectParser(JSON_FACTORY));
        });
		GenericUrl url = new GenericUrl(useMangoUrl);
		url.setRawPath(String.format(ENDPOINT_PROJECTS));
		HttpRequest request = requestFactory.buildGetRequest(url);
		HttpHeaders headers = new HttpHeaders();
		headers.setCookie(authCookie.toString());
		request.setHeaders(headers);
		return (ArrayList<Project>)request.execute().parseAs(new TypeToken<ArrayList<Project>>(){}.getType());
	}
	
	private static boolean isAnotherPage(TestIndexResponse response) {
		if(response != null && response.getInfo() != null && response.getInfo().isHasNext()) {
			return true;
		}
		else return false;
	}
	
	// testing
	public static void main(String[] args) {
		try {
			String username = "ian.bisset@infuse.it";
			String password = "usemangouser";
			String umUrl = "https://qa.usemango.co.uk";
			HttpCookie cookie = getSessionCookie(umUrl, username, password);
			TestIndexParams params = new TestIndexParams();
			params.setAssignedTo("");
			params.setFolderName("useMango");
			params.setProjectId("Ian");
			params.setTestName("");
			params.setTestStatus("");
			TestIndexResponse response = getTestIndex(umUrl, params, cookie);
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			System.out.println(gson.toJson(response));
			System.out.println("Items: "+response.getItems().size());
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
}