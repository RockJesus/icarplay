package com.rockjesus.icarplay;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;

import io.github.jqssun.airplay.R;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.rockjesus.icarplay.carlife.CarLifePlusActivity;
import local.hicarlab.SafetyGuard;

/**
 * iCarPlay 统一入口，按车机屏幕 dp 尺寸自适应的一屏布局。
 *
 * 布局分四块：
 *  1. 顶部品牌栏
 *  2. 主功能卡（5 列一行或 3+2 两行）
 *  3. 状态摘要
 *  4. 一行快捷操作 + 返回原车机桌面
 * 不使用 ScrollView。
 */
public class IcarLauncherActivity extends Activity {

    public static final String VERSION_LABEL = "v0.6.2";

    private static final int COLOR_BG = PageUi.BG;
    private static final int COLOR_CARD = PageUi.CARD;
    private static final int COLOR_CARD_ALT = PageUi.CARD_ALT;
    private static final int COLOR_TEXT = PageUi.TEXT;
    private static final int COLOR_MUTED = PageUi.MUTED;
    private static final int COLOR_BLUE = PageUi.BLUE;
    private static final int COLOR_GREEN = PageUi.GREEN;
    private static final int COLOR_RED = PageUi.RED;
    private static final int COLOR_BORDER = PageUi.BORDER;
    private static final int COLOR_GOLD = PageUi.GOLD;

    private TextView statusView;
    private Button floatButton;
    private SharedPreferences prefs;

    /** 分辨率/比例自适应：按屏幕 dp 尺寸缩放字号、间距和卡片高度。 */
    private float uiScale = 1f;
    private boolean fourAcross;
    private boolean narrow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("icarplay", MODE_PRIVATE);

        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        float widthDp = dm.widthPixels / dm.density;
        float heightDp = dm.heightPixels / dm.density;
        float rawScale = Math.min(widthDp / 1000f, heightDp / 560f);
        uiScale = Math.max(0.9f, Math.min(1.45f, rawScale));
        // 纵向空间不足（高密度 1920×720 车机常见）时，四个主功能卡排成一行，
        // 把高度留给状态和返回按钮；普通 16:9 或更方的屏幕仍用 2×2。
        fourAcross = heightDp < 520f;
        narrow = widthDp < 760f;
        boolean showFooter = heightDp >= 420f;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(PageUi.background(this));
        root.setPadding(dp(18), dp(10), dp(18), dp(8));
        setContentView(root);

