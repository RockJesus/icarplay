package com.rockjesus.icarplay;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 网页投屏（WebRTC）：车机做接收端。
 *
 * <p>打开即启动本地信令服务并用 WebView 加载接收页；手机浏览器打开页面上的地址，
 * 选择共享屏幕后画面直连到车机。媒体流走局域网 P2P，信令只做 SDP/ICE 转发。
 */
public class MirrorActivity extends FitActivity {

    private MirrorServer server;
    private WebView web;
    private static final long BAR_AUTO_HIDE_MS = 10000L;
    private LinearLayout topBar;
    private final Runnable hideTopTask = new Runnable() {
        @Override
        public void run() {
            if (topBar == null) return;
            topBar.animate().alpha(0f).setDuration(160).withEndAction(() -> {
                if (topBar != null) topBar.setVisibility(View.GONE);
            }).start();
        }
    };
    private TextView linkLabel;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersive();

        server = new MirrorServer();
        int port;
        try {
            port = server.start();
        } catch (Throwable e) {
            toast("投屏服务启动失败：端口被占用");
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(PageUi.BG);
        setContentView(root);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        web.setBackgroundColor(PageUi.BG);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, int errorCode, String description,
                                        String failingUrl) {
                toast("接收页加载失败：" + description);
            }
        });

        String url = "http://127.0.0.1:" + port + "/car?t=" + server.token();
        web.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) showTopBar();
            return false;
        });
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        web.loadUrl(url);

        LinearLayout top = new LinearLayout(this);
        topBar = top;
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(PageUi.dp(this, 12), PageUi.dp(this, 6),
                PageUi.dp(this, 12), PageUi.dp(this, 6));
        top.setBackgroundColor(0xF80A0E18);
        top.addView(PageUi.header(this, "网页投屏", "手机浏览器打开地址 · 共享屏幕直连车机",
                        IcarLauncherActivity.VERSION_LABEL, v -> finish()),
                PageUi.matchWrap(this));

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams toolsLp = PageUi.matchWrap(this);
        PageUi.margin(toolsLp, this, 0, 5, 0, 0);
        top.addView(tools, toolsLp);

        linkLabel = PageUi.text(this, "http://" + server.lanIp() + ":" + port
                + "/send?t=" + server.token(), 8.5f, PageUi.GREEN, false);
        PageUi.mono(linkLabel);
        tools.addView(linkLabel, PageUi.weight(this, 3f));
        tools.addView(barButton("复制地址", PageUi.GOLD, v -> copyLink()),
                PageUi.weight(this, 1f));
        tools.addView(barButton("重连", PageUi.CYAN, v -> web.reload()),
                PageUi.weight(this, 0.8f));

        root.addView(topBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
        showTopBar();
    }

    private void showTopBar() {
        if (topBar == null) return;
        topBar.animate().cancel();
        topBar.setAlpha(1f);
        topBar.setVisibility(View.VISIBLE);
        topBar.removeCallbacks(hideTopTask);
        topBar.postDelayed(hideTopTask, BAR_AUTO_HIDE_MS);
    }

    private TextView barButton(String label, int accent, View.OnClickListener onClick) {
        TextView b = PageUi.text(this, label, 9.5f, accent, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(PageUi.dp(this, 12), PageUi.dp(this, 4),
                PageUi.dp(this, 12), PageUi.dp(this, 4));
        b.setBackground(PageUi.rounded(this, PageUi.blend(PageUi.CARD, accent, 0.18f),
                PageUi.blend(accent, PageUi.TEXT, 0.2f), 9));
        b.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = PageUi.wrap(this);
        PageUi.margin(lp, this, 6, 0, 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void copyLink() {
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(android.content.ClipData.newPlainText("投屏地址",
                        linkLabel.getText().toString()));
                toast("地址已复制到剪贴板");
            }
        } catch (Throwable e) {
            toast("复制失败");
        }
    }

    private void immersive() {
        try {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        try {
            if (web != null) {
                web.loadUrl("about:blank");
                web.destroy();
                web = null;
            }
        } catch (Throwable ignored) {
        }
        if (server != null) {
            server.stop();
            server = null;
        }
        super.onDestroy();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
