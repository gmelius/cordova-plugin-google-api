package nl.xservices.plugins;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.common.api.Scope;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.batch.BatchCallback;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.ListThreadsResponse;
import com.google.api.services.gmail.model.Message;
import com.google.gson.Gson;

import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import android.content.pm.Signature;
import android.widget.Toast;

/**
 * Originally written by Eddy Verbruggen (http://github.com/EddyVerbruggen/cordova-plugin-googleplus)
 * Forked/Duplicated and Modified by PointSource, LLC, 2016.
 */
public class GooglePlus extends CordovaPlugin implements GoogleApiClient.OnConnectionFailedListener {
    private Context androidContext;

    private String loadingText = "Loading...";

    public static final String ACTION_IS_AVAILABLE = "isAvailable";
    private static final String ACTION_GOOGLE_API_CALL = "callGoogleApi";
    public static final String ACTION_LOGIN = "login";
    public static final String ACTION_TRY_SILENT_LOGIN = "trySilentLogin";
    public static final String ACTION_LOGOUT = "logout";
    public static final String ACTION_DISCONNECT = "disconnect";
    public static final String ACTION_GET_SIGNING_CERTIFICATE_FINGERPRINT = "getSigningCertificateFingerprint";

    private final static String FIELD_ACCESS_TOKEN      = "accessToken";
    private final static String FIELD_TOKEN_EXPIRES     = "expires";
    private final static String FIELD_TOKEN_EXPIRES_IN  = "expires_in";
    private final static String VERIFY_TOKEN_URL        = "https://www.googleapis.com/oauth2/v1/tokeninfo?access_token=";

    //String options/config object names passed in to login and trySilentLogin
    public static final String ARGUMENT_WEB_CLIENT_ID = "webClientId";
    public static final String ARGUMENT_SCOPES = "scopes";
    public static final String ARGUMENT_OFFLINE_KEY = "offline";
    public static final String ARGUMENT_HOSTED_DOMAIN = "hostedDomain";

    public static final String TAG = "GooglePlugin";
    public static final int RC_GOOGLEPLUS = 77552; // Request Code to identify our plugin's activities
    public static final int KAssumeStaleTokenSec = 60;
    private static final String[] SCOPES = {GmailScopes.MAIL_GOOGLE_COM };

