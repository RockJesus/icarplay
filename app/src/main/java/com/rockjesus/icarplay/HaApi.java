package com.rockjesus.icarplay;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * 内置的 Home Assistant 客户端（REST 部分）。
 *
 * <p>车机上不装官方伴侣应用，本应用自己完成「连上 HA」这件事：
 * <ul>
 *   <li>地址、长期令牌、默认面板路径都存在 {@code icarplay} preferences 里；</li>
 *   <li>令牌优先用网页登录后自动抓到的 {@code localStorage.hassTokens}，也支持手工粘贴；</li>
 *   <li>只用 {@code Authorization: Bearer} 走 {@code /api/}，不依赖 Google 服务。</li>
 * </ul>
 *
 * <p>{@link #callService(Context, String, String, String)} 留给后续的快捷动作使用，
 * 面板本身仍然是 HA 自己的前端（Lovelace 由用户在 HA 里自建）。
 */
public final class HaApi {

    public static final String PREFS = "icarplay";
    public static final String PREF_URL = "ha_url";
    public static final String PREF_TOKEN = "ha_token";
    public static final String PREF_PATH = "ha_path";
    public static final String PREF_KIOSK = "ha_kiosk";
    public static final String PREF_INSECURE = "ha_insecure";

    private HaApi() {
    }

    public static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 规范化后的基地址（无尾部斜杠）；未配置返回空串。 */
    public static String baseUrl(Context ctx) {
        String raw = prefs(ctx).getString(PREF_URL, "");
        String norm = normalize(raw);
        return norm == null ? "" : stripTrailingSlash(norm);
    }

    /** 长期访问令牌；未配置返回空串。 */
    public static String token(Context ctx) {
        String t = prefs(ctx).getString(PREF_TOKEN, "");
        return t == null ? "" : t.trim();
    }

    public static void saveToken(Context ctx, String token) {
        String clean = token == null ? "" : token.trim();
        if (clean.isEmpty()) return;
        prefs(ctx).edit().putString(PREF_TOKEN, clean).apply();
    }

    /** 默认面板路径（可空）；总会以 / 开头。 */
    public static String panelPath(Context ctx) {
        String raw = prefs(ctx).getString(PREF_PATH, "");
        if (raw == null) return "";
        String clean = raw.trim();
        if (clean.isEmpty()) return "";
        if (!clean.startsWith("/")) clean = "/" + clean;
        return clean;
    }

    /**
     * 面板最终地址：基地址 + 可选路径 + kiosk 参数。
     * 未配置返回空串，由调用方决定是否进设置页。
     */
    public static String panelUrl(Context ctx) {
        String base = baseUrl(ctx);
        if (base.isEmpty()) return "";
        String path = panelPath(ctx);
        String url = path.isEmpty() ? base + "/" : base + path;
        if (!prefs(ctx).getBoolean(PREF_KIOSK, true)) return url;
        if (url.contains("kiosk")) return url;
        int hash = url.indexOf('#');
        String head = hash >= 0 ? url.substring(0, hash) : url;
        String tail = hash >= 0 ? url.substring(hash) : "";
        return head + (head.contains("?") ? "&" : "?") + "kiosk" + tail;
    }

    public static String normalize(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            s = "http://" + s;
        }
        try {
            URL u = new URL(s);
            String host = u.getHost();
            if (host == null || host.isEmpty()) return null;
        } catch (Throwable e) {
            return null;
        }
        return s;
    }

    public static String stripTrailingSlash(String url) {
        if (url == null) return "";
        String s = url.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /**
     * 连通性探测：请求 {@code /api/}，成功返回 HA 的 API 提示语，失败返回 null。
     * 必须在工作线程调用。
     */
    public static String ping(Context ctx, int timeoutMs) {
        return request(ctx, "GET", "/api/", null, timeoutMs);
    }

    /**
     * 调用 HA 服务，例如 {@code callService(ctx, "light", "turn_on",
     * "{\"entity_id\":\"light.living_room\"}")}。返回响应体，失败返回 null。
     * 必须在工作线程调用。
     */
    public static String callService(Context ctx, String domain, String service, String json) {
        if (domain == null || service == null) return null;
        String path = "/api/services/" + domain + "/" + service;
        return request(ctx, "POST", path, json == null ? "{}" : json, 8000);
    }

    /**
     * 读全部实体状态（{@code /api/states}）。返回 JSON 数组文本，失败返回 null。
     * 必须在工作线程调用。
     */
    public static String states(Context ctx) {
        return request(ctx, "GET", "/api/states", null, 10000);
    }

    /**
     * 发出一个 REST 请求。apiPath 以 / 开头；返回响应体（成功）或 null（失败）。
     * 必须在工作线程调用。
     */
    public static String request(Context ctx, String method, String apiPath, String body,
                                 int timeoutMs) {
        String base = baseUrl(ctx);
        if (base.isEmpty()) return null;
        HttpURLConnection conn = null;
        try {
            URL url = new URL(base + apiPath);
            conn = (HttpURLConnection) url.openConnection();
            if (conn instanceof HttpsURLConnection
                    && prefs(ctx).getBoolean(PREF_INSECURE, false)) {
                HttpsURLConnection https = (HttpsURLConnection) conn;
                https.setSSLSocketFactory(trustAll().getSocketFactory());
                https.setHostnameVerifier((hostname, session) -> true);
            }
            int timeout = timeoutMs <= 0 ? 6000 : timeoutMs;
            conn.setConnectTimeout(Math.min(timeout, 6000));
            conn.setReadTimeout(timeout);
            conn.setRequestMethod(method);
            conn.setRequestProperty("Accept", "application/json");
            String token = token(ctx);
            if (!token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(payload.length);
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(payload);
                }
            }
            int code = conn.getResponseCode();
            InputStream in = code >= 200 && code < 300
                    ? conn.getInputStream() : conn.getErrorStream();
            String text = in == null ? "" : readAll(in);
            if (code < 200 || code >= 300) return null;
            return text;
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static String readAll(InputStream in) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return "";
        } finally {
            try {
                in.close();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * 信任所有证书：只服务于「内网自签 HTTPS 的 HA」，由用户在设置页显式打开，
     * 默认关闭，避免把整车机变成中间人攻击的靶子。
     */
    private static SSLContext trustAll() throws Exception {
        TrustManager[] managers = new TrustManager[]{new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }};
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, managers, new java.security.SecureRandom());
        return context;
    }
}
