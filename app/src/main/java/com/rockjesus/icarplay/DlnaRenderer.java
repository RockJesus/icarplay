package com.rockjesus.icarplay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.view.Surface;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 轻量 DLNA / UPnP DMR：SSDP 组播应答 + 最小 HTTP 控制点 + MediaPlayer / ImageView 呈现。
 * 接收别人推过来的音频、视频、图片，不主动发现网络里的其他设备，也不改动系统网络配置。
 * 手机端用支持 DLNA 投送的 App（音乐“投送到设备”、相册投屏、文件管理器 Cast）选中本机即可。
 */
public final class DlnaRenderer {

    public static final String ST_NO_MEDIA = "NO_MEDIA_PRESENT";
    public static final String ST_STOPPED = "STOPPED";
    public static final String ST_PLAYING = "PLAYING";
    public static final String ST_PAUSED = "PAUSED_PLAYBACK";
    public static final String ST_TRANSITIONING = "TRANSITIONING";

    public static final String KIND_AUDIO = "audio";
    public static final String KIND_VIDEO = "video";
    public static final String KIND_IMAGE = "image";

    private static final String[] VIDEO_EXT = {".mp4", ".mkv", ".webm", ".avi", ".mov",
            ".m4v", ".ts", ".m3u8", ".flv", ".3gp", ".m2ts"};
    private static final String[] IMAGE_EXT = {".jpg", ".jpeg", ".png", ".gif", ".bmp",
            ".webp", ".heic", ".heif"};

    private static final String AVT = "urn:schemas-upnp-org:service:AVTransport:1";
    private static final String RCS = "urn:schemas-upnp-org:service:RenderingControl:1";
    private static final String CMS = "urn:schemas-upnp-org:service:ConnectionManager:1";
    private static final String DEVTYPE = "urn:schemas-upnp-org:device:MediaRenderer:1";
    private static final String SSDP_ADDR = "239.255.255.250";
    private static final int SSDP_PORT = 1900;

    private final Context context;
    private final String uuid;
    private final String deviceName;
    private final AudioManager audio;

    private ServerSocket serverSocket;
    private MulticastSocket ssdpSocket;
    private WifiManager.MulticastLock multicastLock;
    private Thread httpThread;
    private Thread ssdpThread;
    private Thread notifyThread;

    private volatile boolean running;
    private volatile int port = -1;
    private volatile String state = ST_NO_MEDIA;
    private volatile String currentUri = "";
    private volatile String title = "";
    private volatile String lastError = "";

    private MediaPlayer player;
    private boolean pendingPlay;
    private volatile String kind = "";
    private volatile Surface videoSurface;
    private volatile Listener listener;
    private volatile int imageToken;

    /** 页面侧回调：切换媒体类型、贴图片。回调可能来自工作线程，实现方自行切主线程。 */
    public interface Listener {
        void onKind(String kind, String title);

        void onImage(Bitmap bitmap, String title);
    }

    public DlnaRenderer(Context context) {
        this.context = context.getApplicationContext();
        String tail = UUID.randomUUID().toString().replace("-", "");
        this.uuid = "uuid:" + tail.substring(0, 8) + "-" + tail.substring(8, 12) + "-"
                + tail.substring(12, 16) + "-" + tail.substring(16, 20) + "-"
                + tail.substring(20, 32);
        String suffix = tail.substring(24, 28);
        this.deviceName = "iCarPlay 车机 · " + suffix;
        this.audio = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
    }

    // ---------- 对外状态 ----------

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getUuid() {
        return uuid;
    }

    public String getKind() {
        return kind;
    }

    public void setListener(Listener value) {
        this.listener = value;
    }

    /** 视频投送时页面把 SurfaceView 的 Surface 交进来；音频/图片传 null 即可。 */
    public void setVideoSurface(Surface surface) {
        this.videoSurface = surface;
        try {
            MediaPlayer p = player;
            if (p != null && surface != null) p.setSurface(surface);
        } catch (Exception ignored) {
        }
    }

    public String getState() {
        return state;
    }

    public String getCurrentUri() {
        return currentUri;
    }

    public String getTitle() {
        return title;
    }

    public String getLastError() {
        return lastError;
    }

    public int getVolume() {
        if (audio == null) return 0;
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        return max <= 0 ? 0 : Math.round(cur * 100f / max);
    }

