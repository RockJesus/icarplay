package com.rockjesus.icarplay;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.SslErrorHandler;
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
 * HA 面板：用系统 WebView 打开 Home Assistant 前端（仪表盘由用户在 HA 里自建）。
 *
 * <p>不依赖官方伴侣应用，也不需要另外装任何 APK：登录一次后本应用会自己从
 * {@code localStorage.hassTokens} 里取出长期令牌，之后用 {@link HaApi} 直接走
 * {@code /api/}（连通性探测、服务调用），面板本身仍然渲染 HA 自己的 Lovelace 页面。
 */
public class HaPanelActivity extends FitActivity {

    /** 从 HA 前端 localStorage 里取长期令牌。 */
    private static final String JS_READ_TOKEN =
            "(function(){try{var raw=localStorage.getItem('hassTokens');"
                    + "if(!raw)return '';var t=JSON.parse(raw);"
                    + "return (t&&t.access_token)?t.access_token:'';}catch(e){return '';}})()";

    /** 工具条 10 秒无操作自动隐藏，触摸屏幕任意位置再唤出。 */
    private static final long BAR_AUTO_HIDE_MS = 10000L;

    private SharedPreferences prefs;
    private WebView web;
    private LinearLayout bar;
    private boolean barVisible = true;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable hideBarTask = new Runnable() {
        @Override
        public void run() {
            hideBar();
        }
    };

