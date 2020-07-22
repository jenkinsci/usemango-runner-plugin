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
import java.util.logging.Logger;

public class AuthUtil {
    private static final Logger LOGGER = Logger.getLogger("useMangoRunner");

    public static String[] getAuthTokens(String username, String password) throws UseMangoException {
        final Map<String, String> authParams = new HashMap<>();
        authParams.put("USERNAME", username);
        authParams.put("PASSWORD", password);

        Log.fine("Authenticating user - " + username);
        return initiateAuthRequest(AuthFlowType.USER_PASSWORD_AUTH, authParams);
    }

    public static String[] refreshAuthTokens(String refreshToken) throws UseMangoException {
        final Map<String, String> authParams = new HashMap<>();
        authParams.put("REFRESH_TOKEN", refreshToken);

        Log.fine("Refreshing authentication tokens");
        return initiateAuthRequest(AuthFlowType.REFRESH_TOKEN_AUTH, authParams);
    }

    private static String[] initiateAuthRequest(AuthFlowType flowType, Map<String, String> authParams) throws UseMangoException {
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
            String idToken = result.getAuthenticationResult().getIdToken();
            String refreshToken = result.getAuthenticationResult().getRefreshToken();
            Log.fine("Completed Cognito request.");
            return new String[] {idToken, refreshToken};
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

