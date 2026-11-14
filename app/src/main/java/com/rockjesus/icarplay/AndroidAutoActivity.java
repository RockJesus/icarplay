package com.rockjesus.icarplay;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.jqssun.airplay.R;
import info.anodsplace.headunit.App;
import info.anodsplace.headunit.aap.AapProjectionActivity;
import info.anodsplace.headunit.aap.AapService;
import info.anodsplace.headunit.connection.UsbAccessoryMode;

/**
 * Android Auto 接收端入口。
 *
 * 有线：USB AOA，把手机切到 accessory 模式后由 AapService 走 AAP/TLS/H.264。
 * 无线：手机端启动 Android Auto 的 Headunit Server，车机通过 Wi-Fi 连 5277 端口。
 *
 * 协议层来自 harryjph/android-auto-headunit（AGPL-3.0），不含 Google 私有应用。
 */
public class AndroidAutoActivity extends FitActivity {

    private static final String ACTION_USB_PERMISSION = "com.rockjesus.icarplay.USB_PERMISSION";
    private static final String CONNECTED_ACTION = "info.anodsplace.info.headunit.ACTION_CONNECTED";
    private static final String DISCONNECT_ACTION = "info.anodsplace.info.headunit.ACTION_DISCONNECT";
    private static final int REQ_PERMISSIONS = 41;

    private TextView status;
    private TextView statusPill;
    private TextView lanView;
    private UsbManager usbManager;
    private boolean waitingForAccessory;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

