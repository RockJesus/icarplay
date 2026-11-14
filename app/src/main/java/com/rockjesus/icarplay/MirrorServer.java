package com.rockjesus.icarplay;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 网页投屏服务（WebRTC）。
 *
 * <p>车机当接收端：本服务在局域网里提供两个页面和一道路由：
 * <ul>
 *   <li>{@code GET /car?t=…} 车机自己的接收页（WebView 打开）</li>
 *   <li>{@code GET /send?t=…} 手机浏览器打开的发送页，用 {@code getDisplayMedia} 采集屏幕</li>
 *   <li>{@code GET /ws?t=…&role=…} WebSocket 信令，只做 SDP/ICE 文本转发</li>
 * </ul>
 * 媒体流走 WebRTC 直连（局域网 host candidate），不经过本服务，也不出公网。
 * 全部用纯 JDK socket 实现，不引入依赖、不申请系统权限。
 */
public class MirrorServer {

    private static final int PORT_START = 8180;
    private static final int PORT_TRIES = 8;

    private ServerSocket server;
    private Thread acceptThread;
    private volatile boolean running;
    private int port = -1;
    private final String token = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private final List<Client> clients = Collections.synchronizedList(new ArrayList<Client>());

    public synchronized int start() throws IOException {
        if (running) return port;
        IOException last = null;
        for (int i = 0; i < PORT_TRIES; i++) {
            try {
                server = new ServerSocket(PORT_START + i);
                port = PORT_START + i;
                break;
            } catch (IOException e) {
                last = e;
            }
        }
        if (server == null) throw last == null ? new IOException("no free port") : last;
        running = true;
        acceptThread = new Thread(this::acceptLoop, "mirror-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        return port;
    }

    public synchronized void stop() {
        running = false;
        try {
            if (server != null) server.close();
        } catch (IOException ignored) {
        }
        server = null;
        synchronized (clients) {
            for (Client c : clients) c.close();
            clients.clear();
        }
    }

    public String token() {
        return token;
    }

    public int port() {
        return port;
    }

    public String lanIp() {
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs.hasMoreElements()) {
                NetworkInterface nif = ifs.nextElement();
                if (!nif.isUp() || nif.isLoopback()) continue;
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        String host = a.getHostAddress();
                        if (host != null && !host.startsWith("127.")) return host;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return "127.0.0.1";
    }

    // ---------- accept ----------

    private void acceptLoop() {
        while (running) {
            try {
                Socket s = server.accept();
                Thread t = new Thread(() -> handle(s), "mirror-conn");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if (!running) return;
            }
        }
    }

    private void handle(Socket socket) {
        try {
            socket.setSoTimeout(0);
            InputStream in = new BufferedInputStream(socket.getInputStream());
            String requestLine = readLine(in);
            if (requestLine == null) {
                closeQuietly(socket);
                return;
            }
            String path = requestLine.split(" ").length > 1 ? requestLine.split(" ")[1] : "/";
            String query = "";
            int q = path.indexOf('?');
            if (q >= 0) {
                query = path.substring(q + 1);
                path = path.substring(0, q);
            }
            String upgrade = null;
            String wsKey = null;
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                String lower = line.toLowerCase(Locale.US);
                if (lower.startsWith("upgrade:")) upgrade = line.substring(8).trim();
                if (lower.startsWith("sec-websocket-key:")) wsKey = line.substring(18).trim();
            }

            String t = param(query, "t");
            boolean open = "/".equals(path) || "/favicon.ico".equals(path);
            if (!token.equals(t) && !open) {
                write(socket, 403, "text/plain; charset=utf-8", "403 令牌已过期，请重开车机端页面");
                return;
            }

            if ("websocket".equalsIgnoreCase(upgrade) && wsKey != null) {
                if ("/ws".equals(path)) {
                    doWebSocket(socket, in, wsKey);
                } else {
                    write(socket, 404, "text/plain", "404");
                }
                return;
            }

            if ("/".equals(path) || "/car".equals(path)) {
                write(socket, 200, "text/html; charset=utf-8", carPage());
            } else if ("/send".equals(path)) {
                write(socket, 200, "text/html; charset=utf-8", sendPage());
            } else if ("/favicon.ico".equals(path)) {
                write(socket, 204, "text/plain", "");
            } else {
                write(socket, 404, "text/plain; charset=utf-8", "not found");
            }
            closeQuietly(socket);
        } catch (Throwable ignored) {
            closeQuietly(socket);
        }
        // WebSocket 连接由 doWebSocket 持续持有，这里不再关闭
    }

