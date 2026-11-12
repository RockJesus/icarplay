package com.rockjesus.icarplay;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.github.jqssun.airplay.service.AirPlayService;

/** AirPlay 接收单页：沿用首页的深色车机风格，只保留启动、停止和返回。 */
public class AirPlayActivity extends Activity {
    private AirPlayService service;
    private TextView status;
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
        int pad = PageUi.dp(this, 24);
        root.setPadding(pad, PageUi.dp(this, 18), pad, PageUi.dp(this, 18));

        root.addView(PageUi.header(this, "AirPlay 接收", IcarLauncherActivity.VERSION_LABEL),
                PageUi.matchWrap(this));

        LinearLayout intro = PageUi.card(this, PageUi.BLUE, 16, 12);
        LinearLayout.LayoutParams ilp = PageUi.matchWrap(this);
        PageUi.margin(ilp, this, 0, 12, 0, 0);
        root.addView(intro, ilp);
        intro.addView(PageUi.text(this, "镜像 · 音频", 10f, PageUi.BLUE, true), PageUi.wrap(this));
        intro.addView(PageUi.text(this,
                "接收 iPhone / iPad 的 AirPlay 镜像与音频，需要与车机处于同一局域网。",
                13f, PageUi.TEXT, false), PageUi.wrap(this));

        LinearLayout statusCard = PageUi.card(this, PageUi.GREEN, 16, 12);
        LinearLayout.LayoutParams slp = PageUi.matchWrap(this);
        PageUi.margin(slp, this, 0, 10, 0, 0);
        root.addView(statusCard, slp);
        status = PageUi.text(this, "正在读取状态…", 13f, PageUi.TEXT, true);
        statusCard.addView(status, PageUi.wrap(this));

        LinearLayout stepsCard = PageUi.card(this, PageUi.GOLD, 16, 10);
        LinearLayout.LayoutParams klp = PageUi.matchWrap(this);
        PageUi.margin(klp, this, 0, 10, 0, 0);
        root.addView(stepsCard, klp);
        stepsCard.addView(PageUi.text(this, "连接步骤", 10f, PageUi.GOLD, true),
                PageUi.wrap(this));
        stepsCard.addView(PageUi.text(this,
                "1  手机与车机连接同一 Wi-Fi 或热点\n"
                        + "2  打开 iPhone 控制中心的“屏幕镜像”\n"
                        + "3  选择列表中的 iCarPlay 接收端",
                12.5f, PageUi.MUTED, false), PageUi.wrap(this));

        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, PageUi.dp(this, 52));
        PageUi.margin(blp, this, 0, 10, 0, 0);
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, PageUi.dp(this, 52), 1f);
        lp.setMargins(0, 0, PageUi.dp(this, 8), 0);
        buttons.addView(start, lp);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, PageUi.dp(this, 52), 1f);
        buttons.addView(stop, lp2);

        Button back = PageUi.back(this);
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams backLp = PageUi.fixed(this, 48);
        PageUi.margin(backLp, this, 0, 10, 0, 0);
        root.addView(back, backLp);

        setContentView(root);
        bindService(new Intent(this, AirPlayService.class), conn, Context.BIND_AUTO_CREATE);
    }

    private void refreshState() {
        if (status == null) return;
        if (service == null) {
            status.setText("接收服务未连接");
            start.setEnabled(false);
            stop.setEnabled(false);
            return;
        }
        String stateName = service.getServerState().getValue() == null
                ? "STOPPED" : service.getServerState().getValue().name();
        int count = service.getConnectionCount().getValue() == null
                ? 0 : service.getConnectionCount().getValue();
        String label;
        if ("RUNNING".equals(stateName)) {
            label = count > 0 ? "正在接收 · " + count + " 台设备已连接" : "等待 iPhone 连接";
        } else if ("ERROR".equals(stateName)) {
            label = "启动失败，请检查本地网络权限";
        } else {
            label = "未运行";
        }
        status.setText(label);
        start.setEnabled(!"RUNNING".equals(stateName));
        stop.setEnabled("RUNNING".equals(stateName));
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
