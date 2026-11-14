package com.rockjesus.icarplay;

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * 连接补强页：把“没有外置路由器时，五种投屏方式各自走哪条路”讲清楚。
 * 只做能力判定和向导，不改系统网络配置、不抢默认桌面。
 */
public class LinkBoostActivity extends FitActivity {

    private TextView btMetric;
    private TextView usbMetric;
    private TextView wifiMetric;
    private TextView apMetric;
    private TextView summary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(PageUi.background(this));
        int pad = PageUi.dp(this, 18);
        root.setPadding(pad, PageUi.dp(this, 10), pad, PageUi.dp(this, 8));
        setFitContentView(root);

        root.addView(PageUi.header(this, "连接补强", IcarLauncherActivity.VERSION_LABEL),
                PageUi.matchWrap(this));

        TextView sub = PageUi.text(this, "没有外置路由器 · 无线 / 有线 / 适配器三条路",
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

        LinearLayout ability = PageUi.card(this, PageUi.CYAN, 12, 8);
        content.addView(ability, PageUi.matchWrap(this));
        ability.addView(PageUi.text(this, "本机能力", 8.5f, PageUi.CYAN, true),
                PageUi.wrap(this));

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams mlp = PageUi.matchWrap(this);
        PageUi.margin(mlp, this, 0, 5, 0, 0);
        ability.addView(metrics, mlp);
        btMetric = metric(metrics, "蓝牙", PageUi.PURPLE);
        usbMetric = metric(metrics, "USB Host", PageUi.BLUE);
        wifiMetric = metric(metrics, "Wi‑Fi 客户端", PageUi.GREEN);
        apMetric = metric(metrics, "自建热点", PageUi.GOLD);

        summary = PageUi.text(this, "—", 9.5f, PageUi.MUTED, false);
        summary.setPadding(0, PageUi.dp(this, 5), 0, 0);
        ability.addView(summary, PageUi.matchWrap(this));

        LinearLayout ways = PageUi.card(this, PageUi.PURPLE, 12, 8);
        LinearLayout.LayoutParams wlp = PageUi.matchWrap(this);
        PageUi.margin(wlp, this, 0, 8, 0, 0);
        content.addView(ways, wlp);
        ways.addView(PageUi.text(this, "投屏方式", 8.5f, PageUi.PURPLE, true),
                PageUi.wrap(this));

        way(ways, "AirPlay", "iPhone 无线，同一局域网即可", PageUi.BLUE, v -> {
            showDialog("AirPlay（iPhone）",
                    "结论：本机内置 AirPlay 接收端，iPhone 不需要任何适配器。\n"
                            + "无线：手机与车机连同一 Wi‑Fi / 热点，车机当客户端即可；\n"
                            + "也可以在连接中心开应用内热点，让 iPhone 连车机。\n"
                            + "有线：不需要，AirPlay 只走网络。\n"
                            + "前提：同网段、同一 Wi‑Fi 没开 AP 隔离；iPhone 控制中心点“屏幕镜像”。");
        });

        way(ways, "HUAWEI HiCar", "无线 / 有线都支持，但需车机侧支持", PageUi.CYAN, v -> {
            showDialog("HUAWEI HiCar",
                    "结论：HiCar 官方无线和有线两种都支持，但这两种都要“车机端”支持 HiCar；"
                            + "2020 款锐际 SYNC+ 没有原厂 HiCar，靠外接 HiCar 适配器（USB 盒子）才能用。\n\n"
                            + "无线：华为手机蓝牙配对 + 同一 Wi‑Fi，由 HiCar 适配器发起（适配器自带蓝牙天线）。\n"
                            + "有线：手机 USB 直连车机 USB 口，数据线必须支持传输，手机端要允许 USB 配件。\n"
                            + "本应用：内置 HiCar 接收入口，点首页 HiCar 卡或直接进 HiCar 页。");
        });

        way(ways, "Android Auto", "有线可直连，无线要适配器", PageUi.GOLD,
                v -> showDialog("Android Auto",
                        "结论：AA 原生无线需要车机 Wi‑Fi + 蓝牙双向握手，SYNC+ 不支持；"
                                + "有线可以直接走本应用内置的 AA 接收。\n"
                                + "有线：USB 数据线插车机 USB 口，手机装 Android Auto，首次连接允许调试/配件。\n"
                                + "无线：用第三方 USB 无线适配器（AAWireless 一类），适配器自己开 Wi‑Fi 给手机连。\n"
                                + "注意：AA 需要有线/USB 通道先通，USB 口供电不足会反复断连。"));

        way(ways, "CarLife+", "百度方案，有线优先", PageUi.GREEN, v -> {
            showDialog("CarLife+（安卓）",
                    "结论：CarLife+ 由百度 SDK 提供，本应用只负责入口和连线。\n"
                            + "有线：USB 数据线优先，最稳定。\n"
                            + "无线：需要车机热点或与手机同一 Wi‑Fi，由 SDK 建立通道；"
                            + "没有外置路由器时用手机热点。\n"
                            + "iPhone 请走 AirPlay，CarLife+ 主要面向安卓。");
        });

        way(ways, "DLNA 音频", "把车机当音箱，点开接收页", PageUi.PINK,
                v -> startActivity(new Intent(this, DlnaActivity.class)));

        way(ways, "Miracast", "系统级接收，普通 APK 装不了", PageUi.RED, v -> {
            showDialog("Miracast（Wi‑Fi Display）",
                    "结论：做 Miracast 接收端需要系统级 Wi‑Fi Display sink 权限，"
                            + "普通第三方 APK 拿不到，本机不支持接收。\n"
                            + "替代：安卓手机可用 DLNA 音频、或视频 App 自带投送；iPhone 用 AirPlay。\n"
                            + "如果你的车机以后刷到支持 Cast 的系统，这条才有意义。");
        });

        Button back = PageUi.back(this);
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams backLp = PageUi.fixed(this, 42);
        PageUi.margin(backLp, this, 0, 8, 0, 0);
        root.addView(back, backLp);

        refresh();
    }

