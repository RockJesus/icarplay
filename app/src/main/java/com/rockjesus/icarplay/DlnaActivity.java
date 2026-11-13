package com.rockjesus.icarplay;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * DLNA 音频接收页：手机把歌“投送”到车机，车机当音箱放。
 * 只接收同一局域网内主动推过来的音频流，不扫描别人的设备。
 */
public class DlnaActivity extends Activity {

    private static DlnaRenderer renderer;

    private TextView statePill;
    private TextView stateText;
    private TextView nameValue;
    private TextView portValue;
    private TextView trackValue;
    private TextView progressValue;
    private TextView addressValue;
    private Button start;
    private Button stop;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 1000);
        }
    };

    static DlnaRenderer renderer(Context context) {
        if (renderer == null) renderer = new DlnaRenderer(context);
        return renderer;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        renderer(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(PageUi.background(this));
        int pad = PageUi.dp(this, 18);
        root.setPadding(pad, PageUi.dp(this, 10), pad, PageUi.dp(this, 8));
        setContentView(root);

        root.addView(PageUi.header(this, "DLNA 音频接收", IcarLauncherActivity.VERSION_LABEL),
                PageUi.matchWrap(this));

        TextView sub = PageUi.text(this, "安卓手机 / 电脑把音乐投送到车机 · 同一局域网",
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

        LinearLayout statusCard = PageUi.card(this, PageUi.GREEN, 14, 9);
        content.addView(statusCard, PageUi.matchWrap(this));
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(PageUi.text(this, "接收状态", 8.5f, PageUi.GREEN, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        statePill = PageUi.statusPill(this, "未启动", PageUi.BLUE);
        head.addView(statePill, PageUi.wrap(this));
        statusCard.addView(head, PageUi.matchWrap(this));

        stateText = PageUi.text(this, "未启动", 16f, PageUi.TEXT, true);
        stateText.setPadding(0, PageUi.dp(this, 2), 0, PageUi.dp(this, 7));
        statusCard.addView(stateText, PageUi.wrap(this));

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.addView(metrics, PageUi.matchWrap(this));
        nameValue = metric(metrics, "设备名", "—", PageUi.CYAN);
        portValue = metric(metrics, "控制端口", "—", PageUi.BLUE);
        progressValue = metric(metrics, "播放进度", "—", PageUi.GOLD);

        LinearLayout trackCard = PageUi.card(this, PageUi.BLUE, 14, 8);
        LinearLayout.LayoutParams tlp = PageUi.matchWrap(this);
        PageUi.margin(tlp, this, 0, 8, 0, 0);
        content.addView(trackCard, tlp);
        trackCard.addView(PageUi.text(this, "正在播放", 9f, PageUi.BLUE, true), PageUi.wrap(this));
        trackValue = PageUi.text(this, "还没有收到音频", 12f, PageUi.MUTED, false);
        trackValue.setPadding(0, PageUi.dp(this, 3), 0, 0);
        trackValue.setMaxLines(2);
        trackCard.addView(trackValue, PageUi.matchWrap(this));

        LinearLayout guideCard = PageUi.card(this, PageUi.GOLD, 14, 8);
        LinearLayout.LayoutParams glp = PageUi.matchWrap(this);
        PageUi.margin(glp, this, 0, 8, 0, 0);
        content.addView(guideCard, glp);
        LinearLayout guideHead = new LinearLayout(this);
        guideHead.setOrientation(LinearLayout.HORIZONTAL);
        guideHead.setGravity(Gravity.CENTER_VERTICAL);
        guideHead.addView(PageUi.text(this, "怎么用", 9f, PageUi.GOLD, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        addressValue = PageUi.text(this, "—", 9f, PageUi.MUTED, false);
        PageUi.mono(addressValue);
        guideHead.addView(addressValue, PageUi.wrap(this));
        guideCard.addView(guideHead, PageUi.matchWrap(this));
        TextView guide = PageUi.text(this,
                "1  手机与车机连同一 Wi‑Fi，点下面“启动接收”\n"
                        + "2  手机音乐 App 里点“投送到设备 / DLNA / Cast”，选本机设备名\n"
                        + "3  选中后车机会自动播放；音量用方向盘或车机音量键",
                11f, PageUi.MUTED, false);
        guide.setPadding(0, PageUi.dp(this, 3), 0, 0);
        guideCard.addView(guide, PageUi.matchWrap(this));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, PageUi.dp(this, 48));
        PageUi.margin(blp, this, 0, 6, 0, 0);
        root.addView(buttons, blp);

        start = PageUi.button(this, "启动接收", 13.5f, PageUi.GREEN);
        stop = PageUi.button(this, "停止接收", 13.5f, PageUi.RED);
        Button copy = PageUi.button(this, "复制地址", 13.5f, PageUi.GOLD);
        start.setOnClickListener(v -> startReceiver());
        stop.setOnClickListener(v -> stopReceiver());
        copy.setOnClickListener(v -> copyAddress());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, PageUi.dp(this, 48), 1f);
        lp.setMargins(0, 0, PageUi.dp(this, 7), 0);
        buttons.addView(start, lp);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, PageUi.dp(this, 48), 1f);
        lp2.setMargins(0, 0, PageUi.dp(this, 7), 0);
        buttons.addView(stop, lp2);
        buttons.addView(copy, new LinearLayout.LayoutParams(0, PageUi.dp(this, 48), 1f));

        Button back = PageUi.back(this);
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams backLp = PageUi.fixed(this, 42);
        PageUi.margin(backLp, this, 0, 6, 0, 0);
        root.addView(back, backLp);

        refresh();
    }

    private TextView metric(LinearLayout row, String label, String value, int accent) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(PageUi.dp(this, 7), PageUi.dp(this, 5),
                PageUi.dp(this, 7), PageUi.dp(this, 5));
        box.setBackground(PageUi.rounded(this, PageUi.blend(PageUi.CARD, accent, 0.08f),
                PageUi.blend(PageUi.BORDER, accent, 0.28f), 9));
        box.addView(PageUi.text(this, label, 8f, PageUi.MUTED, false), PageUi.wrap(this));
        TextView v = PageUi.text(this, value, 11f, accent, true);
        v.setSingleLine(true);
        box.addView(v, PageUi.wrap(this));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        PageUi.margin(lp, this, 3, 0, 3, 0);
        row.addView(box, lp);
        return v;
    }

    private void startReceiver() {
        DlnaRenderer r = renderer(this);
        if (r.isRunning()) {
            toast("已经在接收，等手机投送就行");
            return;
        }
        boolean ok = r.start();
        refresh();
        if (!ok) {
            toast(r.getLastError().isEmpty() ? "启动失败，检查是否连了 Wi‑Fi" : r.getLastError());
        } else {
            toast("DLNA 接收已启动：" + r.getDeviceName());
        }
    }

    private void stopReceiver() {
        DlnaRenderer r = renderer(this);
        if (!r.isRunning()) {
            toast("当前没有在接收");
            return;
        }
        r.stop();
        refresh();
        toast("DLNA 接收已停止");
    }

    private void copyAddress() {
        DlnaRenderer r = renderer(this);
        String value = r.isRunning() ? r.location() : PageUi.localNetwork(this);
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("iCarPlay DLNA", value));
            toast("已复制：" + value);
        }
    }

    private void refresh() {
        if (stateText == null) return;
        DlnaRenderer r = renderer(this);
        boolean running = r.isRunning();
        String state = r.getState();
        String label;
        int accent;
        if (!running) {
            label = "未启动";
            accent = PageUi.BLUE;
        } else if (DlnaRenderer.ST_PLAYING.equals(state)) {
            label = "正在播放";
            accent = PageUi.GREEN;
        } else if (DlnaRenderer.ST_PAUSED.equals(state)) {
            label = "已暂停";
            accent = PageUi.GOLD;
        } else if (r.getCurrentUri().isEmpty()) {
            label = "已就绪，等待手机投送";
            accent = PageUi.CYAN;
        } else {
            label = "已载入，等待播放";
            accent = PageUi.GOLD;
        }
        stateText.setText(label);
        stateText.setTextColor(accent);
        statePill.setText(running ? (DlnaRenderer.ST_PLAYING.equals(state) ? "播放中" : "已就绪")
                : "未启动");
        statePill.setTextColor(accent);
        statePill.setBackground(PageUi.rounded(this,
                PageUi.blend(PageUi.CARD, accent, 0.16f),
                PageUi.blend(PageUi.BORDER, accent, 0.55f), 20));

        nameValue.setText(running ? r.getDeviceName() : "—");
        portValue.setText(running ? String.valueOf(r.getPort()) : "—");
        progressValue.setText(running && !r.getCurrentUri().isEmpty()
                ? r.getPositionText() : "—");
        trackValue.setText(running
                ? (r.getCurrentUri().isEmpty()
                ? (r.getLastError().isEmpty() ? "还没有收到音频" : r.getLastError())
                : r.getTitle() + "\n" + shorten(r.getCurrentUri()))
                : "还没有收到音频");
        trackValue.setTextColor(!running || r.getCurrentUri().isEmpty()
                ? PageUi.MUTED : PageUi.TEXT);
        addressValue.setText(running ? "http://" + hostOf(r.location()) : "未启动");
        start.setEnabled(!running);
        stop.setEnabled(running);
    }

    private static String hostOf(String location) {
        String value = location == null ? "" : location;
        int start = value.indexOf("://");
        int slash = value.indexOf('/', start + 3);
        return slash > 0 ? value.substring(start + 3, slash) : value;
    }

    private static String shorten(String uri) {
        if (uri == null) return "";
        String value = uri;
        if (value.length() > 64) value = "…" + value.substring(value.length() - 60);
        return value;
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(tick);
        // 接收服务用 application context 运行，离开页面也可以继续放歌；
        // 真正的停止只在用户点“停止接收”或进程结束时发生。
        super.onDestroy();
    }
}
