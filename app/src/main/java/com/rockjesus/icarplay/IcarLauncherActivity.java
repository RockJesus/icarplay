package com.rockjesus.icarplay;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.rockjesus.icarplay.carlife.CarLifePlusActivity;

import io.github.jqssun.airplay.R;
import local.hicarlab.SafetyGuard;

/**
 * iCarPlay 统一入口：五种互联方式、车机安全状态和快捷操作都在一屏。
 *
 * 视觉上使用同一套深色玻璃卡片：左上角图标、右上角实时状态、下方名称与说明。
 * 分辨率按屏幕 dp 自适应：宽屏五卡横排，较窄或纵向空间不足时改为 3+2。
 */
public class IcarLauncherActivity extends Activity {

    public static final String VERSION_LABEL = "v0.7.9";

    private TextView statusView;
    private View statusDot;
    private Button floatButton;
    private SharedPreferences prefs;

    private TextView airplayState;
    private TextView carplayState;
    private TextView carlifeState;
    private TextView autoState;
    private TextView hicarState;

    private float uiScale = 1f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("icarplay", MODE_PRIVATE);

        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        float widthDp = dm.widthPixels / dm.density;
        float heightDp = dm.heightPixels / dm.density;
        float rawScale = Math.min(widthDp / 1000f, heightDp / 560f);
        uiScale = Math.max(0.9f, Math.min(1.45f, rawScale));
        boolean fiveAcross = dm.widthPixels >= 1100
                && (dm.widthPixels / (float) dm.heightPixels) >= 1.5f;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(PageUi.background(this));
        root.setPadding(dp(18), dp(10), dp(18), dp(8));
        setContentView(root);

        root.addView(buildHeader(), wrap());

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setMinimumHeight(dp(90));
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        gridLp.topMargin = dp(8);
        root.addView(grid, gridLp);

        if (fiveAcross) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            grid.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            addCard(row, cardAirPlay());
            addCard(row, cardCarPlay());
            addCard(row, cardCarLife());
            addCard(row, cardAuto());
            addCard(row, cardHiCar());
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

            addCard(row1, cardAirPlay());
            addCard(row1, cardCarPlay());
            addCard(row1, cardCarLife());

