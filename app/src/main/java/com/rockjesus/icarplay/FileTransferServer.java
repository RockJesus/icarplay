package com.rockjesus.icarplay;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 车机侧的文件收发小服务：手机浏览器打开就能把 APK 传进车机，也能把车机上的 APK 下载回手机。
 *
 * <p>刻意只依赖 JDK 的 socket：车机是老系统，不引第三方 HTTP 框架；端口固定 8090（被占用时顺延），
 * 只接受 apk / apks，单文件上限 260MB，只绑定局域网可达的正常 HTTP 端口。
 */
public final class FileTransferServer {

    public static final int DEFAULT_PORT = 8090;
    private static final long MAX_UPLOAD_BYTES = 260L * 1024 * 1024L;
    private static final Set<String> ALLOWED_EXT = new HashSet<>(Arrays.asList("apk", "apks"));

    public interface Listener {
        void onLog(String line);
    }

    private final File root;
    private final String versionLabel;
    private final Listener listener;
    private ServerSocket server;
    private Thread acceptThread;
    private volatile boolean running;
    private volatile int port = DEFAULT_PORT;

    public FileTransferServer(File root, String versionLabel, Listener listener) {
        this.root = root;
        this.versionLabel = versionLabel == null ? "" : versionLabel;
        this.listener = listener;
        if (!this.root.exists()) this.root.mkdirs();
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public int port() {
        return port;
    }

    public synchronized boolean start(int requestedPort) {
        if (running) return true;
        try {
            for (int i = 0; i < 8; i++) {
                int candidate = requestedPort + i;
                try {
                    server = new ServerSocket(candidate);
                    server.setReuseAddress(true);
                    port = candidate;
                    break;
                } catch (IOException busy) {
                    if (i == 7) throw busy;
                    log("端口 " + candidate + " 被占用，尝试下一个");
                }
            }
            running = true;
            acceptThread = new Thread(this::acceptLoop, "icarplay-transfer-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();
            log("服务已启动，端口 " + port);
            return true;
        } catch (IOException e) {
            log("启动失败：" + e);
            closeQuietly(server);
            server = null;
            return false;
        }
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        closeQuietly(server);
        server = null;
        acceptThread = null;
        log("服务已停止");
    }

    private void acceptLoop() {
        while (running) {
            Socket client = null;
            try {
                client = server.accept();
                final Socket socket = client;
                Thread worker = new Thread(() -> handle(socket), "icarplay-transfer-client");
                worker.setDaemon(true);
                worker.start();
            } catch (IOException e) {
                if (running) log("accept 结束：" + e);
                closeQuietly(client);
            }
        }
    }

    // ---------------------------------------------------------------- request

    private void handle(Socket socket) {
        try (Socket sock = socket) {
            sock.setSoTimeout(120_000);
            BufferedInputStream in = new BufferedInputStream(sock.getInputStream());
            OutputStream out = new BufferedOutputStream(sock.getOutputStream());

            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) return;
            String[] parts = requestLine.split(" ");
            if (parts.length < 3) {
                respond(out, 400, "text/plain; charset=utf-8", "bad request");
                return;
            }
            String method = parts[0];
            String target = parts[1];

            Map<String, String> headers = new HashMap<>();
            String line;
            int guard = 0;
            while ((line = readLine(in)) != null && !line.isEmpty() && guard++ < 64) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    headers.put(line.substring(0, colon).trim().toLowerCase(Locale.US),
                            line.substring(colon + 1).trim());
                }
            }

            String path = target;
            String query = "";
            int q = target.indexOf('?');
            if (q >= 0) {
                path = target.substring(0, q);
                query = target.substring(q + 1);
            }
            Map<String, String> params = parseQuery(query);

            if ("GET".equals(method) && (path.equals("/") || path.equals("/index.html"))) {
                serveIndex(out);
            } else if ("GET".equals(method) && path.equals("/api/list")) {
                serveList(out);
            } else if ("GET".equals(method) && path.equals("/health")) {
                respond(out, 200, "application/json; charset=utf-8", "{\"ok\":true}");
            } else if ("GET".equals(method) && path.equals("/dl")) {
                serveDownload(out, params.get("name"));
            } else if ("PUT".equals(method) || "POST".equals(method)) {
                if (path.equals("/upload") || path.equals("/api/upload")) {
                    receiveUpload(in, out, headers, params.get("name"));
                } else {
                    respond(out, 404, "text/plain; charset=utf-8", "not found");
                }
            } else {
                respond(out, 404, "text/plain; charset=utf-8", "not found");
            }
            out.flush();
        } catch (Throwable e) {
            log("请求处理失败：" + e);
        }
    }

    private void serveIndex(OutputStream out) throws IOException {
        String page = INDEX_HTML
                .replace("__ADDRESS__", "http://" + hostAddress() + ":" + port)
                .replace("__LIMIT__", "260MB");
        respond(out, 200, "text/html; charset=utf-8", page);
    }

    private void serveList(OutputStream out) throws IOException {
        respond(out, 200, "application/json; charset=utf-8", listJson());
    }

    private String listJson() {
        List<File> files = listFiles();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"port\":").append(port).append(",\"files\":[");
        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm", Locale.US);
        for (int i = 0; i < files.size(); i++) {
            File f = files.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"name\":\"").append(jsonEscape(f.getName())).append("\",\"size\":").append(f.length())
                    .append(",\"human\":\"").append(formatSize(f.length())).append("\"")
                    .append(",\"time\":\"").append(fmt.format(new Date(f.lastModified()))).append("\"}")
                    ;
        }
        sb.append("]}");
        return sb.toString();
    }

    private void serveDownload(OutputStream out, String rawName) throws IOException {
        File file = resolve(rawName);
        if (file == null || !file.isFile()) {
            respond(out, 404, "text/plain; charset=utf-8", "file not found");
            return;
        }
        String encoded = urlEncode(file.getName());
        StringBuilder head = new StringBuilder();
        head.append("HTTP/1.1 200 OK\r\n");
        head.append("Content-Type: application/vnd.android.package-archive\r\n");
        head.append("Content-Length: ").append(file.length()).append("\r\n");
        head.append("Content-Disposition: attachment; filename=\"").append(file.getName())
                .append("\"; filename*=UTF-8''").append(encoded).append("\r\n");
        head.append("Cache-Control: no-store\r\nConnection: close\r\n\r\n");
        out.write(head.toString().getBytes(StandardCharsets.UTF_8));
        try (InputStream fin = new java.io.FileInputStream(file)) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = fin.read(buf)) > 0) out.write(buf, 0, n);
        }
        log("已发送 " + file.getName() + "（" + formatSize(file.length()) + "）");
    }

    private void receiveUpload(InputStream in, OutputStream out,
                               Map<String, String> headers, String rawName) throws IOException {
        String name = sanitize(rawName);
        if (name == null) {
            respondJson(out, 400, "{\"ok\":false,\"error\":\"只接受 .apk / .apks 文件\"}");
            return;
        }
        long declared = -1;
        String len = headers.get("content-length");
        if (len != null) {
            try {
                declared = Long.parseLong(len.trim());
            } catch (NumberFormatException ignored) {
                declared = -1;
            }
        }
        if (declared > MAX_UPLOAD_BYTES) {
            respondJson(out, 413, "{\"ok\":false,\"error\":\"文件超过 260MB\"}");
            return;
        }

        File dir = new File(root, "incoming");
        if (!dir.exists()) dir.mkdirs();
        File part = new File(dir, name + ".part");
        File target = uniqueFile(dir, name);
        long written = 0;
        try (FileOutputStream fos = new FileOutputStream(part)) {
            byte[] buf = new byte[16 * 1024];
            if (declared >= 0) {
                long left = declared;
                while (left > 0) {
                    int want = (int) Math.min(buf.length, left);
                    int n = in.read(buf, 0, want);
                    if (n < 0) break;
                    if (written + n > MAX_UPLOAD_BYTES) throw new IOException("too large");
                    fos.write(buf, 0, n);
                    written += n;
                    left -= n;
                }
            } else {
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (written + n > MAX_UPLOAD_BYTES) throw new IOException("too large");
                    fos.write(buf, 0, n);
                    written += n;
                }
            }
            fos.flush();
        }
        if (written <= 0) {
            part.delete();
            respondJson(out, 400, "{\"ok\":false,\"error\":\"没有收到内容\"}");
            return;
        }
        if (target.exists()) target.delete();
        if (!part.renameTo(target)) {
            part.delete();
            respondJson(out, 500, "{\"ok\":false,\"error\":\"保存失败\"}");
            return;
        }
        log("收到 " + target.getName() + "（" + formatSize(written) + "）");
        respondJson(out, 200, "{\"ok\":true,\"name\":\"" + jsonEscape(target.getName())
                + "\",\"size\":" + written + "}");
    }

    // ---------------------------------------------------------------- helpers

    public List<File> listFiles() {
        List<File> all = new ArrayList<>();
        collect(root, all);
        all.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return all;
    }

    private void collect(File dir, List<File> into) {
        if (dir == null || !dir.isDirectory()) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isDirectory()) {
                collect(f, into);
            } else if (f.getName().toLowerCase(Locale.US).endsWith(".apk")
                    || f.getName().toLowerCase(Locale.US).endsWith(".apks")) {
                into.add(f);
            }
        }
    }

    private File resolve(String rawName) {
        String name = sanitize(rawName);
        if (name == null) return null;
        File direct = new File(root, name);
        if (direct.isFile()) return direct;
        File incoming = new File(new File(root, "incoming"), name);
        if (incoming.isFile()) return incoming;
        for (File f : listFiles()) {
            if (f.getName().equals(name)) return f;
        }
        return null;
    }

    private String sanitize(String raw) {
        if (raw == null) return null;
        String name = raw;
        try {
            name = URLDecoder.decode(name, "UTF-8");
        } catch (Throwable ignored) {
        }
        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        StringBuilder safe = new StringBuilder();
        for (int i = 0; i < name.length() && safe.length() < 96; i++) {
            char c = name.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '-' || c == '_' || c == '(' || c == ')' || c == ' '
                    || (c >= '\u4e00' && c <= '\u9fff');
            safe.append(ok ? c : '_');
        }
        String cleaned = safe.toString().trim();
        if (cleaned.isEmpty()) return null;
        String lower = cleaned.toLowerCase(Locale.US);
        String ext = lower.endsWith(".apks") ? "apks" : (lower.endsWith(".apk") ? "apk" : "");
        if (!ALLOWED_EXT.contains(ext)) return null;
        return cleaned;
    }

    private File uniqueFile(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; i < 50; i++) {
            File alt = new File(dir, base + "-" + i + ext);
            if (!alt.exists()) return alt;
        }
        return f;
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static String jsonEscape(String value) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') sb.append('\\').append(c);
            else if (c < 0x20) sb.append(' ');
            else sb.append(c);
        }
        return sb.toString();
    }

    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (Throwable e) {
            return value;
        }
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq > 0 ? pair.substring(0, eq) : pair;
            String v = eq > 0 ? pair.substring(eq + 1) : "";
            try {
                map.put(URLDecoder.decode(k, "UTF-8"),
                        URLDecoder.decode(v, "UTF-8"));
            } catch (Throwable ignored) {
            }
        }
        return map;
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                byte[] bytes = buf.toByteArray();
                int len = bytes.length;
                if (len > 0 && bytes[len - 1] == '\r') len--;
                return new String(bytes, 0, len, StandardCharsets.UTF_8);
            }
            if (buf.size() > 8192) return null;
            buf.write(c);
        }
        return buf.size() == 0 ? null : new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void respond(OutputStream out, int code, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String status = code == 200 ? "OK" : (code == 400 ? "Bad Request"
                : (code == 404 ? "Not Found" : (code == 413 ? "Payload Too Large" : "Error")));
        String head = "HTTP/1.1 " + code + " " + status + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n"
                + "X-Content-Type-Options: nosniff\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
    }

    private static void respondJson(OutputStream out, int code, String body) throws IOException {
        respond(out, code, "application/json; charset=utf-8", body);
    }

    private static void closeQuietly(ServerSocket socket) {
        if (socket == null) return;
        try {
            socket.close();
        } catch (Throwable ignored) {
        }
    }

    private static void closeQuietly(Socket socket) {
        if (socket == null) return;
        try {
            socket.close();
        } catch (Throwable ignored) {
        }
    }

    private void log(String line) {
        if (listener != null) listener.onLog(line);
    }

    /** 本机第一块「站点本地」IPv4，用来在页面上打印手机该填哪个地址。 */
    public static String hostAddress() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> nets =
                    java.net.NetworkInterface.getNetworkInterfaces();
            String fallback = null;
            while (nets.hasMoreElements()) {
                java.net.NetworkInterface net = nets.nextElement();
                if (!net.isUp()) continue;
                java.util.Enumeration<java.net.InetAddress> addrs = net.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress addr = addrs.nextElement();
                    if (addr instanceof java.net.Inet4Address) {
                        String host = addr.getHostAddress();
                        if (addr.isSiteLocalAddress()) return host;
                        if (fallback == null) fallback = host;
                    }
                }
            }
            if (fallback != null) return fallback;
        } catch (Throwable ignored) {
        }
        return "车机本机地址";
    }

    // ---------------------------------------------------------------- page

    private static final String INDEX_HTML =
            "<!doctype html><html lang=zh><meta charset=utf-8>"
            + "<meta name=viewport content='width=device-width,initial-scale=1'>"
            + "<title>iCarPlay 传文件</title>"
            + "<style>"
            + "*{box-sizing:border-box;margin:0;padding:0}"
            + "body{min-height:100vh;background:radial-gradient(120% 80% at 10% 0,#0b1120 0,#04050a 60%),#04050a;"
            + "color:#f6f8ff;font:16px/1.5 -apple-system,'PingFang SC','Noto Sans CJK SC',sans-serif;padding:26px 18px 40px}"
            + ".wrap{max-width:560px;margin:0 auto}"
            + "h1{font-size:30px;letter-spacing:1px;background:linear-gradient(90deg,#9b6cff,#38f2ff);"
            + "-webkit-background-clip:text;background-clip:text;color:transparent;text-align:center}"
            + ".sub{color:#8d9bb3;font-size:13px;text-align:center;margin-top:6px}"
            + ".card{border-radius:18px;padding:16px;margin-top:14px;border:1px solid #223047;"
            + "background:linear-gradient(180deg,#101726,#04050a)}"
            + ".card.green{box-shadow:inset 0 0 0 1px rgba(99,255,166,.25)}"
            + ".card.cyan{box-shadow:inset 0 0 0 1px rgba(56,242,255,.25)}"
            + ".label{font-size:12px;letter-spacing:1.4px;color:#8d9bb3}"
            + ".addr{font:600 19px 'SF Mono',Menlo,monospace;color:#38f2ff;word-break:break-all;margin-top:6px}"
            + "a{color:#38f2ff}"
            + "input[type=file]{display:none}"
            + ".pick{display:block;text-align:center;padding:22px 10px;border-radius:14px;margin-top:12px;"
            + "border:1px dashed rgba(56,242,255,.5);color:#f6f8ff;font-weight:700;background:rgba(56,242,255,.06)}"
            + ".pick:active{background:rgba(56,242,255,.16)}"
            + ".bar{height:8px;border-radius:6px;background:#101726;margin-top:12px;overflow:hidden;display:none}"
            + ".bar i{display:block;height:100%;width:0;background:linear-gradient(90deg,#9b6cff,#38f2ff,#63ffa6)}"
            + ".msg{font-size:14px;color:#8d9bb3;margin-top:10px;min-height:20px}"
            + ".row{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:11px 0;"
            + "border-bottom:1px solid rgba(34,48,71,.7)}"
            + ".row:last-child{border-bottom:0}"
            + ".name{font-size:15px;font-weight:600;word-break:break-all}"
            + ".meta{font-size:12px;color:#8d9bb3;margin-top:2px}"
            + ".dl{flex:0 0 auto;font-size:13px;font-weight:700;color:#63ffa6;border:1px solid rgba(99,255,166,.45);"
            + "border-radius:12px;padding:7px 13px;text-decoration:none}"
            + "</style>"
            + "<div class=wrap>"
            + "<h1>iCarPlay 传文件</h1>"
            + "<div class=sub>手机 ⇄ 车机 · APK 互传 · 只在同一局域网内可用</div>"
            + "<div class='card green'><div class=label>车机地址</div><div class=addr>__ADDRESS__</div>"
            + "<div class=sub style='text-align:left;margin-top:8px'>用手机浏览器打开这个地址即可上传 APK；"
            + "单文件上限 __LIMIT__，只收 .apk / .apks。</div></div>"
            + "<div class='card cyan'><div class=label>把 APK 传到车机</div>"
            + "<input id=f type=file accept='.apk,.apks,application/vnd.android.package-archive'>"
            + "<label class=pick for=f>选择 APK · 开始上传</label>"
            + "<div class=bar id=bar><i></i></div><div class=msg id=msg>选好文件就会自动开始上传</div></div>"
            + "<div class=card><div class=label>车机上的 APK</div><div id=list class=msg>读取中…</div></div>"
            + "<div class=sub style='margin-top:16px'>页面只做收发，不在后台安装；装包请在车机 iCarPlay 页点“安装”。</div>"
            + "</div>"
            + "<script>"
            + "var f=document.getElementById('f'),bar=document.getElementById('bar').firstElementChild,"
            + "msg=document.getElementById('msg'),list=document.getElementById('list');"
            + "function human(n){if(n<1024)return n+' B';if(n<1048576)return (n/1024).toFixed(0)+' KB';"
            + "return (n/1048576).toFixed(1)+' MB';}"
            + "function load(){fetch('/api/list').then(function(r){return r.json()}).then(function(d){"
            + "if(!d.files.length){list.innerHTML='车机里还没有 APK';return;}"
            + "list.innerHTML=d.files.map(function(x){return '<div class=row><div><div class=name>'+x.name+"
            + "'</div><div class=meta>'+x.human+' · '+x.time+'</div></div><a class=dl href=\"/dl?name='+"
            + "encodeURIComponent(x.name)+'\">下载</a></div>';}).join('');"
            + "}).catch(function(){list.innerHTML='读取失败，刷新页面重试';});}"
            + "f.onchange=function(){var file=f.files[0];if(!file)return;"
            + "var x=new XMLHttpRequest();x.open('PUT','/upload?name='+encodeURIComponent(file.name));"
            + "bar.parentNode.style.display='block';msg.textContent='上传中 0%';"
            + "x.upload.onprogress=function(e){if(e.lengthComputable){var p=Math.round(e.loaded/e.total*100);"
            + "bar.style.width=p+'%';msg.textContent='上传中 '+p+'%';}};"
            + "x.onload=function(){if(x.status===200){msg.textContent='上传完成：'+file.name;"
            + "bar.style.width='100%';load();}else{msg.textContent='上传失败：'+(x.responseText||x.status);}};"
            + "x.onerror=function(){msg.textContent='网络中断，确认手机和车机在同一局域网';};"
            + "x.send(file);};load();"
            + "</script></html>";
}