    // Wraps our service connection to Google Play services and provides access to the users sign in state and Google APIs
    private GoogleApiClient mGoogleApiClient;
    private CallbackContext savedCallbackContext;
    private GoogleAccountCredential mCredential;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        androidContext = webView.getContext();
    }

    @Override
    public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) throws JSONException {
        this.savedCallbackContext = callbackContext;

        if (ACTION_IS_AVAILABLE.equals(action)) {
            final boolean avail = true;
            savedCallbackContext.success("" + avail);

        } else if (ACTION_LOGIN.equals(action)) {
            //pass args into api client build
            buildGoogleApiClient(args.optJSONObject(0));

            // Tries to Log the user in
            Log.i(TAG, "Trying to Log in!");
            cordova.setActivityResultCallback(this); //sets this class instance to be an activity result listener
            signIn();

        } else if (ACTION_TRY_SILENT_LOGIN.equals(action)) {
            //pass args into api client build
            buildGoogleApiClient(args.optJSONObject(0));

            Log.i(TAG, "Trying to do silent login!");
            trySilentLogin();

        } else if (ACTION_LOGOUT.equals(action)) {
            Log.i(TAG, "Trying to logout!");
            signOut();

        } else if (ACTION_DISCONNECT.equals(action)) {
            Log.i(TAG, "Trying to disconnect the user");
            disconnect();

        } else if (ACTION_GET_SIGNING_CERTIFICATE_FINGERPRINT.equals(action)) {
            getSigningCertificateFingerprint();

        } else if (ACTION_GOOGLE_API_CALL.equals(action)) {
            callGoogleApi(callbackContext, args.optJSONObject(0));
        } else {
            Log.i(TAG, "This action doesn't exist");
            return false;

        }
        return true;
    }

    private static Map<String, String> jsonToMap(JSONObject json) throws JSONException {
        Map<String, String> retMap = new HashMap<String, String>();

        if(json != JSONObject.NULL) {
            retMap = toMap(json);
        }
        return retMap;
    }

    private static Map<String, String> toMap(JSONObject object) throws JSONException {
        Map<String, String> map = new HashMap<String, String>();

        Iterator<String> keysItr = object.keys();
        while(keysItr.hasNext()) {
            String key = keysItr.next();
            if (object.optString(key) != null) {
                String value = object.getString(key);
                map.put(key, value);
            }
        }
        return map;
    }

    private void test () {
        JsonBatchCallback<Message> callback = new JsonBatchCallback<Message>() {

            public void onSuccess(Message calendar, HttpHeaders responseHeaders) {
                // printCalendar(calendar);
                // addedCalendarsUsingBatch.add(calendar);
            }

            public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) {
                System.out.println("Error Message: " + e.getMessage());
            }
        };

        Gmail client2 = new com.google.api.services.gmail.Gmail.Builder(
                null, null, null)
                .setApplicationName("Gmail API Usage")
                .build();
        BatchRequest batch = client2.batch();

    }

    private void callBatchGoogleApi(final CallbackContext savedCallbackContext, final JSONObject jsonObject) {
        // Get a handler that can be used to post to the main thread
        Handler mainHandler = new Handler(androidContext.getMainLooper());

        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    List<BatchRequestPojo> requests = new ArrayList<BatchRequestPojo>();
                    JSONArray array = jsonObject.getJSONArray("requests");
                    for (int i = 0 ; i < array.length(); i++) {
                        JSONObject obj = array.getJSONObject(i);
                        requests.add(new BatchRequestPojo(jsonObject.getString("requestMethod"), jsonObject.getString("requestUrl"), jsonObject.has("data") ? jsonObject.getJSONObject("data").toString() : null, jsonToMap(jsonObject.getJSONObject("urlParams"))));
                    }

                    new BatchRequestCordova(savedCallbackContext, mCredential, requests).execute();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } // This is your code
        };
        mainHandler.post(myRunnable);
    }

    private void callGoogleApi(final CallbackContext savedCallbackContext, final JSONObject jsonObject) {
        // Get a handler that can be used to post to the main thread
        Handler mainHandler = new Handler(androidContext.getMainLooper());

        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    new MakeRequestTask(savedCallbackContext, mCredential, jsonToMap(jsonObject.getJSONObject("urlParams")), jsonObject.getString("requestMethod"), jsonObject.has("data") ? jsonObject.getJSONObject("data").toString() : null, jsonObject.getString("requestUrl")).execute();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } // This is your code
        };
        mainHandler.post(myRunnable);
    }

    /**
     * Set options for login and Build the GoogleApiClient if it has not already been built.
     * @param clientOptions - the options object passed in the login function
     */
    private synchronized void buildGoogleApiClient(JSONObject clientOptions) throws JSONException {
        if (clientOptions == null) {
            return;
        }

        //If options have been passed in, they could be different, so force a rebuild of the client
        // disconnect old client iff it exists
        if (this.mGoogleApiClient != null) this.mGoogleApiClient.disconnect();
        // nullify
        this.mGoogleApiClient = null;

        Log.i(TAG, "Building Google options");

        // Make our SignIn Options builder.
        GoogleSignInOptions.Builder gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN);

        // request the default scopes
        gso.requestEmail().requestProfile();

        // We're building the scopes on the Options object instead of the API Client
        // b/c of what was said under the "addScope" method here:
        // https://developers.google.com/android/reference/com/google/android/gms/common/api/GoogleApiClient.Builder.html#public-methods
        String scopes = clientOptions.optString(ARGUMENT_SCOPES, null);

        if (scopes != null && !scopes.isEmpty()) {
            // We have a string of scopes passed in. Split by space and request
            for (String scope : scopes.split(" ")) {
                gso.requestScopes(new Scope(scope));
            }
        }

        // Try to get web client id
        String webClientId = clientOptions.optString(ARGUMENT_WEB_CLIENT_ID, null);

        // if webClientId included, we'll request an idToken
        if (webClientId != null && !webClientId.isEmpty()) {
            gso.requestIdToken(webClientId);

            // if webClientId is included AND offline is true, we'll request the serverAuthCode
            if (clientOptions.optBoolean(ARGUMENT_OFFLINE_KEY, false)) {
                gso.requestServerAuthCode(webClientId, false);
            }
        }

        // Try to get hosted domain
        String hostedDomain = clientOptions.optString(ARGUMENT_HOSTED_DOMAIN, null);

        // if hostedDomain included, we'll request a hosted domain account
        if (hostedDomain != null && !hostedDomain.isEmpty()) {
            gso.setHostedDomain(hostedDomain);
        }

        //Now that we have our options, let's build our Client
        Log.i(TAG, "Building GoogleApiClient");

        GoogleApiClient.Builder builder = new GoogleApiClient.Builder(webView.getContext())
                .addOnConnectionFailedListener(this)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso.build());

        this.mGoogleApiClient = builder.build();

        Log.i(TAG, "GoogleApiClient built");
    }

    // The Following functions were implemented in reference to Google's example here:
    // https://github.com/googlesamples/google-services/blob/master/android/signin/app/src/main/java/com/google/samples/quickstart/signin/SignInActivity.java

    /**
     * Starts the sign in flow with a new Intent, which should respond to our activity listener here.
     */
    private void signIn() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(this.mGoogleApiClient);
        cordova.getActivity().startActivityForResult(signInIntent, RC_GOOGLEPLUS);
    }

    /**
     * Tries to log the user in silently using existing sign in result information
     */
    private void trySilentLogin() {
        ConnectionResult apiConnect =  mGoogleApiClient.blockingConnect();

        if (apiConnect.isSuccess()) {
            handleSignInResult(Auth.GoogleSignInApi.silentSignIn(this.mGoogleApiClient).await());
        }
    }

    /**
     * Signs the user out from the client
     */
    private void signOut() {
        if (this.mGoogleApiClient == null) {
            savedCallbackContext.error("Please use login or trySilentLogin before logging out");
            return;
        }

        ConnectionResult apiConnect = mGoogleApiClient.blockingConnect();

        if (apiConnect.isSuccess()) {
            Auth.GoogleSignInApi.signOut(this.mGoogleApiClient).setResultCallback(
                    new ResultCallback<Status>() {
                        @Override
                        public void onResult(Status status) {
                            //on success, tell cordova
                            if (status.isSuccess()) {
                                savedCallbackContext.success("Logged user out");
                            } else {
                                savedCallbackContext.error(status.getStatusCode());
                            }
                        }
                    }
            );
        }
    }

    /**
     * Disconnects the user and revokes access
     */
    private void disconnect() {
        if (this.mGoogleApiClient == null) {
            savedCallbackContext.error("Please use login or trySilentLogin before disconnecting");
            return;
        }

        ConnectionResult apiConnect = mGoogleApiClient.blockingConnect();

        if (apiConnect.isSuccess()) {
            Auth.GoogleSignInApi.revokeAccess(this.mGoogleApiClient).setResultCallback(
                    new ResultCallback<Status>() {
                        @Override
                        public void onResult(Status status) {
                            if (status.isSuccess()) {
                                savedCallbackContext.success("Disconnected user");
                            } else {
                                savedCallbackContext.error(status.getStatusCode());
                            }
                        }
                    }
            );
        }
    }

    /**
     * Handles failure in connecting to google apis.
     *
     * @param result is the ConnectionResult to potentially catch
     */
    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.i(TAG, "Unresolvable failure in connecting to Google APIs");
        savedCallbackContext.error(result.getErrorCode());
    }

    /**
     * Listens for and responds to an activity result. If the activity result request code matches our own,
     * we know that the sign in Intent that we started has completed.
     *
     * The result is retrieved and send to the handleSignInResult function.
     *
     * @param requestCode The request code originally supplied to startActivityForResult(),
     * @param resultCode The integer result code returned by the child activity through its setResult().
     * @param intent Information returned by the child activity
     */
    @Override
    public void onActivityResult(int requestCode, final int resultCode, final Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        Log.i(TAG, "In onActivityResult");

        if (requestCode == RC_GOOGLEPLUS) {
            Log.i(TAG, "One of our activities finished up");
            //Call handleSignInResult passing in sign in result object
            handleSignInResult(Auth.GoogleSignInApi.getSignInResultFromIntent(intent));
        }
        else {
            Log.i(TAG, "This wasn't one of our activities");
        }
    }

    /**
     * Function for handling the sign in result
     * Handles the result of the authentication workflow.
     *
     * If the sign in was successful, we build and return an object containing the users email, id, displayname,
     * id token, and (optionally) the server authcode.
     *
     * If sign in was not successful, for some reason, we return the status code to web app to be handled.
     * Some important Status Codes:
     *      SIGN_IN_CANCELLED = 12501 -> cancelled by the user, flow exited, oauth consent denied
     *      SIGN_IN_FAILED = 12500 -> sign in attempt didn't succeed with the current account
     *      SIGN_IN_REQUIRED = 4 -> Sign in is needed to access API but the user is not signed in
     *      INTERNAL_ERROR = 8
     *      NETWORK_ERROR = 7
     *
     * @param signInResult - the GoogleSignInResult object retrieved in the onActivityResult method.
     */
    private void handleSignInResult(final GoogleSignInResult signInResult) {
        if (this.mGoogleApiClient == null) {
            savedCallbackContext.error("GoogleApiClient was never initialized");
            return;
        }

        if (signInResult == null) {
            savedCallbackContext.error("SignInResult is null");
            return;
        }

        Log.i(TAG, "Handling SignIn Result");

        if (!signInResult.isSuccess()) {
            Log.i(TAG, "Wasn't signed in");

            //Return the status code to be handled client side
            savedCallbackContext.error(signInResult.getStatus().getStatusCode());
        } else {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    GoogleSignInAccount acct = signInResult.getSignInAccount();
                    JSONObject result = new JSONObject();
                    try {
                        JSONObject accessTokenBundle = getAuthToken(
                                cordova.getActivity(), acct.getAccount(), true
                        );
                        result.put(FIELD_ACCESS_TOKEN, accessTokenBundle.get(FIELD_ACCESS_TOKEN));
                        result.put(FIELD_TOKEN_EXPIRES, accessTokenBundle.get(FIELD_TOKEN_EXPIRES));
                        result.put(FIELD_TOKEN_EXPIRES_IN, accessTokenBundle.get(FIELD_TOKEN_EXPIRES_IN));
                        result.put("email", acct.getEmail());
                        result.put("idToken", acct.getIdToken());
                        result.put("serverAuthCode", acct.getServerAuthCode());
                        result.put("userId", acct.getId());
                        result.put("displayName", acct.getDisplayName());
                        result.put("familyName", acct.getFamilyName());
                        result.put("givenName", acct.getGivenName());
                        result.put("imageUrl", acct.getPhotoUrl());
                        mCredential = GoogleAccountCredential.usingOAuth2(androidContext, Arrays.asList(SCOPES));
                        mCredential.setBackOff(new ExponentialBackOff());
                        mCredential.setSelectedAccount(acct.getAccount());
                        savedCallbackContext.success(result);
                    } catch (Exception e) {
                        savedCallbackContext.error("Trouble obtaining result, error: " + e.getMessage());
                    }
                    return null;
                }
            }.execute();
        }
    }

    private void getSigningCertificateFingerprint() {
        String packageName = webView.getContext().getPackageName();
        int flags = PackageManager.GET_SIGNATURES;
        PackageManager pm = webView.getContext().getPackageManager();
        try {
            PackageInfo packageInfo = pm.getPackageInfo(packageName, flags);
            Signature[] signatures = packageInfo.signatures;
            byte[] cert = signatures[0].toByteArray();

            String strResult = "";
            MessageDigest md;
            md = MessageDigest.getInstance("SHA1");
            md.update(cert);
            for (byte b : md.digest()) {
                String strAppend = Integer.toString(b & 0xff, 16);
                if (strAppend.length() == 1) {
                    strResult += "0";
                }
                strResult += strAppend;
                strResult += ":";
            }
            // strip the last ':'
            strResult = strResult.substring(0, strResult.length()-1);
            strResult = strResult.toUpperCase();
            this.savedCallbackContext.success(strResult);

        } catch (Exception e) {
            e.printStackTrace();
            savedCallbackContext.error(e.getMessage());
        }
    }

    private JSONObject getAuthToken(Activity activity, Account account, boolean retry) throws Exception {
        AccountManager manager = AccountManager.get(activity);
        AccountManagerFuture<Bundle> future = manager.getAuthToken(account, "oauth2:profile email", null, activity, null, null);
        Bundle bundle = future.getResult();
        String authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
        try {
            return verifyToken(authToken);
        } catch (IOException e) {
            if (retry) {
                manager.invalidateAuthToken("com.google", authToken);
                return getAuthToken(activity, account, false);
            } else {
                throw e;
            }
        }
    }

    private JSONObject verifyToken(String authToken) throws IOException, JSONException {
        URL url = new URL(VERIFY_TOKEN_URL+authToken);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setInstanceFollowRedirects(true);
        String stringResponse = fromStream(
                new BufferedInputStream(urlConnection.getInputStream())
        );
        /* expecting:
        {
            "issued_to": "608941808256-43vtfndets79kf5hac8ieujto8837660.apps.googleusercontent.com",
            "audience": "608941808256-43vtfndets79kf5hac8ieujto8837660.apps.googleusercontent.com",
            "user_id": "107046534809469736555",
            "scope": "https://www.googleapis.com/auth/userinfo.profile",
            "expires_in": 3595,
            "access_type": "offline"
        }*/

        Log.d("AuthenticatedBackend", "token: " + authToken + ", verification: " + stringResponse);
        JSONObject jsonResponse = new JSONObject(
                stringResponse
        );
        int expires_in = jsonResponse.getInt(FIELD_TOKEN_EXPIRES_IN);
        if (expires_in < KAssumeStaleTokenSec) {
            throw new IOException("Auth token soon expiring.");
        }
        jsonResponse.put(FIELD_ACCESS_TOKEN, authToken);
        jsonResponse.put(FIELD_TOKEN_EXPIRES, expires_in + (System.currentTimeMillis()/1000));
        return jsonResponse;
    }

    public static String fromStream(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    private class MakeRequestTask extends AsyncTask<Void, Void, String> {
        private com.google.api.services.gmail.Gmail mService = null;
        private Map<String, String> urlParams;
        private CallbackContext savedCallbackContext;
        private String requestMethod;
        private String jsonObject;
        private String requestUrl;

        MakeRequestTask(CallbackContext savedCallbackContext, GoogleAccountCredential credential, Map<String, String> urlParams, String requestMethod, String jsonObject, String requestUrl) {
            this.savedCallbackContext = savedCallbackContext;
            this.requestMethod = requestMethod;
            this.jsonObject = jsonObject;
            this.requestUrl = requestUrl;
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.gmail.Gmail.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("Gmail API Usage")
                    .build();
            this.urlParams = urlParams;
        }

        /**
         * Background task to call Gmail API.
         * @param params no parameters needed for this task.
         */
        @Override
        protected String doInBackground(Void... params) {
            try {
                return getDataFromApi();
            } catch (Exception e) {
                cancel(true);
                return null;
            }
        }

        /**
         * Fetch a list of Gmail labels attached to the specified account.
         * @return List of Strings labels.
         * @throws IOException
         */
        private String getDataFromApi() throws IOException {
            try {
                //hs.put("userId", "me");
                //Object t1 = new GoogleApiRequest<Object>(mService, "GET", "{userId}/labels", null, Object.class, urlParams).execute();
                Object t1 = new GoogleApiRequest<Object>(mService, requestMethod, requestUrl, jsonObject, Object.class, urlParams).execute();
                String t2 = new Gson().toJson(t1);
                return t2;
            }catch (Exception e) {
                System.out.println("asdfasdff" + e.toString());
            }
            return null;
        }


        @Override
        protected void onPreExecute() {
        }

        @Override
        protected void onPostExecute(String output) {
            //Toast.makeText(androidContext, output, Toast.LENGTH_LONG).show();
            savedCallbackContext.success(output);
        }

        @Override
        protected void onCancelled() {
            /*if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            MainActivity.REQUEST_AUTHORIZATION);
                } else {
                    mOutputText.setText("The following error occurred:\n"
                            + mLastError.getMessage());
                }
            } else {
                mOutputText.setText("Request cancelled.");
            }*/
        }
    }

    private class BatchRequestCordova extends AsyncTask<Void, Void, String> {
        private final List<GoogleApiRequest<Object>> requests;
        private com.google.api.services.gmail.Gmail mService = null;
        private CallbackContext savedCallbackContext;

        BatchRequestCordova(CallbackContext savedCallbackContext, GoogleAccountCredential credential, List<BatchRequestPojo> requestsPojo) {
            this.savedCallbackContext = savedCallbackContext;
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.gmail.Gmail.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("Gmail API Usage")
                    .build();
            requests = new ArrayList<GoogleApiRequest<Object>>();
            for (BatchRequestPojo request: requestsPojo) {
                this.requests.add(new GoogleApiRequest<Object>(mService, request.getRequestMethod(), request.getRequestUrl(), request.getJsonObject(), Object.class, request.getUrlParams()));
            }
        }

        /**
         * Background task to call Gmail API.
         * @param params no parameters needed for this task.
         */
        @Override
        protected String doInBackground(Void... params) {
            try {
                return getDataFromApi(this.requests);
            } catch (Exception e) {
                cancel(true);
                return null;
            }
        }

        private String getDataFromApi(List<GoogleApiRequest<Object>> requests) throws IOException {
            try {
                BatchRequest batch = mService.batch();

                for (GoogleApiRequest<Object> gooleApiRequest:
                     requests) {
                    gooleApiRequest.queue(batch, Object.class, new BatchCallback<Object, String>() {
                        @Override
                        public void onSuccess(Object o, HttpHeaders responseHeaders) throws IOException {
                            String t2 = new Gson().toJson(o);
                            savedCallbackContext.success(t2);
                        }

                        @Override
                        public void onFailure(String s, HttpHeaders responseHeaders) throws IOException {
                            savedCallbackContext.error(s);
                        }
                    });
                }

                batch.execute();
            }catch (Exception e) {
                System.out.println("asdfasdff" + e.toString());
            }
            return null;
        }
    }
}
