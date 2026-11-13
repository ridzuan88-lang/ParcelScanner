package com.chewanninja.parcelscanner;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/** Talks to the Google Apps Script web app. Always call from a background thread. */
public class SheetsClient {

    public static final class Result {
        /** OK, DUPLICATE, INVALID (server said no, final) | UNAUTHORIZED, ERROR, NETWORK, NOURL (retry later) */
        public String status = "ERROR";
        public String date = "";
        public String dateTime = "";
        public String message = "";
    }

    public Result send(String url, String key, ScanRecord r) {
        Result res = new Result();
        if (url == null || url.isEmpty()) {
            res.status = "NOURL";
            res.message = "Apps Script URL not set (Settings)";
            return res;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("key", key);
            body.put("mode", r.mode);
            body.put("tracking", r.tracking);
            body.put("scanId", r.scanId);
            parse(post(url, body.toString()), res);
        } catch (JSONException e) {
            res.status = "ERROR";
            res.message = "Unexpected reply from server (deployment access must be 'Anyone')";
        } catch (Exception e) {
            res.status = "NETWORK";
            res.message = "Network: " + e.getClass().getSimpleName()
                    + (e.getMessage() != null ? " - " + e.getMessage() : "");
        }
        return res;
    }

    public Result ping(String url, String key) {
        Result res = new Result();
        if (url == null || url.isEmpty()) {
            res.status = "NOURL";
            res.message = "Apps Script URL not set";
            return res;
        }
        try {
            String full = url + (url.contains("?") ? "&" : "?") + "action=ping&key=" + URLEncoder.encode(key, "UTF-8");
            parse(get(full), res);
        } catch (JSONException e) {
            res.status = "ERROR";
            res.message = "Unexpected reply (deployment access must be 'Anyone')";
        } catch (Exception e) {
            res.status = "NETWORK";
            res.message = "Network: " + e.getClass().getSimpleName()
                    + (e.getMessage() != null ? " - " + e.getMessage() : "");
        }
        return res;
    }

    private static void parse(String text, Result res) throws JSONException {
        JSONObject j = new JSONObject(text);
        res.status = j.optString("status", "ERROR");
        res.date = j.optString("date", "");
        res.dateTime = j.optString("dateTime", "");
        res.message = j.optString("message", "");
    }

    private static HttpURLConnection open(String url, String method) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(false); // Apps Script answers POST with a 302 we must follow with GET
        c.setConnectTimeout(15000);
        c.setReadTimeout(25000);
        c.setRequestMethod(method);
        c.setRequestProperty("User-Agent", "ParcelScanner-KaicomK7");
        return c;
    }

    private String post(String url, String body) throws IOException {
        HttpURLConnection c = open(url, "POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
        byte[] b = body.getBytes("UTF-8");
        c.setFixedLengthStreamingMode(b.length);
        try (OutputStream o = c.getOutputStream()) {
            o.write(b);
        }
        return readFollowing(c);
    }

    private String get(String url) throws IOException {
        return readFollowing(open(url, "GET"));
    }

    private String readFollowing(HttpURLConnection c) throws IOException {
        for (int i = 0; i < 5; i++) {
            int code = c.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException("Redirect without Location");
                c = open(loc, "GET");
                continue;
            }
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in == null) throw new IOException("HTTP " + code);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            c.disconnect();
            return bo.toString("UTF-8");
        }
        throw new IOException("Too many redirects");
    }
}
