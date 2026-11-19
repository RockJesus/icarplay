package com.rockjesus.icarplay;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Set;

/**
 * 无线 CarPlay 向导：三种无线模式的操作路径 + 前置条件实测。
 *
 * <p>无线 CarPlay 的音视频全走 Wi‑Fi，蓝牙只负责配对与唤醒。车机 API 27（Android 8.1）
 * 上 Wi‑Fi Direct 属于固件相关的旧路径，成功率最低，所以这里把「iPhone 热点 → 车机连入」
 * 排在第一位，并明确标注每条路径的把握程度。
 */
public class CarPlayWirelessActivity extends FitActivity {

    private TextView btValue;
    private TextView wifiValue;
    private TextView bandValue;
    private TextView p2pValue;
    private TextView engineValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(PageUi.background(this));
        root.setPadding(PageUi.dp(this, 16), PageUi.dp(this, 10),
                PageUi.dp(this, 16), PageUi.dp(this, 8));
        setFitContentView(root);

        root.addView(PageUi.header(this, "无线 CarPlay 向导", "蓝牙配对，Wi‑Fi 传音视频 · 8.1 车机优先 iPhone 热点", IcarLauncherActivity.VERSION_LABEL), PageUi.matchWrap(this));

        // 前置条件实测
        LinearLayout checkCard = PageUi.card(this, PageUi.GREEN, 12, 10);
        checkCard.addView(PageUi.text(this, "前置条件（实测）", 9.5f, PageUi.GREEN, true),
                PageUi.matchWrap(this));

        LinearLayout checkList = new LinearLayout(this);
        checkList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams checkLp = PageUi.matchWrap(this);
        PageUi.margin(checkLp, this, 0, 6, 0, 0);
        btValue = addCheck(checkList, "蓝牙配对");
        wifiValue = addCheck(checkList, "Wi‑Fi 开关");
        bandValue = addCheck(checkList, "当前网络 / 频段");
        p2pValue = addCheck(checkList, "Wi‑Fi Direct");
        engineValue = addCheck(checkList, "CarPlay 接收引擎");
        checkCard.addView(checkList, checkLp);