    public void setVolume(int percent) {
        if (audio == null) return;
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int value = Math.round(Math.max(0, Math.min(100, percent)) * max / 100f);
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0);
        } catch (Exception ignored) {
        }
    }

    public int getDurationMs() {
        try {
            MediaPlayer p = player;
            return p == null ? 0 : Math.max(0, p.getDuration());
        } catch (Exception ignored) {
            return 0;
        }
    }

    public int getPositionMs() {
        try {
            MediaPlayer p = player;
            return p == null ? 0 : Math.max(0, p.getCurrentPosition());
        } catch (Exception ignored) {
            return 0;
        }
    }

    public String getPositionText() {
        if (KIND_IMAGE.equals(kind)) return ST_PLAYING.equals(state) ? "静态图片" : "—";
        int pos = Math.max(0, getPositionMs());
        int dur = Math.max(0, getDurationMs());
        return clock(pos) + " / " + (dur <= 0 ? "—" : clock(dur));
    }

    public static String clock(int ms) {
        int total = Math.max(0, ms) / 1000;
        return String.format(Locale.US, "%02d:%02d", total / 60, total % 60);
    }

    // ---------- 启停 ----------

    public boolean start() {
        if (running) return true;
        lastError = "";
        acquireMulticastLock();
        int tried = 0;
        while (tried < 8 && serverSocket == null) {
            try {
                ServerSocket candidate = new ServerSocket();
                candidate.setReuseAddress(true);
                candidate.bind(new InetSocketAddress(8099 + tried));
                serverSocket = candidate;
                port = candidate.getLocalPort();
            } catch (Exception e) {
                tried++;
            }
        }
        if (serverSocket == null) {
            lastError = "本机 8099–8106 端口都被占用，无法启动接收";
            releaseMulticastLock();
            return false;
        }
        running = true;
        state = ST_NO_MEDIA;
        httpThread = new Thread(this::serveHttp, "dlna-http");
        httpThread.start();
        ssdpThread = new Thread(this::serveSsdp, "dlna-ssdp");
        ssdpThread.start();
        notifyThread = new Thread(this::sendAliveLoop, "dlna-notify");
        notifyThread.start();
        return true;
    }

    public void stop() {
        running = false;
        releasePlayer();
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) {
        }
        serverSocket = null;
        try {
            if (ssdpSocket != null) ssdpSocket.close();
        } catch (Exception ignored) {
        }
        ssdpSocket = null;
        httpThread = null;
        ssdpThread = null;
        notifyThread = null;
        state = ST_NO_MEDIA;
        currentUri = "";
        title = "";
        port = -1;
        releaseMulticastLock();
    }

    private void acquireMulticastLock() {
        try {
            WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return;
            multicastLock = wm.createMulticastLock("icarplay-dlna");
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();
        } catch (Exception ignored) {
        }
    }

    private void releaseMulticastLock() {
        try {
            if (multicastLock != null && multicastLock.isHeld()) multicastLock.release();
        } catch (Exception ignored) {
        }
        multicastLock = null;
    }

    // ---------- 本机地址 ----------

    private String localIp() {
        try {
            WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                android.net.DhcpInfo dhcp = wm.getDhcpInfo();
                if (dhcp != null && dhcp.ipAddress != 0) {
                    int v = dhcp.ipAddress;
                    return String.format(Locale.US, "%d.%d.%d.%d",
                            v & 0xff, (v >> 8) & 0xff, (v >> 16) & 0xff, (v >> 24) & 0xff);
                }
            }
        } catch (Exception ignored) {
        }
        try {
            java.util.Enumeration<java.net.NetworkInterface> all =
                    java.net.NetworkInterface.getNetworkInterfaces();
            while (all != null && all.hasMoreElements()) {
                java.net.NetworkInterface nif = all.nextElement();
                if (nif == null || !nif.isUp() || nif.isLoopback()) continue;
                java.util.Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof java.net.Inet4Address && !a.isLoopbackAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }

    public String location() {
        return "http://" + localIp() + ":" + (port > 0 ? port : 8099) + "/desc.xml";
    }

    // ---------- SSDP ----------

    private void serveSsdp() {
        try {
            MulticastSocket ms = new MulticastSocket(SSDP_PORT);
            ssdpSocket = ms;
            ms.setReuseAddress(true);
            ms.joinGroup(InetAddress.getByName(SSDP_ADDR));
            ms.setSoTimeout(1000);
            byte[] buffer = new byte[2048];
            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    ms.receive(packet);
                } catch (java.net.SocketTimeoutException timeout) {
                    continue;
                } catch (Exception e) {
                    break;
                }
                String request = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
                if (!request.startsWith("M-SEARCH")) continue;
                String st = headerValue(request, "ST");
                if (st == null) st = DEVTYPE;
                if (!matchesSearch(st)) continue;
                String target = st;
                if ("ssdp:all".equalsIgnoreCase(st) || "upnp:rootdevice".equalsIgnoreCase(st)) {
                    target = "upnp:rootdevice";
                }
                String reply = "HTTP/1.1 200 OK\r\n"
                        + "CACHE-CONTROL: max-age=1800\r\n"
                        + "EXT:\r\n"
                        + "LOCATION: " + location() + "\r\n"
                        + "SERVER: Android/1.0 UPnP/1.0 iCarPlay/1.0\r\n"
                        + "ST: " + target + "\r\n"
                        + "USN: " + uuid + "::" + target + "\r\n"
                        + "\r\n";
                byte[] out = reply.getBytes("UTF-8");
                DatagramPacket response = new DatagramPacket(out, out.length,
                        packet.getAddress(), packet.getPort());
                try {
                    ms.send(response);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            if (running) lastError = "SSDP 组播启动失败：" + e.getClass().getSimpleName();
        }
    }

    private boolean matchesSearch(String st) {
        if (st == null) return false;
        String s = st.toLowerCase(Locale.US);
        return s.equals("ssdp:all") || s.equals("upnp:rootdevice")
                || s.equals(DEVTYPE) || s.startsWith("urn:schemas-upnp-org:service:")
                || s.startsWith("urn:schemas-upnp-org:device:mediarenderer");
    }

    private static String headerValue(String raw, String name) {
        String[] lines = raw.split("\r\n");
        for (String line : lines) {
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            if (line.substring(0, colon).trim().equalsIgnoreCase(name)) {
                return line.substring(colon + 1).trim();
            }
        }
        return null;
    }

    private void sendAliveLoop() {
        byte[] buffer = new byte[2048];
        while (running) {
            sendNotify(buffer, "ssdp:alive");
            long slept = 0;
            while (running && slept < 30_000) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
                slept += 1000;
            }
        }
    }

    private void sendNotify(byte[] buffer, String nts) {
        try {
            InetAddress group = InetAddress.getByName(SSDP_ADDR);
            String[] types = {"upnp:rootdevice", DEVTYPE,
                    "urn:schemas-upnp-org:service:AVTransport:1",
                    "urn:schemas-upnp-org:service:RenderingControl:1"};
            for (String nt : types) {
                String message = "NOTIFY * HTTP/1.1\r\n"
                        + "HOST: " + SSDP_ADDR + ":" + SSDP_PORT + "\r\n"
                        + "CACHE-CONTROL: max-age=1800\r\n"
                        + "LOCATION: " + location() + "\r\n"
                        + "NT: " + nt + "\r\n"
                        + "NTS: " + nts + "\r\n"
                        + "SERVER: Android/1.0 UPnP/1.0 iCarPlay/1.0\r\n"
                        + "USN: " + uuid + "::" + nt + "\r\n"
                        + "\r\n";
                byte[] out = message.getBytes("UTF-8");
                DatagramPacket packet = new DatagramPacket(out, out.length, group, SSDP_PORT);
                MulticastSocket ms = ssdpSocket;
                if (ms != null) ms.send(packet);
            }
        } catch (Exception ignored) {
        }
    }

    // ---------- HTTP ----------

    private void serveHttp() {
        while (running) {
            Socket socket = null;
            try {
                ServerSocket ss = serverSocket;
                if (ss == null) break;
                socket = ss.accept();
                socket.setSoTimeout(4000);
                handle(socket);
            } catch (Exception e) {
                if (running && lastError.isEmpty()) {
                    lastError = "HTTP 服务异常：" + e.getClass().getSimpleName();
                }
            } finally {
                try {
                    if (socket != null) socket.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void handle(Socket socket) {
        try {
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream headBytes = new ByteArrayOutputStream();
            int last = -1;
            boolean headerEnd = false;
            int guard = 0;
            while (!headerEnd && guard++ < 8192) {
                int b = in.read();
                if (b < 0) return;
                headBytes.write(b);
                if ((last == '\n' || last == '\r') && b == '\n') {
                    String so = headBytes.toString("UTF-8");
                    if (so.endsWith("\r\n\r\n") || so.endsWith("\n\n")) headerEnd = true;
                }
                last = b;
            }
            String head = headBytes.toString("UTF-8");
            String[] lines = head.split("\r\n");
            if (lines.length == 0) return;
            String requestLine = lines[0];
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0].toUpperCase(Locale.US);
            String path = parts[1];
            int query = path.indexOf('?');
            if (query >= 0) path = path.substring(0, query);

            int contentLength = 0;
            String soapAction = "";
            for (String line : lines) {
                int colon = line.indexOf(':');
                if (colon <= 0) continue;
                String name = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                if (name.equalsIgnoreCase("Content-Length")) {
                    try {
                        contentLength = Integer.parseInt(value);
                    } catch (Exception ignored) {
                    }
                } else if (name.equalsIgnoreCase("SOAPAction")) {
                    soapAction = value;
                }
            }
            String body = "";
            if (contentLength > 0 && contentLength < 65536) {
                byte[] raw = new byte[contentLength];
                int read = 0;
                while (read < contentLength) {
                    int got = in.read(raw, read, contentLength - read);
                    if (got <= 0) break;
                    read += got;
                }
                body = new String(raw, 0, read, "UTF-8");
            }

            if (method.equals("POST")) {
                respondSoap(socket, path, soapAction, body);
            } else if (path.endsWith("desc.xml") || path.equals("/")) {
                respondXml(socket, descriptionXml());
            } else if (path.contains("scpd")) {
                respondXml(socket, scpdXml(path));
            } else if (path.equals("/state")) {
                respondText(socket, stateText());
            } else if (method.equals("SUBSCRIBE")) {
                respondSubscribe(socket);
            } else if (method.equals("UNSUBSCRIBE")) {
                respondEmpty(socket, "200 OK");
            } else {
                respondText(socket, "iCarPlay DLNA MediaRenderer\n" + stateText());
            }
        } catch (Exception ignored) {
        }
    }

    public String stateText() {
        return "STATE=" + state
                + "\nDEVICE=" + deviceName
                + "\nKIND=" + (kind.isEmpty() ? "-" : kind)
                + "\nURI=" + currentUri
                + "\nTITLE=" + title
                + "\nPOSITION=" + getPositionText()
                + "\nVOLUME=" + getVolume()
                + "\nERROR=" + lastError + "\n";
    }

    private void respondXml(Socket socket, String xml) {
        try {
            byte[] out = xml.getBytes("UTF-8");
            OutputStream os = new BufferedOutputStream(socket.getOutputStream());
            String header = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: text/xml; charset=\"utf-8\"\r\n"
                    + "Content-Length: " + out.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            os.write(header.getBytes("UTF-8"));
            os.write(out);
            os.flush();
        } catch (Exception ignored) {
        }
    }

    private void respondText(Socket socket, String text) {
        try {
            byte[] out = text.getBytes("UTF-8");
            OutputStream os = new BufferedOutputStream(socket.getOutputStream());
            String header = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: text/plain; charset=\"utf-8\"\r\n"
                    + "Content-Length: " + out.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            os.write(header.getBytes("UTF-8"));
            os.write(out);
            os.flush();
        } catch (Exception ignored) {
        }
    }

    private void respondEmpty(Socket socket, String status) {
        try {
            OutputStream os = new BufferedOutputStream(socket.getOutputStream());
            os.write(("HTTP/1.1 " + status + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                    .getBytes("UTF-8"));
            os.flush();
        } catch (Exception ignored) {
        }
    }

    private void respondSubscribe(Socket socket) {
        try {
            String sid = "uuid:" + UUID.randomUUID().toString();
            OutputStream os = new BufferedOutputStream(socket.getOutputStream());
            String header = "HTTP/1.1 200 OK\r\n"
                    + "SID: " + sid + "\r\n"
                    + "TIMEOUT: Second-1800\r\n"
                    + "Content-Length: 0\r\n"
                    + "Connection: close\r\n\r\n";
            os.write(header.getBytes("UTF-8"));
            os.flush();
        } catch (Exception ignored) {
        }
    }

    // ---------- SOAP ----------

    private void respondSoap(Socket socket, String path, String soapAction, String body) {
        String action = extractAction(soapAction, body);
        String result;
        if (action == null) {
            result = soapFault();
        } else if (path.contains("rcs") || action.contains("Volume") || action.contains("Mute")) {
            result = handleRenderingControl(action, body);
        } else if (path.contains("cms") || action.contains("ProtocolInfo")
                || action.contains("ConnectionIDs")) {
            result = handleConnectionManager(action);
        } else {
            result = handleAvTransport(action, body);
        }
        try {
            byte[] out = result.getBytes("UTF-8");
            OutputStream os = new BufferedOutputStream(socket.getOutputStream());
            String header = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: text/xml; charset=\"utf-8\"\r\n"
                    + "EXT:\r\n"
                    + "Content-Length: " + out.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            os.write(header.getBytes("UTF-8"));
            os.write(out);
            os.flush();
        } catch (Exception ignored) {
        }
    }

    private static String extractAction(String soapAction, String body) {
        String source = soapAction == null || soapAction.isEmpty() ? body : soapAction;
        if (source == null) return null;
        Matcher m = Pattern.compile("#([A-Za-z0-9_]+)\"").matcher(source);
        if (m.find()) return m.group(1);
        m = Pattern.compile("<u:([A-Za-z0-9_]+)").matcher(body == null ? "" : body);
        if (m.find()) return m.group(1);
        return null;
    }

    private String handleAvTransport(String action, String body) {
        switch (action) {
            case "SetAVTransportURI": {
                String uri = arg(body, "CurrentURI");
                String metadata = arg(body, "CurrentURIMetaData");
                lastError = "";
                setTrack(uri, metadata);
                return avtResponse(action, "");
            }
            case "Play": {
                if (currentUri.isEmpty()) {
                    lastError = "还没有收到音频地址，请先在手机上选择本机作为投送目标";
                    return avtResponse(action, "");
                }
                play();
                return avtResponse(action, "");
            }
            case "Pause": {
                pause();
                return avtResponse(action, "");
            }
            case "Stop": {
                stopPlayback();
                return avtResponse(action, "");
            }
            case "Seek": {
                seek(body);
                return avtResponse(action, "");
            }
            case "GetTransportInfo":
                return avtResponse(action, "<CurrentTransportState>" + state
                        + "</CurrentTransportState><CurrentTransportStatus>OK"
                        + "</CurrentTransportStatus><CurrentSpeed>1</CurrentSpeed>");
            case "GetCurrentTransportActions":
                return avtResponse(action, "<Actions>Play,Pause,Stop,Seek</Actions>");
            case "GetPositionInfo":
                return avtResponse(action, "<Track>1</Track><TrackDuration>"
                        + durationText() + "</TrackDuration><TrackMetaData></TrackMetaData>"
                        + "<TrackURI>" + escape(currentUri) + "</TrackURI><RelTime>"
                        + clock(getPositionMs()) + "</RelTime><AbsTime>"
                        + clock(getPositionMs()) + "</AbsTime><RelCount>0</RelCount>"
                        + "<AbsCount>0</AbsCount>");
            case "GetMediaInfo":
                return avtResponse(action, "<NrTracks>" + (currentUri.isEmpty() ? 0 : 1)
                        + "</NrTracks><MediaDuration>" + durationText()
                        + "</MediaDuration><CurrentURI>" + escape(currentUri)
                        + "</CurrentURI><CurrentURIMetaData></CurrentURIMetaData>"
                        + "<PlayMedium>NETWORK</PlayMedium>");
            case "SetNextAVTransportURI":
            case "GetTransportSettings":
            case "GetDeviceCapabilities":
                return avtResponse(action, "");
            default:
                return soapFault();
        }
    }

    private String handleRenderingControl(String action, String body) {
        switch (action) {
            case "GetVolume":
                return rcsResponse(action, "<CurrentVolume>" + getVolume() + "</CurrentVolume>");
            case "SetVolume": {
                String value = arg(body, "DesiredVolume");
                try {
                    setVolume(Integer.parseInt(value.trim()));
                } catch (Exception ignored) {
                }
                return rcsResponse(action, "");
            }
            case "GetMute":
                return rcsResponse(action, "<CurrentMute>0</CurrentMute>");
            case "SetMute":
                return rcsResponse(action, "");
            case "GetVolumeDBRange":
                return rcsResponse(action, "<MinValue>-80</MinValue><MaxValue>0</MaxValue>");
            case "ListPresets":
                return rcsResponse(action, "<CurrentPresetNameList>FactoryDefaults"
                        + "</CurrentPresetNameList>");
            case "SelectPreset":
                return rcsResponse(action, "");
            default:
                return soapFault();
        }
    }

    private String handleConnectionManager(String action) {
        if ("GetProtocolInfo".equals(action)) {
            return cmsResponse(action,
                    "<Source></Source><Sink>http-get:*:audio/mpeg:*,"
                            + "http-get:*:audio/mp4:*,http-get:*:audio/aac:*,"
                            + "http-get:*:audio/x-flac:*,http-get:*:audio/wav:*,"
                            + "http-get:*:audio/x-wav:*,http-get:*:audio/ogg:*,"
                            + "http-get:*:video/mp4:*,http-get:*:video/x-matroska:*,"
                            + "http-get:*:video/webm:*,http-get:*:video/mpeg:*,"
                            + "http-get:*:video/quicktime:*,http-get:*:image/jpeg:*,"
                            + "http-get:*:image/png:*,http-get:*:image/gif:*,"
                            + "http-get:*:image/webp:*</Sink>");
        }
        if ("GetCurrentConnectionIDs".equals(action)) {
            return cmsResponse(action, "<ConnectionIDs>0</ConnectionIDs>");
        }
        if ("GetCurrentConnectionInfo".equals(action)) {
            return cmsResponse(action, "<RcsID>0</RcsID><AVTransportID>0</AVTransportID>"
                    + "<ProtocolInfo></ProtocolInfo><PeerConnectionManager></PeerConnectionManager>"
                    + "<PeerConnectionID>-1</PeerConnectionID><Direction>Input</Direction>"
                    + "<Status>OK</Status>");
        }
        return soapFault();
    }

    private String durationText() {
        int dur = getDurationMs();
        if (dur <= 0) return "00:00:00";
        int total = dur / 1000;
        return String.format(Locale.US, "%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60);
    }

    private static String avtResponse(String action, String payload) {
        return envelope("AVTransport", action, payload);
    }

    private static String rcsResponse(String action, String payload) {
        return envelope("RenderingControl", action, payload);
    }

    private static String cmsResponse(String action, String payload) {
        return envelope("ConnectionManager", action, payload);
    }

    private static String envelope(String service, String action, String payload) {
        String type = "urn:schemas-upnp-org:service:" + service + ":1";
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n"
                + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\""
                + " s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
                + "<s:Body><u:" + action + "Response xmlns:u=\"" + type + "\">"
                + payload
                + "</u:" + action + "Response></s:Body></s:Envelope>";
    }

    private static String soapFault() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n"
                + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\""
                + " s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
                + "<s:Body><s:Fault><faultcode>s:Client</faultcode>"
                + "<faultstring>UPnPError</faultstring><detail>"
                + "<UPnPError xmlns=\"urn:schemas-upnp-org:control-1-0\">"
                + "<errorCode>401</errorCode><errorDescription>Invalid Action</errorDescription>"
                + "</UPnPError></detail></s:Fault></s:Body></s:Envelope>";
    }

    /** 从 SOAP 报文里取标签文本，兼容 &amp; 等转义。 */
    private static String arg(String body, String tag) {
        if (body == null) return "";
        Matcher m = Pattern.compile("<" + tag + "[^>]*>(.*?)</" + tag + ">",
                Pattern.DOTALL).matcher(body);
        if (!m.find()) return "";
        return unescape(m.group(1)).trim();
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String unescape(String value) {
        return value.replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
    }

    // ---------- 播放 ----------

    private void setTrack(String uri, String metadata) {
        state = ST_TRANSITIONING;
        if (uri == null || uri.isEmpty() || uri.startsWith("urn:")) {
            currentUri = uri == null ? "" : uri;
            title = guessTitle(currentUri, metadata);
            kind = "";
            imageToken++;
            releasePlayer();
            clearImage();
            pendingPlay = false;
            state = ST_NO_MEDIA;
            lastError = "只支持直接地址（http/https）的音频 / 视频 / 图片，这份投送用的是不支持的形式";
            notifyKind();
            return;
        }
        currentUri = uri;
        title = guessTitle(currentUri, metadata);
        kind = classify(currentUri, metadata);
        imageToken++;
        releasePlayer();
        clearImage();
        pendingPlay = false;
        stopStateAfterLoad = false;
        notifyKind();
        if (KIND_IMAGE.equals(kind)) {
            loadImage(currentUri, title);
            return;
        }
        try {
            MediaPlayer p = new MediaPlayer();
            p.setAudioStreamType(AudioManager.STREAM_MUSIC);
            Surface surface = videoSurface;
            if (KIND_VIDEO.equals(kind) && surface != null) {
                p.setSurface(surface);
                try {
                    p.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
                } catch (Exception ignored) {
                }
            }
            p.setOnPreparedListener(mp -> {
                state = ST_STOPPED;
                if (pendingPlay) {
                    pendingPlay = false;
                    try {
                        mp.start();
                        state = ST_PLAYING;
                    } catch (Exception e) {
                        state = ST_STOPPED;
                        lastError = "开始播放失败：" + e.getClass().getSimpleName();
                    }
                }
            });
            p.setOnCompletionListener(mp -> {
                if (!ST_PAUSED.equals(state)) state = ST_STOPPED;
            });
            p.setOnErrorListener((mp, what, extra) -> {
                state = ST_STOPPED;
                lastError = (KIND_VIDEO.equals(kind) ? "视频" : "音频")
                        + "播放失败（" + what + "/" + extra + "），可能是格式不支持或地址不可达";
                releasePlayer();
                return true;
            });
            p.setDataSource(currentUri);
            player = p;
            p.prepareAsync();
        } catch (Exception e) {
            state = ST_STOPPED;
            lastError = "无法打开这个地址：" + e.getClass().getSimpleName();
            releasePlayer();
        }
    }

    /** 按 DIDL 元数据里的 upnp:class / protocolInfo 判断类型，再退回按扩展名判断。 */
    private static String classify(String uri, String metadata) {
        String meta = metadata == null ? "" : metadata.toLowerCase(Locale.US);
        if (meta.contains("object.item.imageitem") || meta.contains("image/")) return KIND_IMAGE;
        if (meta.contains("object.item.videoitem") || meta.contains("video/")) return KIND_VIDEO;
        String value = uri == null ? "" : uri;
        int cut = value.indexOf('?');
        if (cut > 0) value = value.substring(0, cut);
        value = value.toLowerCase(Locale.US);
        for (String ext : VIDEO_EXT) {
            if (value.endsWith(ext)) return KIND_VIDEO;
        }
        for (String ext : IMAGE_EXT) {
            if (value.endsWith(ext)) return KIND_IMAGE;
        }
        return KIND_AUDIO;
    }

    private void notifyKind() {
        Listener l = listener;
        if (l != null) {
            try {
                l.onKind(kind, title);
            } catch (Exception ignored) {
            }
        }
    }

    private void clearImage() {
        Listener l = listener;
        if (l != null) {
            try {
                l.onImage(null, "");
            } catch (Exception ignored) {
            }
        }
    }

    /** 图片投送：下载到内存后交给页面贴图，视频/音频走 MediaPlayer。 */
    private void loadImage(final String uri, final String name) {
        final int token = ++imageToken;
        Thread worker = new Thread(() -> {
            Bitmap bitmap = null;
            String failure = "";
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(uri).openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(12000);
                conn.setInstanceFollowRedirects(true);
                conn.connect();
                InputStream in = conn.getInputStream();
                try {
                    bitmap = BitmapFactory.decodeStream(in);
                } finally {
                    try {
                        in.close();
                    } catch (Exception ignored) {
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                failure = "图片下载失败：" + e.getClass().getSimpleName();
            }
            if (token != imageToken) return;
            if (bitmap == null) {
                state = ST_STOPPED;
                lastError = failure.isEmpty() ? "图片解析失败，可能不是 JPEG/PNG" : failure;
                return;
            }
            state = ST_PLAYING;
            lastError = "";
            Listener l = listener;
            if (l != null) {
                try {
                    l.onImage(bitmap, name);
                } catch (Exception ignored) {
                }
            }
        }, "dlna-image");
        worker.setDaemon(true);
        worker.start();
    }

    private boolean stopStateAfterLoad;

    private static String guessTitle(String uri, String metadata) {
        try {
            Matcher m = Pattern.compile("<dc:title>(.*?)</dc:title>", Pattern.DOTALL)
                    .matcher(metadata == null ? "" : metadata);
            if (m.find()) {
                String t = unescape(m.group(1)).trim();
                if (!t.isEmpty()) return t;
            }
        } catch (Exception ignored) {
        }
        String value = uri == null ? "" : uri;
        int cut = value.indexOf('?');
        if (cut > 0) value = value.substring(0, cut);
        while (value.endsWith("/") && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
        }
        int slash = value.lastIndexOf('/');
        String tail = slash >= 0 ? value.substring(slash + 1) : value;
        try {
            tail = java.net.URLDecoder.decode(tail, "UTF-8");
        } catch (Exception ignored) {
        }
        return tail.isEmpty() ? "未命名音频" : tail;
    }

    private void play() {
        if (KIND_IMAGE.equals(kind)) {
            if (currentUri.isEmpty()) {
                pendingPlay = true;
                return;
            }
            state = ST_PLAYING;
            return;
        }
        MediaPlayer p = player;
        if (p == null) {
            pendingPlay = true;
            return;
        }
        try {
            if (p.isPlaying()) {
                state = ST_PLAYING;
                return;
            }
            p.start();
            state = ST_PLAYING;
        } catch (IllegalStateException e) {
            pendingPlay = true;
        } catch (Exception e) {
            lastError = "播放失败：" + e.getClass().getSimpleName();
        }
    }

    private void pause() {
        if (KIND_IMAGE.equals(kind)) {
            if (ST_PLAYING.equals(state)) state = ST_PAUSED;
            return;
        }
        try {
            if (player != null && player.isPlaying()) {
                player.pause();
                state = ST_PAUSED;
            }
        } catch (Exception ignored) {
        }
    }

    private void stopPlayback() {
        if (KIND_IMAGE.equals(kind)) {
            imageToken++;
            clearImage();
            state = currentUri.isEmpty() ? ST_NO_MEDIA : ST_STOPPED;
            return;
        }
        try {
            if (player != null) {
                player.pause();
                player.seekTo(0);
            }
        } catch (Exception ignored) {
        }
        state = currentUri.isEmpty() ? ST_NO_MEDIA : ST_STOPPED;
    }

    private void seek(String body) {
        String target = arg(body, "Target");
        int ms = parseClock(target);
        try {
            if (player != null && ms >= 0) player.seekTo(ms);
        } catch (Exception ignored) {
        }
    }

    private static int parseClock(String value) {
        try {
            String[] parts = value.trim().split(":");
            int h = 0, m = 0, s = 0;
            if (parts.length == 3) {
                h = Integer.parseInt(parts[0]);
                m = Integer.parseInt(parts[1]);
                s = Integer.parseInt(parts[2]);
            } else if (parts.length == 2) {
                m = Integer.parseInt(parts[0]);
                s = Integer.parseInt(parts[1]);
            }
            return ((h * 60 + m) * 60 + s) * 1000;
        } catch (Exception ignored) {
            return -1;
        }
    }

    private void releasePlayer() {
        try {
            if (player != null) {
                player.reset();
                player.release();
            }
        } catch (Exception ignored) {
        }
        player = null;
    }

    // ---------- 描述文件 ----------

    private String descriptionXml() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n"
                + "<root xmlns=\"urn:schemas-upnp-org:device-1-0\""
                + " xmlns:dlna=\"urn:schemas-dlna-org:device-1-0\">"
                + "<specVersion><major>1</major><minor>0</minor></specVersion>"
                + "<device>"
                + "<deviceType>" + DEVTYPE + "</deviceType>"
                + "<friendlyName>" + escape(deviceName) + "</friendlyName>"
                + "<manufacturer>iCarPlay</manufacturer>"
                + "<manufacturerURL>http://icarplay.local/</manufacturerURL>"
                + "<modelDescription>车机 DLNA 投送接收（音频·视频·图片）</modelDescription>"
                + "<modelName>iCarPlay MediaRenderer</modelName>"
                + "<modelNumber>0.9.5</modelNumber>"
                + "<UDN>" + uuid + "</UDN>"
                + "<dlna:X_DLNADOC>DMR-1.50</dlna:X_DLNADOC>"
                + "<serviceList>"
                + service("AVTransport", AVT)
                + service("RenderingControl", RCS)
                + service("ConnectionManager", CMS)
                + "</serviceList>"
                + "</device></root>";
    }

    private static String service(String name, String type) {
        return "<service><serviceType>" + type + "</serviceType>"
                + "<serviceId>urn:upnp-org:serviceId:" + name + "</serviceId>"
                + "<SCPDURL>/scpd/" + name.toLowerCase(Locale.US) + ".xml</SCPDURL>"
                + "<controlURL>/control/" + name.toLowerCase(Locale.US) + "</controlURL>"
                + "<eventSubURL>/event/" + name.toLowerCase(Locale.US) + "</eventSubURL>"
                + "</service>";
    }

    private String scpdXml(String path) {
        String name;
        String lower = path.toLowerCase(Locale.US);
        if (lower.contains("rcs")) name = "RenderingControl";
        else if (lower.contains("cms")) name = "ConnectionManager";
        else name = "AVTransport";
        StringBuilder actions = new StringBuilder();
        if ("AVTransport".equals(name)) {
            actions.append(action("SetAVTransportURI",
                    in("InstanceID") + in("CurrentURI") + in("CurrentURIMetaData")));
            actions.append(action("Play", in("InstanceID") + in("Speed")));
            actions.append(action("Pause", in("InstanceID")));
            actions.append(action("Stop", in("InstanceID")));
            actions.append(action("Seek", in("InstanceID") + in("Unit") + in("Target")));
            actions.append(action("GetTransportInfo", in("InstanceID"),
                    out("CurrentTransportState") + out("CurrentTransportStatus")
                            + out("CurrentSpeed")));
            actions.append(action("GetPositionInfo", in("InstanceID"),
                    out("Track") + out("TrackDuration") + out("TrackMetaData")
                            + out("TrackURI") + out("RelTime") + out("AbsTime")
                            + out("RelCount") + out("AbsCount")));
            actions.append(action("GetMediaInfo", in("InstanceID"),
                    out("NrTracks") + out("MediaDuration") + out("CurrentURI")
                            + out("CurrentURIMetaData") + out("PlayMedium")));
            actions.append(action("GetCurrentTransportActions", in("InstanceID"),
                    out("Actions")));
        } else if ("RenderingControl".equals(name)) {
            actions.append(action("GetVolume", in("InstanceID") + in("Channel"),
                    out("CurrentVolume")));
            actions.append(action("SetVolume", in("InstanceID") + in("Channel")
                    + in("DesiredVolume")));
            actions.append(action("GetMute", in("InstanceID") + in("Channel"),
                    out("CurrentMute")));
            actions.append(action("SetMute", in("InstanceID") + in("Channel") + in("DesiredMute")));
        } else {
            actions.append(action("GetProtocolInfo", "",
                    out("Source") + out("Sink")));
            actions.append(action("GetCurrentConnectionIDs", "", out("ConnectionIDs")));
        }
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n"
                + "<scpd xmlns=\"urn:schemas-upnp-org:service-1-0\">"
                + "<specVersion><major>1</major><minor>0</minor></specVersion>"
                + "<actionList>" + actions + "</actionList>"
                + "<serviceStateTable></serviceStateTable></scpd>";
    }

    private static String action(String name, String args) {
        return action(name, args, "");
    }

    private static String action(String name, String args, String outArgs) {
        return "<action><name>" + name + "</name><argumentList>" + args
                + ("".equals(outArgs) ? "" : outArgs) + "</argumentList></action>";
    }

    private static String in(String name) {
        return "<argument><name>" + name + "</name><direction>in</direction>"
                + "<relatedStateVariable>A_ARG_TYPE_" + name + "</relatedStateVariable></argument>";
    }

    private static String out(String name) {
        return "<argument><name>" + name + "</name><direction>out</direction>"
                + "<relatedStateVariable>A_ARG_TYPE_" + name + "</relatedStateVariable></argument>";
    }
}
