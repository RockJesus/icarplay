# -*- coding: utf-8 -*-
"""DlnaRenderer 播放段改造：音频 / 视频 / 图片分流"""
import io

P = "app/src/main/java/com/rockjesus/icarplay/DlnaRenderer.java"
s = io.open(P, encoding="utf-8").read()

OLD_SET_TRACK = '''    private void setTrack(String uri, String metadata) {
        state = ST_TRANSITIONING;
        currentUri = uri == null ? "" : uri;
        title = guessTitle(currentUri, metadata);
        releasePlayer();
        stopStateAfterLoad = false;
        if (currentUri.isEmpty() || currentUri.startsWith("urn:")) {
            state = ST_NO_MEDIA;
            lastError = "只支持直接音频地址（http/https），这份投送用的是不支持的形式";
            return;
        }
        try {
            MediaPlayer p = new MediaPlayer();
            p.setAudioStreamType(AudioManager.STREAM_MUSIC);
            p.setOnPreparedListener(mp -> {
                state = ST_STOPPED;
                if (pendingPlay) {
                    pendingPlay = false;
                    mp.start();
                    state = ST_PLAYING;
                }
            });
            p.setOnCompletionListener(mp -> state = ST_STOPPED);
            p.setOnErrorListener((mp, what, extra) -> {
                state = ST_STOPPED;
                lastError = "音频播放失败（" + what + "/" + extra + "），可能是格式不支持或地址不可达";
                releasePlayer();
                return true;
            });
            p.setDataSource(currentUri);
            player = p;
            p.prepareAsync();
        } catch (Exception e) {
            state = ST_STOPPED;
            lastError = "无法打开这个音频地址：" + e.getClass().getSimpleName();
            releasePlayer();
        }
    }
'''

NEW_SET_TRACK = '''    private void setTrack(String uri, String metadata) {
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
'''

assert OLD_SET_TRACK in s
s = s.replace(OLD_SET_TRACK, NEW_SET_TRACK, 1)

# play / pause / stop 对图片做特判
OLD_PLAY = '''    private void play() {
        MediaPlayer p = player;
        if (p == null) {
            pendingPlay = true;
            return;
        }'''
NEW_PLAY = '''    private void play() {
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
        }'''
assert OLD_PLAY in s
s = s.replace(OLD_PLAY, NEW_PLAY, 1)

OLD_PAUSE = '''    private void pause() {
        try {
            if (player != null && player.isPlaying()) {'''
NEW_PAUSE = '''    private void pause() {
        if (KIND_IMAGE.equals(kind)) {
            if (ST_PLAYING.equals(state)) state = ST_PAUSED;
            return;
        }
        try {
            if (player != null && player.isPlaying()) {'''
assert OLD_PAUSE in s
s = s.replace(OLD_PAUSE, NEW_PAUSE, 1)

OLD_STOP = '''    private void stopPlayback() {
        try {
            if (player != null) {
                player.pause();
                player.seekTo(0);
            }
        } catch (Exception ignored) {
        }
        state = currentUri.isEmpty() ? ST_NO_MEDIA : ST_STOPPED;
    }'''
NEW_STOP = '''    private void stopPlayback() {
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
    }'''
assert OLD_STOP in s
s = s.replace(OLD_STOP, NEW_STOP, 1)

io.open(P, "w", encoding="utf-8").write(s)
print("renderer part2 ok")
