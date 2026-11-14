package com.rockjesus.icarplay;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * HA 面板：用系统 WebView 直接打开 Home Assistant 前端。
 *
 * <p>车机是 Android 8.1、没有 GMS，官方 HA 伴侣应用的推送/定位/传感器都依赖 Google 服务，
 * 而 WebView 方案只吃网络和页面渲染，能把「车上控制家里」这件事先跑通。
 * 页面支持隐藏侧边栏（kiosk）、屏幕常亮、横屏铺满，地址存在本机 preferences 里。
 */
public class HaPanelActivity extends FitActivity {

    private static final String PREF_URL = "ha_url";
    private static final String PREF_KIOSK = "ha_kiosk";

    private SharedPreferences prefs;
    private WebView web;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("icarplay", MODE_PRIVATE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersive();

        String url = prefs.getString(PREF_URL, "");
        if (url == null || url.isEmpty()) {
            showSetup();
        } else {
            showPanel(url);
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

    // ---------- 首次打开：填地址 ----------

    private void showSetup() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackground(PageUi.background(this));
        int pad = PageUi.dp(this, 18);
        shell.setPadding(pad, PageUi.dp(this, 10), pad, PageUi.dp(this, 8));

        shell.addView(PageUi.header(this, "HA 面板", IcarLauncherActivity.VERSION_LABEL),
                PageUi.matchWrap(this));

        TextView sub = PageUi.text(this,
                "用系统 WebView 打开 Home Assistant · 横屏铺满 · 屏幕常亮 · 不依赖 Google 服务",
                10.5f, PageUi.MUTED, false);
        sub.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams subLp = PageUi.matchWrap(this);
        PageUi.margin(subLp, this, 0, 3, 0, 10);
        shell.addView(sub, subLp);

        LinearLayout card = PageUi.card(this, PageUi.CYAN, 16, 14);

        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.setGravity(Gravity.CENTER_VERTICAL);
        labelRow.addView(PageUi.text(this, "Home Assistant 地址", 11f, PageUi.CYAN, true));
        TextView kioskPill = PageUi.statusPill(this, "kiosk 隐藏侧边栏",
                prefs.getBoolean(PREF_KIOSK, true) ? PageUi.GREEN : PageUi.MUTED);
        LinearLayout.LayoutParams klp = PageUi.wrap(this);
        PageUi.margin(klp, this, 10, 0, 0, 0);
        labelRow.addView(kioskPill, klp);
        card.addView(labelRow, PageUi.matchWrap(this));

        EditText input = new EditText(this);
        input.setText(prefs.getString(PREF_URL, ""));
        input.setHint("http://192.168.x.x:8123");
        input.setHintTextColor(PageUi.MUTED);
        input.setTextColor(PageUi.TEXT);
        input.setTextSize(14f);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setBackground(PageUi.rounded(this, PageUi.CARD_ALT, PageUi.BORDER, 10));
        input.setPadding(PageUi.dp(this, 10), PageUi.dp(this, 8),
                PageUi.dp(this, 10), PageUi.dp(this, 8));
        LinearLayout.LayoutParams ilp = PageUi.matchWrap(this);
        PageUi.margin(ilp, this, 0, 8, 0, 0);
        card.addView(input, ilp);

        TextView tip = PageUi.text(this,
                "地址要填完整的 http:// 或 https:// 前缀；同一 Wi‑Fi 下可直接用车机浏览器访问该地址自测。",
                9.5f, PageUi.MUTED, false);
        LinearLayout.LayoutParams tlp = PageUi.matchWrap(this);
        PageUi.margin(tlp, this, 0, 8, 0, 0);
        card.addView(tip, tlp);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = PageUi.matchWrap(this);
        PageUi.margin(rowLp, this, 0, 12, 0, 0);

        Button save = PageUi.button(this, "保存并打开", 12f, PageUi.GREEN);
        save.setOnClickListener(v -> {
            String url = normalize(input.getText().toString());
            if (url == null) {
                toast("地址不合法，要 http:// 或 https:// 开头");
                return;
            }
            prefs.edit().putString(PREF_URL, url).apply();
            showPanel(url);
        });
        btnRow.addView(save, PageUi.weight(this, 1.4f));

        Button kioskBtn = PageUi.button(this, "切换 kiosk", 11f, PageUi.CYAN);
        kioskBtn.setOnClickListener(v -> {
            boolean now = !prefs.getBoolean(PREF_KIOSK, true);
            prefs.edit().putBoolean(PREF_KIOSK, now).apply();
            kioskPill.setText(now ? "kiosk 隐藏侧边栏" : "kiosk 关闭");
            kioskPill.setTextColor(now ? PageUi.GREEN : PageUi.MUTED);
            toast(now ? "下次打开将隐藏侧边栏" : "下次打开显示侧边栏");
        });
        LinearLayout.LayoutParams klp2 = PageUi.weight(this, 1);
        PageUi.margin(klp2, this, 8, 0, 0, 0);
        btnRow.addView(kioskBtn, klp2);
        card.addView(btnRow, rowLp);

        shell.addView(card, PageUi.matchWrap(this));

        TextView foot = PageUi.text(this,
                "建议先在 HA 里给车机单独做一个宽屏 dashboard，横屏 8:3 才排得好看。",
                9.5f, PageUi.MUTED, false);
        LinearLayout.LayoutParams flp = PageUi.matchWrap(this);
        PageUi.margin(flp, this, 2, 10, 2, 0);
        shell.addView(foot, flp);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = PageUi.matchWrap(this);
        PageUi.margin(blp, this, 0, 10, 0, 0);
        Button back = PageUi.back(this);
        back.setOnClickListener(v -> finish());
        bottom.addView(back, PageUi.weight(this, 1));
        shell.addView(bottom, blp);

        setFitContentView(shell);
    }

    // ---------- 已配置：直接进面板 ----------

    private void showPanel(String url) {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        web.setBackgroundColor(Color.BLACK);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, int errorCode, String description,
                                        String failingUrl) {
                toast("面板打不开：" + description + "，检查地址与 Wi‑Fi");
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 顶部细工具条：返回 / 刷新 / 设置
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(PageUi.dp(this, 10), 0, PageUi.dp(this, 10), 0);
        bar.setBackgroundColor(0xCC04050A);

        TextView title = PageUi.text(this, "HA 面板", 10f, PageUi.CYAN, true);
        PageUi.mono(title);
        bar.addView(title, PageUi.weight(this, 1));

        bar.addView(smallBarButton("刷新", PageUi.CYAN, v -> {
            if (web != null) web.reload();
        }));
        bar.addView(smallBarButton("首页", PageUi.GREEN, v -> {
            if (web != null) web.loadUrl(targetUrl(prefs.getString(PREF_URL, "")));
        }));
        bar.addView(smallBarButton("设置", PageUi.GOLD, v -> showSetup()));
        bar.addView(smallBarButton("返回", PageUi.RED, v -> finish()));

        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, PageUi.dp(this, 40), Gravity.TOP);
        root.addView(bar, barLp);

        web.loadUrl(targetUrl(url));

        // 工具条 6 秒后淡出，点顶部 40dp 区域再唤出，避免长期遮挡面板
        bar.animate().alpha(0f).setStartDelay(6000).setDuration(600).withEndAction(() -> {
            bar.setVisibility(View.GONE);
            bar.setAlpha(1f);
        }).start();

        View hotspot = new View(this);
        hotspot.setBackgroundColor(Color.TRANSPARENT);
        hotspot.setOnClickListener(v -> {
            if (bar.getVisibility() == View.GONE) {
                bar.setVisibility(View.VISIBLE);
                bar.setAlpha(1f);
                bar.animate().alpha(0f).setStartDelay(6000).setDuration(600).withEndAction(() -> {
                    bar.setVisibility(View.GONE);
                    bar.setAlpha(1f);
                }).start();
            }
        });
        root.addView(hotspot, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, PageUi.dp(this, 24), Gravity.TOP));
    }

    private TextView smallBarButton(String label, int accent, View.OnClickListener onClick) {
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

    /** 加 kiosk 参数隐藏 HA 侧边栏。 */
    private String targetUrl(String raw) {
        if (raw == null || raw.isEmpty()) return "about:blank";
        if (!prefs.getBoolean(PREF_KIOSK, true)) return raw;
        if (raw.contains("kiosk")) return raw;
        return raw + (raw.contains("?") ? "&" : "?") + "kiosk";
    }

    private String normalize(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            s = "http://" + s;
        }
        try {
            new java.net.URL(s);
        } catch (Throwable e) {
            return null;
        }
        return s;
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
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
