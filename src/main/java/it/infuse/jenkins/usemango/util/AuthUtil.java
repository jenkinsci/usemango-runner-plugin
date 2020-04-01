package it.infuse.jenkins.usemango.util;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProviderClientBuilder;
import com.amazonaws.services.cognitoidp.model.*;
import it.infuse.jenkins.usemango.exception.UseMangoException;

import java.util.HashMap;
import java.util.Map;

public class AuthUtil {

    public static String[] getAuthTokens(String username, String password) throws UseMangoException {
        final Map<String, String> authParams = new HashMap<>();
        authParams.put("USERNAME", username);
        authParams.put("PASSWORD", password);

        return initiateAuthRequest(AuthFlowType.USER_PASSWORD_AUTH, authParams);
    }

    public static String[] refreshAuthTokens(String refreshToken) throws UseMangoException {
        final Map<String, String> authParams = new HashMap<>();
        authParams.put("REFRESH_TOKEN", refreshToken);

        return initiateAuthRequest(AuthFlowType.REFRESH_TOKEN_AUTH, authParams);
    }

    private static String[] initiateAuthRequest(AuthFlowType flowType, Map<String, String> authParams) throws UseMangoException {
        try {
            String clientId = "1ei8usvet2gm5dqmps2fute4o0";

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
            return new String[] {idToken, refreshToken};
        } catch (AWSCognitoIdentityProviderException e) {
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