    /** 本次会话是否已经尝试过取令牌，避免每次翻页都执行。 */
    private boolean tokenTried;
    private boolean tokenToastShown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = HaApi.prefs(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersive();

        if (HaApi.baseUrl(this).isEmpty()) {
            showSetup();
        } else {
            showPanel();
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

    // ---------- 设置页 ----------

    private void showSetup() {
        destroyWeb();
        bar = null;
        ui.removeCallbacks(hideBarTask);
        tokenTried = false;

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackground(PageUi.background(this));
        int pad = PageUi.dp(this, 18);
        shell.setPadding(pad, PageUi.dp(this, 10), pad, PageUi.dp(this, 8));

        shell.addView(PageUi.header(this, "HA 面板", "内置 HA 客户端 · 不依赖官方 App · 面板在 HA 里自建", IcarLauncherActivity.VERSION_LABEL, v -> finish()),
                PageUi.matchWrap(this));

        // ---------- 地址 ----------
        LinearLayout card = PageUi.card(this, PageUi.CYAN, 16, 14);

        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.setGravity(Gravity.CENTER_VERTICAL);
        labelRow.addView(PageUi.text(this, "Home Assistant 地址", 11f, PageUi.CYAN, true));
        TextView kioskPill = PageUi.statusPill(this, "kiosk 隐藏侧边栏",
                prefs.getBoolean(HaApi.PREF_KIOSK, true) ? PageUi.GREEN : PageUi.MUTED);
        LinearLayout.LayoutParams klp = PageUi.wrap(this);
        PageUi.margin(klp, this, 10, 0, 0, 0);
        labelRow.addView(kioskPill, klp);
        card.addView(labelRow, PageUi.matchWrap(this));

        EditText input = edit(prefs.getString(HaApi.PREF_URL, ""),
                "http://192.168.x.x:8123", InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout.LayoutParams ilp = PageUi.matchWrap(this);
        PageUi.margin(ilp, this, 0, 8, 0, 0);
        card.addView(input, ilp);

        TextView tip = PageUi.text(this,
                "地址要填完整的 http:// 或 https:// 前缀；同一 Wi‑Fi 下可直接用手机浏览器访问该地址自测。",
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
            String url = HaApi.normalize(input.getText().toString());
            if (url == null) {
                toast("地址不合法，要 http:// 或 https:// 开头");
                return;
            }
            prefs.edit().putString(HaApi.PREF_URL, HaApi.stripTrailingSlash(url)).apply();
            showPanel();
        });
        btnRow.addView(save, PageUi.weight(this, 1.2f));

        Button test = PageUi.button(this, "测试连接", 11.5f, PageUi.CYAN);
        bindTestButton(test, input);
        LinearLayout.LayoutParams tlp2 = PageUi.weight(this, 1);
        PageUi.margin(tlp2, this, 8, 0, 0, 0);
        btnRow.addView(test, tlp2);
        card.addView(btnRow, rowLp);

        LinearLayout btnRow2 = new LinearLayout(this);
        btnRow2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp2 = PageUi.matchWrap(this);
        PageUi.margin(rowLp2, this, 0, 10, 0, 0);

        Button kioskBtn = PageUi.button(this, "切换 kiosk", 11f, PageUi.PURPLE);
        kioskBtn.setOnClickListener(v -> {
            boolean now = !prefs.getBoolean(HaApi.PREF_KIOSK, true);
            prefs.edit().putBoolean(HaApi.PREF_KIOSK, now).apply();
            kioskPill.setText(now ? "kiosk 隐藏侧边栏" : "kiosk 关闭");
            kioskPill.setTextColor(now ? PageUi.GREEN : PageUi.MUTED);
            toast(now ? "下次打开将隐藏侧边栏" : "下次打开显示侧边栏");
        });
        btnRow2.addView(kioskBtn, PageUi.weight(this, 1));

        Button insecureBtn = PageUi.button(this,
                prefs.getBoolean(HaApi.PREF_INSECURE, false) ? "证书错误：忽略" : "证书：严格校验",
                11f, PageUi.GOLD);
        insecureBtn.setOnClickListener(v -> {
            boolean now = !prefs.getBoolean(HaApi.PREF_INSECURE, false);
            prefs.edit().putBoolean(HaApi.PREF_INSECURE, now).apply();
            insecureBtn.setText(now ? "证书错误：忽略" : "证书：严格校验");
            insecureBtn.setTextColor(now ? PageUi.GOLD : PageUi.MUTED);
            toast(now ? "已允许忽略证书错误（仅建议内网自签 HA 使用）"
                    : "已恢复严格证书校验");
        });
        LinearLayout.LayoutParams ilp2 = PageUi.weight(this, 1);
        PageUi.margin(ilp2, this, 8, 0, 0, 0);
        btnRow2.addView(insecureBtn, ilp2);
        card.addView(btnRow2, rowLp2);

        shell.addView(card, PageUi.matchWrap(this));

        // ---------- 令牌 ----------
        LinearLayout tokenCard = PageUi.card(this, PageUi.PURPLE, 14, 12);
        LinearLayout.LayoutParams tokenLp = PageUi.matchWrap(this);
        PageUi.margin(tokenLp, this, 0, 10, 0, 0);

        LinearLayout tokenRow = new LinearLayout(this);
        tokenRow.setOrientation(LinearLayout.HORIZONTAL);
        tokenRow.setGravity(Gravity.CENTER_VERTICAL);
        tokenRow.addView(PageUi.text(this, "访问令牌", 11f, PageUi.PURPLE, true));
        String tokenNow = HaApi.token(this);
        TextView tokenPill = PageUi.statusPill(this,
                tokenNow.isEmpty() ? "未取到" : "已就绪",
                tokenNow.isEmpty() ? PageUi.MUTED : PageUi.GREEN);
        LinearLayout.LayoutParams tklp = PageUi.wrap(this);
        PageUi.margin(tklp, this, 10, 0, 0, 0);
        tokenRow.addView(tokenPill, tklp);
        tokenCard.addView(tokenRow, PageUi.matchWrap(this));

        EditText tokenInput = edit(tokenNow, "粘贴长期令牌（可选）",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tokenInput.setSingleLine(true);
        LinearLayout.LayoutParams tilp = PageUi.matchWrap(this);
        PageUi.margin(tilp, this, 0, 8, 0, 0);
        tokenCard.addView(tokenInput, tilp);

        TextView tokenTip = PageUi.text(this,
                "进面板后登录一次，本应用会自动从页面里取到令牌，无需手工粘贴；"
                        + "也可以自己在 HA「个人资料 → 长期访问令牌」里创建后贴在这里。",
                9.5f, PageUi.MUTED, false);
        LinearLayout.LayoutParams ttlp = PageUi.matchWrap(this);
        PageUi.margin(ttlp, this, 0, 8, 0, 0);
        tokenCard.addView(tokenTip, ttlp);

        Button saveToken = PageUi.button(this, "保存令牌", 11f, PageUi.PURPLE);
        saveToken.setOnClickListener(v -> {
            String t = tokenInput.getText().toString().trim();
            if (t.isEmpty()) {
                toast("令牌为空；留空则等面板登录后自动获取");
                return;
            }
            HaApi.saveToken(this, t);
            tokenPill.setText("已就绪");
            tokenPill.setTextColor(PageUi.GREEN);
            toast("令牌已保存");
        });
        LinearLayout.LayoutParams stlp = PageUi.matchWrap(this);
        PageUi.margin(stlp, this, 0, 10, 0, 0);
        tokenCard.addView(saveToken, stlp);

        shell.addView(tokenCard, tokenLp);

        // ---------- 路径 ----------
        LinearLayout pathCard = PageUi.card(this, PageUi.GOLD, 14, 12);
        LinearLayout.LayoutParams pathLp = PageUi.matchWrap(this);
        PageUi.margin(pathLp, this, 0, 10, 0, 0);
        pathCard.addView(PageUi.text(this, "默认面板路径（可选）", 11f, PageUi.GOLD, true),
                PageUi.matchWrap(this));

        EditText pathInput = edit(HaApi.panelPath(this), "/lovelace/car",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout.LayoutParams pllp = PageUi.matchWrap(this);
        PageUi.margin(pllp, this, 0, 8, 0, 0);
        pathCard.addView(pathInput, pllp);

        TextView pathTip = PageUi.text(this,
                "填你在 HA 里给车机做的那块 dashboard 的路径，打开就直接落到它；留空进默认首页。",
                9.5f, PageUi.MUTED, false);
        LinearLayout.LayoutParams ptlp = PageUi.matchWrap(this);
        PageUi.margin(ptlp, this, 0, 8, 0, 0);
        pathCard.addView(pathTip, ptlp);

        Button savePath = PageUi.button(this, "保存路径", 11f, PageUi.GOLD);
        savePath.setOnClickListener(v -> {
            String raw = pathInput.getText().toString().trim();
            if (raw.isEmpty()) {
                prefs.edit().remove(HaApi.PREF_PATH).apply();
                toast("已清空路径，下次打开进默认首页");
                return;
            }
            String clean = raw.startsWith("/") ? raw : "/" + raw;
            prefs.edit().putString(HaApi.PREF_PATH, clean).apply();
            toast("已保存：打开面板直接进 " + clean);
        });
        LinearLayout.LayoutParams svlp = PageUi.matchWrap(this);
        PageUi.margin(svlp, this, 0, 10, 0, 0);
        pathCard.addView(savePath, svlp);

        shell.addView(pathCard, pathLp);

        // ---------- 车机状态上报（Companion 的传感器一侧） ----------
        LinearLayout syncCard = PageUi.card(this, PageUi.GREEN, 14, 12);
        LinearLayout.LayoutParams syncLp = PageUi.matchWrap(this);
        PageUi.margin(syncLp, this, 0, 10, 0, 0);

        LinearLayout syncRow = new LinearLayout(this);
        syncRow.setOrientation(LinearLayout.HORIZONTAL);
        syncRow.setGravity(Gravity.CENTER_VERTICAL);
        syncRow.addView(PageUi.text(this, "车机状态 → Home Assistant", 11f,
                PageUi.GREEN, true));
        boolean syncOn = HaCompanion.enabled(this);
        TextView syncPill = PageUi.statusPill(this,
                syncOn ? (HaCompanion.registered(this) ? "已注册" : "待注册") : "已关闭",
                syncOn ? (HaCompanion.registered(this) ? PageUi.GREEN : PageUi.GOLD)
                        : PageUi.MUTED);
        LinearLayout.LayoutParams splp = PageUi.wrap(this);
        PageUi.margin(splp, this, 10, 0, 0, 0);
        syncRow.addView(syncPill, splp);
        syncCard.addView(syncRow, PageUi.matchWrap(this));

        TextView syncTip = PageUi.text(this,
                "像官方 Companion 一样把车机状态推到 HA：电量/充电、联网与 WiFi 名、存储、内存、"
                        + "开机时长、屏幕、音量、USB、前台应用、各投屏引擎运行态。只上报状态，不传画面。",
                9.5f, PageUi.MUTED, false);
        LinearLayout.LayoutParams stlp2 = PageUi.matchWrap(this);
        PageUi.margin(stlp2, this, 0, 8, 0, 0);
        syncCard.addView(syncTip, stlp2);

        TextView syncStatus = PageUi.text(this, HaCompanion.lastText(this),
                9.5f, PageUi.CYAN, false);
        LinearLayout.LayoutParams sslp = PageUi.matchWrap(this);
        PageUi.margin(sslp, this, 0, 8, 0, 0);
        syncCard.addView(syncStatus, sslp);

        LinearLayout syncRow2 = new LinearLayout(this);
        syncRow2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams sr2lp = PageUi.matchWrap(this);
        PageUi.margin(sr2lp, this, 0, 10, 0, 0);

        Button syncBtn = PageUi.button(this, syncOn ? "关闭状态上报" : "开启状态上报",
                11.5f, PageUi.GREEN);
        syncBtn.setOnClickListener(v -> {
            if (HaCompanion.enabled(this)) {
                prefs.edit().putBoolean(HaCompanion.P_ENABLED, false).apply();
                HaSyncService.stop(this);
                syncBtn.setText("开启状态上报");
                syncBtn.setTextColor(PageUi.GREEN);
                syncPill.setText("已关闭");
                syncPill.setTextColor(PageUi.MUTED);
                syncStatus.setText("已关闭，不再上报");
                toast("已关闭状态上报");
            } else {
                prefs.edit().putBoolean(HaCompanion.P_ENABLED, true).apply();
                HaSyncService.startIfEnabled(this);
                syncBtn.setText("关闭状态上报");
                syncBtn.setTextColor(PageUi.GOLD);
                syncPill.setText(HaCompanion.registered(this) ? "已注册" : "待注册");
                syncPill.setTextColor(HaCompanion.registered(this) ? PageUi.GREEN : PageUi.GOLD);
                syncStatus.setText("已开启，首次上报请稍候…");
                toast("已开启，正在向 HA 注册车机设备");
            }
        });
        syncRow2.addView(syncBtn, PageUi.weight(this, 1.2f));

        Button syncNow = PageUi.button(this, "立即上报", 11f, PageUi.CYAN);
        syncNow.setOnClickListener(v -> {
            if (!HaCompanion.enabled(this)) {
                toast("先开启状态上报");
                return;
            }
            syncNow.setEnabled(false);
            syncNow.setText("上报中…");
            new Thread(() -> {
                String err = HaCompanion.push(this);
                runOnUiThread(() -> {
                    syncNow.setEnabled(true);
                    syncNow.setText("立即上报");
                    syncStatus.setText(HaCompanion.lastText(this));
                    boolean ok = HaCompanion.registered(this);
                    syncPill.setText(ok ? "已注册" : "待注册");
                    syncPill.setTextColor(ok ? PageUi.GREEN : PageUi.GOLD);
                    toast(err == null ? "已上报到 HA" : "上报失败：" + err);
                });
            }).start();
        });
        LinearLayout.LayoutParams snlp = PageUi.weight(this, 1);
        PageUi.margin(snlp, this, 8, 0, 0, 0);
        syncRow2.addView(syncNow, snlp);
        syncCard.addView(syncRow2, sr2lp);

        LinearLayout syncRow3 = new LinearLayout(this);
        syncRow3.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams sr3lp = PageUi.matchWrap(this);
        PageUi.margin(sr3lp, this, 0, 8, 0, 0);

        Button intervalBtn = PageUi.button(this,
                "间隔 " + intervalLabel(HaCompanion.intervalSec(this)),
                11f, PageUi.PURPLE);
        intervalBtn.setOnClickListener(v -> {
            int now = HaCompanion.intervalSec(this);
            int next = now < 60 ? 60 : (now < 300 ? 300 : 30);
            prefs.edit().putInt(HaCompanion.P_INTERVAL, next).apply();
            intervalBtn.setText("间隔 " + intervalLabel(next));
            toast("上报间隔已设为 " + intervalLabel(next));
        });
        syncRow3.addView(intervalBtn, PageUi.weight(this, 1));

        Button reReg = PageUi.button(this, "重新注册", 11f, PageUi.GOLD);
        reReg.setOnClickListener(v -> {
            HaCompanion.reset(this);
            syncPill.setText("待注册");
            syncPill.setTextColor(PageUi.GOLD);
            syncStatus.setText("已清除注册信息，下次上报会重新注册");
            toast("已清除注册信息");
        });
        LinearLayout.LayoutParams rrlp = PageUi.weight(this, 1);
        PageUi.margin(rrlp, this, 8, 0, 0, 0);
        syncRow3.addView(reReg, rrlp);
        syncCard.addView(syncRow3, sr3lp);

        shell.addView(syncCard, syncLp);

        TextView foot = PageUi.text(this,
                "面板本身由 HA 自己的 Lovelace 渲染；本应用只负责登录态、连通性与全屏显示。",
                9.5f, PageUi.MUTED, false);
        LinearLayout.LayoutParams flp = PageUi.matchWrap(this);
        PageUi.margin(flp, this, 2, 10, 2, 0);
        shell.addView(foot, flp);


        setFitContentView(shell);
    }

    /** 供「测试连接」按钮复用：点一下 → 后台 ping → toast 结果。 */
    private void bindTestButton(Button test, EditText input) {
        test.setOnClickListener(v -> {
            String url = HaApi.normalize(input.getText().toString());
            if (url == null) {
                toast("地址不合法，要 http:// 或 https:// 开头");
                return;
            }
            prefs.edit().putString(HaApi.PREF_URL, HaApi.stripTrailingSlash(url)).apply();
            test.setEnabled(false);
            test.setText("测试中…");
            new Thread(() -> {
                String body = HaApi.ping(this, 6000);
                runOnUiThread(() -> {
                    test.setText("测试连接");
                    test.setEnabled(true);
                    if (body != null) {
                        toast(HaApi.token(this).isEmpty()
                                ? "连接成功（尚未取到令牌，进去登录一次即可）"
                                : "连接成功（令牌已就绪）");
                    } else {
                        toast("连接失败：检查地址、Wi-Fi，或把「忽略证书错误」打开（自签 HTTPS）");
                    }
                });
            }, "ha-ping").start();
        });
    }

    private EditText edit(String value, String hint, int inputType) {
        EditText e = new EditText(this);
        e.setText(value == null ? "" : value);
        e.setHint(hint);
        e.setHintTextColor(PageUi.MUTED);
        e.setTextColor(PageUi.TEXT);
        e.setTextSize(14f);
        e.setSingleLine(true);
        e.setInputType(inputType);
        e.setBackground(PageUi.rounded(this, PageUi.CARD_ALT, PageUi.BORDER, 10));
        e.setPadding(PageUi.dp(this, 10), PageUi.dp(this, 8),
                PageUi.dp(this, 10), PageUi.dp(this, 8));
        return e;
    }

    // ---------- 面板 ----------

    private void showPanel() {
        destroyWeb();
        tokenTried = false;
        tokenToastShown = false;

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

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                if (prefs.getBoolean(HaApi.PREF_INSECURE, false)) {
                    handler.proceed();
                } else {
                    handler.cancel();
                    toast("HTTPS 证书校验失败；内网自签可在设置里打开「忽略证书错误」");
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                maybeHarvestToken(view);
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 点一下屏幕就把工具条唤回来（不拦截事件，页面照常接收）
        web.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                showBar();
            }
            return false;
        });

        // 顶部浮层：统一页头 + 工具行（10 秒自动隐藏，点屏幕唤回）
        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(PageUi.dp(this, 12), PageUi.dp(this, 6),
                PageUi.dp(this, 12), PageUi.dp(this, 6));
        bar.setBackgroundColor(0xF80A0E18);
        bar.addView(PageUi.header(this, "HA 面板", "点屏幕唤回工具条 · 10 秒自动隐藏",
                        IcarLauncherActivity.VERSION_LABEL, v -> finish()),
                PageUi.matchWrap(this));

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams toolsLp = PageUi.matchWrap(this);
        PageUi.margin(toolsLp, this, 0, 5, 0, 0);
        bar.addView(tools, toolsLp);

        LinearLayout.LayoutParams bw = PageUi.weight(this, 1f);
        TextView t1 = smallBarButton("刷新", PageUi.CYAN, v -> {
            if (web != null) web.reload();
        });
        tools.addView(t1, bw);
        TextView t2 = smallBarButton("首页", PageUi.GREEN, v -> loadPanel());
        tools.addView(t2, PageUi.weight(this, 1f));
        TextView t3 = smallBarButton("设置", PageUi.GOLD, v -> showSetup());
        tools.addView(t3, PageUi.weight(this, 1f));

        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        root.addView(bar, barLp);

        showBar();
        loadPanel();
    }

    /** 显示工具条并重置 10 秒倒计时。 */
    private void showBar() {
        if (bar == null) return;
        ui.removeCallbacks(hideBarTask);
        if (!barVisible) {
            barVisible = true;
            bar.setVisibility(View.VISIBLE);
            bar.setAlpha(0f);
            bar.animate().alpha(1f).setDuration(160).start();
        }
        ui.postDelayed(hideBarTask, BAR_AUTO_HIDE_MS);
    }

    /** 淡出并收回工具条，让页面顶部区域完整可点。 */
    private void hideBar() {
        if (bar == null || !barVisible) return;
        barVisible = false;
        bar.animate().alpha(0f).setDuration(200).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (bar != null && !barVisible) bar.setVisibility(View.GONE);
            }
        }).start();
    }

    private void loadPanel() {
        if (web == null) return;
        String url = HaApi.panelUrl(this);
        web.loadUrl(url.isEmpty() ? "about:blank" : url);
    }

    /**
     * 面板加载完成后尝试从 localStorage 里取出 long-lived token。
     * 只取一次：取到了以后，REST 请求和后续面板复用都不再需要重新登录。
     */
    private void maybeHarvestToken(WebView view) {
        if (tokenTried) return;
        tokenTried = true;
        try {
            view.evaluateJavascript(JS_READ_TOKEN, value -> {
                String token = unwrapJsString(value);
                if (token != null && !token.isEmpty()
                        && !token.equals(HaApi.token(HaPanelActivity.this))) {
                    HaApi.saveToken(HaPanelActivity.this, token);
                    if (!tokenToastShown) {
                        tokenToastShown = true;
                        toast("已自动取到 HA 令牌");
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    /** 把 evaluateJavascript 返回的 JSON 字符串（形如 "abc"）还原成明文。 */
    private String unwrapJsString(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty() || "null".equals(s)) return "";
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private TextView smallBarButton(String label, int accent, View.OnClickListener onClick) {
        TextView b = PageUi.text(this, label, 9.5f, accent, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(PageUi.dp(this, 12), PageUi.dp(this, 4),
                PageUi.dp(this, 12), PageUi.dp(this, 4));
        b.setBackground(PageUi.rounded(this, PageUi.blend(PageUi.CARD, accent, 0.18f),
                PageUi.blend(accent, PageUi.TEXT, 0.2f), 9));
        b.setOnClickListener(v -> {
            onClick.onClick(v);
            showBar();
        });
        LinearLayout.LayoutParams lp = PageUi.wrap(this);
        PageUi.margin(lp, this, 6, 0, 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void destroyWeb() {
        try {
            if (web != null) {
                ViewGroup parent = (ViewGroup) web.getParent();
                if (parent != null) parent.removeView(web);
                web.loadUrl("about:blank");
                web.destroy();
            }
        } catch (Throwable ignored) {
        }
        web = null;
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
        destroyWeb();
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private String intervalLabel(int sec) {
        if (sec < 60) return sec + " 秒";
        if (sec < 3600) return (sec / 60) + " 分钟";
        return (sec / 3600) + " 小时";
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
