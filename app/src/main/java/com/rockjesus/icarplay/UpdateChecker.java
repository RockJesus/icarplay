package com.rockjesus.icarplay;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 版本更新检查与下载：读 GitHub Releases 的最新 Release，比较版本号，下载 APK 并校验。
 *
 * <p>国内直连 GitHub 资产常常抖动，所以下载走「多源 + 断点续传 + 自动换源重试」：
 * Release 说明里可以带一行镜像地址（见 MIRROR_KEY），代码先用镜像再回落到 GitHub；
 * 每个源失败后从已下载的字节位置续传，三次不过才换下一个源。
 * 下载完成后按 Release 声明的 sha256 digest 与字节数校验，校验不过就删掉不装。
 */
public final class UpdateChecker {

    public static final String REPO = "RockJesus/icarplay";
    public static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    public static final String ASSET = "icarplay.apk";
    /** Release 说明里放镜像的标记行：`mirrors: url1 url2`（一行，空格分隔）。 */
    public static final String MIRROR_KEY = "mirrors:";

    public interface Progress {
        void onProgress(long done, long total);
    }

    /** 换源 / 重试时回调，UI 用它可以显示「源 1/3 · 第 2 次」。 */
    public interface Attempt {
        void onAttempt(int sourceIndex, int sourceCount, String host, int tryIndex);
    }

    public static final class Info {
        public String tag = "";
        public String notes = "";
        public String apkUrl = "";
        public long size = -1;
        public String digest = "";
        public boolean hasApk;
        public final List<String> mirrors = new ArrayList<>();
    }

    private UpdateChecker() {
    }

    /** 取最新 Release；网络失败抛 IOException，由调用方提示。 */
    public static Info latest(int timeoutMs) throws IOException {
        HttpURLConnection conn = open(API, timeoutMs);
        String body = readAll(conn);
        conn.disconnect();
        return parseRelease(body);
    }

    /** 把 GitHub Release JSON 解析成更新信息（拆出来便于离线测试）。 */
    public static Info parseRelease(String body) {
        Info info = new Info();
        if (body == null) return info;
        info.tag = MiniJson.string(body, "tag_name");
        info.notes = MiniJson.string(body, "body");
        info.apkUrl = assetUrl(body, ASSET);
        info.size = assetLong(body, ASSET, "size");
        info.digest = assetString(body, ASSET, "digest");
        info.hasApk = info.apkUrl.startsWith("http");
        info.mirrors.addAll(parseMirrors(info.notes));
        return info;
    }

    /** 从说明里抓 `mirrors: a b c` 这一行的地址。 */
    static List<String> parseMirrors(String notes) {
        List<String> out = new ArrayList<>();
        if (notes == null || notes.isEmpty()) return out;
        for (String line : notes.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (!trimmed.toLowerCase(Locale.US).startsWith(MIRROR_KEY)) continue;
            String rest = trimmed.substring(MIRROR_KEY.length()).trim();
            for (String part : rest.split("[\\s,]+")) {
                if (part.startsWith("http") && !out.contains(part)) out.add(part);
            }
        }
        return out;
    }

