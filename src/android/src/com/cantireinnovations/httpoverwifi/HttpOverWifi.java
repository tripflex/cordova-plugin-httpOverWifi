package com.cantireinnovations.httpoverwifi;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.Override;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.apache.cordova.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class HttpOverWifi extends CordovaPlugin {

    private static final String TAG = "HttpOverWifi";

    private ConnectivityManager connectivityManager;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        connectivityManager = (ConnectivityManager)cordova.getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) {
        if (action.equals("request")) {
            return request(data, callbackContext);
        }
        return false;
    }

    private boolean request(JSONArray data, CallbackContext callbackContext) {
        Log.v(TAG, "Entering request");

        InputArguments inputArguments;
        try {
            inputArguments = getArgumentsFromData(data);
        } catch (JSONException e) {
            callbackContext.error("Invalid options passed, " + e.getMessage());
            return false;
        } catch (MalformedURLException e) {
            callbackContext.error("Invalid url passed, " + e.getMessage());
            return false;
        }

        HttpURLConnection connection;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Log.d(TAG, "Skipping selecting network because lower than Lollipop");
            Log.v(TAG, "Creating request");
            try {
                connection = (HttpURLConnection)inputArguments.getUrl().openConnection();
            } catch (IOException e) {
                callbackContext.error("Got IOException on openConnection, " + e.getMessage());
                return false;
            }
        } else {
            Log.v(TAG, "Selecting network");
            Network wifiNetwork = null;
            for (Network network : connectivityManager.getAllNetworks()) {
                NetworkInfo networkInfo = connectivityManager.getNetworkInfo(network);
                if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                    wifiNetwork = network;
                    break;
                }
            }
            if (wifiNetwork == null) {
                callbackContext.error("Could not find WiFi network to bind to");
                return false;
            }

            Log.v(TAG, "Creating request");
            try {
                connection = (HttpURLConnection)wifiNetwork.openConnection(inputArguments.getUrl());
            } catch (IOException e) {
                callbackContext.error("Got IOException on openConnection, " + e.getMessage());
                return false;
            }
        }

        boolean successful = true;
        int statusCode;
        Map<String, List<String>> responseHeaders;
        String responseBody;
        try {
            try {
                connection.setRequestMethod(inputArguments.getMethod());
            } catch (ProtocolException e) {
                callbackContext.error("Method was not supported or it was set after connection was opened, " + e.getMessage());
                return false;
            }
            for(Map.Entry<String, String> header : inputArguments.getHeaders().entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            connection.setConnectTimeout(inputArguments.getTimeout());
            connection.setReadTimeout(inputArguments.getTimeout());
            boolean hasBody = !inputArguments.getBody().equals("");
            if (hasBody) {
                connection.setDoOutput(true);
                byte[] bodyBytes;
                try {
                    bodyBytes = inputArguments.getBody().getBytes("UTF-8");
                } catch (UnsupportedEncodingException e) {
                    callbackContext.error("UTF-8 was not a supported encoding, " + e.getMessage());
                    return false;
                }
                connection.setFixedLengthStreamingMode(bodyBytes.length);
                OutputStream requestBodyOutputStream;
                try {
                    requestBodyOutputStream = connection.getOutputStream();
                } catch (IOException e) {
                    callbackContext.error("Could not get request output stream, " + e.getMessage());
                    return false;
                }
                OutputStream requestBodyStream = new BufferedOutputStream(requestBodyOutputStream);
                try {
                    requestBodyStream.write(bodyBytes);
                } catch(IOException e) {
                    callbackContext.error("Got IOException when writing request body, " + e.getMessage());
                    return false;
                } finally {
                    try {
                        requestBodyStream.close();
                    } catch (IOException e) {
                        callbackContext.error("Got IOException when closing request body, " + e.getMessage());
                        return false;
                    } finally {
                        try {
                            requestBodyOutputStream.close();
                        } catch (IOException e) {
                            callbackContext.error("Got IOException when closing buffered writer for request body, " + e.getMessage());
                            return false;
                        }
                    }
                }
            }

            Log.v(TAG, "Connecting");
            try {
                connection.connect();
            } catch(IOException e) {
                Log.d(TAG, "Got IOException on connect, " + e.getMessage());
                successful = false;
            }

            try {
                statusCode = connection.getResponseCode();
            } catch(IOException e) {
                statusCode = -1;
                successful = false;
            }

            responseHeaders = connection.getHeaderFields();
            if (responseHeaders == null) {
                responseHeaders = new HashMap<String, List<String>>();
            }

            InputStream responseBodyInputStream;
            try {
                responseBodyInputStream = connection.getInputStream();
            } catch(IOException e) {
                responseBodyInputStream = connection.getErrorStream();
                successful = false;
            }
            InputStream responseBodyStream = new BufferedInputStream(responseBodyInputStream);
            // Delimiter is set at start of input so next() returns entire string
            Scanner scanner = new Scanner(responseBodyStream, "UTF-8").useDelimiter("\\A");
            responseBody = scanner.hasNext() ? scanner.next() : "";
            scanner.close();
            try {
                responseBodyStream.close();
            } catch (IOException e) {
                callbackContext.error("Error closing response stream, " + e.getMessage());
                return false;
            } finally {
                if (responseBodyInputStream != null) {
                    try {
                        responseBodyInputStream.close();
                    } catch(IOException e) {
                        callbackContext.error("Got IOException when closing buffered reader for response body, " + e.getMessage());
                        return false;
                    }
                }
            }
        } finally {
            connection.disconnect();
        }

        Log.v(TAG, "Creating response object");
        JSONObject returnVal = new JSONObject();
        try {
            returnVal.put("status", statusCode);
            returnVal.put("data", responseBody);
            JSONObject returnHeaders = new JSONObject();
            for(Map.Entry<String, List<String>> header : responseHeaders.entrySet()) {
                StringBuilder valueBuilder = new StringBuilder();
                boolean first = true;
                for(String value : header.getValue()) {
                    if (!first) {
                        valueBuilder.append(",");
                    } else {
                        first = false;
                    }
                    valueBuilder.append(value);
                }
                if (header.getKey() != null ) {
                    returnHeaders.put(header.getKey(), valueBuilder.toString());
                }
            }
            returnVal.put("headers", returnHeaders);
        } catch (JSONException e) {
            callbackContext.error("Error building return value, " + e.getMessage());
            return false;
        }

        Log.v(TAG, "Exiting request");
        if (successful) {
            callbackContext.success(returnVal);
        } else {
            callbackContext.error(returnVal);
        }
        return successful;
    }

    private InputArguments getArgumentsFromData(JSONArray data) throws JSONException, MalformedURLException {
        Log.v(TAG, "Interpreting arguments");
        JSONObject options = data.getJSONObject(0);
        String method = options.getString("method");
        URL url = new URL(options.getString("url"));
        HashMap<String, String> headers = new HashMap<String, String>();
        JSONObject optHeaders = options.optJSONObject("headers");
        if (optHeaders != null) {
            Iterator<String> headerNames = optHeaders.keys();
            while(headerNames.hasNext()) {
                String headerName = headerNames.next();
                headers.put(headerName, optHeaders.getString(headerName));
            }
        }
        String body = options.optString("data");
        final int TEN_SECONDS = 10000;
        int timeout = options.optInt("timeout", TEN_SECONDS);
        return new InputArguments(method, url, headers, body, timeout);
    }

    private class InputArguments {

        private String method;
        private URL url;
        private HashMap<String, String> headers;
        private String body;
        private int timeout;

        public InputArguments(
                String method,
                URL url,
                HashMap<String, String> headers,
                String body,
                int timeout
        ) {
            this.method = method;
            this.url = url;
            this.headers = headers;
            this.body = body;
            this.timeout = timeout;
        }

        public String getMethod() {
            return method;
        }

        public URL getUrl() {
            return url;
        }

        public HashMap<String, String> getHeaders() {
            return headers;
        }

        public String getBody() {
            return body;
        }

        public int getTimeout() {
            return timeout;
        }
    }
}