    // ---------- HTTP ----------

    private void write(Socket socket, int code, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        OutputStream out = socket.getOutputStream();
        StringBuilder head = new StringBuilder();
        head.append("HTTP/1.1 ").append(code).append(' ')
                .append(code == 200 ? "OK" : (code == 204 ? "No Content" : (code == 403 ? "Forbidden" : "Not Found")))
                .append("\r\n");
        head.append("Content-Type: ").append(type).append("\r\n");
        head.append("Content-Length: ").append(bytes.length).append("\r\n");
        head.append("Cache-Control: no-store\r\n");
        head.append("Connection: close\r\n\r\n");
        out.write(head.toString().getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }

    private String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') buf.write(b);
            if (buf.size() > 8192) break;
        }
        if (b == -1 && buf.size() == 0) return null;
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }

    private String param(String query, String key) {
        for (String kv : query.split("&")) {
            int eq = kv.indexOf('=');
            if (eq > 0 && key.equals(kv.substring(0, eq))) return kv.substring(eq + 1);
        }
        return "";
    }

    // ---------- WebSocket 信令 ----------

    private void doWebSocket(Socket socket, InputStream in, String key) throws IOException {
        String accept = null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] sha = md.digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                    .getBytes(StandardCharsets.US_ASCII));
            accept = java.util.Base64.getEncoder().encodeToString(sha);
        } catch (Throwable ignored) {
        }
        if (accept == null) {
            closeQuietly(socket);
            return;
        }
        OutputStream out = socket.getOutputStream();
        String head = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.flush();

        Client client = new Client(socket, out);
        synchronized (clients) {
            clients.add(client);
            if (clients.size() > 2) {
                // 只允许车机 + 一台手机，多出来的拒绝，避免信令串线
                client.close();
                clients.remove(client);
                return;
            }
        }
        broadcast("{\"type\":\"peer\",\"count\":" + clients.size() + "}", client);

        try {
            while (running && !socket.isClosed()) {
                String msg = readFrame(in);
                if (msg == null) break;
                if (msg.isEmpty()) continue;
                broadcast(msg, client);
            }
        } catch (Throwable ignored) {
        } finally {
            synchronized (clients) {
                clients.remove(client);
            }
            client.close();
        }
    }

    private String readFrame(InputStream in) throws IOException {
        int b0 = in.read();
        int b1 = in.read();
        if (b0 == -1 || b1 == -1) return null;
        int opcode = b0 & 0x0F;
        boolean masked = (b1 & 0x80) != 0;
        long len = b1 & 0x7F;
        if (len == 126) {
            len = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
        } else if (len == 127) {
            len = 0;
            for (int i = 0; i < 8; i++) len = (len << 8) | (in.read() & 0xFF);
        }
        byte[] mask = new byte[4];
        if (masked) {
            for (int i = 0; i < 4; i++) mask[i] = (byte) in.read();
        }
        if (opcode == 0x8) return null;             // close
        if (opcode == 0x9 || opcode == 0xA) return ""; // ping/pong 忽略
        byte[] data = new byte[(int) Math.min(len, 1 << 20)];
        int read = 0;
        while (read < data.length) {
            int r = in.read(data, read, data.length - read);
            if (r < 0) return null;
            read += r;
        }
        if (masked) {
            for (int i = 0; i < data.length; i++) data[i] ^= mask[i % 4];
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    private void broadcast(String text, Client except) {
        synchronized (clients) {
            for (Client c : new ArrayList<>(clients)) {
                if (c == except) continue;
                c.sendText(text);
            }
        }
    }

    private static final class Client {
        final Socket socket;
        final OutputStream out;

        Client(Socket socket, OutputStream out) {
            this.socket = socket;
            this.out = out;
        }

        synchronized void sendText(String text) {
            try {
                byte[] payload = text.getBytes(StandardCharsets.UTF_8);
                ByteArrayOutputStream frame = new ByteArrayOutputStream();
                frame.write(0x81);
                if (payload.length < 126) {
                    frame.write(payload.length);
                } else if (payload.length < 65536) {
                    frame.write(126);
                    frame.write((payload.length >> 8) & 0xFF);
                    frame.write(payload.length & 0xFF);
                } else {
                    frame.write(127);
                    frame.write(0);
                    frame.write(0);
                    frame.write(0);
                    frame.write(0);
                    frame.write((payload.length >> 24) & 0xFF);
                    frame.write((payload.length >> 16) & 0xFF);
                    frame.write((payload.length >> 8) & 0xFF);
                    frame.write(payload.length & 0xFF);
                }
                frame.write(payload);
                out.write(frame.toByteArray());
                out.flush();
            } catch (IOException ignored) {
            }
        }

        void close() {
            try {
                socket.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private void closeQuietly(Socket s) {
        try {
            if (s != null) s.close();
        } catch (Throwable ignored) {
        }
    }

    // ---------- 页面 ----------

    private String carPage() {
        String js = "var TOKEN='" + token + "';var WS='/ws?t='+TOKEN+'&role=car';"
                + "var pc=null,ws=null,v=document.getElementById('v'),"
                + "hint=document.getElementById('hint'),stat=document.getElementById('stat');"
                + "function log(t){stat.textContent=t}"
                + "function send(o){if(ws&&ws.readyState===1)ws.send(JSON.stringify(o))}"
                + "function connect(){ws=new WebSocket((location.protocol==='https:'?'wss://':'ws://')"
                + "+location.host+WS);"
                + "ws.onopen=function(){log('信令已连接，等待手机')};"
                + "ws.onclose=function(){log('信令断开，3 秒后重连');setTimeout(connect,3000)};"
                + "ws.onmessage=function(e){var m=JSON.parse(e.data);"
                + "if(m.type==='offer'){gotOffer(m.sdp)}"
                + "else if(m.type==='ice'){if(pc&&m.candidate){pc.addIceCandidate(m.candidate).catch(function(){})}}"
                + "else if(m.type==='peer'){log('在线设备 '+m.count+'/2')}"
                + "else if(m.type==='bye'){log('手机已停止投屏');if(pc){pc.close();pc=null}hint.style.display='flex'}"
                + "}};"
                + "function gotOffer(sdp){if(pc){pc.close()}"
                + "pc=new RTCPeerConnection({iceServers:[]});"
                + "pc.onicecandidate=function(e){if(e.candidate)send({type:'ice',candidate:e.candidate})};"
                + "pc.ontrack=function(e){v.srcObject=e.streams[0];v.play().catch(function(){});hint.style.display='none';log('已连接，正在投屏')};"
                + "pc.onconnectionstatechange=function(){"
                + "if(pc.connectionState==='connected'){hint.style.display='none';log('已连接，正在投屏')}"
                + "else if(pc.connectionState==='failed'||pc.connectionState==='disconnected'){"
                + "log('连接中断，等手机重新开始');hint.style.display='flex'}};"
                + "pc.setRemoteDescription(sdp).then(function(){return pc.createAnswer()})"
                + ".then(function(a){return pc.setLocalDescription(a)})"
                + ".then(function(){send({type:'answer',sdp:pc.localDescription})});}"
                + "connect();";

        String url = "http://" + lanIp() + ":" + port + "/send?t=" + token;
        return "<!doctype html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>网页投屏 · 车机</title><style>"
                + "html,body{margin:0;height:100%;background:#04050A;color:#F6F8FF;"
                + "font-family:system-ui,-apple-system,'Noto Sans SC',sans-serif;overflow:hidden}"
                + "video{width:100%;height:100%;object-fit:contain;background:#000;display:block}"
                + "#hint{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;"
                + "justify-content:center;gap:14px;text-align:center;padding:20px}"
                + "#hint h1{font-size:26px;margin:0;letter-spacing:.06em;color:#00E5FF}"
                + "#hint p{margin:0;color:#8D9BB3;font-size:15px;line-height:1.7}"
                + "#url{font-family:ui-monospace,monospace;font-size:22px;color:#63FFA6;"
                + "background:#0A0E17;border:1px solid #223047;border-radius:12px;padding:10px 18px}"
                + "</style></head><body><video id='v' autoplay playsinline muted></video>"
                + "<div id='hint'><h1>网页投屏</h1>"
                + "<p><span id='stat'>等待手机连接</span></p>"
                + "<div id='url'>" + url + "</div>"
                + "<p>手机连同一个 Wi‑Fi，用 <b>安卓 Chrome</b> 打开上面地址<br>"
                + "点「开始共享屏幕」即可投到车机；iPhone 不支持此方式，请用 AirPlay</p>"
                + "</div><script>" + js + "</script></body></html>";
    }

    private String sendPage() {
        return "<!doctype html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>投屏到车机</title><style>"
                + "body{margin:0;background:#04050A;color:#F6F8FF;"
                + "font-family:system-ui,-apple-system,'Noto Sans SC',sans-serif;"
                + "display:flex;flex-direction:column;align-items:center;justify-content:center;"
                + "min-height:100vh;gap:18px;padding:24px;text-align:center}"
                + "h1{font-size:24px;margin:0;color:#00E5FF;letter-spacing:.05em}"
                + "button{font-size:18px;padding:14px 34px;border-radius:999px;border:0;"
                + "background:linear-gradient(135deg,#9B6CFF,#38F2FF);color:#04050A;font-weight:700}"
                + "p{color:#8D9BB3;font-size:14px;line-height:1.7;margin:0;max-width:420px}"
                + "#st{font-family:ui-monospace,monospace;color:#63FFA6}"
                + "video{width:min(92vw,420px);border-radius:12px;background:#000}"
                + "</style></head><body><h1>投屏到车机</h1>"
                + "<p>这台手机的画面会通过 Wi‑Fi 直连到车机，不上传云端。</p>"
                + "<button id='go'>开始共享屏幕</button>"
                + "<video id='local' autoplay playsinline muted></video>"
                + "<p id='st'>未开始</p>"
                + "<script>var TOKEN='" + token + "',WS='ws://'+location.host+'/ws?t='+TOKEN+'&role=phone';"
                + "var pc=null,ws=null,stream=null;"
                + "function send(o){if(ws&&ws.readyState===1)ws.send(JSON.stringify(o))}"
                + "function connect(){ws=new WebSocket(WS);"
                + "ws.onmessage=function(e){var m=JSON.parse(e.data);"
                + "if(m.type==='answer'){pc.setRemoteDescription(m.sdp)}"
                + "else if(m.type==='ice'){if(m.candidate)pc.addIceCandidate(m.candidate).catch(function(){})}};"
                + "ws.onclose=function(){setTimeout(connect,2000)}}"
                + "async function start(){try{"
                + "stream=await navigator.mediaDevices.getDisplayMedia({video:{frameRate:30},audio:false});"
                + "document.getElementById('local').srcObject=stream;"
                + "if(!ws)connect();"
                + "pc=new RTCPeerConnection({iceServers:[]});"
                + "stream.getTracks().forEach(function(t){pc.addTrack(t,stream)});"
                + "pc.onicecandidate=function(e){if(e.candidate)send({type:'ice',candidate:e.candidate})};"
                + "var offer=await pc.createOffer();await pc.setLocalDescription(offer);"
                + "send({type:'offer',sdp:pc.localDescription});"
                + "document.getElementById('st').textContent='已开始投屏，保持此页在前台';"
                + "stream.getVideoTracks()[0].onended=function(){stop()};"
                + "}catch(err){document.getElementById('st').textContent="
                + "'失败：'+err.message+'（安卓 Chrome 才支持共享屏幕）'}}"
                + "function stop(){send({type:'bye'});if(pc){pc.close();pc=null}"
                + "if(stream){stream.getTracks().forEach(function(t){t.stop()});stream=null}"
                + "document.getElementById('st').textContent='已停止'}"
                + "document.getElementById('go').onclick=start;"
                + "window.addEventListener('beforeunload',stop);"
                + "</script></body></html>";
    }
}
