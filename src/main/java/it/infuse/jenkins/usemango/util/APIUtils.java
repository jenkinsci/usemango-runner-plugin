package it.infuse.jenkins.usemango.util;

import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.gson.reflect.TypeToken;
import it.infuse.jenkins.usemango.model.Project;
import it.infuse.jenkins.usemango.model.TestIndexParams;
import it.infuse.jenkins.usemango.model.TestIndexResponse;
import it.infuse.jenkins.usemango.model.UmUser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class APIUtils {

	static HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    static JsonFactory JSON_FACTORY = new JacksonFactory();
    final static String API_VERSION = "/v1";
    final static String ENDPOINT_PROJECTS 	= "/projects";
    final static String ENDPOINT_TESTINDEX = "/projects/%s/testindex";
    final static String ENDPOINT_USERS = "/users";
    final static String ENDPOINT_PROJECT_TAGS = ENDPOINT_PROJECTS + "/%s/testtags";
	private static final Logger LOGGER = Logger.getLogger("useMangoRunner");

	public static String getTestServiceUrl() {
		String testServiceURL = System.getenv("UM_TESTSSERVICE_URL");
		if (testServiceURL == null) {
			testServiceURL = "https://tests.api.usemango.co.uk";
		}
		return testServiceURL + API_VERSION;
	}

	public static TestIndexResponse getTestIndex(TestIndexParams params, String idToken) throws IOException {
		HttpRequestFactory requestFactory = HTTP_TRANSPORT.createRequestFactory(
				(HttpRequest request) -> {request.setParser(new JsonObjectParser(JSON_FACTORY));
        });
		
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
		HttpResponse response = request.execute();
		return (ArrayList<Project>)response.parseAs(new TypeToken<ArrayList<Project>>(){}.getType());
	}

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
	
	private static boolean isAnotherPage(TestIndexResponse response) {
		if(response != null && response.getInfo() != null && response.getInfo().isHasNext()) {
			return true;
		}
		else return false;
	}

	private static HttpHeaders getHeadersForServer(String idToken){
		HttpHeaders headers = new HttpHeaders();
		headers.setAuthorization("Bearer " + idToken);
		return headers;
	}
}