package com.rockjesus.icarplay;

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

        root.addView(PageUi.header(this, "CarPlay 连接检测", IcarLauncherActivity.VERSION_LABEL),
                PageUi.matchWrap(this));

        TextView sub = PageUi.text(this,
                "先检测车机能力，再发起连接 · 有线走 USB，无线走 Wi‑Fi + 蓝牙",
                10.5f, PageUi.MUTED, false);
        sub.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams subLp = PageUi.matchWrap(this);
        PageUi.margin(subLp, this, 0, 3, 0, 8);
        root.addView(sub, subLp);

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

    private void refresh() {
        fill(wiredList, CarPlayCheck.wired(this), wiredSummary);
        fill(wirelessList, CarPlayCheck.wireless(this), wirelessSummary);
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
