package com.rockjesus.icarplay;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 豆包：在车机上直接用豆包网页版。
 *
 * <p>扫码登录走官方网页的二维码，账号与 Cookie 由 WebView 自己保存，应用不碰任何凭据。
 * 语音输入把麦克风权限透传给网页（豆包网页自带语音入口时即可用），
 * 语音回复由网页自身播放，不需要额外音频通道。
 */
public class DoubaoActivity extends FitActivity {

    private static final String HOME_URL = "https://www.doubao.com/chat/";
    /** 伪装成桌面 Chrome，避免网页把 WebView 当成不支持的浏览器。 */
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private WebView web;
    private TextView statePill;
    private static final int REQ_MIC = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersive();
        buildPanel();
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

    private void buildPanel() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUserAgentString(UA);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        web.setBackgroundColor(Color.BLACK);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
                updateState(url);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description,
                                        String failingUrl) {
                toast("打不开豆包：" + description + "，先确认车机网络与热点");
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    boolean wantsAudio = false;
                    for (String r : request.getResources()) {
                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) {
                            wantsAudio = true;
                        }
                    }
                    if (!wantsAudio) {
                        request.deny();
                        return;
                    }
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                            == PackageManager.PERMISSION_GRANTED) {
                        request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                    } else {
                        Toast.makeText(DoubaoActivity.this,
                                "正在申请麦克风权限，请允许后再按一次语音", Toast.LENGTH_SHORT).show();
                        requestUserMic();
                    }
                });
            }
        });

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) cm.setAcceptThirdPartyCookies(web, true);

        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(PageUi.dp(this, 10), 0, PageUi.dp(this, 10), 0);
        bar.setBackgroundColor(0xCC04050A);

        TextView title = PageUi.text(this, "豆包", 10f, PageUi.PURPLE, true);
        PageUi.mono(title);
        bar.addView(title, PageUi.weight(this, 0.6f));

        statePill = PageUi.text(this, "未登录 · 手机扫码", 8.5f, PageUi.MUTED, false);
        bar.addView(statePill, PageUi.weight(this, 1.6f));

        bar.addView(barButton("刷新", PageUi.CYAN, v -> web.reload()));
        bar.addView(barButton("首页", PageUi.GREEN, v -> web.loadUrl(HOME_URL)));
        bar.addView(barButton("麦克风", PageUi.GOLD, v -> {
            ensureMicThenHint();
        }));
        bar.addView(barButton("返回", PageUi.RED, v -> finish()));

        root.addView(bar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, PageUi.dp(this, 40), Gravity.TOP));

        web.loadUrl(HOME_URL);
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

    /** 申请录音权限；已授权时提示去用网页里的麦克风按钮。 */
    private void ensureMicThenHint() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            toast("麦克风已就绪：直接点网页里的话筒按钮说话");
            return;
        }
        requestUserMic();
    }

    private void requestUserMic() {
        try {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
        } catch (Throwable e) {
            toast("这台车机不支持应用申请麦克风权限");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_MIC) return;
        boolean ok = grantResults != null && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        toast(ok ? "麦克风已允许，点网页话筒即可语音对话" : "麦克风被拒绝，只能文字输入");
    }

    private void updateState(String url) {
        try {
            boolean logged = CookieManager.getInstance().getCookie(url) != null
                    && CookieManager.getInstance().getCookie(url).length() > 30;
            statePill.setText(logged ? "已登录 · Cookie 已保存" : "未登录 · 手机扫码");
            statePill.setTextColor(logged ? PageUi.GREEN : PageUi.MUTED);
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
    protected void onPause() {
        try {
            CookieManager.getInstance().flush();
        } catch (Throwable ignored) {
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        try {
            CookieManager.getInstance().flush();
            if (web != null) {
                web.loadUrl("about:blank");
                web.destroy();
                web = null;
            }
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
