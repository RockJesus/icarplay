package com.rockjesus.icarplay;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * CarPlay 连接方式检测页（参考 CarUp 的「先检测、再连接」）。
 *
 * <p>左栏有线、右栏无线，逐条列出前置条件；「请求 USB 权限」会真的调起系统授权弹框，
 * 连接卡住时可以直接看出停在 USB、蓝牙、Wi‑Fi、认证还是 iPhone 授权。
 */
public class CarPlayDetectActivity extends FitActivity {

    private static final String ACTION_USB_PERM = "com.rockjesus.icarplay.USB_PERM";

    private LinearLayout wiredList;
    private LinearLayout wirelessList;
    private TextView wiredSummary;
    private TextView wirelessSummary;
    private TextView hint;
    private TextView bootPill;
    private TextView btPill;
    private TextView usagePill;
    private TextView writePill;
    private TextView listenPill;
    private BroadcastReceiver usbReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(PageUi.background(this));
        root.setPadding(PageUi.dp(this, 16), PageUi.dp(this, 10),
                PageUi.dp(this, 16), PageUi.dp(this, 8));
        setFitContentView(root);

        root.addView(PageUi.header(this, "CarPlay 连接检测", "先检测车机能力，再发起连接 · 有线 USB / 无线 Wi‑Fi + 蓝牙", IcarLauncherActivity.VERSION_LABEL),
                PageUi.matchWrap(this));

        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(columns, colLp);

        wiredList = new LinearLayout(this);
        wirelessList = new LinearLayout(this);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        PageUi.margin(leftLp, this, 0, 0, 6, 0);
        columns.addView(buildColumn("有线 CarPlay", PageUi.BLUE, true), leftLp);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        PageUi.margin(rightLp, this, 6, 0, 0, 0);
        columns.addView(buildColumn("无线 CarPlay", PageUi.GREEN, false), rightLp);

        LinearLayout.LayoutParams engineLp = PageUi.matchWrap(this);
        PageUi.margin(engineLp, this, 0, 8, 0, 0);
        root.addView(buildEngineCard(), engineLp);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams btnLp = PageUi.matchWrap(this);
        PageUi.margin(btnLp, this, 0, 8, 0, 0);
        buttons.addView(PageUi.button(this, "刷新检测", 10.5f, PageUi.CYAN), PageUi.weight(this, 1));
        Button perm = PageUi.button(this, "请求 USB 权限", 10.5f, PageUi.GOLD);
        perm.setOnClickListener(v -> requestUsb());
        LinearLayout.LayoutParams permLp = PageUi.weight(this, 1);
        PageUi.margin(permLp, this, 8, 0, 8, 0);
        buttons.addView(perm, permLp);
        buttons.addView(PageUi.button(this, "复制报告", 10.5f, PageUi.PURPLE), PageUi.weight(this, 1));
        buttons.getChildAt(0).setOnClickListener(v -> {
            refresh();
            toast("检测已刷新");
        });
        buttons.getChildAt(2).setOnClickListener(v -> copyReport());
        root.addView(buttons, btnLp);

        hint = PageUi.text(this,
                "认证未配置时，iPhone 会在认证阶段直接断开——这一步不解决，有线无线都连不上。",
                9.5f, PageUi.MUTED, false);
        hint.setPadding(PageUi.dp(this, 10), PageUi.dp(this, 6),
                PageUi.dp(this, 10), PageUi.dp(this, 6));
        hint.setBackground(PageUi.rounded(this, PageUi.CARD, PageUi.BORDER, 8));
        LinearLayout.LayoutParams hintLp = PageUi.matchWrap(this);
        PageUi.margin(hintLp, this, 0, 6, 0, 0);
        root.addView(hint, hintLp);

        Button back = PageUi.back(this);
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams backLp = PageUi.matchWrap(this);
        PageUi.margin(backLp, this, 0, 6, 0, 0);
        root.addView(back, backLp);

        usbReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!ACTION_USB_PERM.equals(intent.getAction())) return;
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                toast(granted ? "USB 权限已授权，可以继续连接" : "USB 权限被拒绝");
                refresh();
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_USB_PERM);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(usbReceiver, filter);
        }

        refresh();
    }

    private LinearLayout buildColumn(String title, int accent, boolean wired) {
        LinearLayout card = PageUi.card(this, accent, 12, 10);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(PageUi.text(this, title, 10.5f, accent, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView summary = PageUi.statusPill(this, "检测中", PageUi.MUTED);
        if (wired) wiredSummary = summary; else wirelessSummary = summary;
        head.addView(summary, PageUi.wrap(this));
        card.addView(head, PageUi.matchWrap(this));

        LinearLayout list = wired ? wiredList : wirelessList;
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listLp = PageUi.matchWrap(this);
        PageUi.margin(listLp, this, 0, 6, 0, 0);
        card.addView(list, listLp);
        return card;
    }

    /**
     * 引擎增强卡片：把 DiPlay 里「代码在包里、入口默认关」的四项摆出来。
     *
     * <p>自启与蓝牙电话联动是组件开关（点一下即时生效，不重启进程）；
     * 使用情况访问、修改系统设置是特殊权限，只能跳系统设置页让用户手动勾选。
     */
    private LinearLayout buildEngineCard() {
        LinearLayout card = PageUi.card(this, PageUi.PURPLE, 12, 8);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(PageUi.text(this, "引擎增强 · 默认关，按需开", 10f, PageUi.PURPLE, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(PageUi.text(this, "DiPlay 内置能力", 9f, PageUi.MUTED, false),
                PageUi.wrap(this));
        card.addView(head, PageUi.matchWrap(this));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = PageUi.matchWrap(this);
        PageUi.margin(rowLp, this, 0, 6, 0, 0);

        bootPill = PageUi.statusPill(this, "关", PageUi.MUTED);
        row1.addView(cell("引擎开机自启", bootPill, this::toggleBoot), PageUi.weight(this, 1));

        btPill = PageUi.statusPill(this, "关", PageUi.MUTED);
        LinearLayout.LayoutParams btLp = PageUi.weight(this, 1);
        PageUi.margin(btLp, this, 8, 0, 0, 0);
        row1.addView(cell("蓝牙电话联动", btPill, this::toggleBt), btLp);
        card.addView(row1, rowLp);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp2 = PageUi.matchWrap(this);
        PageUi.margin(rowLp2, this, 0, 6, 0, 0);

        usagePill = PageUi.statusPill(this, "未授权", PageUi.MUTED);
        row2.addView(cell("使用情况访问", usagePill,
                () -> {
                    EngineExtra.openUsageAccess(this);
                    toast("在系统列表里给 iCarPlay 打开「使用情况访问」后返回");
                }), PageUi.weight(this, 1));

        writePill = PageUi.statusPill(this, "未授权", PageUi.MUTED);
        LinearLayout.LayoutParams writeLp = PageUi.weight(this, 1);
        PageUi.margin(writeLp, this, 8, 0, 0, 0);
        row2.addView(cell("修改系统设置", writePill,
                () -> {
                    EngineExtra.openWriteSettings(this);
                    toast("在系统列表里给 iCarPlay 打开「修改系统设置」后返回");
                }), writeLp);
        card.addView(row2, rowLp2);

        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp3 = PageUi.matchWrap(this);
        PageUi.margin(rowLp3, this, 0, 6, 0, 0);

        listenPill = PageUi.statusPill(this, "关", PageUi.MUTED);
        row3.addView(cell("开机静默待命（连上才出画面）", listenPill, this::toggleAutoListen),
                PageUi.weight(this, 1));
        card.addView(row3, rowLp3);

        return card;
    }

    private LinearLayout cell(String label, TextView pill, Runnable onClick) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.HORIZONTAL);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        int px = PageUi.dp(this, 10);
        int py = PageUi.dp(this, 7);
        cell.setPadding(px, py, px, py);
        cell.setBackground(PageUi.rounded(this, PageUi.CARD_ALT, PageUi.BORDER, 10));

        TextView name = PageUi.text(this, label, 9.5f, PageUi.TEXT, false);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        cell.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        cell.addView(pill, PageUi.wrap(this));
        cell.setOnClickListener(v -> onClick.run());
        return cell;
    }

    /** 开机自启会抢开机前台，必须二次确认，绝不静默打开。 */
    private void toggleBoot() {
        boolean on = EngineExtra.enabled(this, EngineExtra.BOOT_RECEIVER);
        if (!on) {
            new AlertDialog.Builder(this)
                    .setTitle("开启引擎开机自启？")
                    .setMessage("开启后，车机每次开机时引擎会自己尝试拉起 CarPlay 会话，"
                            + "可能抢占开机后的前台画面。只有在确认不会影响车机正常启动时才建议打开。")
                    .setNegativeButton("保持关闭", null)
                    .setPositiveButton("确认开启", (d, w) -> applyReceiver(
                            EngineExtra.BOOT_RECEIVER, true))
                    .show();
        } else {
            applyReceiver(EngineExtra.BOOT_RECEIVER, false);
        }
    }

    /** 开机静默待命：不抢前台，iPhone 连上后才把画面顶上来。 */
    private void toggleAutoListen() {
        boolean on = EngineExtra.autoListenEnabled(this);
        if (!on) {
            new AlertDialog.Builder(this)
                    .setTitle("开启开机静默待命？")
                    .setMessage("开机后只起一个前台服务开始监听，车机原生界面不受影响；"
                            + "只有 iPhone 真的连上来时才显示投屏画面。会多一条常驻通知。")
                    .setNegativeButton("保持关闭", null)
                    .setPositiveButton("确认开启", (d, w) -> {
                        boolean ok = EngineExtra.setAutoListen(this, true);
                        toast(ok ? "已开启：每次开机自动待命" : "开启失败，车机可能限制了组件状态");
                        refreshEngine();
                    })
                    .show();
        } else {
            EngineExtra.setAutoListen(this, false);
            toast("已关闭开机静默待命");
            refreshEngine();
        }
    }

    private void toggleBt() {
        applyReceiver(EngineExtra.BT_RECEIVER, !EngineExtra.enabled(this, EngineExtra.BT_RECEIVER));
    }

    private void applyReceiver(String cls, boolean on) {
        if (!EngineExtra.present(this, cls)) {
            toast("这个 APK 里没有该引擎组件（可能被裁剪）");
            refreshEngine();
            return;
        }
        boolean ok = EngineExtra.setEnabled(this, cls, on);
        toast(ok ? (on ? "已开启" : "已关闭") : "切换失败，车机可能限制了组件状态修改");
        refreshEngine();
    }

    private void refreshEngine() {
        if (bootPill == null) return;
        boolean bootOn = EngineExtra.enabled(this, EngineExtra.BOOT_RECEIVER);
        bootPill.setText(bootOn ? "开" : "关");
        bootPill.setTextColor(bootOn ? PageUi.GOLD : PageUi.MUTED);

        boolean btOn = EngineExtra.enabled(this, EngineExtra.BT_RECEIVER);
        btPill.setText(btOn ? "开" : "关");
        btPill.setTextColor(btOn ? PageUi.GREEN : PageUi.MUTED);

        boolean usage = EngineExtra.hasUsageAccess(this);
        usagePill.setText(usage ? "已授权" : "未授权");
        usagePill.setTextColor(usage ? PageUi.GREEN : PageUi.MUTED);

        boolean write = EngineExtra.canWriteSettings(this);
        writePill.setText(write ? "已授权" : "未授权");
        writePill.setTextColor(write ? PageUi.GREEN : PageUi.MUTED);

        boolean listen = EngineExtra.autoListenEnabled(this);
        listenPill.setText(listen ? "开" : "关");
        listenPill.setTextColor(listen ? PageUi.GREEN : PageUi.MUTED);
    }

    private void refresh() {
        fill(wiredList, CarPlayCheck.wired(this), wiredSummary);
        fill(wirelessList, CarPlayCheck.wireless(this), wirelessSummary);
        refreshEngine();
        boolean auth = CarPlayCore.hasAuth(this);
        hint.setText(auth
                ? "认证材料已就位。下一步缺的是协议引擎：有了引擎，认证通过后才能出画面。"
                : "认证未配置：iPhone 会在认证阶段直接断开——先解决认证，再谈有线还是无线。");
        hint.setTextColor(auth ? PageUi.MUTED : PageUi.GOLD);
    }

    private void fill(LinearLayout container, List<CarPlayCheck.Item> items, TextView summary) {
        container.removeAllViews();
        for (CarPlayCheck.Item item : items) {
            container.addView(row(item), PageUi.matchWrap(this));
        }
        int ready = CarPlayCheck.readyCount(items);
        summary.setText(ready + "/" + items.size() + " 就绪");
        summary.setTextColor(ready == items.size() ? PageUi.GREEN
                : (ready >= items.size() / 2 ? PageUi.GOLD : PageUi.RED));
    }

    private LinearLayout row(CarPlayCheck.Item item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = PageUi.matchWrap(this);
        PageUi.margin(rowLp, this, 0, 3, 0, 0);
        row.setLayoutParams(rowLp);

        TextView name = PageUi.text(this, item.name, 9.5f, PageUi.TEXT, false);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = PageUi.text(this, item.value, 9.5f, colorOf(item.state), true);
        value.setSingleLine(true);
        value.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f);
        value.setGravity(Gravity.END);
        row.addView(value, valueLp);
        return row;
    }

    private int colorOf(int state) {
        switch (state) {
            case CarPlayCheck.OK: return PageUi.GREEN;
            case CarPlayCheck.WARN: return PageUi.GOLD;
            case CarPlayCheck.FAIL: return PageUi.RED;
            default: return PageUi.MUTED;
        }
    }

    private void requestUsb() {
        try {
            UsbManager um = (UsbManager) getSystemService(Context.USB_SERVICE);
            if (um == null) {
                toast("这台车机没有 USB 服务");
                return;
            }
            UsbDevice target = null;
            for (UsbDevice d : um.getDeviceList().values()) {
                if (d.getVendorId() == 0x05AC) {
                    target = d;
                    break;
                }
            }
            if (target == null) {
                toast("没找到 Apple 设备，先插上 iPhone 数据线");
                return;
            }
            if (um.hasPermission(target)) {
                toast("该设备已有 USB 权限，不需要重复授权");
                return;
            }
            PendingIntent pi = PendingIntent.getBroadcast(this, 0,
                    new Intent(ACTION_USB_PERM), PendingIntent.FLAG_IMMUTABLE);
            um.requestPermission(target, pi);
            toast("已请求 USB 权限，请在系统弹框里点允许");
        } catch (Throwable e) {
            toast("USB 权限请求失败：" + e.getClass().getSimpleName());
        }
    }

    private void copyReport() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("CarPlay 检测", CarPlayCheck.report(this)));
                toast("检测报告已复制");
            }
        } catch (Throwable e) {
            toast("复制失败");
        }
    }

    @Override
    protected void onDestroy() {
        try {
            if (usbReceiver != null) unregisterReceiver(usbReceiver);
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