            View leftPad = new View(this);
            row2.addView(leftPad, new LinearLayout.LayoutParams(0, 1, 0.5f));
            addCard(row2, cardAuto());
            addCard(row2, cardHiCar());
            View rightPad = new View(this);
            row2.addView(rightPad, new LinearLayout.LayoutParams(0, 1, 0.5f));
        }

        root.addView(buildStatusPanel(), statusLp());

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams toolsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        toolsLp.topMargin = dp(8);
        root.addView(tools, toolsLp);

        floatButton = makeToolButton("悬浮按钮：开", v -> toggleFloat());
        tools.addView(floatButton);
        tools.addView(makeToolButton("连接中心", v ->
                startActivity(new Intent(IcarLauncherActivity.this, NetworkCenterActivity.class))));
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
        back.setTextColor(PageUi.TEXT);
        back.setTypeface(null, Typeface.BOLD);
        back.setGravity(Gravity.CENTER);
        back.setMinHeight(0);
        back.setMinimumHeight(0);
        back.setPadding(dp(10), 0, dp(10), 0);
        back.setStateListAnimator(null);
        back.setBackground(PageUi.surfaceState(this, PageUi.BLUE, 12));
        back.setOnClickListener(v -> goCarHome());
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        backLp.topMargin = dp(8);
        root.addView(back, backLp);

        startAirPlayByDefault();
    }

    /** 首页启动时默认拉起 AirPlay 接收服务；失败不影响其余入口。 */
    private void startAirPlayByDefault() {
        try {
            Intent intent = new Intent(this, io.github.jqssun.airplay.service.AirPlayService.class);
            intent.setAction(io.github.jqssun.airplay.service.AirPlayService.ACTION_START_SERVER);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Throwable ignored) { }
    }

    private View buildHeader() {
        FrameLayout header = new FrameLayout(this);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("iCarPlay");
        title.setTextColor(PageUi.TEXT);
        sp(title, 24f);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setLetterSpacing(0.02f);
        PageUi.gradientText(title, PageUi.TITLE_GRADIENT);
        brand.addView(title, wrap());

        // 标题固定在顶部正中，版本胶囊留在右上角。
        header.addView(brand, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL));

        TextView version = new TextView(this);
        version.setText(VERSION_LABEL);
        version.setTextColor(PageUi.CYAN);
        sp(version, 10f);
        PageUi.mono(version);
        version.setPadding(dp(10), dp(4), dp(10), dp(4));
        version.setBackground(PageUi.rounded(this, PageUi.blend(PageUi.CARD, PageUi.PURPLE, 0.16f),
                PageUi.blend(PageUi.PURPLE, PageUi.CYAN, 0.45f), 20));
        header.addView(version, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL));

        return header;
    }

    private View cardAirPlay() {
        return makeCard("AirPlay", "镜像 · 音频", PageUi.BLUE,
                R.drawable.ic_ui_airplay, R.drawable.ic_brand_apple,
                "待启动", PageUi.BLUE,
                v -> startActivity(new Intent(this, AirPlayActivity.class)));
    }

    private View cardCarPlay() {
        return makeCard("CarPlay", "有线 / 无线", PageUi.PURPLE,
                R.drawable.ic_ui_carplay, R.drawable.ic_brand_apple,
                "未配置", PageUi.RED,
                v -> startActivity(new Intent(this, CarPlayActivity.class)));
    }

    private View cardCarLife() {
        return makeCard("CarLife+", "无线 / 有线", PageUi.GOLD,
                R.drawable.ic_ui_carlife, R.drawable.ic_brand_baidu,
                "待连接", PageUi.GOLD,
                v -> startActivity(new Intent(this, CarLifePlusActivity.class)));
    }

    private View cardAuto() {
        return makeCard("Android Auto", "USB / 无线", PageUi.GREEN,
                R.drawable.ic_ui_auto, R.drawable.ic_brand_android,
                "待连接", PageUi.GREEN,
                v -> startActivity(new Intent(this, AndroidAutoActivity.class)));
    }

    private View cardHiCar() {
        return makeCard("Huawei HiCar", "华为手机", PageUi.RED,
                R.drawable.ic_ui_hicar, R.drawable.ic_brand_huawei,
                "待连接", PageUi.RED, v -> {
                    try {
                        startActivity(new Intent(this, local.hicarlab.HomeActivity.class));
                    } catch (Throwable t) {
                        toast("HiCar 组件未就绪：" + t.getClass().getSimpleName());
                    }
                });
    }

    private LinearLayout makeCard(String title, String subtitle, int accent, int iconRes,
                                  int brandRes, String initialStatus, int statusAccent,
                                  View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(11), dp(12), dp(10));
        card.setBackground(PageUi.surfaceState(this, accent, 16));
        card.setElevation(dp(2));
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(title + "，" + subtitle);
        card.setOnClickListener(listener);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(PageUi.iconBadge(this, iconRes, accent, 38),
                new LinearLayout.LayoutParams(dp(38), dp(38)));
        View spacer = new View(this);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView state = PageUi.statusPill(this, initialStatus, statusAccent);
        top.addView(state, wc());
        card.addView(top, wrap());

        // 品牌 logo 放在卡片剩余空间的正中并放大，作为卡片的视觉主体。
        FrameLayout logoBox = new FrameLayout(this);
        ImageView brandIcon = new ImageView(this);
        brandIcon.setImageResource(brandRes);
        brandIcon.setColorFilter(accent);
        brandIcon.setAlpha(0.96f);
        brandIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        // 五个品牌标资源都已按内容裁边并统一到同一视觉盒，这里用同一尺寸即可保证体量一致。
        int brandSize = dp(64);
        FrameLayout.LayoutParams brandLp = new FrameLayout.LayoutParams(
                brandSize, brandSize, Gravity.CENTER);
        logoBox.addView(brandIcon, brandLp);
        card.addView(logoBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(PageUi.TEXT);
        sp(titleView, 16.5f);
        titleView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        titleView.setLetterSpacing(0.03f);
        titleView.setGravity(Gravity.START);
        titleRow.addView(titleView, wc());

        card.addView(titleRow, wrap());

        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextColor(accent);
        sp(subtitleView, 9.5f);
        subtitleView.setLetterSpacing(0.05f);
        subtitleView.setPadding(0, dp(2), 0, dp(5));
        card.addView(subtitleView, wrap());

        View rule = new View(this);
        rule.setBackground(PageUi.rainbow(this, 1));
        LinearLayout.LayoutParams ruleLp = new LinearLayout.LayoutParams(dp(34), dp(2));
        ruleLp.bottomMargin = dp(6);
        card.addView(rule, ruleLp);

        TextView open = new TextView(this);
        open.setText("打开  →");
        open.setTextColor(PageUi.blend(accent, PageUi.TEXT, 0.4f));
        sp(open, 9.5f);
        open.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        open.setLetterSpacing(0.05f);
        card.addView(open, wrap());

        if ("AirPlay".equals(title)) airplayState = state;
        else if ("CarPlay".equals(title)) carplayState = state;
        else if ("CarLife+".equals(title)) carlifeState = state;
        else if ("Android Auto".equals(title)) autoState = state;
        else hicarState = state;

        return card;
    }

    private void addCard(LinearLayout row, View card) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        row.addView(card, lp);
    }

    private LinearLayout buildStatusPanel() {
        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(14), dp(9), dp(14), dp(9));
        statusCard.setBackground(PageUi.surface(this, PageUi.BLUE, 14));

        LinearLayout statusHead = new LinearLayout(this);
        statusHead.setOrientation(LinearLayout.HORIZONTAL);
        statusHead.setGravity(Gravity.CENTER_VERTICAL);
        statusDot = new View(this);
        statusDot.setBackground(PageUi.rounded(this, PageUi.GREEN, 0, 4));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotLp.setMargins(0, 0, dp(7), 0);
        statusHead.addView(statusDot, dotLp);
        TextView statusLabel = new TextView(this);
        statusLabel.setText("车机状态");
        statusLabel.setTextColor(PageUi.MUTED);
        sp(statusLabel, 9.5f);
        statusLabel.setTypeface(null, Typeface.BOLD);
        statusHead.addView(statusLabel, wc());
        View spacer = new View(this);
        statusHead.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView hint = new TextView(this);
        hint.setText("点状态可清除安全模式");
        hint.setTextColor(PageUi.blend(PageUi.MUTED, PageUi.BG, 0.25f));
        sp(hint, 8.5f);
        statusHead.addView(hint, wc());
        statusCard.addView(statusHead, wrap());

        statusView = new TextView(this);
        statusView.setTextColor(PageUi.TEXT);
        sp(statusView, 10.5f);
        statusView.setLineSpacing(dp(2f), 1f);
        statusView.setPadding(0, dp(4), 0, 0);
        statusCard.addView(statusView, wrap());
        return statusCard;
    }

    private LinearLayout.LayoutParams statusLp() {
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = dp(8);
        return statusLp;
    }

    private Button makeToolButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        sp(button, 11.5f);
        button.setTextColor(PageUi.TEXT);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setStateListAnimator(null);
        button.setBackground(PageUi.button(this, "", 11.5f, PageUi.BLUE).getBackground());
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

    private void goCarHome() {
        if (HomeSafety.goToSystemHome(this)) {
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
            toast("无障碍服务未连接，请在系统设置里重新开启 iCarPlay 悬浮按钮");
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

        boolean airplayRunning = isAirPlayServiceRunning();
        boolean autoRunning = isAndroidAutoServiceRunning();
        boolean hicarRunning = isHiCarServiceRunning();
        boolean carlifeRunning = prefs.getBoolean("carlife_active", false);

        statusDot.setBackground(PageUi.rounded(this,
                safeMode ? PageUi.GOLD : (defaultHome ? PageUi.RED : PageUi.GREEN), 0, 4));

        SpannableStringBuilder line1 = new SpannableStringBuilder();
        chip(line1, defaultHome ? "默认桌面" : "原车桌面",
                defaultHome ? PageUi.RED : PageUi.GREEN);
        line1.append("    ");
        chip(line1, overlay ? "悬浮窗已授予" : "悬浮窗未授予",
                overlay ? PageUi.GREEN : PageUi.GOLD);
        line1.append("    ");
        chip(line1, safeMode ? "安全模式已触发" : "安全模式正常",
                safeMode ? PageUi.GOLD : PageUi.GREEN);
        line1.append("    ");
        chip(line1, usb > 0 ? "USB " + usb + " 个" : "USB 未检测",
                usb > 0 ? PageUi.BLUE : PageUi.MUTED);

        String device = Build.MANUFACTURER + " " + Build.MODEL
                + "  ·  Android " + Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ")";
        String floatState = running
                ? (PermissionOnboarding.isAccessibilityEnabled(this) ? "悬浮按钮运行中·无障碍" : "悬浮按钮运行中")
                : "悬浮按钮未运行";

        statusView.setText(line1);
        statusView.append("\n" + device + "  ·  " + floatState);

        setPill(airplayState, airplayRunning ? "待连接" : "待启动", PageUi.BLUE);
        boolean carplayReady = CarPlayCore.hasAuth(this) || CarPlayCore.hasLocalIdentity(this);
        setPill(carplayState, carplayReady ? "已配置" : (auth.equals("未配置") ? "未配置" : auth),
                carplayReady ? PageUi.GREEN : PageUi.RED);
        setPill(carlifeState, carlifeRunning ? "运行中" : "待连接",
                carlifeRunning ? PageUi.GREEN : PageUi.GOLD);
        setPill(autoState, autoRunning ? "运行中" : "待连接",
                autoRunning ? PageUi.GREEN : PageUi.GREEN);
        setPill(hicarState, hicarRunning ? "运行中" : "待连接",
                hicarRunning ? PageUi.GREEN : PageUi.RED);

        floatButton.setText(running ? "悬浮按钮：关" : "悬浮按钮：开");
        floatButton.setTextColor(running ? PageUi.GREEN : PageUi.TEXT);

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

    private void chip(SpannableStringBuilder builder, String text, int color) {
        int start = builder.length();
        builder.append("● ").append(text);
        builder.setSpan(new ForegroundColorSpan(color), start, builder.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void setPill(TextView view, String text, int accent) {
        if (view == null) return;
        view.setText(text);
        view.setTextColor(accent);
        view.setBackground(PageUi.rounded(this, PageUi.blend(PageUi.CARD, accent, 0.16f),
                PageUi.blend(PageUi.BORDER, accent, 0.55f), 20));
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

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wc() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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
