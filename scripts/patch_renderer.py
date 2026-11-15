# -*- coding: utf-8 -*-
"""给 DlnaRenderer 增加视频/图片投送能力"""
import io

P = "app/src/main/java/com/rockjesus/icarplay/DlnaRenderer.java"
s = io.open(P, encoding="utf-8").read()


def rep(old, new, count=1):
    global s
    assert old in s, old[:60]
    s = s.replace(old, new, count)


# 1) imports
rep(
    "import android.content.Context;\nimport android.media.AudioManager;\nimport android.media.MediaPlayer;\nimport android.net.wifi.WifiManager;\n",
    "import android.content.Context;\nimport android.graphics.Bitmap;\nimport android.graphics.BitmapFactory;\n"
    "import android.media.AudioManager;\nimport android.media.MediaPlayer;\nimport android.net.wifi.WifiManager;\n"
    "import android.view.Surface;\n",
)
rep(
    "import java.net.DatagramPacket;\nimport java.net.InetAddress;\n",
    "import java.net.DatagramPacket;\nimport java.net.HttpURLConnection;\nimport java.net.InetAddress;\n",
)
rep(
    "import java.net.Socket;\nimport java.util.Locale;\n",
    "import java.net.Socket;\nimport java.net.URL;\nimport java.util.Locale;\n",
)

# 2) 类注释与常量
rep(
    " * 轻量 DLNA / UPnP AudioRenderer：SSDP 组播应答 + 最小 HTTP 控制点 + MediaPlayer 播放。\n"
    " * 只做别人推过来的音频的接收端，不主动发现网络里的其他设备，也不改动系统网络配置。\n"
    " * 手机端用支持 DLNA 投送的播放器（如各家音乐 App 的“投送到设备”）选中本机即可播放。\n",
    " * 轻量 DLNA / UPnP DMR：SSDP 组播应答 + 最小 HTTP 控制点 + MediaPlayer / ImageView 呈现。\n"
    " * 接收别人推过来的音频、视频、图片，不主动发现网络里的其他设备，也不改动系统网络配置。\n"
    " * 手机端用支持 DLNA 投送的 App（音乐“投送到设备”、相册投屏、文件管理器 Cast）选中本机即可。\n",
)
rep(
    '    public static final String ST_TRANSITIONING = "TRANSITIONING";\n',
    '    public static final String ST_TRANSITIONING = "TRANSITIONING";\n\n'
    '    public static final String KIND_AUDIO = "audio";\n'
    '    public static final String KIND_VIDEO = "video";\n'
    '    public static final String KIND_IMAGE = "image";\n\n'
    '    private static final String[] VIDEO_EXT = {".mp4", ".mkv", ".webm", ".avi", ".mov",\n'
    '            ".m4v", ".ts", ".m3u8", ".flv", ".3gp", ".m2ts"};\n'
    '    private static final String[] IMAGE_EXT = {".jpg", ".jpeg", ".png", ".gif", ".bmp",\n'
    '            ".webp", ".heic", ".heif"};\n',
)

# 3) 字段
rep(
    "    private MediaPlayer player;\n    private boolean pendingPlay;\n",
    "    private MediaPlayer player;\n    private boolean pendingPlay;\n"
    "    private volatile String kind = \"\";\n"
    "    private volatile Surface videoSurface;\n"
    "    private volatile Listener listener;\n"
    "    private volatile int imageToken;\n\n"
    "    /** 页面侧回调：切换媒体类型、贴图片。回调可能来自工作线程，实现方自行切主线程。 */\n"
    "    public interface Listener {\n"
    "        void onKind(String kind, String title);\n\n"
    "        void onImage(Bitmap bitmap, String title);\n"
    "    }\n",
)

