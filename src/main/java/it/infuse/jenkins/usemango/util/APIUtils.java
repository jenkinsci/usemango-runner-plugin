package it.infuse.jenkins.usemango.util;

import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import it.infuse.jenkins.usemango.model.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class APIUtils {

	static HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    static JsonFactory JSON_FACTORY = new JacksonFactory();
    final static String API_VERSION = "/v1";
	final static String ENDPOINT_PROJECTS 	= "/projects";
    final static String ENDPOINT_PROJECT 	= ENDPOINT_PROJECTS + "/%s";
    final static String ENDPOINT_TESTINDEX = ENDPOINT_PROJECT + "/testindex";
    final static String ENDPOINT_USERS = "/users";
    final static String ENDPOINT_PROJECT_TAGS = ENDPOINT_PROJECT + "/testtags";
    final static String ENDPOINT_TEST_SCENARIOS = ENDPOINT_PROJECT + "/tests/%s/scenarios";

	public static String getTestServiceUrl() {
		String testServiceURL = System.getenv("UM_TEST_SERVICE_URL");
		if (testServiceURL == null) {
			testServiceURL = "https://tests.api.usemango.co.uk";
		}
		return testServiceURL + API_VERSION;
	}

	public static String getTestAppUrl() {
		String testAppURL = System.getenv("UM_TEST_APP_URL");
		if (testAppURL == null) {
			return "https://app.usemango.co.uk";
		}
		return testAppURL;
	}

	public static TestIndexResponse getTestIndex(TestIndexParams params, String idToken) throws IOException {
		HttpRequestFactory requestFactory = HTTP_TRANSPORT.createRequestFactory(
				(HttpRequest request) -> {request.setParser(new JsonObjectParser(JSON_FACTORY));
        });
		Log.fine("Loading test index with filters: " + new Gson().toJson(params));
		TestIndexResponse response = null;
		while(true) { // handle pagination
		    GenericUrl url = new GenericUrl(getTestServiceUrl());
			url.setRawPath(API_VERSION + String.format(ENDPOINT_TESTINDEX, params.getProjectId()));
			url.set("tags", params.getTags());
			url.set("filter", params.getTestName());
			url.set("status", params.getTestStatus());
			url.set("assignee", params.getAssignedTo());
			if(isAnotherPage(response)) url.set("cursor", response.getInfo().getNext());
			HttpRequest request = requestFactory.buildGetRequest(url);
			request.setHeaders(getHeadersForServer(idToken));
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
	public static List<Project> getProjects(String idToken) throws IOException {
		HttpRequestFactory requestFactory = HTTP_TRANSPORT.createRequestFactory(
				(HttpRequest request) -> {request.setParser(new JsonObjectParser(JSON_FACTORY));
        });
		GenericUrl url = new GenericUrl(getTestServiceUrl());
		url.setRawPath(API_VERSION + ENDPOINT_PROJECTS);
		HttpRequest request = requestFactory.buildGetRequest(url);
		request.setHeaders(getHeadersForServer(idToken));
		Log.fine("Loading projects.");
		HttpResponse response = request.execute();
		return (ArrayList<Project>)response.parseAs(new TypeToken<ArrayList<Project>>(){}.getType());
	}

	@SuppressWarnings("unchecked")
	public static List<String> getProjectTags(String idToken, String project) throws IOException {
		HttpRequestFactory requestFactory = HTTP_TRANSPORT.createRequestFactory(
				(HttpRequest request) -> {request.setParser(new JsonObjectParser(JSON_FACTORY));
				});
		GenericUrl url = new GenericUrl(getTestServiceUrl());
		url.setRawPath(API_VERSION + String.format(ENDPOINT_PROJECT_TAGS, project));
		HttpRequest request = requestFactory.buildGetRequest(url);
		request.setHeaders(getHeadersForServer(idToken));
		return (ArrayList<String>)request.execute().parseAs(new TypeToken<ArrayList<String>>(){}.getType());
	}

	@SuppressWarnings("unchecked")
	public static List<UmUser> getUsers(String idToken) throws IOException {
		HttpRequestFactory requestFactory = HTTP_TRANSPORT.createRequestFactory(
				(HttpRequest request) -> {request.setParser(new JsonObjectParser(JSON_FACTORY));
				});
		GenericUrl url = new GenericUrl(getTestServiceUrl());
		url.setRawPath(API_VERSION + ENDPOINT_USERS);
		HttpRequest request = requestFactory.buildGetRequest(url);
		request.setHeaders(getHeadersForServer(idToken));
		return (ArrayList<UmUser>)request.execute().parseAs(new TypeToken<ArrayList<UmUser>>(){}.getType());
	}

	@SuppressWarnings("unchecked")
	public static List<Scenario> getScenarios(String idToken, String projectId, String testId) throws IOException {
		HttpRequestFactory requestFactory = HTTP_TRANSPORT.createRequestFactory(
				(HttpRequest request) -> {request.setParser(new JsonObjectParser(JSON_FACTORY));
				});
		GenericUrl url = new GenericUrl(getTestServiceUrl());
		url.setRawPath(API_VERSION + String.format(ENDPOINT_TEST_SCENARIOS, projectId, testId));
		Log.info("Requesting scenarios for test '" + testId + "'");
		HttpRequest request = requestFactory.buildGetRequest(url);
		request.setHeaders(getHeadersForServer(idToken));
		HttpResponse response = request.execute();
		return (ArrayList<Scenario>)response.parseAs(new TypeToken<ArrayList<Scenario>>(){}.getType());
	}
	
	private static boolean isAnotherPage(TestIndexResponse response) {
		return response != null && response.getInfo() != null && response.getInfo().isHasNext();
	}

	private static HttpHeaders getHeadersForServer(String idToken){
		HttpHeaders headers = new HttpHeaders();
		headers.setAuthorization("Bearer " + idToken);
		return headers;
	}
}