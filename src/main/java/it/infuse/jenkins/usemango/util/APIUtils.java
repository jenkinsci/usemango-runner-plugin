package it.infuse.jenkins.usemango.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpStatus;

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
import com.google.gson.reflect.TypeToken;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import it.infuse.jenkins.usemango.exception.UseMangoException;
import it.infuse.jenkins.usemango.model.Project;
import it.infuse.jenkins.usemango.model.TestIndexParams;
import it.infuse.jenkins.usemango.model.TestIndexResponse;

public class APIUtils {

	static HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    static JsonFactory JSON_FACTORY = new JacksonFactory();
    
    final static String ENDPOINT_AUTHENTICATE 	= "/v1.5/authenticate";
    final static String ENDPOINT_PROJECTS 	= "/v1.5/projects";
    final static String ENDPOINT_TESTINDEX 	= "/v1.5/projects/%s/testindex";

    private static String ID_TOKEN;
    
	public static void authenticate(String useMangoUrl, String email, String password) throws UseMangoException, IOException {
		HttpRequestFactory requestFactory = HTTP_TRANSPORT.createRequestFactory();
		GenericUrl url = new GenericUrl(useMangoUrl);
		url.setRawPath(ENDPOINT_AUTHENTICATE);
		GenericData data = new GenericData();
		data.put("UserName", email);
		data.put("Password", password);
		JsonHttpContent httpContent = new JsonHttpContent(new JacksonFactory(), data);
		HttpRequest request = requestFactory.buildPostRequest(url, httpContent);
		HttpResponse response = request.execute();
		if(response != null) {
			if(response.getStatusCode() == HttpStatus.SC_OK) {
				String responseJson = response.parseAsString();
				JsonObject tokenJson = new JsonParser().parse(responseJson).getAsJsonObject();
				String idToken = tokenJson.get("IdToken").getAsString();
				if(idToken != null) {
					ID_TOKEN = idToken;
				}
				else throw new UseMangoException("No Id token returned from "+useMangoUrl+ENDPOINT_AUTHENTICATE);
			}
			else throw new UseMangoException("Invalid response from "+useMangoUrl+ENDPOINT_AUTHENTICATE+" - status code: "+response.getStatusCode());
		}
		else throw new UseMangoException("Error retrieving tokens from "+useMangoUrl+ENDPOINT_AUTHENTICATE+" - response is null");
	}
	
	public static TestIndexResponse getTestIndex(String useMangoUrl, TestIndexParams params) throws IOException {
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
			request.setHeaders(getHeadersForServer());
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
	public static List<Project> getProjects(String useMangoUrl) throws IOException {
		HttpRequestFactory requestFactory = HTTP_TRANSPORT.createRequestFactory(
				(HttpRequest request) -> {request.setParser(new JsonObjectParser(JSON_FACTORY));
        });
		GenericUrl url = new GenericUrl(useMangoUrl);
		url.setRawPath(String.format(ENDPOINT_PROJECTS));
		HttpRequest request = requestFactory.buildGetRequest(url);
		request.setHeaders(getHeadersForServer());
		return (ArrayList<Project>)request.execute().parseAs(new TypeToken<ArrayList<Project>>(){}.getType());
	}
	
	private static boolean isAnotherPage(TestIndexResponse response) {
		if(response != null && response.getInfo() != null && response.getInfo().isHasNext()) {
			return true;
		}
		else return false;
	}

	private static HttpHeaders getHeadersForServer(){
		HttpHeaders headers = new HttpHeaders();
		headers.setAuthorization("Bearer " + ID_TOKEN);
		return headers;
	}
	
}