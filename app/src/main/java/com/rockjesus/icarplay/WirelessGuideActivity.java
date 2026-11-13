package com.rockjesus.icarplay;

import android.app.Activity;
import android.content.Intent;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
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

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * 一键无线投屏向导：手机开热点 → 车机加入 → 打开对应接收页，三步收在一页。
 * 只读网络状态和跳系统页面，不自己改 Wi‑Fi、不抢默认桌面。
 */
public class WirelessGuideActivity extends Activity {

    private TextView ssidValue;
    private TextView ipValue;
    private TextView bandValue;
    private TextView gatewayValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(PageUi.background(this));
        int pad = PageUi.dp(this, 18);
        root.setPadding(pad, PageUi.dp(this, 10), pad, PageUi.dp(this, 8));
        setContentView(root);

        root.addView(PageUi.header(this, "一键无线投屏", IcarLauncherActivity.VERSION_LABEL),
                PageUi.matchWrap(this));

        TextView sub = PageUi.text(this,
                "手机开热点 → 车机加入 → 打开接收页 · 没有外置路由器也能投",
                10.5f, PageUi.MUTED, false);
        sub.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams subLp = PageUi.matchWrap(this);
        PageUi.margin(subLp, this, 0, 3, 0, 7);
        root.addView(sub, subLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, PageUi.matchWrap(this));

        // 第 1 步：确认网络
        LinearLayout step1 = PageUi.card(this, PageUi.GREEN, 12, 6);
        content.addView(step1, PageUi.matchWrap(this));
        step1.addView(stepHeader("第 1 步 · 确认车机网络", "当前链路", PageUi.GREEN),
                PageUi.matchWrap(this));

        LinearLayout rowA = new LinearLayout(this);
        rowA.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowAlp = PageUi.matchWrap(this);
        PageUi.margin(rowAlp, this, 0, 3, 0, 0);
        ssidValue = metric(rowA, "当前 Wi‑Fi", "—", PageUi.CYAN);
        ipValue = metric(rowA, "本机 IP", "—", PageUi.BLUE);
        bandValue = metric(rowA, "频段", "—", PageUi.GOLD);
        gatewayValue = metric(rowA, "网关", "—", PageUi.GREEN);
        step1.addView(rowA, rowAlp);

        LinearLayout step1Buttons = new LinearLayout(this);
        step1Buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams s1blp = PageUi.matchWrap(this);
        PageUi.margin(s1blp, this, 0, 4, 0, 0);
        step1.addView(step1Buttons, s1blp);
        step1Buttons.addView(small("打开 Wi‑Fi 设置", PageUi.BLUE, v -> openWifi()),
                weightSmall());
        step1Buttons.addView(small("网络体检", PageUi.CYAN,
                v -> open(NetworkCenterActivity.class, "连接中心打不开")), weightSmall());

        // 第 2 步：手机热点
        LinearLayout step2 = PageUi.card(this, PageUi.PURPLE, 12, 6);
        LinearLayout.LayoutParams s2lp = PageUi.matchWrap(this);
        PageUi.margin(s2lp, this, 0, 6, 0, 0);
        content.addView(step2, s2lp);
        step2.addView(stepHeader("第 2 步 · 手机开热点（车机加入）", "推荐", PageUi.PURPLE),
                PageUi.matchWrap(this));
        TextView tips = PageUi.text(this,
                "iPhone：设置 → 个人热点 → 打开，顺手关掉“最大兼容性”更容易拿到 5GHz\n"
                        + "安卓：设置 → 个人热点 → 打开，频段选 5GHz；车机连上后本页 Wi‑Fi 会变成热点名",
                9.5f, PageUi.MUTED, false);
        tips.setLineSpacing(PageUi.dp(this, 2f), 1f);
        tips.setPadding(0, PageUi.dp(this, 3), 0, 0);
        step2.addView(tips, PageUi.matchWrap(this));

        LinearLayout step2Buttons = new LinearLayout(this);
        step2Buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams s2blp = PageUi.matchWrap(this);
        PageUi.margin(s2blp, this, 0, 4, 0, 0);
        step2.addView(step2Buttons, s2blp);
        step2Buttons.addView(small("打开热点设置", PageUi.PURPLE, v -> openHotspot()), weightSmall());
        step2Buttons.addView(small("连接补强", PageUi.CYAN,
                v -> open(LinkBoostActivity.class, "连接补强打不开")), weightSmall());

        // 第 3 步：选投送方式
        LinearLayout step3 = PageUi.card(this, PageUi.CYAN, 12, 6);
        LinearLayout.LayoutParams s3lp = PageUi.matchWrap(this);
        PageUi.margin(s3lp, this, 0, 6, 0, 0);
        content.addView(step3, s3lp);
        step3.addView(stepHeader("第 3 步 · 打开对应接收页", "看手机品牌选", PageUi.CYAN),
                PageUi.matchWrap(this));

        LinearLayout sendRow1 = new LinearLayout(this);
        sendRow1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams sr1 = PageUi.matchWrap(this);
        PageUi.margin(sr1, this, 0, 4, 0, 0);
        step3.addView(sendRow1, sr1);
        sendRow1.addView(small("AirPlay（iPhone）", PageUi.BLUE,
                v -> open(AirPlayActivity.class, "AirPlay 页打不开")), weightSmall());
        sendRow1.addView(small("HUAWEI HiCar", PageUi.RED,
                v -> openHicar()), weightSmall());

