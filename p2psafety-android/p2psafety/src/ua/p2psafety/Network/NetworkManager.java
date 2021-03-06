package ua.p2psafety.Network;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.util.Log;

import com.facebook.Session;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ua.p2psafety.Event;
import ua.p2psafety.SosManager;
import ua.p2psafety.User;
import ua.p2psafety.roles.Role;
import ua.p2psafety.util.Utils;

public class NetworkManager {
    private static final String SERVER_URL = "http://p2psafety.staging.42cc.co";

    private static final int CODE_SUCCESS = 201;

    private static int DIALOG_NETWORK_ERROR = 10;
    private static int DIALOG_NO_CONNECTION = 100;

    private static HttpClient httpClient;
    private static ExecutorService executor = Executors.newSingleThreadExecutor();
    private static ObjectMapper mapper = new ObjectMapper();

    public static void init(Context c) {
        HttpParams httpParams = new BasicHttpParams();
        HttpProtocolParams.setVersion(httpParams, HttpVersion.HTTP_1_1);
        //HttpProtocolParams.setContentCharset(httpParams, "unicode");
        HttpConnectionParams.setConnectionTimeout(httpParams, 0);
        HttpConnectionParams.setSoTimeout(httpParams, 0);
        httpClient = new DefaultHttpClient(httpParams);
    }

