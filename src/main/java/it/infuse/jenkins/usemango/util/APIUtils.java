package it.infuse.jenkins.usemango.util;

import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.GenericData;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import it.infuse.jenkins.usemango.exception.UseMangoException;
import it.infuse.jenkins.usemango.model.Project;
import it.infuse.jenkins.usemango.model.TestIndexParams;
import it.infuse.jenkins.usemango.model.TestIndexResponse;
import it.infuse.jenkins.usemango.model.UmUser;
import org.apache.http.HttpStatus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class APIUtils {

	static HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    static JsonFactory JSON_FACTORY = new JacksonFactory();

    final static String API_VERSION = "/v2.1";
    final static String ENDPOINT_AUTHENTICATE 	= API_VERSION + "/authenticate";
    final static String ENDPOINT_REFRESH_TOKEN = API_VERSION + "/authenticate/refresh";
    final static String ENDPOINT_PROJECTS 	= API_VERSION + "/projects";
    final static String ENDPOINT_TESTINDEX = API_VERSION + "/projects/%s/testindex";
    final static String ENDPOINT_USERS = API_VERSION + "/users";
    final static String ENDPOINT_PROJECT_TAGS = ENDPOINT_PROJECTS + "/%s/testtags";
    
	public static String[] getAuthenticationTokens(String useMangoUrl, String email, String password) throws UseMangoException, IOException {
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
                String refreshToken = tokenJson.get("RefreshToken").getAsString();
				if(idToken != null && refreshToken != null) {
					String[] tokens = new String[2];
				    tokens[0] = idToken;
				    tokens[1] = refreshToken;
				    return tokens;
				}
				else throw new UseMangoException("No Id token returned from "+useMangoUrl+ENDPOINT_AUTHENTICATE);
			}
			else throw new UseMangoException("Invalid response from "+useMangoUrl+ENDPOINT_AUTHENTICATE+" - status code: "+response.getStatusCode());
		}
		else throw new UseMangoException("Error retrieving tokens from "+useMangoUrl+ENDPOINT_AUTHENTICATE+" - response is null");
	}

	public static String refreshIdToken(String useMangoUrl, String refreshToken) throws IOException, UseMangoException {
        HttpRequestFactory requestFactory = HTTP_TRANSPORT.createRequestFactory();
        GenericUrl url = new GenericUrl(useMangoUrl);
        url.setRawPath(ENDPOINT_REFRESH_TOKEN);
        JsonHttpContent httpContent = new JsonHttpContent(new JacksonFactory(), refreshToken);
        HttpRequest request = requestFactory.buildPostRequest(url, httpContent);
        HttpResponse response = null;
        try {
            response = request.execute();
        } catch (HttpResponseException e){
            if (e.getStatusCode() == 401 && e.getStatusMessage() == "Unauthorized"){
                throw new UseMangoException("Expired refresh token");
            }
        }
        if(response != null) {
            if(response.getStatusCode() == HttpStatus.SC_OK) {
                String responseJson = response.parseAsString();
                JsonObject tokenJson = new JsonParser().parse(responseJson).getAsJsonObject();
                String idToken = tokenJson.get("IdToken").getAsString();
                if(idToken != null) {
                    return idToken;
                }
                else throw new UseMangoException("No Id token returned from "+useMangoUrl+ENDPOINT_REFRESH_TOKEN);
            }
            else throw new UseMangoException("Invalid response from "+useMangoUrl+ENDPOINT_REFRESH_TOKEN+" - status code: "+response.getStatusCode());
        }
        else throw new UseMangoException("Error retrieving tokens from "+useMangoUrl+ENDPOINT_REFRESH_TOKEN+" - response is null");
    }

	public static TestIndexResponse getTestIndex(String useMangoUrl, TestIndexParams params, String idToken) throws IOException {
		HttpRequestFactory requestFactory = HTTP_TRANSPORT.createRequestFactory(
				(HttpRequest request) -> {request.setParser(new JsonObjectParser(JSON_FACTORY));
        });
		
		TestIndexResponse response = null;
		while(true) { // handle pagination
		    GenericUrl url = new GenericUrl(useMangoUrl);
			url.setRawPath(String.format(ENDPOINT_TESTINDEX, params.getProjectId()));
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
	public static List<Project> getProjects(String useMangoUrl, String idToken) throws IOException {
		HttpRequestFactory requestFactory = HTTP_TRANSPORT.createRequestFactory(
				(HttpRequest request) -> {request.setParser(new JsonObjectParser(JSON_FACTORY));
        });
		GenericUrl url = new GenericUrl(useMangoUrl);
		url.setRawPath(String.format(ENDPOINT_PROJECTS));
		HttpRequest request = requestFactory.buildGetRequest(url);
		request.setHeaders(getHeadersForServer(idToken));
		return (ArrayList<Project>)request.execute().parseAs(new TypeToken<ArrayList<Project>>(){}.getType());
	}

	public static List<String> getProjectTags(String useMangoUrl, String idToken, String project) throws IOException {
		HttpRequestFactory requestFactory = HTTP_TRANSPORT.createRequestFactory(
				(HttpRequest request) -> {request.setParser(new JsonObjectParser(JSON_FACTORY));
				});
		GenericUrl url = new GenericUrl(useMangoUrl);
		url.setRawPath(String.format(ENDPOINT_PROJECT_TAGS, project));
		HttpRequest request = requestFactory.buildGetRequest(url);
		request.setHeaders(getHeadersForServer(idToken));
		return (ArrayList<String>)request.execute().parseAs(new TypeToken<ArrayList<String>>(){}.getType());
	}

	public static List<UmUser> getUsers(String useMangoUrl, String idToken) throws IOException {
		HttpRequestFactory requestFactory = HTTP_TRANSPORT.createRequestFactory(
				(HttpRequest request) -> {request.setParser(new JsonObjectParser(JSON_FACTORY));
				});
		GenericUrl url = new GenericUrl(useMangoUrl);
		url.setRawPath(ENDPOINT_USERS);
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