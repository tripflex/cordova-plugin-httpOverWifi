package com.cantireinnovations.httpoverwifi;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
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

    private static final String TAG = "com.cantireinnovations.httpoverwifi";

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
        String method;
        URL url;
        HashMap<String, String> headers = new HashMap<String, String>();
        String body;
        int timeout;
        try {
            JSONObject options = data.getJSONObject(0);
            method = options.getString("method");
            url = new URL(options.getString("url"));
            JSONObject optHeaders = options.optJSONObject("headers");
            if (optHeaders != null) {
                Iterator<String> headerNames = optHeaders.keys();
                while(headerNames.hasNext()) {
                    String headerName = headerNames.next();
                    headers.put(headerName, optHeaders.getString(headerName));
                }
            }
            body = options.optString("data");
            final int TEN_SECONDS = 10000;
            timeout = options.optInt("timeout", TEN_SECONDS);
        } catch (JSONException e) {
            callbackContext.error("Invalid options passed, " + e.getMessage());
            return false;
        } catch (MalformedURLException e) {
            callbackContext.error("Invalid url passed, " + e.getMessage());
            return false;
        }

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

        HttpURLConnection connection;
        try {
            connection = (HttpURLConnection)wifiNetwork.openConnection(url);
        } catch (IOException e) {
            callbackContext.error("Got IOException on openConnection, " + e.getMessage());
            return false;
        }

        boolean successful = true;
        int statusCode;
        Map<String, List<String>> responseHeaders;
        String responseBody;
        try {
            try {
                connection.setRequestMethod(method);
            } catch (ProtocolException e) {
                callbackContext.error("Method was not supported or it was set after connection was opened, " + e.getMessage());
                return false;
            }
            for(Map.Entry<String, String> header : headers.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            boolean hasBody = !body.equals("");
            if (hasBody) {
                connection.setDoOutput(true);
                byte[] bodyBytes;
                try {
                    bodyBytes = body.getBytes("UTF-8");
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
        if (successful) {
            callbackContext.success(returnVal);
        } else {
            callbackContext.error(returnVal);
        }
        return successful;
    }

    /*private void copyStream(InputStream in, OutputStream out) {
        final int BUFFER_SIZE = 1024;
        byte[] buffer = new byte[BUFFER_SIZE];
        int offset = 0;
        int bytesRead;
        do {
            bytesRead = in.read(buffer, offset, BUFFER_SIZE);
            out.write(buffer, offset, bytesRead);
            offset += bytesRead;
        } while(bytesRead > 0);
    }*/
}