        LinearLayout checkBtns = new LinearLayout(this);
        checkBtns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams cbLp = PageUi.matchWrap(this);
        PageUi.margin(cbLp, this, 0, 8, 0, 0);
        checkBtns.addView(PageUi.button(this, "重新检测", 10f, PageUi.CYAN), PageUi.weight(this, 1));
        Button wifiSet = PageUi.button(this, "Wi‑Fi 设置", 10f, PageUi.BLUE);
        wifiSet.setOnClickListener(v -> openSettings(Settings.ACTION_WIFI_SETTINGS));
        LinearLayout.LayoutParams wsLp = PageUi.weight(this, 1);
        PageUi.margin(wsLp, this, 8, 0, 8, 0);
        checkBtns.addView(wifiSet, wsLp);
        Button btSet = PageUi.button(this, "蓝牙配对", 10f, PageUi.GOLD);
        btSet.setOnClickListener(v -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS));
        checkBtns.addView(btSet, PageUi.weight(this, 1));
        checkBtns.getChildAt(0).setOnClickListener(v -> {
            refresh();
            toast("已重新检测");
        });
        checkCard.addView(checkBtns, cbLp);
        root.addView(checkCard, PageUi.matchWrap(this));

        // 三种模式
        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams modesLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        PageUi.margin(modesLp, this, 0, 8, 0, 0);
        root.addView(modes, modesLp);

        LinearLayout a = modeCard(
                "① iPhone 热点", PageUi.GREEN, "推荐 · 成功率最高",
                "1. iPhone 打开「个人热点」，关闭「最大兼容性」争取 5GHz\n"
                        + "2. 车机 Wi‑Fi 连到该热点\n"
                        + "3. 接收画面里选「Existing Wi‑Fi / Same LAN」\n"
                        + "4. iPhone 上确认允许连接");
        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        PageUi.margin(aLp, this, 0, 0, 5, 0);
        modes.addView(a, aLp);

        LinearLayout b = modeCard(
                "② 车机热点", PageUi.GOLD, "中等 · 取决于固件",
                "1. 车机系统设置开 WPA2 热点（8–63 位密码）\n"
                        + "2. iPhone 连到该热点\n"
                        + "3. 接收画面自动读热点名/密码，失败时手动填\n"
                        + "4. 开热点时别同时连别的无线网络");
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        PageUi.margin(bLp, this, 5, 0, 5, 0);
        modes.addView(b, bLp);

        LinearLayout c = modeCard(
                "③ Wi‑Fi Direct", PageUi.RED, "最低 · 8.1 未经验证",
                "1. 两端蓝牙先配对\n"
                        + "2. 接收端发起 P2P，自己生成组密钥\n"
                        + "3. 8.1 走固件相关旧路径，频点未校验\n"
                        + "4. 前两条都失败再试这个");
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        PageUi.margin(cLp, this, 5, 0, 0, 0);
        modes.addView(c, cLp);

        // 底部动作
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actLp = PageUi.matchWrap(this);
        PageUi.margin(actLp, this, 0, 8, 0, 0);

        Button launch = PageUi.button(this, "开始 CarPlay 接收", 10.5f, PageUi.GREEN);
        launch.setOnClickListener(v -> {
            if (!DiPlayBridge.isInstalled(this)) {
                toast("接收模块缺失，正常安装包自带");
                return;
            }
            toast(DiPlayBridge.launchSafely(this));
        });
        Button hotspot = PageUi.button(this, "无线配置", 10.5f, PageUi.GOLD);
        hotspot.setOnClickListener(v -> showHotspotDialog());
        LinearLayout.LayoutParams hsLp = PageUi.weight(this, 1);
        PageUi.margin(hsLp, this, 6, 0, 0, 0);
        actions.addView(launch, PageUi.weight(this, 1.2f));
        actions.addView(hotspot, hsLp);

        Button back = PageUi.back(this);
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams backLp = PageUi.weight(this, 0.8f);
        PageUi.margin(backLp, this, 8, 0, 0, 0);
        actions.addView(back, backLp);
        root.addView(actions, actLp);

        refresh();
    }

    private LinearLayout modeCard(String title, int accent, String badge, String steps) {
        LinearLayout card = PageUi.card(this, accent, 12, 10);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.addView(PageUi.text(this, title, 10.5f, accent, true), PageUi.matchWrap(this));
        TextView badgeView = PageUi.text(this, badge, 8.5f, PageUi.MUTED, false);
        LinearLayout.LayoutParams badgeLp = PageUi.matchWrap(this);
        PageUi.margin(badgeLp, this, 0, 2, 0, 0);
        head.addView(badgeView, badgeLp);
        card.addView(head, PageUi.matchWrap(this));

        TextView body = PageUi.text(this, steps, 9f, PageUi.TEXT, false);
        body.setLineSpacing(PageUi.dp(this, 3f), 1f);
        LinearLayout.LayoutParams bodyLp = PageUi.matchWrap(this);
        PageUi.margin(bodyLp, this, 0, 6, 0, 0);
        card.addView(body, bodyLp);
        return card;
    }

    private TextView addCheck(LinearLayout parent, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = PageUi.matchWrap(this);
        PageUi.margin(rowLp, this, 0, 3, 0, 0);
        row.setLayoutParams(rowLp);

        row.addView(PageUi.text(this, label, 9.5f, PageUi.TEXT, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = PageUi.text(this, "检测中", 9.5f, PageUi.MUTED, true);
        value.setGravity(Gravity.END);
        value.setSingleLine(true);
        row.addView(value, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        parent.addView(row);
        return value;
    }


    /** 直接把车机热点名/密码写进内置引擎，省得每次进引擎里手填。 */
    private void showHotspotDialog() {
        final int pad = PageUi.dp(this, 16);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, PageUi.dp(this, 10), pad, 0);

        final EditText ssid = new EditText(this);
        ssid.setHint("车机热点名 / Wi-Fi 名（≤32 字节）");
        String cur = DiPlayBridge.enginePrefs(this).getString("manual_hotspot_ssid", "");
        if (cur == null) cur = "";
        if (cur.isEmpty()) {
            String live = currentWifiSsid();
            if (live != null) cur = live;
        }
        ssid.setText(cur);
        box.addView(ssid);

        final EditText pass = new EditText(this);
        pass.setHint("密码（WPA2，8–63 位；开放热点留空）");
        pass.setText(DiPlayBridge.enginePrefs(this).getString("manual_hotspot_passphrase", ""));
        box.addView(pass);

        final CheckBox sameLan = new CheckBox(this);
        sameLan.setText("同网模式：车机与 iPhone 连同一个路由器，不用车机开热点");
        sameLan.setChecked("EXISTING_WIFI".equals(DiPlayBridge.engineMode(this)));
        box.addView(sameLan);

        new AlertDialog.Builder(this)
                .setTitle("无线 CarPlay 配置")
                .setView(box)
                .setPositiveButton("保存并启用无线", (d, w) -> {
                    String s1 = ssid.getText().toString().trim();
                    String s2 = pass.getText().toString();
                    if (s1.isEmpty()) {
                        toast("热点名不能为空，否则接收画面会直接闪退");
                        return;
                    }
                    if (s1.getBytes().length > 32) {
                        toast("热点名太长（超过 32 字节）");
                        return;
                    }
                    if (!s2.isEmpty() && (s2.length() < 8 || s2.length() > 63)) {
                        toast("密码需 8–63 位，或留空用开放热点");
                        return;
                    }
                    if (sameLan.isChecked()) DiPlayBridge.useExistingWifi(this, s1, s2);
                    else DiPlayBridge.useCarHotspot(this, s1, s2);
                    toast("已保存，点「开始 CarPlay 接收」走无线");
                    refresh();
                })
                .setNeutralButton("改回有线", (d, w) -> {
                    DiPlayBridge.useWired(this);
                    toast("已切回有线（USB）");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String currentWifiSsid() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return null;
            WifiInfo info = wm.getConnectionInfo();
            if (info == null) return null;
            String raw = info.getSSID();
            if (raw == null || raw.isEmpty() || "<unknown ssid>".equals(raw)) return null;
            return raw.replace("\"", "");
        } catch (Throwable e) {
            return null;
        }
    }

    private void refresh() {
        boolean btOn = false;
        boolean iphoneBonded = false;
        String bondedName = null;
        try {
            BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter ba = bm == null ? null : bm.getAdapter();
            btOn = ba != null && ba.isEnabled();
            if (btOn) {
                Set<BluetoothDevice> bonded = ba.getBondedDevices();
                if (bonded != null) {
                    for (BluetoothDevice d : bonded) {
                        String n = d.getName();
                        if (n != null && (n.toLowerCase().contains("iphone")
                                || n.toLowerCase().contains("ipad"))) {
                            iphoneBonded = true;
                            bondedName = n;
                            break;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        if (!btOn) {
            btValue.setText("蓝牙未开启");
            btValue.setTextColor(PageUi.RED);
        } else if (iphoneBonded) {
            btValue.setText("已配对 " + bondedName);
            btValue.setTextColor(PageUi.GREEN);
        } else {
            btValue.setText("已开启 · 未见 iPhone");
            btValue.setTextColor(PageUi.GOLD);
        }

        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        boolean wifiOn = false;
        try {
            wifiOn = wm != null && wm.isWifiEnabled();
        } catch (Throwable ignored) {
        }
        wifiValue.setText(wifiOn ? "已开启" : "未开启");
        wifiValue.setTextColor(wifiOn ? PageUi.GREEN : PageUi.RED);

        String ssid = null;
        int freq = 0;
        try {
            WifiInfo info = wm == null ? null : wm.getConnectionInfo();
            if (info != null) {
                String raw = info.getSSID();
                if (raw != null && !raw.isEmpty() && !"<unknown ssid>".equals(raw)) {
                    ssid = raw.replace("\"", "");
                }
                freq = info.getFrequency();
            }
        } catch (Throwable ignored) {
        }
        if (ssid == null) {
            bandValue.setText("未连接 Wi‑Fi");
            bandValue.setTextColor(PageUi.GOLD);
        } else {
            String band = freq >= 4900 && freq <= 5900 ? "5GHz" : (freq > 0 ? "2.4GHz" : "未知");
            bandValue.setText(ssid + " · " + band);
            bandValue.setTextColor(band.equals("5GHz") ? PageUi.GREEN : PageUi.GOLD);
        }

        boolean p2p = getPackageManager() != null
                && getPackageManager().hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_WIFI_DIRECT);
        p2pValue.setText(p2p ? "支持" : "不支持");
        p2pValue.setTextColor(p2p ? PageUi.GREEN : PageUi.RED);

        boolean diplay = DiPlayBridge.isInstalled(this);
        engineValue.setText(diplay ? "DiPlay " + DiPlayBridge.version(this) : "未安装");
        engineValue.setTextColor(diplay ? PageUi.GREEN : PageUi.RED);
    }

    private void openSettings(String action) {
        try {
            startActivity(new Intent(action));
        } catch (Throwable e) {
            toast("车机没有这个系统设置入口，请在原生设置里手动操作");
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