            if (ACTION_USB_PERMISSION.equals(action)) {
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                if (granted && device != null) {
                    startForDevice(device);
                } else {
                    setStatus("USB 权限被拒绝。请重新插拔数据线后再试。");
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action) && device != null) {
                if (isAccessory(device)) {
                    startAap(device);
                } else if (waitingForAccessory) {
                    setStatus("手机仍以普通模式枚举，未进入 Android Auto 配件模式。\n"
                            + "请确认 USB 口支持数据，并在手机上允许 USB 调试/文件传输。");
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                setStatus("USB 已断开。");
            } else if (CONNECTED_ACTION.equals(action)) {
                setStatus("Android Auto 已连接，正在打开投影画面…");
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        openProjection();
                    }
                }, 400);
            } else if (DISCONNECT_ACTION.equals(action)) {
                setStatus("Android Auto 已断开。");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(PageUi.background(this));
        root.setPadding(PageUi.dp(this, 18), PageUi.dp(this, 10),
                PageUi.dp(this, 18), PageUi.dp(this, 8));
        setFitContentView(root);

        root.addView(PageUi.header(this, "Android Auto 接收", IcarLauncherActivity.VERSION_LABEL),
                PageUi.matchWrap(this));

        TextView sub = PageUi.text(this, "USB 有线 / 手机 Headunit Server 无线", 11f,
                PageUi.MUTED, false);
        sub.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams subLp = PageUi.matchWrap(this);
        PageUi.margin(subLp, this, 0, 3, 0, 7);
        root.addView(sub, subLp);

        LinearLayout statusCard = PageUi.card(this, PageUi.GREEN, 14, 9);
        root.addView(statusCard, PageUi.matchWrap(this));
        LinearLayout statusHead = new LinearLayout(this);
        statusHead.setOrientation(LinearLayout.HORIZONTAL);
        statusHead.setGravity(Gravity.CENTER_VERTICAL);
        statusHead.addView(PageUi.text(this, "连接状态", 8.5f, PageUi.GREEN, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        statusPill = PageUi.statusPill(this, "待连接", PageUi.GREEN);
        statusHead.addView(statusPill, PageUi.wrap(this));
        statusCard.addView(statusHead, PageUi.matchWrap(this));
        status = PageUi.text(this, "未连接。请用 USB 数据线把手机接到车机 USB 口，或在手机端启动 Headunit Server。",
                12.5f, PageUi.TEXT, false);
        status.setPadding(0, PageUi.dp(this, 3), 0, PageUi.dp(this, 5));
        statusCard.addView(status, PageUi.matchWrap(this));

        LinearLayout netRow = new LinearLayout(this);
        netRow.setOrientation(LinearLayout.HORIZONTAL);
        netRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView netLabel = PageUi.text(this, "本机网络", 8.5f, PageUi.GOLD, true);
        netRow.addView(netLabel, PageUi.wrap(this));
        lanView = PageUi.text(this, PageUi.localNetwork(this), 9.5f, PageUi.MUTED, false);
        lanView.setPadding(PageUi.dp(this, 8), 0, 0, 0);
        netRow.addView(lanView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        statusCard.addView(netRow, PageUi.matchWrap(this));

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setMinimumHeight(PageUi.dp(this, 96));
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        PageUi.margin(gridLp, this, 0, 8, 0, 0);
        root.addView(grid, gridLp);

        Button usb = PageUi.button(this, "连接 USB 手机", 14f, PageUi.BLUE);
        usb.setOnClickListener(v -> chooseUsbDevice());
        Button wifi = PageUi.button(this, "无线连接", 14f, PageUi.GREEN);
        wifi.setOnClickListener(v -> showWifiDialog());
        Button projection = PageUi.button(this, "打开投影画面", 14f, PageUi.CYAN);
        projection.setOnClickListener(v -> openProjection());
        Button stop = PageUi.button(this, "停止 Android Auto", 14f, PageUi.RED);
        stop.setOnClickListener(v -> {
            stopService(new Intent(AndroidAutoActivity.this, AapService.class));
            setStatus("Android Auto 已停止。");
        });
        grid.addView(row(usb, wifi), rowLp(0));
        grid.addView(row(projection, stop), rowLp(6));

        TextView note = PageUi.text(this,
                "有线：手机安装 Android Auto，USB 口必须支持数据，首次连接会请求 USB 权限。\n"
                        + "无线：手机开发者选项启动 Headunit Server，车机输入手机 IP，端口 5277。",
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

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(CONNECTED_ACTION);
        filter.addAction(DISCONNECT_ACTION);
        ContextCompat.registerReceiver(this, usbReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        requestRuntimePermissions();
        refreshStatus();
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

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(usbReceiver);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    private void refreshStatus() {
        if (lanView != null) lanView.setText(PageUi.localNetwork(this));
        if (App.provide(this).getTransport().isAlive()) {
            setStatus("Android Auto 协议通道已连接。可以打开投影画面。");
            return;
        }
        if (usbManager == null) {
            setStatus("车机没有 USB Host 服务，无法接收 Android Auto。");
        }
    }

    private void requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT < 23) return;
        List<String> missing = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.RECORD_AUDIO);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (!missing.isEmpty()) {
            requestPermissions(missing.toArray(new String[0]), REQ_PERMISSIONS);
        }
    }

    private void chooseUsbDevice() {
        if (usbManager == null) {
            setStatus("车机没有 USB Host 服务，无法接收 Android Auto。");
            return;
        }

        Map<String, UsbDevice> devices = usbManager.getDeviceList();
        List<UsbDevice> candidates = new ArrayList<>();
        for (UsbDevice device : devices.values()) {
            if (isCandidate(device)) candidates.add(device);
        }

        if (candidates.isEmpty()) {
            setStatus("未发现可用的 USB 手机。\n"
                    + "请用数据线连接手机，并在手机上选择“文件传输 / MTP”。\n"
                    + "如果只插了 U 盘或 USB Hub，请换一个 USB 口。");
            return;
        }

        String[] labels = new String[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            UsbDevice device = candidates.get(i);
            String name = device.getProductName();
            if (name == null || name.isEmpty()) name = "USB 设备";
            labels[i] = String.format("%s  (%04x:%04x)", name, device.getVendorId(), device.getProductId());
        }

        new AlertDialog.Builder(this)
                .setTitle("选择要连接的手机")
                .setItems(labels, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        requestUsbPermission(candidates.get(which));
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void requestUsbPermission(UsbDevice device) {
        if (usbManager == null) return;
        if (usbManager.hasPermission(device)) {
            startForDevice(device);
            return;
        }

        setStatus("等待 USB 授权…");
        Intent permissionIntent = new Intent(ACTION_USB_PERMISSION);
        permissionIntent.setPackage(getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, permissionIntent, flags);
        usbManager.requestPermission(device, pi);
    }

    private void startForDevice(UsbDevice device) {
        if (isAccessory(device)) {
            startAap(device);
            return;
        }

        setStatus("正在把手机切换到 Android Auto 配件模式…\n"
                + "如果手机弹出“允许 USB 调试/文件传输”，请点允许。");
        waitingForAccessory = true;

        boolean switched = new UsbAccessoryMode(usbManager).connectAndSwitch(device);
        if (!switched) {
            waitingForAccessory = false;
            setStatus("切换失败。常见原因：\n"
                    + "· USB 口只供电、不支持数据；\n"
                    + "· 手机没有安装 Android Auto；\n"
                    + "· 手机不响应 AOA 2.0（部分定制系统）。\n"
                    + "换一根数据线或换 USB 口再试。");
            return;
        }
        setStatus("已发送 AOA 切换。等待手机重新枚举为 Android Auto 配件…");
    }

    private void startAap(UsbDevice device) {
        waitingForAccessory = false;
        setStatus("Android Auto 配件已识别，正在启动协议服务…");
        ContextCompat.startForegroundService(this, AapService.createIntent(device, this));
    }

    private void openProjection() {
        if (!App.provide(this).getTransport().isAlive()) {
            toast("还没有连上 Android Auto，先完成 USB 或无线连接。");
            return;
        }
        startActivity(AapProjectionActivity.intent(this));
    }

    private void showWifiDialog() {
        EditText input = new EditText(this);
        input.setHint("手机 IP，例如 192.168.1.25");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText("192.168.1.25");

        new AlertDialog.Builder(this)
                .setTitle("无线 Android Auto")
                .setMessage("手机端：Android Auto 设置 → 连点“版本”10 次 → 右上角菜单 → Start headunit server。\n"
                        + "手机和车机必须在同一 Wi-Fi，端口 5277。")
                .setView(input)
                .setPositiveButton("连接", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String ip = input.getText().toString().trim();
                        if (ip.isEmpty()) {
                            toast("请输入手机 IP");
                            return;
                        }
                        startWifi(ip);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void startWifi(String ip) {
        setStatus("正在连接 " + ip + ":5277 …\n"
                + "如果一直失败，确认手机已点 Start headunit server，且没有被 Wi-Fi 助手切网。");
        ContextCompat.startForegroundService(this, AapService.createIntent(ip, this));
    }

    private static boolean isAccessory(UsbDevice device) {
        if (device == null) return false;
        int vid = device.getVendorId();
        int pid = device.getProductId();
        return vid == 0x18D1 && (pid == 0x2D00 || pid == 0x2D01);
    }

    private static boolean isCandidate(UsbDevice device) {
        if (device == null || device.getInterfaceCount() == 0) return false;
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            int cls = device.getInterface(i).getInterfaceClass();
            if (cls == UsbConstants.USB_CLASS_HUB || cls == UsbConstants.USB_CLASS_MASS_STORAGE) {
                return false;
            }
        }
        return true;
    }

    private void setStatus(String text) {
        if (status != null) status.setText(text);
        if (statusPill == null) return;
        boolean connected = text.contains("已连接") || text.contains("已识别");
        boolean error = text.contains("失败") || text.contains("拒绝") || text.contains("无法");
        int accent = connected ? PageUi.GREEN : (error ? PageUi.RED : PageUi.GOLD);
        statusPill.setText(connected ? "已连接" : (error ? "异常" : "进行中"));
        statusPill.setTextColor(accent);
        statusPill.setBackground(PageUi.rounded(this,
                PageUi.blend(PageUi.CARD, accent, 0.16f),
                PageUi.blend(PageUi.BORDER, accent, 0.55f), 20));
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }
}
