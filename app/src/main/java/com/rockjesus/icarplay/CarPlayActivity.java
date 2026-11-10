package com.rockjesus.icarplay;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** CarPlay 接收页：有线 / 无线入口 + 认证状态。 */
public class CarPlayActivity extends Activity {

    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0E1216"));
        int pad = dp(22);
        root.setPadding(pad, dp(16), pad, dp(16));

        TextView title = new TextView(this);
        title.setText("CarPlay 接收");
        title.setTextColor(Color.parseColor("#E6EDF3"));
        title.setTextSize(20);
        root.addView(title, wrap());

        TextView sub = new TextView(this);
        sub.setText("iPhone → 锐际 SYNC+ · 有线 USB / 无线 Wi-Fi");
        sub.setTextColor(Color.parseColor("#8B949E"));
        sub.setTextSize(12);
        sub.setPadding(0, dp(6), 0, dp(12));
        root.addView(sub, wrap());

        statusView = new TextView(this);
        statusView.setTextColor(Color.parseColor("#C9D1D9"));
        statusView.setTextSize(13);
        statusView.setLineSpacing(dp(4), 1f);
        root.addView(statusView, wrap());

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.VERTICAL);
        buttons.setPadding(0, dp(14), 0, 0);

        buttons.addView(makeButton("有线连接（USB）", new View.OnClickListener() {
            public void onClick(View v) {
                toast(CarPlayCore.startWired(CarPlayActivity.this));
            }
        }), wrap());

        buttons.addView(makeButton("无线连接（Wi-Fi）", new View.OnClickListener() {
            public void onClick(View v) {
                toast(CarPlayCore.startWireless(CarPlayActivity.this));
            }
        }), wrap());

        buttons.addView(makeButton("检查认证材料", new View.OnClickListener() {
            public void onClick(View v) {
                if (CarPlayCore.hasLocalIdentity(CarPlayActivity.this)) {
                    toast("已找到 identity.pk8 + certificate.p7b");
                } else {
                    toast("未找到本地证书。" + CarPlayCore.identityHint(CarPlayActivity.this));
                }
                refresh();
            }
        }), wrap());

        buttons.addView(makeButton("认证与连接设置", new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(CarPlayActivity.this, AuthSettingsActivity.class));
            }
        }), wrap());

        buttons.addView(makeButton("返回主界面", new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        }), wrap());

        root.addView(buttons, wrap());

        TextView note = new TextView(this);
        note.setText("\n当前状态：\n"
                + "· CarPlay 协议骨架已接入（链路、认证后端、USB 枚举）；\n"
                + "· 真正的 iAP2 / MFi / AirPlay 隧道尚未接入；\n"
                + "· 没有 Apple 认证身份时，iPhone 不会完成配对；\n"
                + "· 本地证书需 DER 编码的 PKCS#7 证书与未加密 PKCS#8 私钥。");
        note.setTextColor(Color.parseColor("#8B949E"));
        note.setTextSize(11);
        root.addView(note, wrap());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
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
        statusView.setText(
                "认证后端：" + CarPlayCore.authLabel(this) + "\n"
                        + "认证可用：" + (hasAuth ? "是" : "否") + "\n"
                        + "本地证书：" + (local ? "已找到" : "未找到") + "\n"
                        + "Apple USB 设备：" + (usb > 0 ? usb + " 个" : "未检测到") + "\n"
                        + "协议引擎：" + (hasAuth ? "待接入" : "未配置认证，未启动"));
    }

    private Button makeButton(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setGravity(Gravity.CENTER);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = wrap();
        lp.bottomMargin = dp(7);
        b.setLayoutParams(lp);
        return b;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
