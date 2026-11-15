# -*- coding: utf-8 -*-
"""DlnaActivity：加投送画面区（视频 SurfaceView / 图片 ImageView）"""
import io

P = "app/src/main/java/com/rockjesus/icarplay/DlnaActivity.java"
s = io.open(P, encoding="utf-8").read()


def rep(old, new, count=1):
    global s
    assert old in s, old[:70]
    s = s.replace(old, new, count)


rep(
    "import android.app.Activity;\nimport android.content.ClipData;\n",
    "import android.app.Activity;\nimport android.content.ClipData;\n",
)
rep(
    "import android.content.Intent;\nimport android.os.Bundle;\n",
    "import android.content.Intent;\nimport android.graphics.Bitmap;\nimport android.os.Bundle;\n",
)
rep(
    "import android.view.Gravity;\nimport android.view.View;\nimport android.view.ViewGroup;\n",
    "import android.view.Gravity;\nimport android.view.Surface;\nimport android.view.SurfaceHolder;\n"
    "import android.view.SurfaceView;\nimport android.view.View;\nimport android.view.ViewGroup;\n",
)
rep(
    "import android.widget.Button;\nimport android.widget.LinearLayout;\n",
    "import android.widget.Button;\nimport android.widget.FrameLayout;\nimport android.widget.ImageView;\n"
    "import android.widget.LinearLayout;\n",
)

rep(
    " * DLNA 音频接收页：手机把歌“投送”到车机，车机当音箱放。\n"
    " * 只接收同一局域网内主动推过来的音频流，不扫描别人的设备。\n",
    " * DLNA 投送接收页：手机把歌、视频、图片“投送”到车机，车机当音箱 + 副屏用。\n"
    " * 只接收同一局域网内主动推过来的媒体，不扫描别人的设备。\n",
)

rep(
    "    private TextView addressValue;\n    private Button start;\n    private Button stop;\n",
    "    private TextView addressValue;\n"
    "    private TextView trackLabel;\n"
    "    private LinearLayout stageCard;\n"
    "    private SurfaceView stageVideo;\n"
    "    private ImageView stageImage;\n"
    "    private TextView stageHint;\n"
    "    private Button start;\n    private Button stop;\n",
)

rep(
    '        root.addView(PageUi.header(this, "DLNA 音频接收", IcarLauncherActivity.VERSION_LABEL),\n'
    '                PageUi.matchWrap(this));\n\n'
    '        TextView sub = PageUi.text(this, "安卓手机 / 电脑把音乐投送到车机 · 同一局域网",\n'
    '                10.5f, PageUi.MUTED, false);\n',
    '        root.addView(PageUi.header(this, "DLNA 投送接收", IcarLauncherActivity.VERSION_LABEL),\n'
    '                PageUi.matchWrap(this));\n\n'
    '        TextView sub = PageUi.text(this, "安卓手机 / 电脑把音乐 · 视频 · 图片投送到车机 · 同一局域网",\n'
    '                10.5f, PageUi.MUTED, false);\n',
)

# 画面卡：插在“正在播放”卡之前
OLD_TRACK = '''        LinearLayout trackCard = PageUi.card(this, PageUi.BLUE, 14, 8);
        LinearLayout.LayoutParams tlp = PageUi.matchWrap(this);
        PageUi.margin(tlp, this, 0, 8, 0, 0);
        content.addView(trackCard, tlp);
        trackCard.addView(PageUi.text(this, "正在播放", 9f, PageUi.BLUE, true), PageUi.wrap(this));
        trackValue = PageUi.text(this, "还没有收到音频", 12f, PageUi.MUTED, false);
'''

NEW_TRACK = '''        stageCard = PageUi.card(this, PageUi.CYAN, 12, 8);
        LinearLayout.LayoutParams slp = PageUi.matchWrap(this);
        PageUi.margin(slp, this, 0, 8, 0, 0);
        content.addView(stageCard, slp);
        LinearLayout stageHead = new LinearLayout(this);
        stageHead.setOrientation(LinearLayout.HORIZONTAL);
        stageHead.setGravity(Gravity.CENTER_VERTICAL);
        stageHead.addView(PageUi.text(this, "投送画面", 9f, PageUi.CYAN, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        stageHead.addView(PageUi.statusPill(this, "视频 / 图片", PageUi.CYAN), PageUi.wrap(this));
        stageCard.addView(stageHead, PageUi.matchWrap(this));

        FrameLayout stage = new FrameLayout(this);
        stage.setBackground(PageUi.rounded(this, PageUi.blend(PageUi.BG, PageUi.CYAN, 0.10f),
                PageUi.blend(PageUi.BORDER, PageUi.CYAN, 0.30f), 10));
        LinearLayout.LayoutParams stageLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, PageUi.dp(this, 196f));
        PageUi.margin(stageLp, this, 0, 5, 0, 0);
        stageCard.addView(stage, stageLp);

        stageVideo = new SurfaceView(this);
        stage.addView(stageVideo, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        stageImage = new ImageView(this);
        stageImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        stageImage.setVisibility(View.GONE);
        stage.addView(stageImage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        stageHint = PageUi.text(this, "投送视频或图片时，这里显示画面", 10.5f, PageUi.MUTED, false);
        stageHint.setGravity(Gravity.CENTER);
        stage.addView(stageHint, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        stageCard.setVisibility(View.GONE);

        LinearLayout trackCard = PageUi.card(this, PageUi.BLUE, 14, 8);
        LinearLayout.LayoutParams tlp = PageUi.matchWrap(this);
        PageUi.margin(tlp, this, 0, 8, 0, 0);
        content.addView(trackCard, tlp);
        trackLabel = PageUi.text(this, "正在投送", 9f, PageUi.BLUE, true);
        trackCard.addView(trackLabel, PageUi.wrap(this));
        trackValue = PageUi.text(this, "还没有收到投送", 12f, PageUi.MUTED, false);
'''

