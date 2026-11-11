package com.rockjesus.icarplay;

import android.app.Activity;
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
 * CarPlay 接收页：单屏全部显示。
 * 状态、有线/无线、认证检查、认证设置、返回都在一页，不滚动。
 */
public class CarPlayActivity extends Activity {

    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PageUi.BG);
        root.setPadding(PageUi.dp(this, 18), PageUi.dp(this, 10),
                PageUi.dp(this, 18), PageUi.dp(this, 8));
        setContentView(root);

        root.addView(PageUi.header(this, "CarPlay 接收", "v0.4.8"), PageUi.matchWrap(this));

        TextView sub = PageUi.text(this, "iPhone → 车机 · 有线 USB / 无线 Wi-Fi", 11f,
                PageUi.MUTED, false);
        LinearLayout.LayoutParams subLp = PageUi.matchWrap(this);
        PageUi.margin(subLp, this, 0, 3, 0, 6);
        root.addView(sub, subLp);

        statusView = PageUi.text(this, "正在读取认证状态…", 11.5f, PageUi.TEXT, false);
        statusView.setPadding(PageUi.dp(this, 12), PageUi.dp(this, 8),
                PageUi.dp(this, 12), PageUi.dp(this, 8));
        statusView.setBackground(PageUi.rounded(this, PageUi.CARD, PageUi.BORDER, 10));
        root.addView(statusView, PageUi.matchWrap(this));

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setMinimumHeight(PageUi.dp(this, 110));
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        PageUi.margin(gridLp, this, 0, 8, 0, 0);
        root.addView(grid, gridLp);

        grid.addView(row(
                action("有线连接（USB）", PageUi.BLUE,
                        v -> toast(CarPlayCore.startWired(CarPlayActivity.this))),
                action("无线连接（Wi-Fi）", PageUi.GREEN,
                        v -> toast(CarPlayCore.startWireless(CarPlayActivity.this)))
        ), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout row2 = row(
                action("检查认证材料", PageUi.MUTED,
                        v -> {
                            if (CarPlayCore.hasLocalIdentity(CarPlayActivity.this)) {
                                toast("已找到 identity.pk8 + certificate.p7b");
                            } else {
                                toast("未找到本地证书。" + CarPlayCore.identityHint(CarPlayActivity.this));
                            }
                            refresh();
                        }),
                action("认证与连接设置", PageUi.BLUE,
                        v -> startActivity(new Intent(CarPlayActivity.this, AuthSettingsActivity.class)))
        );
        LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        PageUi.margin(row2Lp, this, 0, 6, 0, 0);
        grid.addView(row2, row2Lp);

        TextView note = PageUi.text(this,
                "CarPlay 需要 Apple MFi 认证材料；没有证书时，iPhone 不会完成配对。\n"
                        + "当前只接入链路、认证后端和 USB 枚举，iAP2 / MFi / AirPlay 隧道尚未接通。",
                9.5f, PageUi.MUTED, false);
        note.setPadding(PageUi.dp(this, 10), PageUi.dp(this, 6),
                PageUi.dp(this, 10), PageUi.dp(this, 6));
        note.setBackground(PageUi.rounded(this, PageUi.CARD, PageUi.BORDER, 8));
        LinearLayout.LayoutParams noteLp = PageUi.matchWrap(this);
        PageUi.margin(noteLp, this, 0, 6, 0, 0);
        root.addView(note, noteLp);

        Button back = PageUi.back(this);
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams backLp = PageUi.fixed(this, 42);
        PageUi.margin(backLp, this, 0, 8, 0, 0);
        root.addView(back, backLp);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
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

    private Button action(String title, int accent, View.OnClickListener l) {
        Button b = PageUi.button(this, title, 15f, accent);
        b.setOnClickListener(l);
        b.setGravity(Gravity.CENTER);
        return b;
    }

    private void refresh() {
        boolean hasAuth = CarPlayCore.hasAuth(this);
        boolean local = CarPlayCore.hasLocalIdentity(this);
        int usb = CarPlayCore.appleUsbCount(this);
        statusView.setText(
                "认证后端：" + CarPlayCore.authLabel(this)
                        + "    认证可用：" + (hasAuth ? "是" : "否") + "\n"
                        + "本地证书：" + (local ? "已找到" : "未找到")
                        + "    Apple USB：" + (usb > 0 ? usb + " 个" : "未检测到") + "\n"
                        + "协议引擎：" + (hasAuth ? "待接入" : "未配置认证，未启动"));
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