    /** 远端是否比当前版本新：逐段比较，段数不同时缺位按 0 处理。 */
    public static boolean isNewer(String currentLabel, String remoteTag) {
        int[] a = parseVersion(currentLabel);
        int[] b = parseVersion(remoteTag);
        if (b == null) return false;
        if (a == null) return true;
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int left = i < a.length ? a[i] : 0;
            int right = i < b.length ? b[i] : 0;
            if (left != right) return right > left;
        }
        return false;
    }

    private static int[] parseVersion(String label) {
        if (label == null) return null;
        String clean = label.trim().toLowerCase(Locale.US);
        if (clean.startsWith("v")) clean = clean.substring(1);
        String[] parts = clean.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String digits = parts[i].replaceAll("[^0-9]", "");
            if (digits.isEmpty()) return null;
            try {
                out[i] = Integer.parseInt(digits);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return out;
    }

    /** 下载：镜像优先，失败换下一个源，每个源最多三次且从断点续传。 */
    public static File download(Info info, File dir, Progress progress) throws IOException {
        return download(info, dir, progress, null);
    }

    public static File download(Info info, File dir, Progress progress, Attempt attempt) throws IOException {
        if (!info.hasApk && info.mirrors.isEmpty()) throw new IOException("Release 里没有 " + ASSET);
        if (!dir.exists()) dir.mkdirs();

        List<String> sources = new ArrayList<>(info.mirrors);
        if (info.apkUrl.startsWith("http") && !sources.contains(info.apkUrl)) sources.add(info.apkUrl);

        IOException last = null;
        for (int s = 0; s < sources.size(); s++) {
            String src = sources.get(s);
            for (int t = 0; t < 3; t++) {
                if (attempt != null) attempt.onAttempt(s + 1, sources.size(), hostOf(src), t + 1);
                try {
                    return fetch(src, info, dir, progress);
                } catch (IOException e) {
                    last = e;
                    sleep((t + 1) * 700L);
                }
            }
        }
        throw last == null ? new IOException("所有下载源都失败了") : last;
    }

    private static File fetch(String url, Info info, File dir, Progress progress) throws IOException {
        String safeTag = info.tag.replaceAll("[^0-9A-Za-z._-]", "");
        File target = new File(dir, "icarplay-" + safeTag + ".apk");
        File part = new File(dir, target.getName() + ".part");
        long have = part.exists() ? part.length() : 0;
        if (info.size > 0 && have > info.size) {
            part.delete();
            have = 0;
        }
        if (info.size > 0 && have == info.size) {
            verify(part, info);
            replace(part, target);
            if (progress != null) progress.onProgress(have, have);
            return target;
        }

        HttpURLConnection conn = open(url, 20000);
        if (have > 0) conn.setRequestProperty("Range", "bytes=" + have + "-");
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            conn.disconnect();
            throw new IOException("HTTP " + code);
        }
        if (code == 200 && have > 0) {
            // 服务器不支持续传，只能从头来
            part.delete();
            have = 0;
        }
        long total = info.size > 0 ? info.size : totalFrom(conn, have);
        long done = have;
        try (InputStream in = new BufferedInputStream(conn.getInputStream(), 8192);
             OutputStream out = new FileOutputStream(part, have > 0)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                if (progress != null) progress.onProgress(done, total);
            }
        } finally {
            conn.disconnect();
        }

        try {
            verify(part, info);
        } catch (IOException e) {
            // 校验不过就把半成品删掉，下一次重试从零开始，避免续传出错的文件越滚越大
            part.delete();
            throw e;
        }
        replace(part, target);
        return target;
    }

    private static long totalFrom(HttpURLConnection conn, long have) {
        String range = conn.getHeaderField("Content-Range");
        if (range != null) {
            int slash = range.lastIndexOf('/');
            if (slash > 0) {
                try {
                    return Long.parseLong(range.substring(slash + 1).trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        long len = conn.getContentLength();
        return len > 0 ? len + have : -1;
    }

    private static void verify(File part, Info info) throws IOException {
        long size = part.length();
        if (size <= 0) throw new IOException("下载文件是空的");
        if (info.size > 0 && size != info.size) {
            throw new IOException("下载不完整：" + size + " / " + info.size + " 字节");
        }
        if (info.digest != null && info.digest.toLowerCase(Locale.US).startsWith("sha256:")) {
            String want = info.digest.substring(info.digest.indexOf(':') + 1).trim().toLowerCase(Locale.US);
            String got = sha256(part);
            if (!got.equals(want)) throw new IOException("SHA-256 校验不通过");
        }
    }

    private static void replace(File part, File target) throws IOException {
        if (target.exists()) target.delete();
        if (!part.renameTo(target)) throw new IOException("保存失败");
    }

    static String sha256(File file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new FileInputStream(file)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            }
            return hex(md.digest());
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException("当前系统没有 SHA-256");
        }
    }

    private static String hostOf(String url) {
        try {
            String host = new URL(url).getHost();
            return host == null ? url : host;
        } catch (Throwable e) {
            return url;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static HttpURLConnection open(String url, int timeoutMs) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("User-Agent", "iCarPlay-Updater");
        return conn;
    }

    private static String readAll(HttpURLConnection conn) throws IOException {
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            conn.disconnect();
            throw new IOException("HTTP " + code);
        }
        try (InputStream in = conn.getInputStream()) {
            byte[] data = new byte[64 * 1024];
            StringBuilder sb = new StringBuilder();
            int n;
            int guard = 0;
            while ((n = in.read(data)) > 0 && guard++ < 64) {
                sb.append(new String(data, 0, n, StandardCharsets.UTF_8));
            }
            return sb.toString();
        }
    }

    /** 在 assets 数组里按名字取一个资产对象（花括号配对、跳过字符串内容）。 */
    private static String assetObject(String json, String name) {
        int assetsAt = json.indexOf("\"assets\"");
        if (assetsAt < 0) return "";
        int open = json.indexOf('[', assetsAt);
        if (open < 0) return "";
        int depth = 0;
        boolean inStr = false;
        boolean esc = false;
        int objStart = -1;
        for (int i = open + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inStr) {
                if (esc) esc = false;
                else if (c == '\\') esc = true;
                else if (c == '"') inStr = false;
                continue;
            }
            if (c == '"') { inStr = true; continue; }
            if (c == '{') {
                depth++;
                if (depth == 1) objStart = i;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objStart >= 0) {
                    String obj = json.substring(objStart, i + 1);
                    if (name.equals(MiniJson.string(obj, "name"))) return obj;
                    objStart = -1;
                }
            } else if (c == ']' && depth == 0) {
                break;
            }
        }
        return "";
    }

    private static String assetUrl(String json, String name) {
        return MiniJson.string(assetObject(json, name), "browser_download_url");
    }

    private static long assetLong(String json, String name, String key) {
        return MiniJson.number(assetObject(json, name), key);
    }

    private static String assetString(String json, String name, String key) {
        return MiniJson.string(assetObject(json, name), key);
    }

    static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format(Locale.US, "%02x", b));
        return sb.toString();
    }

    /** 够用就好的 JSON 取值：只取顶层字符串 / 数字，不处理数组嵌套之外的复杂结构。 */
    static final class MiniJson {
        static String string(String json, String key) {
            if (json == null || json.isEmpty()) return "";
            int at = keyIndex(json, key);
            if (at < 0) return "";
            int start = json.indexOf('"', at + key.length() + 2);
            if (start < 0) return "";
            StringBuilder sb = new StringBuilder();
            boolean esc = false;
            for (int i = start + 1; i < json.length(); i++) {
                char c = json.charAt(i);
                if (esc) {
                    switch (c) {
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': break;
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case 'u':
                            if (i + 4 < json.length()) {
                                try {
                                    sb.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                                    i += 4;
                                } catch (Throwable ignored) {
                                }
                            }
                            break;
                        default: sb.append(c);
                    }
                    esc = false;
                } else if (c == '\\') {
                    esc = true;
                } else if (c == '"') {
                    return sb.toString();
                } else {
                    sb.append(c);
                }
            }
            return "";
        }

        static long number(String json, String key) {
            int at = keyIndex(json, key);
            if (at < 0) return -1;
            int start = at + key.length() + 2;
            while (start < json.length() && !(json.charAt(start) >= '0' && json.charAt(start) <= '9')
                    && json.charAt(start) != '-') start++;
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
            try {
                return Long.parseLong(json.substring(start, end));
            } catch (Throwable e) {
                return -1;
            }
        }

        private static int keyIndex(String json, String key) {
            int a = json.indexOf("\"" + key + "\":");
            if (a >= 0) return a;
            int b = json.indexOf("\"" + key + "\": ");
            if (b >= 0) return b;
            return json.indexOf("\"" + key + "\"");
        }
    }
}