    private TextView metric(LinearLayout row, String label, int accent) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(PageUi.dp(this, 6), PageUi.dp(this, 4),
                PageUi.dp(this, 6), PageUi.dp(this, 4));
        box.setBackground(PageUi.rounded(this, PageUi.blend(PageUi.CARD, accent, 0.08f),
                PageUi.blend(PageUi.BORDER, accent, 0.28f), 9));
        box.addView(PageUi.text(this, label, 7.5f, PageUi.MUTED, false), PageUi.wrap(this));
        TextView v = PageUi.text(this, "—", 10f, accent, true);
        v.setSingleLine(true);
        box.addView(v, PageUi.wrap(this));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        PageUi.margin(lp, this, 2.5f, 0, 2.5f, 0);
        row.addView(box, lp);
        return v;
    }

    private void way(LinearLayout card, String name, String status, int accent,
                     View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(PageUi.surfaceState(this, accent, 11));
        row.setPadding(PageUi.dp(this, 9), PageUi.dp(this, 3.5f),
                PageUi.dp(this, 9), PageUi.dp(this, 3.5f));
        LinearLayout.LayoutParams lp = PageUi.matchWrap(this);
        PageUi.margin(lp, this, 0, 4, 0, 0);
        row.setOnClickListener(listener);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.addView(PageUi.text(this, name, 10f, accent, true), PageUi.wrap(this));
        TextView st = PageUi.text(this, status, 8f, PageUi.MUTED, false);
        st.setSingleLine(true);
        info.addView(st, PageUi.matchWrap(this));
        row.addView(info, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView arrow = PageUi.text(this, "›", 15f, accent, true);
        row.addView(arrow, PageUi.wrap(this));
        card.addView(row, lp);
    }

    private void refresh() {
        if (btMetric == null) return;
        BluetoothAdapter adapter = null;
        try {
            BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            adapter = bm == null ? null : bm.getAdapter();
        } catch (Exception ignored) {
        }
        if (adapter == null) {
            btMetric.setText("不可用");
            btMetric.setTextColor(PageUi.MUTED);
        } else {
            boolean on = adapter.isEnabled();
            btMetric.setText(on ? "开" : "关");
            btMetric.setTextColor(on ? PageUi.GREEN : PageUi.RED);
        }

        boolean usbHost = getPackageManager()
                .hasSystemFeature("android.hardware.usb.host");
        UsbManager um = (UsbManager) getSystemService(Context.USB_SERVICE);
        int usbCount = 0;
        try {
            usbCount = um == null || um.getDeviceList() == null ? 0 : um.getDeviceList().size();
        } catch (Exception ignored) {
        }
        usbMetric.setText(usbHost ? (usbCount == 0 ? "支持 · 无设备" : "支持 · " + usbCount + " 台")
                : "未声明");
        usbMetric.setTextColor(usbHost ? PageUi.BLUE : PageUi.MUTED);

        WifiManager wm = (WifiManager) getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        boolean wifiOn = wm != null && wm.isWifiEnabled();
        String ssid = "未连接";
        if (wm != null) {
            WifiInfo info = wm.getConnectionInfo();
            String raw = info == null ? null : info.getSSID();
            if (raw != null) raw = raw.replace("\"", "");
            if (raw != null && !raw.contains("<unknown") && info != null
                    && info.getNetworkId() != -1) {
                ssid = raw;
            }
        }
        wifiMetric.setText(wifiOn ? "开" : "关");
        wifiMetric.setTextColor(wifiOn ? PageUi.GREEN : PageUi.MUTED);

        boolean apCap = Build.VERSION.SDK_INT >= 26;
        apMetric.setText(apCap ? "可试" : "不支持");
        apMetric.setTextColor(apCap ? PageUi.GOLD : PageUi.MUTED);

        boolean p2p = getPackageManager().hasSystemFeature("android.hardware.wifi.direct");
        Boolean five = fiveGhz(wm);
        String gateway = "—";
        if (wm != null) {
            DhcpInfo dhcp = wm.getDhcpInfo();
            if (dhcp != null && dhcp.gateway != 0) {
                int v = dhcp.gateway;
                gateway = String.format(Locale.US, "%d.%d.%d.%d",
                        v & 0xff, (v >> 8) & 0xff, (v >> 16) & 0xff, (v >> 24) & 0xff);
            }
        }
        summary.setText("当前 Wi‑Fi：" + ssid + " · 网关 " + gateway
                + "\nP2P " + (p2p ? "支持" : "无") + " · 5GHz "
                + (five == null ? "未声明" : (five ? "支持" : "不支持"))
                + " · 无线投屏不需要外网");
    }

    private static Boolean fiveGhz(WifiManager wm) {
        if (wm == null) return null;
        try {
            Method m = wm.getClass().getMethod("is5GHzBandSupported");
            Object result = m.invoke(wm);
            return result instanceof Boolean ? (Boolean) result : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 与主页同一套视觉语言：紫青渐变标题 + 彩虹细线 + 霓虹按钮，替换系统默认弹窗。 */
    private void showDialog(String title, String message) {
        final Dialog dlg = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xCC04050A);
        root.setOnClickListener(v -> dlg.dismiss());

        LinearLayout panel = PageUi.card(this, PageUi.CYAN, 20, 18);
        panel.setOnClickListener(v -> { });

        TextView titleView = PageUi.text(this, title, 18f, PageUi.TEXT, true);
        PageUi.gradientText(titleView, PageUi.TITLE_GRADIENT);
        panel.addView(titleView, PageUi.matchWrap(this));

        View line = new View(this);
        line.setBackground(PageUi.rainbow(this, 1.5f));
        LinearLayout.LayoutParams lineLp = PageUi.fixed(this, 3f);
        PageUi.margin(lineLp, this, 0, 10, 0, 12);
        panel.addView(line, lineLp);

        TextView body = PageUi.text(this, message, 12.5f, PageUi.blend(PageUi.TEXT, PageUi.MUTED, 0.82f), false);
        body.setLineSpacing(PageUi.dp(this, 3f), 1f);
        panel.addView(body, PageUi.matchWrap(this));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams barLp = PageUi.matchWrap(this);
        PageUi.margin(barLp, this, 0, 16, 0, 0);
        panel.addView(bar, barLp);

        Button net = PageUi.button(this, "连接中心", 12.5f, PageUi.PINK);
        net.setOnClickListener(v -> {
            dlg.dismiss();
            try {
                startActivity(new Intent(this, NetworkCenterActivity.class));
            } catch (Exception e) {
                Toast.makeText(this, "连接中心打不开", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams netLp = PageUi.weight(this, 1f);
        PageUi.margin(netLp, this, 0, 0, 5, 0);
        bar.addView(net, netLp);

        Button ok = PageUi.button(this, "知道了", 12.5f, PageUi.CYAN);
        ok.setOnClickListener(v -> dlg.dismiss());
        LinearLayout.LayoutParams okLp = PageUi.weight(this, 1f);
        PageUi.margin(okLp, this, 5, 0, 0, 0);
        bar.addView(ok, okLp);

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int panelW = Math.min(PageUi.dp(this, 620f), screenW - PageUi.dp(this, 120f));
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                panelW, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(panel, panelLp);
        dlg.setContentView(root);
        dlg.show();
    }
}
