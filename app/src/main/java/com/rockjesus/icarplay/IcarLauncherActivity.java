package com.rockjesus.icarplay;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import io.github.jqssun.airplay.MainActivity;

/**
 * iCarPlay 统一入口。
 *
 * 一个 APK 内包含：
 *  - AirPlay 接收（上游 UxPlay 协议栈）
 *  - CarPlay 接收入口（协议骨架 + 认证后端，真正握手待 MFi 材料）
 *  - 内置小白点（FloatService）
 */
public class IcarLauncherActivity extends Activity {

    private TextView statusView;
    private Button floatButton;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("icarplay", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0E1216"));
        int pad = dp(22);
        root.setPadding(pad, dp(16), pad, dp(16));

        TextView title = new TextView(this);
        title.setText("iCarPlay");
        title.setTextColor(Color.parseColor("#E6EDF3"));
        title.setTextSize(22);
        root.addView(title, wrap());

        TextView sub = new TextView(this);
        sub.setText("2020 福特锐际 · SYNC+ 1.0 · Android 8.1\n"
                + "AirPlay + Android Auto + CarPlay + 内置小白点，一个 APK");
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

        buttons.addView(makeButton("AirPlay 接收（镜像 / 音频）", new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(IcarLauncherActivity.this, MainActivity.class));
            }
        }), wrap());

        buttons.addView(makeButton("CarPlay 接收（有线 / 无线）", new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(IcarLauncherActivity.this, CarPlayActivity.class));
            }
        }), wrap());

        buttons.addView(makeButton("Android Auto 接收（USB / 无线）", new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(IcarLauncherActivity.this, AndroidAutoActivity.class));
            }
        }), wrap());

        floatButton = makeButton("开启内置小白点", new View.OnClickListener() {
            public void onClick(View v) {
                if (!Settings.canDrawOverlays(IcarLauncherActivity.this)) {
                    toast("先授权：显示在上层 / 悬浮窗");
                    openOverlayPermission();
                    return;
                }
                startService(new Intent(IcarLauncherActivity.this, FloatService.class));
                prefs.edit().putBoolean("float_running", true).apply();
                refresh();
            }
        });
        buttons.addView(floatButton, wrap());

        buttons.addView(makeButton("关闭内置小白点", new View.OnClickListener() {
            public void onClick(View v) {
                stopService(new Intent(IcarLauncherActivity.this, FloatService.class));
                prefs.edit().putBoolean("float_running", false).apply();
                refresh();
            }
        }), wrap());

        buttons.addView(makeButton("授予悬浮窗权限", new View.OnClickListener() {
            public void onClick(View v) { openOverlayPermission(); }
        }), wrap());

        buttons.addView(makeButton("认证与连接设置", new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(IcarLauncherActivity.this, AuthSettingsActivity.class));
            }
        }), wrap());

        buttons.addView(makeButton("这个版本能做什么", new View.OnClickListener() {
            public void onClick(View v) {
                toast("AirPlay 已接入；CarPlay 协议骨架已接入，认证材料仍需自备。");
            }
        }), wrap());

        root.addView(buttons, wrap());

        TextView note = new TextView(this);
        note.setText("\n说明：\n"
                + "· 悬浮球由本 APK 自带，不需要第二个小白点 App；\n"
                + "· 默认不注册开机自启，避免 SYNC+ 重启循环；\n"
                + "· AirPlay 不需要 MFi，iOS 可以直接发现并镜像；\n"
                + "· Android Auto 有线走 USB AOA，无线需手机开启 Headunit Server；\n"
                + "· CarPlay 需要 Apple MFi 认证身份，没有材料无法完成握手。");
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
        boolean overlay = Settings.canDrawOverlays(this);
        boolean running = prefs.getBoolean("float_running", false);
        String auth = CarPlayCore.authLabel(this);
        int usb = CarPlayCore.appleUsbCount(this);

        statusView.setText(
                "设备：" + Build.MANUFACTURER + " " + Build.MODEL
                        + "  ·  Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n"
                        + "悬浮窗权限：" + (overlay ? "已授予" : "未授予") + "\n"
                        + "内置小白点：" + (running ? "运行中" : "未运行") + "\n"
                        + "AirPlay 服务：" + (isAirPlayServiceRunning() ? "运行中" : "未运行") + "\n"
                        + "CarPlay 认证：" + auth + "\n"
                        + "Apple USB 设备：" + (usb > 0 ? usb + " 个" : "未检测到") + "\n"
                        + "Android Auto：" + (isAndroidAutoServiceRunning() ? "服务运行中" : "未运行"));

        floatButton.setText(running ? "重启内置小白点" : "开启内置小白点");
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