        root.addView(buildHeader(), wrap());

        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(12), dp(7), dp(12), dp(8));
        statusCard.setBackground(PageUi.rounded(this, PageUi.tint(PageUi.BLUE, 0.05f),
                PageUi.tint(PageUi.BLUE, 0.24f), 12));
        LinearLayout statusHead = new LinearLayout(this);
        statusHead.setOrientation(LinearLayout.HORIZONTAL);
        statusHead.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        dot.setBackground(PageUi.rounded(this, COLOR_GREEN, 0, 4));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(7), dp(7));
        dotLp.setMargins(0, 0, dp(6), 0);
        statusHead.addView(dot, dotLp);
        TextView statusLabel = new TextView(this);
        statusLabel.setText("运行状态");
        statusLabel.setTextColor(COLOR_MUTED);
        sp(statusLabel, 9.5f);
        statusLabel.setTypeface(null, Typeface.BOLD);
        statusHead.addView(statusLabel, wrap());
        statusCard.addView(statusHead, wrap());

        statusView = new TextView(this);
        statusView.setTextColor(COLOR_TEXT);
        sp(statusView, 10.8f);
        statusView.setLineSpacing(dp(1.5f), 1f);
        statusView.setPadding(0, dp(3), 0, 0);
        statusCard.addView(statusView, wrap());
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = dp(8);

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setMinimumHeight(dp(90));
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        gridLp.topMargin = dp(8);
        root.addView(grid, gridLp);

        // 主功能卡在上、状态摘要在下：先保证四张卡有可见高度，再显示运行状态。
        root.addView(statusCard, statusLp);

        if (fourAcross) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            grid.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            addCard(row, makeCard("AirPlay", "镜像 · 音频", COLOR_BLUE, R.drawable.ic_ui_airplay, v ->
                    startActivity(new Intent(IcarLauncherActivity.this, AirPlayActivity.class))));
            addCard(row, makeCard("CarPlay", "有线 / 无线", COLOR_TEXT, R.drawable.ic_ui_carplay, v ->
                    startActivity(new Intent(IcarLauncherActivity.this, CarPlayActivity.class))));
            addCard(row, makeCard("CarLife+", "无线 / 有线", COLOR_GOLD, R.drawable.ic_ui_carlife, v ->
                    startActivity(new Intent(IcarLauncherActivity.this, CarLifePlusActivity.class))));
            addCard(row, makeCard("Android Auto", "USB / 无线", COLOR_GREEN, R.drawable.ic_ui_auto, v ->
                    startActivity(new Intent(IcarLauncherActivity.this, AndroidAutoActivity.class))));
            addCard(row, makeCard("Huawei HiCar", "华为手机", COLOR_RED, R.drawable.ic_ui_hicar, v -> {
                try {
                    startActivity(new Intent(IcarLauncherActivity.this, local.hicarlab.HomeActivity.class));
                } catch (Throwable t) {
                    toast("HiCar 组件未就绪：" + t.getClass().getSimpleName());
                }
            }));
        } else {
            LinearLayout row1 = new LinearLayout(this);
            row1.setOrientation(LinearLayout.HORIZONTAL);
            grid.addView(row1, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            LinearLayout row2 = new LinearLayout(this);
            row2.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            row2Lp.topMargin = dp(6);
            grid.addView(row2, row2Lp);

            addCard(row1, makeCard("AirPlay", "镜像 · 音频", COLOR_BLUE, R.drawable.ic_ui_airplay, v ->
                    startActivity(new Intent(IcarLauncherActivity.this, AirPlayActivity.class))));
            addCard(row1, makeCard("CarPlay", "有线 / 无线", COLOR_TEXT, R.drawable.ic_ui_carplay, v ->
                    startActivity(new Intent(IcarLauncherActivity.this, CarPlayActivity.class))));
            addCard(row1, makeCard("CarLife+", "无线 / 有线", COLOR_GOLD, R.drawable.ic_ui_carlife, v ->
                    startActivity(new Intent(IcarLauncherActivity.this, CarLifePlusActivity.class))));
            addCard(row2, makeCard("Android Auto", "USB / 无线", COLOR_GREEN, R.drawable.ic_ui_auto, v ->
                    startActivity(new Intent(IcarLauncherActivity.this, AndroidAutoActivity.class))));
            addCard(row2, makeCard("Huawei HiCar", "华为手机", COLOR_RED, R.drawable.ic_ui_hicar, v -> {
                try {
                    startActivity(new Intent(IcarLauncherActivity.this, local.hicarlab.HomeActivity.class));
                } catch (Throwable t) {
                    toast("HiCar 组件未就绪：" + t.getClass().getSimpleName());
                }
            }));
        }

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams toolsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
        toolsLp.topMargin = dp(8);
        root.addView(tools, toolsLp);

        floatButton = makeToolButton("小白点：开", v -> toggleFloat());
        tools.addView(floatButton);
        tools.addView(makeToolButton("悬浮窗权限", v -> openOverlayPermission()));
        tools.addView(makeToolButton("认证设置", v ->
                startActivity(new Intent(IcarLauncherActivity.this, AuthSettingsActivity.class))));
        tools.addView(makeToolButton("桌面设置", v -> {
            if (HomeSafety.isDefaultHome(IcarLauncherActivity.this)) {
                toast("本应用当前被设为默认桌面；请在系统设置中选择原车机桌面");
            } else {
                toast("当前仍使用原系统桌面；这里只打开系统桌面选择页");
            }
            HomeSafety.openHomeSettings(IcarLauncherActivity.this);
        }));

        Button back = new Button(this);
        back.setText("←  返回车机主屏幕");
        back.setAllCaps(false);
        sp(back, 13f);
        back.setTextColor(COLOR_TEXT);
        back.setTypeface(null, Typeface.BOLD);
        back.setGravity(Gravity.CENTER);
        back.setMinHeight(0);
        back.setMinimumHeight(0);
        back.setPadding(dp(10), 0, dp(10), 0);
        back.setStateListAnimator(null);
        back.setBackground(PageUi.rounded(this, PageUi.tint(COLOR_BLUE, 0.14f), COLOR_BLUE, 12));
        back.setOnClickListener(v -> goCarHome());
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        backLp.topMargin = dp(8);
        root.addView(back, backLp);

        if (showFooter) {
            TextView footer = new TextView(this);
            footer.setText(VERSION_LABEL);
            footer.setTextColor(COLOR_MUTED);
            sp(footer, 9.5f);
            footer.setGravity(Gravity.CENTER);
            footer.setPadding(0, dp(5), 0, 0);
            root.addView(footer, wrap());
        }
    }

    private LinearLayout buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        View accent = new View(this);
        accent.setBackground(PageUi.rounded(this, COLOR_BLUE, 0, 3));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(3), dp(26));
        alp.setMargins(0, 0, dp(9), 0);
        header.addView(accent, alp);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("iCarPlay");
        title.setTextColor(COLOR_TEXT);
        sp(title, 23f);
        title.setTypeface(null, Typeface.BOLD);
        brand.addView(title, wrap());
        TextView model = new TextView(this);
        model.setText(narrow ? "SYNC+ 车载互联" : "SYNC+ 1.0 · 车载互联中枢");
        model.setTextColor(COLOR_MUTED);
        sp(model, 9.5f);
        brand.addView(model, wrap());
        header.addView(brand, wrap());

        View spacer = new View(this);
        header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        TextView version = new TextView(this);
        version.setText(VERSION_LABEL);
        version.setTextColor(COLOR_MUTED);
        sp(version, 10f);
        version.setPadding(dp(9), dp(3), dp(9), dp(3));
        version.setBackground(PageUi.rounded(this, COLOR_CARD_ALT, COLOR_BORDER, 20));
        header.addView(version, wrap());

        return header;
    }

    private LinearLayout makeCard(String title, String subtitle, int accent, int iconRes,
                                  View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setBackground(PageUi.rounded(this, PageUi.tint(accent, 0.11f),
                PageUi.tint(accent, 0.46f), 14));
        card.setElevation(dp(1));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(listener);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(accent);
        card.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(COLOR_TEXT);
        sp(titleView, 15.5f);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(5);
        card.addView(titleView, titleLp);

        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextColor(accent);
        sp(subtitleView, 9.5f);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setPadding(0, dp(2), 0, 0);
        card.addView(subtitleView, wrap());

        return card;
    }

    private void addCard(LinearLayout row, View card) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        row.addView(card, lp);
    }

    private Button makeToolButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        sp(button, 11.5f);
        button.setTextColor(COLOR_TEXT);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setStateListAnimator(null);
        button.setBackground(PageUi.rounded(this, PageUi.tint(COLOR_BORDER, 0.05f),
                PageUi.tint(COLOR_BORDER, 0.55f), 12));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        button.setLayoutParams(lp);
        return button;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        PermissionOnboarding.onResume(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        PermissionOnboarding.onActivityResult(this, requestCode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionOnboarding.onRequestPermissionsResult(this, requestCode);
    }

    @Override
    public void onBackPressed() {
        goCarHome();
    }

    /**
     * 返回原车机桌面。若本应用被设成默认桌面，则不去启动自己，
     * 而是提示并打开系统桌面选择页，避免在车机上形成“返回又回到本应用”的死循环。
     */
    private void goCarHome() {
        if (HomeSafety.goToSystemHome(this)) {
            // Keep the task (and the floating-dot foreground service) alive when
            // returning to the factory launcher; finish() lets some head units
            // tear down the whole task and the overlay with it.
            PermissionOnboarding.startFloatIfPossible(this);
            moveTaskToBack(true);
            return;
        }
        toast("本应用当前是默认桌面；请先在“桌面设置”切回原车机桌面");
        HomeSafety.openHomeSettings(this);
    }

    private void toggleFloat() {
        boolean running = prefs.getBoolean("float_running", false);
        if (running) {
            stopService(new Intent(this, FloatService.class));
            FloatAccessibilityService.hideIfRunning();
            prefs.edit()
                    .putBoolean("float_running", false)
                    .putBoolean("float_auto", false)
                    .apply();
            refresh();
            return;
        }
        boolean a11y = PermissionOnboarding.isAccessibilityEnabled(this);
        if (a11y) {
            prefs.edit().putBoolean("float_auto", true).apply();
            if (FloatAccessibilityService.showIfRunning()) {
                prefs.edit().putBoolean("float_running", true).apply();
                refresh();
                return;
            }
            toast("无障碍服务未连接，请在系统设置里重新开启 iCarPlay 小白点");
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Throwable ignored) { }
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            toast("先授权：显示在上层 / 悬浮窗");
            openOverlayPermission();
            return;
        }
        Intent service = new Intent(this, FloatService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(service);
        } else {
            startService(service);
        }
        prefs.edit()
                .putBoolean("float_running", true)
                .putBoolean("float_auto", true)
                .apply();
        refresh();
    }

    private void refresh() {
        boolean overlay = Settings.canDrawOverlays(this);
        boolean running = prefs.getBoolean("float_running", false);
        boolean defaultHome = HomeSafety.isDefaultHome(this);
        boolean safeMode = SafetyGuard.safeMode(this);
        String auth = CarPlayCore.authLabel(this);
        int usb = CarPlayCore.appleUsbCount(this);

        String desktop = defaultHome ? "⚠ 本应用为默认桌面" : "原车机桌面";
        String safe = safeMode ? "已触发（点此清除）" : "正常";
        String sep = narrow ? " · " : "   ·   ";
        String aaLabel = narrow ? "AA" : "Android Auto";
        String hiLabel = narrow ? "HiCar" : "Huawei HiCar";

        statusView.setText(
                "设备  " + Build.MANUFACTURER + " " + Build.MODEL
                        + sep + "Android " + Build.VERSION.RELEASE
                        + " (API " + Build.VERSION.SDK_INT + ")\n"
                        + "桌面  " + desktop + sep + "安全模式  " + safe
                        + sep + "悬浮窗  " + (overlay ? "已授予" : "未授予") + "\n"
                        + "小白点  " + (running ? (PermissionOnboarding.isAccessibilityEnabled(this) ? "运行中·无障碍" : "运行中") : "未运行")
                        + sep + "AirPlay  " + (isAirPlayServiceRunning() ? "运行中" : "未运行")
                        + sep + "CarPlay  " + auth
                        + sep + "USB  " + (usb > 0 ? usb + " 个" : "未检测") + "\n"
                        + aaLabel + "  " + (isAndroidAutoServiceRunning() ? "运行中" : "未运行")
                        + sep + hiLabel + "  " + (isHiCarServiceRunning() ? "运行中" : "未运行"));

        floatButton.setText(running ? "小白点：关" : "小白点：开");

        if (safeMode) {
            statusView.setClickable(true);
            statusView.setOnClickListener(v -> {
                SafetyGuard.clearSafeMode(IcarLauncherActivity.this);
                toast("已清除安全模式；开机自启仍保持关闭");
                refresh();
            });
        } else {
            statusView.setClickable(false);
            statusView.setOnClickListener(null);
        }
    }

    @SuppressWarnings("deprecation")
    private boolean isAndroidAutoServiceRunning() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return false;
            for (ActivityManager.RunningServiceInfo info : am.getRunningServices(Integer.MAX_VALUE)) {
                if ("info.anodsplace.headunit.aap.AapService"
                        .equals(info.service.getClassName())) {
                    return true;
                }
            }
        } catch (Exception ignored) { }
        return false;
    }

    @SuppressWarnings("deprecation")
    private boolean isHiCarServiceRunning() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return false;
            for (ActivityManager.RunningServiceInfo info : am.getRunningServices(Integer.MAX_VALUE)) {
                if ("local.hicarlab.ReceiverService".equals(info.service.getClassName())) {
                    return true;
                }
            }
        } catch (Exception ignored) { }
        return false;
    }

    @SuppressWarnings("deprecation")
    private boolean isAirPlayServiceRunning() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return false;
            for (ActivityManager.RunningServiceInfo info : am.getRunningServices(Integer.MAX_VALUE)) {
                if ("io.github.jqssun.airplay.service.AirPlayService"
                        .equals(info.service.getClassName())) {
                    return true;
                }
            }
        } catch (Exception ignored) { }
        return false;
    }

    private void openOverlayPermission() {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        }
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (stroke != 0) {
            drawable.setStroke(dp(1), stroke);
        }
        return drawable;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void sp(TextView view, float baseSp) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseSp * uiScale);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density * uiScale);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
