package com.dwkshop.android;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class ApiClient {
    private static final String PREFS = "dwkshop_auth";
    private static final String TOKEN = "token";
    private static final String REFRESH_TOKEN = "refreshToken";
    private static final String USER_NAME = "userName";

    private final SharedPreferences prefs;

    ApiClient(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    boolean isLoggedIn() {
        return !getToken().isEmpty();
    }

    String userName() {
        return prefs.getString(USER_NAME, "");
    }

    void saveLogin(JSONObject body) {
        prefs.edit()
                .putString(TOKEN, body.optString("token"))
                .putString(REFRESH_TOKEN, body.optString("refreshToken"))
                .putString(USER_NAME, body.optString("name"))
                .apply();
    }

    void clearSession() {
        prefs.edit().clear().apply();
    }

    JSONObject getObject(String path) throws Exception {
        return request("GET", path, null, false).object();
    }

    org.json.JSONArray getArray(String path) throws Exception {
        return request("GET", path, null, false).array();
    }

    JSONObject post(String path, JSONObject body) throws Exception {
        return request("POST", path, body, false).object();
    }

    JSONObject put(String path, JSONObject body) throws Exception {
        return request("PUT", path, body, false).object();
    }

    JSONObject delete(String path) throws Exception {
        return request("DELETE", path, null, false).object();
    }

    private ApiResult request(String method, String path, JSONObject body, boolean retried) throws Exception {
        HttpURLConnection connection = open(method, path);
        if (body != null) {
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
        }

        int code = connection.getResponseCode();
        String text = read(code >= 400 ? connection.getErrorStream() : connection.getInputStream());
        connection.disconnect();

        if (code == 401 && !retried && !path.startsWith("/api/auth/") && refreshToken()) {
            return request(method, path, body, true);
        }

        if (code < 200 || code >= 300) {
            if (code == 401) clearSession();
            throw new IllegalStateException(parseError(code, text));
        }
        return new ApiResult(text);
    }

    private HttpURLConnection open(String method, String path) throws Exception {
        URL url = new URL(BuildConfig.API_BASE_URL + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        String token = getToken();
        if (!token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
        return connection;
    }

    private boolean refreshToken() {
        String refresh = prefs.getString(REFRESH_TOKEN, "");
        if (refresh == null || refresh.isEmpty()) return false;
        try {
            JSONObject payload = new JSONObject().put("refreshToken", refresh);
            HttpURLConnection connection = open("POST", "/api/auth/refresh");
            connection.setDoOutput(true);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = connection.getResponseCode();
            String text = read(code >= 400 ? connection.getErrorStream() : connection.getInputStream());
            connection.disconnect();
            if (code < 200 || code >= 300) return false;
            saveLogin(new JSONObject(text));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String getToken() {
        String token = prefs.getString(TOKEN, "");
        return token == null ? "" : token;
    }

    private static String parseError(int code, String text) {
        if (code >= 500) return "请求失败 (" + code + ")，请稍后重试";
        try {
            JSONObject body = new JSONObject(text);
            String message = body.optString("message", body.optString("error", ""));
            if (!message.isEmpty()) return message;
        } catch (Exception ignored) {
            // Fall through to generic error.
        }
        return "请求失败 (" + code + ")";
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        }
        return builder.toString();
    }

    static final class ApiResult {
        private final String text;

        ApiResult(String text) {
            this.text = text == null || text.isEmpty() ? "{}" : text;
        }

        JSONObject object() throws Exception {
            return new JSONObject(text);
        }

        org.json.JSONArray array() throws Exception {
            return new org.json.JSONArray(text);
        }
    }
}