# 4) 对外方法
rep(
    "    public String getUuid() {\n        return uuid;\n    }\n",
    "    public String getUuid() {\n        return uuid;\n    }\n\n"
    "    public String getKind() {\n        return kind;\n    }\n\n"
    "    public void setListener(Listener value) {\n        this.listener = value;\n    }\n\n"
    "    /** 视频投送时页面把 SurfaceView 的 Surface 交进来；音频/图片传 null 即可。 */\n"
    "    public void setVideoSurface(Surface surface) {\n"
    "        this.videoSurface = surface;\n"
    "        try {\n"
    "            MediaPlayer p = player;\n"
    "            if (p != null && surface != null) p.setSurface(surface);\n"
    "        } catch (Exception ignored) {\n"
    "        }\n"
    "    }\n",
)

# 5) 进度文案按类型
rep(
    "    public String getPositionText() {\n"
    "        int pos = Math.max(0, getPositionMs());\n",
    "    public String getPositionText() {\n"
    "        if (KIND_IMAGE.equals(kind)) return ST_PLAYING.equals(state) ? \"静态图片\" : \"—\";\n"
    "        int pos = Math.max(0, getPositionMs());\n",
)

# 6) stateText 增加 KIND
rep(
    '                + "\\nURI=" + currentUri\n',
    '                + "\\nKIND=" + (kind.isEmpty() ? "-" : kind)\n'
    '                + "\\nURI=" + currentUri\n',
)

# 7) HTTP 默认文案
rep(
    '            respondText(socket, "iCarPlay DLNA AudioRenderer\\n" + stateText());',
    '            respondText(socket, "iCarPlay DLNA MediaRenderer\\n" + stateText());',
)

# 8) 协议能力
rep(
    "                    \"<Source></Source><Sink>http-get:*:audio/mpeg:*,\"\n"
    "                            + \"http-get:*:audio/mp4:*,http-get:*:audio/aac:*, \"\n"
    "                            + \"http-get:*:audio/x-flac:*,http-get:*:audio/wav:*, \"\n"
    "                            + \"http-get:*:audio/x-wav:*,http-get:*:audio/ogg:*</Sink>\");",
    "PLACEHOLDER_NOT_USED",
    0,
) if False else None

old_sink = ('                    "<Source></Source><Sink>http-get:*:audio/mpeg:*,"\n'
            '                            + "http-get:*:audio/mp4:*,http-get:*:audio/aac:*,"\n'
            '                            + "http-get:*:audio/x-flac:*,http-get:*:audio/wav:*,"\n'
            '                            + "http-get:*:audio/x-wav:*,http-get:*:audio/ogg:*</Sink>");')
new_sink = ('                    "<Source></Source><Sink>http-get:*:audio/mpeg:*,"\n'
            '                            + "http-get:*:audio/mp4:*,http-get:*:audio/aac:*,"\n'
            '                            + "http-get:*:audio/x-flac:*,http-get:*:audio/wav:*,"\n'
            '                            + "http-get:*:audio/x-wav:*,http-get:*:audio/ogg:*,"\n'
            '                            + "http-get:*:video/mp4:*,http-get:*:video/x-matroska:*,"\n'
            '                            + "http-get:*:video/webm:*,http-get:*:video/mpeg:*,"\n'
            '                            + "http-get:*:video/quicktime:*,http-get:*:image/jpeg:*,"\n'
            '                            + "http-get:*:image/png:*,http-get:*:image/gif:*,"\n'
            '                            + "http-get:*:image/webp:*</Sink>");')
rep(old_sink, new_sink)

# 9) 描述文件
rep("<modelDescription>车机 DLNA 音频接收</modelDescription>",
    "<modelDescription>车机 DLNA 投送接收（音频·视频·图片）</modelDescription>")
rep("<modelName>iCarPlay AudioRenderer</modelName>",
    "<modelName>iCarPlay MediaRenderer</modelName>")
rep("<modelNumber>0.7.8</modelNumber>", "<modelNumber>0.7.9</modelNumber>")

io.open(P, "w", encoding="utf-8").write(s)
print("renderer part1 ok")
