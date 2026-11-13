package com.rockjesus.icarplay;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * CarPlay 接收页：把认证状态、连接入口和诊断信息放在同一屏。
 * 不伪造“已连接”状态：没有 MFi 认证材料时明确显示未配置。
 */
public class CarPlayActivity extends Activity {

    private TextView authValue;
    private TextView certValue;
    private TextView usbValue;
    private TextView engineValue;
    private TextView statusBadge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(PageUi.background(this));
        root.setPadding(PageUi.dp(this, 18), PageUi.dp(this, 10),
                PageUi.dp(this, 18), PageUi.dp(this, 8));
        setContentView(root);

        root.addView(PageUi.header(this, "CarPlay 接收", IcarLauncherActivity.VERSION_LABEL),
                PageUi.matchWrap(this));

        TextView sub = PageUi.text(this, "iPhone → 车机 · 有线 USB / 无线 Wi‑Fi", 11f,
                PageUi.MUTED, false);
        sub.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams subLp = PageUi.matchWrap(this);
        PageUi.margin(subLp, this, 0, 3, 0, 7);
        root.addView(sub, subLp);

        LinearLayout metrics = PageUi.card(this, PageUi.BLUE, 10, 9);
        LinearLayout.LayoutParams metricsLp = PageUi.matchWrap(this);
        root.addView(metrics, metricsLp);
        LinearLayout metricsHead = new LinearLayout(this);
        metricsHead.setOrientation(LinearLayout.HORIZONTAL);
        metricsHead.setGravity(Gravity.CENTER_VERTICAL);
        metricsHead.addView(PageUi.text(this, "认证与链路状态", 9f, PageUi.BLUE, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        statusBadge = PageUi.statusPill(this, "未配置", PageUi.RED);
        metricsHead.addView(statusBadge, PageUi.wrap(this));
        metrics.addView(metricsHead, PageUi.matchWrap(this));

        LinearLayout m1 = metricRow();
        authValue = metric(m1, "认证后端", "读取中…", PageUi.BLUE);
        certValue = metric(m1, "本地证书", "读取中…", PageUi.GREEN);
        metrics.addView(m1, metricRowLp());

        LinearLayout m2 = metricRow();
        usbValue = metric(m2, "Apple USB", "读取中…", PageUi.GOLD);
        engineValue = metric(m2, "协议引擎", "读取中…", PageUi.MUTED);
        metrics.addView(m2, metricRowLp());

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setMinimumHeight(PageUi.dp(this, 96));
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        PageUi.margin(gridLp, this, 0, 8, 0, 0);
        root.addView(grid, gridLp);

        grid.addView(row(
                action("有线连接（USB）", PageUi.BLUE,
                        v -> toast(CarPlayCore.startWired(CarPlayActivity.this))),
                action("无线连接（Wi‑Fi）", PageUi.GREEN,
                        v -> toast(CarPlayCore.startWireless(CarPlayActivity.this)))
        ), rowLp(0));

        grid.addView(row(
                action("检查认证材料", PageUi.MUTED, v -> checkIdentity()),
                action("认证与连接设置", PageUi.BLUE,
                        v -> startActivity(new Intent(CarPlayActivity.this, AuthSettingsActivity.class)))
        ), rowLp(6));

        grid.addView(row(
                action("刷新状态", PageUi.CYAN, v -> {
                    refresh();
                    toast("状态已刷新");
                }),
                action("复制诊断信息", PageUi.GOLD, v -> copyDiagnostics())
        ), rowLp(6));

        TextView note = PageUi.text(this,
                "使用条件：Apple MFi 认证材料 / 授权芯片。\n"
                        + "未配置认证时，iPhone 不会完成 CarPlay 配对；本页只负责链路、USB 枚举与认证后端。",
                9.5f, PageUi.MUTED, false);
        note.setPadding(PageUi.dp(this, 10), PageUi.dp(this, 6),
                PageUi.dp(this, 10), PageUi.dp(this, 6));
        note.setBackground(PageUi.rounded(this, PageUi.CARD, PageUi.BORDER, 8));
        LinearLayout.LayoutParams noteLp = PageUi.matchWrap(this);
        PageUi.margin(noteLp, this, 0, 6, 0, 0);
        root.addView(note, noteLp);

        Button back = PageUi.back(this);
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams backLp = PageUi.fixed(this, 44);
        PageUi.margin(backLp, this, 0, 8, 0, 0);
        root.addView(back, backLp);
    }

    private LinearLayout.LayoutParams rowLp(float top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        PageUi.margin(lp, this, 0, top, 0, 0);
        return lp;
    }

    private LinearLayout row(Button left, Button right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        PageUi.margin(lp, this, 4, 0, 4, 0);
        row.addView(left, lp);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        PageUi.margin(rp, this, 4, 0, 4, 0);
        row.addView(right, rp);
        return row;
    }

    private LinearLayout metricRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private LinearLayout.LayoutParams metricRowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        PageUi.margin(lp, this, 0, 5, 0, 0);
        return lp;
    }