rep(OLD_TRACK, NEW_TRACK)

# 怎么用文案
rep(
    '                "1  手机与车机连同一 Wi‑Fi，点下面“启动接收”\\n"\n'
    '                        + "2  手机音乐 App 里点“投送到设备 / DLNA / Cast”，选本机设备名\\n"\n'
    '                        + "3  选中后车机会自动播放；音量用方向盘或车机音量键",',
    '                "1  手机与车机连同一 Wi‑Fi，点下面“启动接收”\\n"\n'
    '                        + "2  手机投送：音乐 App 选“投送到设备”，相册选“投屏 / Cast”，视频 App 选“投屏”\\n"\n'
    '                        + "3  音频走车机音响；视频 / 图片显示在本页“投送画面”，音量用方向盘或车机音量键",',
)

# 注册监听 + 绑定 Surface
rep(
    "        refresh();\n    }\n\n    private TextView metric(",
    '''        stageVideo.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                renderer(DlnaActivity.this).setVideoSurface(holder.getSurface());
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                renderer(DlnaActivity.this).setVideoSurface(holder.getSurface());
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                renderer(DlnaActivity.this).setVideoSurface(null);
            }
        });

        renderer(this).setListener(new DlnaRenderer.Listener() {
            @Override
            public void onKind(String kind, String title) {
                runOnUiThread(() -> applyKind(kind, title));
            }

            @Override
            public void onImage(Bitmap bitmap, String title) {
                runOnUiThread(() -> applyImage(bitmap, title));
            }
        });

        refresh();
        applyKind(renderer(this).getKind(), renderer(this).getTitle());
    }

    /** 按媒体类型切换画面区：视频出 Surface，图片出 ImageView，音频整块收起。 */
    private void applyKind(String kind, String title) {
        if (stageCard == null) return;
        boolean video = DlnaRenderer.KIND_VIDEO.equals(kind);
        boolean image = DlnaRenderer.KIND_IMAGE.equals(kind);
        stageCard.setVisibility(video || image ? View.VISIBLE : View.GONE);
        if (!video && !image) return;
        if (video) {
            stageImage.setVisibility(View.GONE);
            stageVideo.setVisibility(View.VISIBLE);
            stageHint.setText("视频投送中 · " + (title == null || title.isEmpty() ? "" : title));
            stageHint.setTextColor(PageUi.MUTED);
        } else {
            stageVideo.setVisibility(View.INVISIBLE);
            stageImage.setVisibility(View.VISIBLE);
        }
        if (trackLabel != null) trackLabel.setText(video ? "正在播放视频" : "正在显示图片");
    }

    private void applyImage(Bitmap bitmap, String title) {
        if (stageImage == null) return;
        if (bitmap == null) {
            stageImage.setImageDrawable(null);
            stageHint.setVisibility(View.VISIBLE);
            stageHint.setText("投送视频或图片时，这里显示画面");
            return;
        }
        stageImage.setImageBitmap(bitmap);
        stageImage.setVisibility(View.VISIBLE);
        stageVideo.setVisibility(View.INVISIBLE);
        stageHint.setVisibility(View.GONE);
        stageCard.setVisibility(View.VISIBLE);
    }

    private TextView metric(''',
)

# refresh 里补 kind 文案
rep(
    '''        trackValue.setText(running
                ? (r.getCurrentUri().isEmpty()
                ? (r.getLastError().isEmpty() ? "还没有收到音频" : r.getLastError())
                : r.getTitle() + "\\n" + shorten(r.getCurrentUri()))
                : "还没有收到音频");
        trackValue.setTextColor(!running || r.getCurrentUri().isEmpty()
                ? PageUi.MUTED : PageUi.TEXT);''',
    '''        String kind = r.getKind();
        String prefix = DlnaRenderer.KIND_VIDEO.equals(kind) ? "视频 · "
                : DlnaRenderer.KIND_IMAGE.equals(kind) ? "图片 · "
                : DlnaRenderer.KIND_AUDIO.equals(kind) ? "音频 · " : "";
        trackValue.setText(running
                ? (r.getCurrentUri().isEmpty()
                ? (r.getLastError().isEmpty() ? "还没有收到投送" : r.getLastError())
                : prefix + r.getTitle() + "\\n" + shorten(r.getCurrentUri()))
                : "还没有收到投送");
        trackValue.setTextColor(!running || r.getCurrentUri().isEmpty()
                ? PageUi.MUTED : PageUi.TEXT);
        if (trackLabel != null) {
            boolean video = DlnaRenderer.KIND_VIDEO.equals(kind);
            boolean image = DlnaRenderer.KIND_IMAGE.equals(kind);
            trackLabel.setText(video ? "正在播放视频" : image ? "正在显示图片" : "正在投送");
            if (stageCard != null && (video || image)) {
                stageCard.setVisibility(View.VISIBLE);
            } else if (stageCard != null) {
                stageCard.setVisibility(View.GONE);
                stageHint.setVisibility(View.VISIBLE);
            }
        }''',
)

# onPause / onDestroy 解绑
rep(
    '''    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }''',
    '''    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (renderer != null && stageCard != null) {
            renderer.setListener(new DlnaRenderer.Listener() {
                @Override
                public void onKind(String kind, String title) {
                    runOnUiThread(() -> applyKind(kind, title));
                }

                @Override
                public void onImage(Bitmap bitmap, String title) {
                    runOnUiThread(() -> applyImage(bitmap, title));
                }
            });
        }
    }''',
)

io.open(P, "w", encoding="utf-8").write(s)
print("dlna activity patched")
