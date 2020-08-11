package it.infuse.jenkins.usemango.util;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProviderClientBuilder;
import com.amazonaws.services.cognitoidp.model.AWSCognitoIdentityProviderException;
import com.amazonaws.services.cognitoidp.model.AuthFlowType;
import com.amazonaws.services.cognitoidp.model.InitiateAuthRequest;
import com.amazonaws.services.cognitoidp.model.InitiateAuthResult;
import it.infuse.jenkins.usemango.exception.UseMangoException;

import java.util.HashMap;
import java.util.Map;

public class AuthUtil {

    public static String[] getAuthTokens(String username, String password) throws UseMangoException {
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)){
            Log.severe("Username or password is null when requesting auth tokens.");
            throw new UseMangoException("Missing username or password when requesting auth tokens.");
        }
        final Map<String, String> authParams = new HashMap<>();
        authParams.put("USERNAME", username);
        authParams.put("PASSWORD", password);

        Log.fine("Authenticating user - " + username);
        InitiateAuthResult result = initiateAuthRequest(AuthFlowType.USER_PASSWORD_AUTH, authParams);

        String idToken = result.getAuthenticationResult().getIdToken();
        String refreshToken = result.getAuthenticationResult().getRefreshToken();
        return new String[] {idToken, refreshToken};
    }

    public static String refreshAuthTokens(String refreshToken) throws UseMangoException {
        if (StringUtils.isBlank(refreshToken)){
            Log.severe("Refresh token is null when refreshing auth tokens");
            throw new UseMangoException("Missing refresh token when refreshing auth tokens.");
        }
        final Map<String, String> authParams = new HashMap<>();
        authParams.put("REFRESH_TOKEN", refreshToken);

        Log.fine("Refreshing authentication tokens");
        InitiateAuthResult result = initiateAuthRequest(AuthFlowType.REFRESH_TOKEN_AUTH, authParams);
        return result.getAuthenticationResult().getIdToken();
    }

    private static InitiateAuthResult initiateAuthRequest(AuthFlowType flowType, Map<String, String> authParams) throws UseMangoException {
        try {
            String clientId = System.getenv("UM_CLIENT_ID");
            if (clientId == null) {
                clientId = "1tehh8kqqp2jnbe3o52r0ojk47";
            }

            AWSCognitoIdentityProvider cognitoClient = AWSCognitoIdentityProviderClientBuilder
                    .standard()
                    .withRegion(Regions.EU_WEST_1)
                    .withCredentials(new AnonymousCredentialsProvider())
                    .build();

            final InitiateAuthRequest authRequest = new InitiateAuthRequest();
            authRequest.withAuthFlow(flowType)
                    .withClientId(clientId)
                    .withAuthParameters(authParams);

            InitiateAuthResult result = cognitoClient.initiateAuth(authRequest);
            Log.fine("Completed Cognito request.");
            return result;
        } catch (AWSCognitoIdentityProviderException e) {
            Log.severe("Cognito request failed: '" + e.getErrorMessage());
            throw new UseMangoException(e.getMessage());
        }
    }

    private static class AnonymousCredentialsProvider implements AWSCredentialsProvider {

        @Override
        public AWSCredentials getCredentials() {
            return null;
        }

        @Override
        public void refresh() {
        }
    }
}