    private TextView metric(LinearLayout row, String label, String value, int accent) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(PageUi.dp(this, 8), PageUi.dp(this, 5),
                PageUi.dp(this, 8), PageUi.dp(this, 5));
        box.setBackground(PageUi.rounded(this, PageUi.blend(PageUi.CARD, accent, 0.08f),
                PageUi.blend(PageUi.BORDER, accent, 0.30f), 9));
        box.addView(PageUi.text(this, label, 8f, PageUi.MUTED, false), PageUi.wrap(this));
        TextView valueView = PageUi.text(this, value, 11.5f, accent, true);
        valueView.setSingleLine(true);
        box.addView(valueView, PageUi.wrap(this));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        PageUi.margin(lp, this, 4, 0, 4, 0);
        row.addView(box, lp);
        return valueView;
    }

    private Button action(String title, int accent, View.OnClickListener l) {
        Button b = PageUi.button(this, title, 14f, accent);
        b.setOnClickListener(l);
        b.setGravity(Gravity.CENTER);
        return b;
    }

    private void checkIdentity() {
        if (CarPlayCore.hasLocalIdentity(this)) {
            toast("已找到 identity.pk8 + certificate.p7b");
        } else {
            toast("未找到本地证书。" + CarPlayCore.identityHint(this));
        }
        refresh();
    }

    private void copyDiagnostics() {
        String text = "iCarPlay " + IcarLauncherActivity.VERSION_LABEL
                + "\n认证后端：" + CarPlayCore.authLabel(this)
                + "\n认证可用：" + (CarPlayCore.hasAuth(this) ? "是" : "否")
                + "\n本地证书：" + (CarPlayCore.hasLocalIdentity(this) ? "已找到" : "未找到")
                + "\nApple USB：" + CarPlayCore.appleUsbCount(this)
                + "\n设备：" + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
                + "\nAndroid " + android.os.Build.VERSION.RELEASE
                + " (API " + android.os.Build.VERSION.SDK_INT + ")";
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("iCarPlay CarPlay 诊断", text));
            toast("诊断信息已复制");
        } else {
            toast("剪贴板不可用");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean hasAuth = CarPlayCore.hasAuth(this);
        boolean local = CarPlayCore.hasLocalIdentity(this);
        int usb = CarPlayCore.appleUsbCount(this);
        String auth = CarPlayCore.authLabel(this);

        authValue.setText(auth);
        certValue.setText(local ? "已找到" : "未找到");
        certValue.setTextColor(local ? PageUi.GREEN : PageUi.RED);
        usbValue.setText(usb > 0 ? usb + " 个" : "未检测");
        usbValue.setTextColor(usb > 0 ? PageUi.GREEN : PageUi.GOLD);
        engineValue.setText(hasAuth ? "认证已配置" : "未启动");
        engineValue.setTextColor(hasAuth ? PageUi.GREEN : PageUi.MUTED);

        if (hasAuth) {
            statusBadge.setText("认证可用");
            statusBadge.setTextColor(PageUi.GREEN);
            statusBadge.setBackground(PageUi.rounded(this,
                    PageUi.blend(PageUi.CARD, PageUi.GREEN, 0.16f),
                    PageUi.blend(PageUi.BORDER, PageUi.GREEN, 0.55f), 20));
        } else {
            statusBadge.setText(local ? "待验证" : "未配置");
            statusBadge.setTextColor(local ? PageUi.GOLD : PageUi.RED);
            statusBadge.setBackground(PageUi.rounded(this,
                    PageUi.blend(PageUi.CARD, local ? PageUi.GOLD : PageUi.RED, 0.16f),
                    PageUi.blend(PageUi.BORDER, local ? PageUi.GOLD : PageUi.RED, 0.55f), 20));
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
