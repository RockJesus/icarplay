package com.rockjesus.icarplay;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 应用管理页：列出车机上装过的第三方 App，给出「卸载 / 应用详情」两个动作。
 *
 * <p>第三方 App 无法静默卸载别的包，这里统一走系统的卸载确认框（ACTION_UNINSTALL_PACKAGE）；
 * 若系统按钮是灰的，多半是被设备管理员、无障碍服务或系统应用身份拦住，页面会把原因和对应设置入口一并给出来。
 */
public class AppManageActivity extends FitActivity {

    private static final int REQ_UNINSTALL = 1201;

    private LinearLayout listBox;
    private TextView countText;
    private TextView resultText;

    private String pendingPackage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackground(PageUi.background(this));
        int pad = PageUi.dp(this, 18);
        shell.setPadding(pad, PageUi.dp(this, 10), pad, PageUi.dp(this, 8));
        setFitContentView(shell);

        shell.addView(PageUi.header(this, "应用管理", IcarLauncherActivity.VERSION_LABEL),
                PageUi.matchWrap(this));

        TextView sub = PageUi.text(this,
                "卸载车载多余的 App · 设置里点应用会闪退的，本页直接调卸载框 · 系统预装通常只能停用",
                10.5f, PageUi.MUTED, false);
        sub.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams subLp = PageUi.matchWrap(this);
        PageUi.margin(subLp, this, 0, 3, 0, 7);
        shell.addView(sub, subLp);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        shell.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        body.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.78f));
        left.addView(buildWhyCard(), PageUi.matchWrap(this));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1.22f);
        rightLp.setMargins(PageUi.dp(this, 8), 0, 0, 0);
        body.addView(right, rightLp);
        right.addView(buildListCard(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        resultText = PageUi.text(this, "点「详情」闪退就跳过它，直接用「卸载」——卸载框不读应用详情页", 9.5f, PageUi.MUTED, false);
        LinearLayout.LayoutParams resultLp = PageUi.matchWrap(this);
        PageUi.margin(resultLp, this, 0, 4, 0, 0);
        shell.addView(resultText, resultLp);

        Button back = PageUi.back(this);
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams backLp = PageUi.fixed(this, 42);
        PageUi.margin(backLp, this, 0, 6, 0, 0);
        shell.addView(back, backLp);

        refresh();
    }

    // ------------------------------------------------------------------ cards

    private LinearLayout buildWhyCard() {
        LinearLayout card = PageUi.card(this, PageUi.GOLD, 14, 9);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(PageUi.text(this, "卸不掉时", 8.5f, PageUi.GOLD, true), PageUi.wrap(this));
        View spacer = new View(this);
        head.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        head.addView(PageUi.statusPill(this, "3 个原因", PageUi.GOLD), PageUi.wrap(this));
        card.addView(head, PageUi.matchWrap(this));

        TextView why = PageUi.text(this,
                "① 被设为「设备管理员」：先去系统安全里取消勾选\n"
                        + "② 开着「无障碍服务」：先关掉该服务，悬浮类 App 基本都卡在这\n"
                        + "③ 系统预装应用：卸不掉，只能在详情页「停用」\n"
                        + "原生设置的按钮是灰的、点不动，多半就是前两种。\n"
                        + "设置里点某个应用直接闪退，说明那个应用的详情页坏了：跳过详情，直接点「卸载」。",
                9.5f, PageUi.MUTED, false);
        why.setLineSpacing(PageUi.dp(this, 1.5f), 1f);
        why.setPadding(0, PageUi.dp(this, 4), 0, 0);
        card.addView(why, PageUi.matchWrap(this));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams row1Lp = PageUi.matchWrap(this);
        PageUi.margin(row1Lp, this, 0, 6, 0, 0);
        card.addView(row1, row1Lp);
        row1.addView(action("无障碍设置", PageUi.CYAN, v -> openAccessibility()), actionLp(6));
        row1.addView(action("设备管理员", PageUi.PURPLE, v -> openDeviceAdmin()), actionLp(0));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams row2Lp = PageUi.matchWrap(this);
        PageUi.margin(row2Lp, this, 0, 5, 0, 0);
        card.addView(row2, row2Lp);
        row2.addView(action("应用列表设置", PageUi.BLUE, v -> openAppSettings()), actionLp(6));
        row2.addView(action("关于本页", PageUi.GREEN, v -> showAbout()), actionLp(0));

        return card;
    }

    private LinearLayout buildListCard() {
        LinearLayout card = PageUi.card(this, PageUi.CYAN, 14, 9);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(PageUi.text(this, "已安装的应用（含系统预装）", 8.5f, PageUi.CYAN, true), PageUi.wrap(this));
        View spacer = new View(this);
        head.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        countText = PageUi.text(this, "—", 8.5f, PageUi.MUTED, false);
        head.addView(countText, PageUi.wrap(this));
        Button reload = PageUi.button(this, "刷新", 9.5f, PageUi.CYAN);
        reload.setOnClickListener(v -> refresh());
        LinearLayout.LayoutParams reloadLp = new LinearLayout.LayoutParams(
                PageUi.dp(this, 62), PageUi.dp(this, 26));
        reloadLp.setMargins(PageUi.dp(this, 8), 0, 0, 0);
        head.addView(reload, reloadLp);
        card.addView(head, PageUi.matchWrap(this));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout.LayoutParams scrollLp = PageUi.matchWrap(this);
        PageUi.margin(scrollLp, this, 0, 5, 0, 0);
        card.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listBox, PageUi.matchWrap(this));
        return card;
    }

    private LinearLayout.LayoutParams actionLp(float gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, PageUi.dp(this, 34), 1f);
        lp.setMargins(0, 0, PageUi.dp(this, gap), 0);
        return lp;
    }

    private Button action(String label, int accent, View.OnClickListener listener) {
        Button b = PageUi.button(this, label, 9.5f, accent);
        b.setOnClickListener(listener);
        return b;
    }

    // ------------------------------------------------------------------ list

    private void refresh() {
        listBox.removeAllViews();
        List<AppEntry> apps = loadApps();
        countText.setText(apps.size() + " 个");
        if (apps.isEmpty()) {
            listBox.addView(PageUi.text(this, "没有可管理的第三方应用（系统应用未列出）", 9.5f,
                    PageUi.MUTED, false), PageUi.matchWrap(this));
            return;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        for (AppEntry app : apps) {
            listBox.addView(buildRow(app, fmt), PageUi.matchWrap(this));
        }
    }

    private View buildRow(AppEntry app, SimpleDateFormat fmt) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = PageUi.matchWrap(this);
        PageUi.margin(rowLp, this, 0, 0, 0, 5);
        row.setLayoutParams(rowLp);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                PageUi.dp(this, 34), PageUi.dp(this, 34));
        iconLp.setMargins(0, 0, PageUi.dp(this, 8), 0);
        row.addView(icon, iconLp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout titleLine = new LinearLayout(this);
        titleLine.setOrientation(LinearLayout.HORIZONTAL);
        titleLine.setGravity(Gravity.CENTER_VERTICAL);
        titleLine.addView(PageUi.text(this, app.label, 11f, PageUi.TEXT, true), PageUi.wrap(this));
        if (app.hasLauncher) {
            LinearLayout.LayoutParams pillLp = PageUi.wrap(this);
            pillLp.setMargins(PageUi.dp(this, 6), 0, 0, 0);
            titleLine.addView(PageUi.statusPill(this, "桌面有图标", PageUi.GREEN), pillLp);
        }
        if (app.damaged) {
            LinearLayout.LayoutParams pillLp = PageUi.wrap(this);
            pillLp.setMargins(PageUi.dp(this, 6), 0, 0, 0);
            titleLine.addView(PageUi.statusPill(this, "安装包缺失", PageUi.RED), pillLp);
        }
        if (app.blockedReason != null) {
            LinearLayout.LayoutParams pillLp = PageUi.wrap(this);
            pillLp.setMargins(PageUi.dp(this, 6), 0, 0, 0);
            titleLine.addView(PageUi.statusPill(this, app.blockedReason, PageUi.RED), pillLp);
        }
        if (app.system) {
            LinearLayout.LayoutParams pillLp = PageUi.wrap(this);
            pillLp.setMargins(PageUi.dp(this, 6), 0, 0, 0);
            titleLine.addView(PageUi.statusPill(this, "系统预装", PageUi.GOLD), pillLp);
        }
        texts.addView(titleLine, PageUi.matchWrap(this));

        texts.addView(PageUi.text(this,
                app.pkg + " · v" + app.version + " · " + app.sizeText + " · " + fmt.format(new Date(app.updated)),
                8.5f, PageUi.MUTED, false), PageUi.matchWrap(this));

        Button uninstall = PageUi.button(this, "卸载", 9.5f, PageUi.PINK);
        uninstall.setOnClickListener(v -> uninstall(app));
        Button details = PageUi.button(this, "详情", 9.5f, PageUi.BLUE);
        details.setOnClickListener(v -> openDetails(app.pkg));
        LinearLayout.LayoutParams btnLp1 = new LinearLayout.LayoutParams(
                PageUi.dp(this, 56), PageUi.dp(this, 32));
        btnLp1.setMargins(PageUi.dp(this, 6), 0, 0, 0);
        row.addView(uninstall, btnLp1);
        LinearLayout.LayoutParams btnLp2 = new LinearLayout.LayoutParams(
                PageUi.dp(this, 56), PageUi.dp(this, 32));
        btnLp2.setMargins(PageUi.dp(this, 5), 0, 0, 0);
        row.addView(details, btnLp2);

        return row;
    }

    private static final class AppEntry {
        String label;
        String pkg;
        String version;
        String sizeText;
        long updated;
        boolean system;
        boolean damaged;
        boolean hasLauncher;
        String blockedReason;
        Drawable icon;
    }

    private List<AppEntry> loadApps() {
        List<AppEntry> out = new ArrayList<>();
        PackageManager pm = getPackageManager();
        String self = getPackageName();
        List<ApplicationInfo> all;
        try {
            all = pm.getInstalledApplications(0);
        } catch (Throwable e) {
            all = new ArrayList<>();
        }
        for (ApplicationInfo info : all) {
            if (info.packageName.equals(self)) continue;
            boolean system = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    && (info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
            AppEntry entry = new AppEntry();
            entry.pkg = info.packageName;
            entry.label = String.valueOf(pm.getApplicationLabel(info));
            entry.system = system;
            entry.icon = pm.getApplicationIcon(info);
            try {
                entry.version = String.valueOf(pm.getPackageInfo(info.packageName, 0).versionName);
            } catch (Throwable ignored) {
                entry.version = "?";
            }
            try {
                entry.sizeText = FileTransferServer.formatSize(new File(info.sourceDir).length());
            } catch (Throwable ignored) {
                entry.sizeText = "—";
            }
            entry.updated = info.sourceDir == null ? 0 : new File(info.sourceDir).lastModified();
            try {
                File src = info.sourceDir == null ? null : new File(info.sourceDir);
                entry.damaged = src == null || !src.exists() || src.length() <= 0;
            } catch (Throwable ignored) {
                entry.damaged = true;
            }
            entry.hasLauncher = hasLauncher(pm, info.packageName);
            entry.blockedReason = blockedReason(info.packageName);
            out.add(entry);
        }
        out.sort((a, b) -> {
            int rankA = a.damaged ? 0 : (a.blockedReason != null ? 1 : (a.system ? 3 : 2));
            int rankB = b.damaged ? 0 : (b.blockedReason != null ? 1 : (b.system ? 3 : 2));
            if (rankA != rankB) return rankA - rankB;
            return a.label.compareToIgnoreCase(b.label);
        });
        return out;
    }

    private boolean hasLauncher(PackageManager pm, String pkg) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setPackage(pkg);
            return !pm.queryIntentActivities(intent, 0).isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 判断这个包被什么挡着卸不掉：设备管理员 / 无障碍服务。 */
    private String blockedReason(String pkg) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm != null) {
                for (ComponentName admin : dpm.getActiveAdmins()) {
                    if (admin != null && pkg.equals(admin.getPackageName())) return "设备管理员";
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            String enabled = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (!TextUtils.isEmpty(enabled) && enabled.contains(pkg)) return "无障碍服务";
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ------------------------------------------------------------------ actions

    private void uninstall(AppEntry app) {
        pendingPackage = app.pkg;
        try {
            Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:" + app.pkg));
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
            startActivityForResult(intent, REQ_UNINSTALL);
            return;
        } catch (Throwable ignored) {
        }
        try {
            Intent delete = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.pkg));
            startActivity(delete);
        } catch (Throwable e) {
            resultText.setText("无法调起卸载：" + e.getClass().getSimpleName()
                    + "；先用「详情」页看能不能停用");
            toast("无法调起卸载，请看详情页");
        }
    }

    private void openDetails(String pkg) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + pkg));
            startActivity(intent);
        } catch (Throwable e) {
            toast("应用详情打不开：" + e.getClass().getSimpleName());
        }
    }

    private void openAccessibility() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Throwable e) {
            toast("无障碍设置打不开，去系统设置里找「无障碍」");
        }
    }

    private void openDeviceAdmin() {
        try {
            startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
        } catch (Throwable e) {
            toast("安全设置打不开，去系统设置里找「安全 / 设备管理员」");
        }
    }

    private void openAppSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
        } catch (Throwable e) {
            toast("应用列表打不开");
        }
    }

    private void showAbout() {
        resultText.setText("本页只调用系统卸载确认框，不申请系统级安装 / 卸载权限，也不会静默删你的应用。");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_UNINSTALL) return;
        String pkg = pendingPackage == null ? "" : pendingPackage;
        pendingPackage = null;
        if (resultCode == RESULT_OK) {
            resultText.setText("已卸载：" + pkg);
            toast("已卸载");
        } else if (resultCode == RESULT_FIRST_USER) {
            resultText.setText("系统拒绝了卸载：" + pkg + " —— 多半是设备管理员或无系统权限，按左侧三步处理");
            toast("系统拒绝卸载，看左侧三个原因");
        } else {
            resultText.setText("取消了卸载：" + pkg);
        }
        refresh();
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
