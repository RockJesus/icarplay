package com.rockjesus.icarplay;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import io.github.jqssun.airplay.service.AirPlayService;

/** AirPlay 接收单页：状态、本机地址、连接步骤和启停都在一屏。 */
public class AirPlayActivity extends Activity {
    private AirPlayService service;
    private TextView status;
    private TextView statusPill;
    private TextView devicesValue;
    private TextView videoValue;
    private TextView networkValue;
    private Button start;
    private Button stop;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            refreshState();
            handler.postDelayed(this, 1000);
        }
    };

    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((AirPlayService.LocalBinder) binder).getService();
            refreshState();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            service = null;
            refreshState();
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(PageUi.background(this));
        int pad = PageUi.dp(this, 18);
        root.setPadding(pad, PageUi.dp(this, 10), pad, PageUi.dp(this, 8));
        setContentView(root);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(PageUi.header(this, "AirPlay 接收", IcarLauncherActivity.VERSION_LABEL),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        statusPill = PageUi.statusPill(this, "未运行", PageUi.BLUE);
        top.addView(statusPill, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(top, PageUi.matchWrap(this));

        TextView sub = PageUi.text(this, "iPhone / iPad 镜像与音频 · 同一局域网", 11f,
                PageUi.MUTED, false);
        LinearLayout.LayoutParams subLp = PageUi.matchWrap(this);
        PageUi.margin(subLp, this, 0, 3, 0, 7);
        root.addView(sub, subLp);

        LinearLayout statusCard = PageUi.card(this, PageUi.GREEN, 14, 9);
        LinearLayout.LayoutParams slp = PageUi.matchWrap(this);
        root.addView(statusCard, slp);
        statusCard.addView(PageUi.text(this, "接收状态", 8.5f, PageUi.GREEN, true),
                PageUi.wrap(this));
        status = PageUi.text(this, "正在读取状态…", 16f, PageUi.TEXT, true);
        status.setPadding(0, PageUi.dp(this, 2), 0, PageUi.dp(this, 7));
        statusCard.addView(status, PageUi.wrap(this));

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.addView(metrics, PageUi.matchWrap(this));
        devicesValue = metric(metrics, "已连接设备", "0", PageUi.BLUE);
        videoValue = metric(metrics, "画面规格", "待连接", PageUi.GREEN);

        LinearLayout guideCard = PageUi.card(this, PageUi.BLUE, 14, 8);
        LinearLayout.LayoutParams glp = PageUi.matchWrap(this);
        PageUi.margin(glp, this, 0, 8, 0, 0);
        root.addView(guideCard, glp);
        guideCard.addView(PageUi.text(this, "连接步骤", 9f, PageUi.BLUE, true),
                PageUi.wrap(this));
        TextView guide = PageUi.text(this,
                "1  手机与车机连接同一 Wi‑Fi 或热点\n"
                        + "2  iPhone 打开控制中心，点“屏幕镜像”\n"
                        + "3  选择接收端 iCarPlay，开始镜像",
                12f, PageUi.MUTED, false);
        guide.setPadding(0, PageUi.dp(this, 3), 0, 0);
        guideCard.addView(guide, PageUi.wrap(this));

        LinearLayout networkCard = PageUi.card(this, PageUi.GOLD, 14, 7);
        LinearLayout.LayoutParams nlp = PageUi.matchWrap(this);
        PageUi.margin(nlp, this, 0, 8, 0, 0);
        root.addView(networkCard, nlp);
        LinearLayout netRow = new LinearLayout(this);
        netRow.setOrientation(LinearLayout.HORIZONTAL);
        netRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView netLabel = PageUi.text(this, "本机地址", 9f, PageUi.GOLD, true);
        netRow.addView(netLabel, PageUi.wrap(this));
        View spacer = new View(this);
        netRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        Button copy = PageUi.button(this, "复制地址", 9.5f, PageUi.GOLD);
        copy.setPadding(PageUi.dp(this, 10), 0, PageUi.dp(this, 10), 0);
        copy.setOnClickListener(v -> copyNetwork());
        netRow.addView(copy, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, PageUi.dp(this, 30)));
        networkCard.addView(netRow, PageUi.matchWrap(this));
        TextView netValue = PageUi.text(this, PageUi.localNetwork(this), 11f, PageUi.TEXT, false);
        netValue.setPadding(0, PageUi.dp(this, 2), 0, 0);
        networkValue = netValue;
        networkCard.addView(netValue, PageUi.wrap(this));

        View gap = new View(this);
        root.addView(gap, new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, PageUi.dp(this, 48));
        PageUi.margin(blp, this, 0, 8, 0, 0);
        root.addView(buttons, blp);

        start = PageUi.button(this, "开始接收", 14f, PageUi.BLUE);
        stop = PageUi.button(this, "停止接收", 14f, PageUi.RED);
        start.setOnClickListener(v -> {
            if (service != null) service.startServer();
            handler.postDelayed(this::refreshState, 600);
        });
        stop.setOnClickListener(v -> {
            if (service != null) service.stopServer();
            handler.postDelayed(this::refreshState, 600);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, PageUi.dp(this, 48), 1f);
        lp.setMargins(0, 0, PageUi.dp(this, 8), 0);
        buttons.addView(start, lp);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, PageUi.dp(this, 48), 1f);
        buttons.addView(stop, lp2);

        Button back = PageUi.back(this);
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams backLp = PageUi.fixed(this, 44);
        PageUi.margin(backLp, this, 0, 8, 0, 0);
        root.addView(back, backLp);

        bindService(new Intent(this, AirPlayService.class), conn, Context.BIND_AUTO_CREATE);
    }

    private TextView metric(LinearLayout row, String label, String value, int accent) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(PageUi.dp(this, 7), PageUi.dp(this, 5),
                PageUi.dp(this, 7), PageUi.dp(this, 5));
        box.setBackground(PageUi.rounded(this, PageUi.blend(PageUi.CARD, accent, 0.08f),
                PageUi.blend(PageUi.BORDER, accent, 0.28f), 9));
        box.addView(PageUi.text(this, label, 8f, PageUi.MUTED, false), PageUi.wrap(this));
        TextView v = PageUi.text(this, value, 11.5f, accent, true);
        v.setSingleLine(true);
        box.addView(v, PageUi.wrap(this));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        PageUi.margin(lp, this, 3, 0, 3, 0);
        row.addView(box, lp);
        return v;
    }

    private void copyNetwork() {
        String value = PageUi.localNetwork(this);
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("iCarPlay 本机地址", value));
            Toast.makeText(this, "已复制：" + value, Toast.LENGTH_LONG).show();
        }
    }

    private void refreshState() {
        if (status == null) return;
        if (service == null) {
            status.setText("接收服务未连接");
            statusPill.setText("服务未连接");
            statusPill.setTextColor(PageUi.RED);
            start.setEnabled(false);
            stop.setEnabled(false);
            return;
        }
        String stateName = service.getServerState().getValue() == null
                ? "STOPPED" : service.getServerState().getValue().name();
        int count = service.getConnectionCount().getValue() == null
                ? 0 : service.getConnectionCount().getValue();
        String resolution = service.getVideoResolution().getValue() == null
                ? "" : service.getVideoResolution().getValue();
        boolean running = "RUNNING".equals(stateName);
        String label;
        if (running) {
            label = count > 0 ? "正在接收" : "等待 iPhone 连接";
        } else if ("ERROR".equals(stateName)) {
            label = "启动失败，请检查本地网络权限";
        } else {
            label = "未运行";
        }
        status.setText(label);
        statusPill.setText(running ? (count > 0 ? "接收中" : "已就绪") : "未运行");
        statusPill.setTextColor(running ? PageUi.GREEN : PageUi.BLUE);
        statusPill.setBackground(PageUi.rounded(this,
                PageUi.blend(PageUi.CARD, running ? PageUi.GREEN : PageUi.BLUE, 0.16f),
                PageUi.blend(PageUi.BORDER, running ? PageUi.GREEN : PageUi.BLUE, 0.55f), 20));
        devicesValue.setText(String.valueOf(count));
        videoValue.setText(resolution.isEmpty() ? (running ? "等待画面" : "待连接") : resolution);
        networkValue.setText(PageUi.localNetwork(this));
        start.setEnabled(!running);
        stop.setEnabled(running);
    }

    @Override protected void onResume() {
        super.onResume();
        handler.post(refresh);
    }

    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refresh);
    }

    @Override protected void onDestroy() {
        try { unbindService(conn); } catch (Throwable ignored) { }
        super.onDestroy();
    }
}