    public static void createEvent(final Context context,
                                   final DeliverResultRunnable<Event> postRunnable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final String TAG = "createEvent";

                if (!Utils.isNetworkConnected(context)) {
//                    errorDialog(context, DIALOG_NO_CONNECTION);
                    if (postRunnable != null) {
                        postRunnable.setResult(null);
                        postRunnable.run();
                    }
                    return;
                }

                String access_token = Session.getActiveSession().getAccessToken();

                try {
                    HttpPost httpPost = new HttpPost(new StringBuilder().append(SERVER_URL)
                            .append("/api/v1/events/").toString());

                    httpPost.setHeader("Accept", "application/json");
                    httpPost.setHeader("Content-type", "application/json");

                    JSONObject json = new JSONObject();
                    json.put("provider", "facebook");
                    json.put("access_token", access_token);
                    StringEntity se = new StringEntity(json.toString());
                    httpPost.setEntity(se);

                    Log.i(TAG, "request: " + httpPost.getRequestLine().toString());
                    Log.i(TAG, "request entity: " + EntityUtils.toString(httpPost.getEntity()));

                    HttpResponse response = null;
                    try {
                        response = httpClient.execute(httpPost);
                    } catch (Exception e) {
                        //errorDialog(context, DIALOG_NETWORK_ERROR);
                        if (postRunnable != null) {
                            postRunnable.setResult(null);
                            postRunnable.run();
                        }
                        return;
                    }

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responseContent = EntityUtils.toString(response.getEntity());
                    Log.i(TAG, "responseCode: " + responseCode);
                    Log.i(TAG, "responseContent: " + responseContent);

                    if (responseCode == CODE_SUCCESS) {
                        Map<String, Object> data = mapper.readValue(responseContent, Map.class);
                        Event event = JsonHelper.jsonToEvent(data);
                        data.clear();

                        postRunnable.setResult(event);
                    } else {
                        postRunnable.setResult(null);
                    }

                    if (postRunnable != null) {
                        postRunnable.run();
                    }
                } catch (Exception e) {
                    //errorDialog(context, DIALOG_NETWORK_ERROR);
                    if (postRunnable != null) {
                        postRunnable.setResult(null);
                        postRunnable.run();
                    }
                }
            }
        });
    }

    public static void updateEventWithAttachment(final Context context,
                                   final File file, final boolean isAudio,
                                   final DeliverResultRunnable<Boolean> postRunnable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final String TAG = "updateEvent";

//                if (!Utils.isNetworkConnected(context)) {
//                    errorDialog(context, DIALOG_NO_CONNECTION);
//                    return;
//                }

                Event event = SosManager.getInstance(context).getEvent();

                try {
                    HttpPost httpPost = new HttpPost(new StringBuilder().append(SERVER_URL)
                            .append("/api/v1/eventupdates/").toString());

                    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                    builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
                    if (isAudio)
                        builder.addPart("audio", new FileBody(file));
                    else
                        builder.addPart("video", new FileBody(file));

                    builder.addTextBody("key", event.getKey(), ContentType.APPLICATION_JSON);

                    httpPost.setEntity(builder.build());

                    HttpResponse response = null;
                    try {
                        response = httpClient.execute(httpPost);
                    } catch (Exception e) {
                        errorDialog(context, DIALOG_NETWORK_ERROR);
                        return;
                    }

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responseContent = EntityUtils.toString(response.getEntity());
                    Log.i(TAG, "responseCode: " + responseCode);
                    Log.i(TAG, "responseContent: " + responseContent);

                    if (responseCode == CODE_SUCCESS) {
                        postRunnable.setResult(true);
                    } else {
                        postRunnable.setResult(false);
                    }

                    postRunnable.run();
                } catch (Exception e) {
                    errorDialog(context, DIALOG_NETWORK_ERROR);
                }
            }
        });
    }

    public static void updateEvent(final Context context,
                                   final Map data,
                                   final DeliverResultRunnable<Boolean> postRunnable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final String TAG = "updateEvent";

//                if (!Utils.isNetworkConnected(context)) {
//                    errorDialog(context, DIALOG_NO_CONNECTION);
//                    return;
//                }

                Event event = SosManager.getInstance(context).getEvent();

                try {
                    HttpPost httpPost = new HttpPost(new StringBuilder().append(SERVER_URL)
                            .append("/api/v1/eventupdates/").toString());

                    httpPost.setHeader("Accept", "application/json");
                    httpPost.setHeader("Content-type", "application/json");

                    JSONObject json = new JSONObject();
                    json.put("key", event.getKey());
                    json.put("text", data.get("text"));
                    try {
                        Location loc = (Location) data.get("loc");
                        JSONObject jsonLocation = new JSONObject();
                        jsonLocation.put("latitude",  loc.getLatitude());
                        jsonLocation.put("longitude", loc.getLongitude());
                        json.put("location", jsonLocation);
                    } catch (Exception e) {}

                    StringEntity se = new StringEntity(json.toString(), "UTF-8");
                    httpPost.setEntity(se);

                    Log.i(TAG, "request: " + httpPost.getRequestLine().toString());
                    Log.i(TAG, "request entity: " + EntityUtils.toString(httpPost.getEntity()));

                    HttpResponse response = null;
                    try {
                        response = httpClient.execute(httpPost);
                    } catch (Exception e) {
                        errorDialog(context, DIALOG_NETWORK_ERROR);
                        return;
                    }

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responseContent = EntityUtils.toString(response.getEntity());
                    Log.i(TAG, "responseCode: " + responseCode);
                    Log.i(TAG, "responseContent: " + responseContent);

                    if (responseCode == CODE_SUCCESS) {
                        postRunnable.setResult(true);
                    } else {
                        postRunnable.setResult(false);
                    }

                    postRunnable.run();
                } catch (Exception e) {
                    errorDialog(context, DIALOG_NETWORK_ERROR);
                }
            }
        });
    }

    // TODO: make it work (now it returns code 401)
    public static void getEvents(final Context context,
                                 final DeliverResultRunnable<List<Event>> postRunnable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final String TAG = "getEvents";

//                if (!Utils.isNetworkConnected(context)) {
//                    errorDialog(context, DIALOG_NO_CONNECTION);
//                    return;
//                }

                try {
                    HttpGet httpGet = new HttpGet(new StringBuilder().append(SERVER_URL)
                            .append("/api/v1/events/")
                            .append("?user=")
                            .append(SosManager.getInstance(context).getEvent().getUser().getId())
                            .toString());

                    httpGet.setHeader("Accept", "application/json");
                    //httpGet.setHeader("Content-type", "application/json");

                    Log.i(TAG, "request: " + httpGet.getRequestLine().toString());

                    HttpResponse response = null;
                    try {
                        response = httpClient.execute(httpGet);
                    } catch (Exception e) {
                        errorDialog(context, DIALOG_NETWORK_ERROR);
                        return;
                    }

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responseContent = EntityUtils.toString(response.getEntity());
                    Log.i(TAG, "responseCode: " + responseCode);
                    Log.i(TAG, "responseContent: " + responseContent);

                    if (responseCode == CODE_SUCCESS) {
                        Map<String, Object> data = mapper.readValue(responseContent, Map.class);
                        Event event = JsonHelper.jsonToEvent(data);
                        data.clear();

                        postRunnable.setResult(null);
                    } else {
                        postRunnable.setResult(null);
                    }

                    if (postRunnable != null) {
                        postRunnable.run();
                    }
                } catch (Exception e) {
                    errorDialog(context, DIALOG_NETWORK_ERROR);
                }
            }
        });
    }

    public static void getRoles(final Context context, final User user,
                                 final DeliverResultRunnable<List<Role>> postRunnable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final int CODE_SUCCESS = 200; // TODO: ask server devs about difference between 200 and 201
                final String TAG = "getRoles";

//                if (!Utils.isNetworkConnected(context)) {
//                    errorDialog(context, DIALOG_NO_CONNECTION);
//                    return;
//                }

                try {
                    StringBuilder url = new StringBuilder()
                            .append(SERVER_URL).append("/api/v1/");

                    if (user == null)
                        url.append("roles/");
                    else
                        url.append("users/")
                           .append(SosManager.getInstance(context).getEvent().getUser().getId())
                           .append("/roles/");

                    HttpGet httpGet = new HttpGet(url.toString());
                    httpGet.setHeader("Accept", "application/json");
                    httpGet.setHeader("Content-type", "application/json");

                    Log.i(TAG, "request: " + httpGet.getRequestLine().toString());

                    HttpResponse response = null;
                    try {
                        response = httpClient.execute(httpGet);
                    } catch (Exception e) {
                        errorDialog(context, DIALOG_NETWORK_ERROR);
                        return;
                    }

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responseContent = EntityUtils.toString(response.getEntity());
                    Log.i(TAG, "responseCode: " + responseCode);
                    Log.i(TAG, "responseContent: " + responseContent);

                    if (responseCode == CODE_SUCCESS) {
                        List<Role> result = JsonHelper.jsonResponseToRoles(responseContent);

//                        // TODO: delete after debug
//                        List<Role> result = new ArrayList<Role>();
////
//                        Role role = new Role();
//                        role.id = "1";
//                        role.name = "Tank";
//                        result.add(role);
//
//                        if (user == null) {
//                            role = new Role();
//                            role.id = "2";
//                            role.name = "Supporter";
//                            result.add(role);
//                        }

                        postRunnable.setResult(result);
                    } else {
                        postRunnable.setResult(null);
                    }

                    if (postRunnable != null) {
                        postRunnable.run();
                    }
                } catch (Exception e) {
                    errorDialog(context, DIALOG_NETWORK_ERROR);
                }
            }
        });
    }

    private static void errorDialog(final Context context, final int type) {
        if (context == null)
            return;

        Activity activity = null;
        try {
            activity = (Activity) context;
        } catch (Exception e) {
            return;
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                try {
//                    final ETAApplication app = (ETAApplication) ((Activity) context).getApplication();
//                    if (app.dialogShown)
//                        return;
//
//                    if (type == DIALOG_NETWORK_ERROR) {
//                        app.dialogShown = true;
//
//                        new AlertDialog.Builder(context)
//                                .setMessage(R.string.network_error)
//                                .setNeutralButton(android.R.string.ok,
//                                        new DialogInterface.OnClickListener() {
//                                            @Override
//                                            public void onClick(DialogInterface dialog, int which) {
//                                                dialog.dismiss();
//                                                app.dialogShown = false;
//                                            }
//                                        })
//                                .setOnCancelListener(new DialogInterface.OnCancelListener() {
//                                    @Override
//                                    public void onCancel(DialogInterface dialog) {
//                                        dialog.dismiss();
//                                        app.dialogShown = false;
//                                    }
//                                }).show();
//                    }
//
//                    if (type == DIALOG_NO_CONNECTION) {
//                        app.dialogShown = true;
//
//                        new AlertDialog.Builder(context)
//                                .setTitle(R.string.connection)
//                                .setMessage(R.string.connection_is_out)
//                                .setIcon(android.R.drawable.ic_dialog_alert)
//                                .setNeutralButton(R.string.connection_settings,
//                                        new DialogInterface.OnClickListener() {
//                                            @Override
//                                            public void onClick(DialogInterface dialog, int which) {
//                                                dialog.dismiss();
//                                                app.dialogShown = false;
//
//                                                // open wi-fi settings
//                                                ((Activity) context)
//                                                        .startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
//                                            }
//                                        })
//                                .setOnCancelListener(new DialogInterface.OnCancelListener() {
//                                    @Override
//                                    public void onCancel(DialogInterface dialog) {
//                                        dialog.dismiss();
//                                        app.dialogShown = false;
//                                    }
//                                }).show();
//                    }
//                } catch (Exception e) {
//                    // if activity is already destroyed, we don't need to show any dialogs
//                    // let them go :)
//                }
            }
        });
    }

    public static void setRoles(final Context context, User user, final List<Role> roles,
                                   final DeliverResultRunnable<Boolean> postRunnable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final String TAG = "setRoles";

                if (!Utils.isNetworkConnected(context)) {
//                    errorDialog(context, DIALOG_NO_CONNECTION);
                    if (postRunnable != null) {
                        postRunnable.setResult(null);
                        postRunnable.run();
                    }
                    return;
                }

                try {
                    HttpPost httpPost = new HttpPost(new StringBuilder().append(SERVER_URL)
                            .append("/api/v1/users/")
                            .append(SosManager.getInstance(context).getEvent().getUser().getId())
                            .append("/roles/")
                            .toString());

                    httpPost.setHeader("Accept", "application/json");
                    httpPost.setHeader("Content-type", "application/json");

                    JSONArray arr = new JSONArray();
                    for (Role role: roles)
                        if (role.checked)
                            arr.put(role.id);

                    JSONObject json = new JSONObject();
                    json.put("role_id", arr);

                    StringEntity se = new StringEntity(json.toString());
                    httpPost.setEntity(se);

                    Log.i(TAG, "request: " + httpPost.getRequestLine().toString());
                    Log.i(TAG, "request entity: " + EntityUtils.toString(httpPost.getEntity()));

                    HttpResponse response = null;
                    try {
                        response = httpClient.execute(httpPost);
                    } catch (Exception e) {
                        //errorDialog(context, DIALOG_NETWORK_ERROR);
                        if (postRunnable != null) {
                            postRunnable.setResult(null);
                            postRunnable.run();
                        }
                        return;
                    }

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responseContent = EntityUtils.toString(response.getEntity());
                    Log.i(TAG, "responseCode: " + responseCode);
                    Log.i(TAG, "responseContent: " + responseContent);

                    if (responseCode == CODE_SUCCESS) {
                        Map<String, Object> data = mapper.readValue(responseContent, Map.class);
                        Event event = JsonHelper.jsonToEvent(data);
                        data.clear();

                        postRunnable.setResult(true);
                    } else {
                        postRunnable.setResult(false);
                    }

                    if (postRunnable != null) {
                        postRunnable.run();
                    }
                } catch (Exception e) {
                    //errorDialog(context, DIALOG_NETWORK_ERROR);
                    if (postRunnable != null) {
                        postRunnable.setResult(null);
                        postRunnable.run();
                    }
                }
            }
        });
    }

    public static abstract class DeliverResultRunnable<Result> implements Runnable {

        private Result result;
        private boolean success = true;
        private int errorCode = -1;

        public void setResult(Result result) {
            this.result = result;
        }

        @Override
        public final void run() {
            if (success) {
                deliver(result);
            } else {
                onError(errorCode);
            }
        }

        public void setUnsuccessful(int errorCode) {
            this.success = false;
            this.errorCode = errorCode;
        }

        public abstract void deliver(Result result);

        public void onError(int errorCode) { }

    }

}