        LinearLayout sendRow2 = new LinearLayout(this);
        sendRow2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams sr2 = PageUi.matchWrap(this);
        PageUi.margin(sr2, this, 0, 4, 0, 0);
        step3.addView(sendRow2, sr2);
        sendRow2.addView(small("CarLife+（安卓）", PageUi.GREEN,
                v -> openCarlife()), weightSmall());
        sendRow2.addView(small("DLNA 音视频", PageUi.GOLD,
                v -> open(DlnaActivity.class, "DLNA 页打不开")), weightSmall());

        // 提示
        LinearLayout noteCard = PageUi.card(this, PageUi.GOLD, 14, 8);
        LinearLayout.LayoutParams nlp = PageUi.matchWrap(this);
        PageUi.margin(nlp, this, 0, 6, 0, 0);
        content.addView(noteCard, nlp);
        noteCard.addView(PageUi.text(this, "走不通时按这个顺序排查", 9f, PageUi.GOLD, true),
                PageUi.wrap(this));
        TextView note = PageUi.text(this,
                "车机必须连上手机热点并拿到 IP 才算通；iPhone 投屏遇 NAT 隔离时用 DLNA 兜底；"
                        + "自建热点只作备选。",
                9.5f, PageUi.MUTED, false);
        note.setPadding(0, PageUi.dp(this, 3), 0, 0);
        noteCard.addView(note, PageUi.matchWrap(this));

        Button back = PageUi.back(this);
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams backLp = PageUi.fixed(this, 38);
        PageUi.margin(backLp, this, 0, 6, 0, 0);
        root.addView(back, backLp);

        refresh();
    }

    private LinearLayout stepHeader(String title, String pill, int accent) {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(PageUi.text(this, title, 9f, accent, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(PageUi.statusPill(this, pill, accent), PageUi.wrap(this));
        return head;
    }

    private TextView metric(LinearLayout row, String label, String value, int accent) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(PageUi.dp(this, 7), PageUi.dp(this, 4),
                PageUi.dp(this, 7), PageUi.dp(this, 4));
        box.setBackground(PageUi.rounded(this, PageUi.blend(PageUi.CARD, accent, 0.08f),
                PageUi.blend(PageUi.BORDER, accent, 0.28f), 9));
        box.addView(PageUi.text(this, label, 8f, PageUi.MUTED, false), PageUi.wrap(this));
        TextView v = PageUi.text(this, value, 10.5f, accent, true);
        v.setSingleLine(true);
        box.addView(v, PageUi.wrap(this));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        PageUi.margin(lp, this, 3, 0, 3, 0);
        row.addView(box, lp);
        return v;
    }

    private Button small(String label, int accent, View.OnClickListener click) {
        Button b = PageUi.button(this, label, 10f, accent);
        b.setOnClickListener(click);
        return b;
    }

    private LinearLayout.LayoutParams weightSmall() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, PageUi.dp(this, 32), 1f);
        lp.setMargins(PageUi.dp(this, 3), 0, PageUi.dp(this, 3), 0);
        return lp;
    }

    private void openWifi() {
        try {
            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        } catch (Exception e) {
            toast("Wi‑Fi 设置打不开");
        }
    }

    private void openHotspot() {
        try {
            startActivity(new Intent("android.settings.TETHER_SETTINGS"));
        } catch (Exception ignored) {
            try {
                startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
            } catch (Exception e) {
                toast("热点设置打不开");
            }
        }
    }

    private void openHicar() {
        try {
            startActivity(new Intent(this, Class.forName("local.hicarlab.HomeActivity")));
        } catch (Exception e) {
            toast("HiCar 组件未就绪");
        }
    }

    private void openCarlife() {
        try {
            startActivity(new Intent(this,
                    Class.forName("com.rockjesus.icarplay.carlife.CarLifePlusActivity")));
        } catch (Exception e) {
            toast("CarLife+ 页打不开");
        }
    }

    private void open(Class<?> target, String failure) {
        try {
            startActivity(new Intent(this, target));
        } catch (Exception e) {
            toast(failure);
        }
    }

    private void refresh() {
        if (ssidValue == null) return;
        WifiManager wm = (WifiManager) getApplicationContext()
                .getSystemService(WIFI_SERVICE);
        String ssid = "未连接";
        String ip = "—";
        String band = "—";
        String gateway = "—";
        boolean ap = false;
        if (wm != null) {
            try {
                WifiInfo info = wm.getConnectionInfo();
                if (info != null && info.getSSID() != null) {
                    String raw = info.getSSID().replace("\"", "");
                    if (!raw.isEmpty() && !"<unknown ssid>".equals(raw)) ssid = raw;
                }
                int ipInt = wm.getDhcpInfo() == null ? 0 : wm.getDhcpInfo().ipAddress;
                if (ipInt != 0) ip = ipv4(ipInt);
                DhcpInfo dhcp = wm.getDhcpInfo();
                if (dhcp != null && dhcp.gateway != 0) gateway = ipv4(dhcp.gateway);
                int freq = info == null ? 0 : info.getFrequency();
                if (freq >= 5000) band = "5GHz · " + freq + "MHz";
                else if (freq > 0) band = "2.4GHz · " + freq + "MHz";
                ap = (Boolean) call(wm, "isWifiApEnabled");
            } catch (Exception ignored) {
            }
        }
        ssidValue.setText(ssid);
        ipValue.setText(ip);
        bandValue.setText(band);
        gatewayValue.setText(gateway);
    }

    private static Object call(WifiManager wm, String method) {
        try {
            Method m = WifiManager.class.getMethod(method);
            return m.invoke(wm);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String ipv4(int value) {
        return String.format(Locale.US, "%d.%d.%d.%d",
                value & 0xff, (value >> 8) & 0xff, (value >> 16) & 0xff, (value >> 24) & 0xff);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }
}
