package com.rockjesus.icarplay;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 无线传文件页：手机 ⇄ 车机互传 APK。
 *
 * <p>车机侧起一个只读/只收 apk 的小 HTTP 服务，手机浏览器打开地址就能上传或下载；
 * 蓝牙作为兜底通道，用系统分享把车机上的 APK 发给已配对手机。页面本身不装包，
 * 装包交给系统的安装确认，避免应用偷偷静默安装。
 */
public class TransferActivity extends Activity {

    private TextView statePill;
    private TextView addressText;
    private TextView hintText;
    private LinearLayout listBox;
    private TextView emptyText;
    private TextView logText;
    private Button startBtn;
    private Button stopBtn;

    private FileTransferServer server;
    private File root;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = new File(getExternalFilesDir(null), "transfer");
        if (!root.exists()) root.mkdirs();

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackground(PageUi.background(this));
        int pad = PageUi.dp(this, 18);
        shell.setPadding(pad, PageUi.dp(this, 10), pad, PageUi.dp(this, 8));
        setContentView(shell);

        shell.addView(PageUi.header(this, "无线传文件", IcarLauncherActivity.VERSION_LABEL),
                PageUi.matchWrap(this));

        TextView sub = PageUi.text(this,
                "手机 ⇄ 车机互传 APK · 同一 Wi‑Fi / 手机热点 / 车机热点 · 蓝牙分享兜底",
                10.5f, PageUi.MUTED, false);
        sub.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams subLp = PageUi.matchWrap(this);
        PageUi.margin(subLp, this, 0, 3, 0, 7);
        shell.addView(sub, subLp);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        shell.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        body.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.15f));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        rightLp.setMargins(PageUi.dp(this, 8), 0, 0, 0);
        body.addView(right, rightLp);

        left.addView(buildServiceCard(), PageUi.matchWrap(this));
        left.addView(buildChannelCard(), withTopMargin(PageUi.matchWrap(this), 8));

        right.addView(buildFilesCard(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView log = PageUi.text(this, "服务未启动", 9.5f, PageUi.MUTED, false);
        log.setPadding(0, PageUi.dp(this, 4), 0, 0);
        LinearLayout.LayoutParams logLp = PageUi.matchWrap(this);
        PageUi.margin(logLp, this, 0, 4, 0, 0);
        shell.addView(log, logLp);
        logText = log;

        Button back = PageUi.back(this);
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams backLp = PageUi.fixed(this, 42);
        PageUi.margin(backLp, this, 0, 6, 0, 0);
        shell.addView(back, backLp);

        copyOwnApk();
        refresh();
        handler.post(tick);
    }

    private LinearLayout.LayoutParams withTopMargin(LinearLayout.LayoutParams lp, float top) {
        lp.topMargin = PageUi.dp(this, top);
        return lp;
    }

    // ------------------------------------------------------------------ cards

    private LinearLayout buildServiceCard() {
        LinearLayout card = PageUi.card(this, PageUi.GREEN, 14, 9);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(PageUi.text(this, "文件服务", 8.5f, PageUi.GREEN, true), PageUi.wrap(this));
        View spacer = new View(this);
        head.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        statePill = PageUi.statusPill(this, "已停止", PageUi.RED);
        head.addView(statePill, PageUi.wrap(this));
        card.addView(head, PageUi.matchWrap(this));

        addressText = PageUi.text(this, "—", 15f, PageUi.CYAN, true);
        PageUi.mono(addressText);
        addressText.setPadding(0, PageUi.dp(this, 4), 0, 0);
        card.addView(addressText, PageUi.matchWrap(this));

        hintText = PageUi.text(this, "点“启动服务”，再用手机浏览器打开上面的地址", 9.5f, PageUi.MUTED, false);
        hintText.setPadding(0, PageUi.dp(this, 3), 0, 0);
        card.addView(hintText, PageUi.matchWrap(this));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = PageUi.matchWrap(this);
        PageUi.margin(rowLp, this, 0, 6, 0, 0);
        card.addView(row, rowLp);

        startBtn = PageUi.button(this, "启动服务", 12f, PageUi.GREEN);
        stopBtn = PageUi.button(this, "停止服务", 12f, PageUi.RED);
        Button copy = PageUi.button(this, "复制地址", 12f, PageUi.GOLD);
        startBtn.setOnClickListener(v -> startServer());
        stopBtn.setOnClickListener(v -> stopServer());
        copy.setOnClickListener(v -> copyAddress());
        row.addView(startBtn, weightWithGap(1f, 6));
        row.addView(stopBtn, weightWithGap(1f, 6));
        row.addView(copy, new LinearLayout.LayoutParams(0, PageUi.dp(this, 42), 1f));
        return card;
    }

    private LinearLayout buildChannelCard() {
        LinearLayout card = PageUi.card(this, PageUi.CYAN, 14, 9);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(PageUi.text(this, "无线通道", 8.5f, PageUi.CYAN, true), PageUi.wrap(this));
        View spacer = new View(this);
        head.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        head.addView(PageUi.statusPill(this, "4 种走法", PageUi.CYAN), PageUi.wrap(this));
        card.addView(head, PageUi.matchWrap(this));

        LinearLayout pills = new LinearLayout(this);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams pillsLp = PageUi.matchWrap(this);
        PageUi.margin(pillsLp, this, 0, 5, 0, 0);
        card.addView(pills, pillsLp);
        addPill(pills, "同一 Wi‑Fi", PageUi.BLUE);
        addPill(pills, "手机热点", PageUi.GREEN);
        addPill(pills, "车机热点", PageUi.PURPLE);
        addPill(pills, "蓝牙分享", PageUi.PINK);

        TextView tips = PageUi.text(this,
                "① 车机与手机连同一个 Wi‑Fi：直接开服务、手机浏览器输地址\n"
                        + "② 没路由：手机开热点 → 车机加入（一键无线投屏向导）\n"
                        + "③ 车机自建热点 → 手机连车机（连接中心可开）\n"
                        + "④ 蓝牙：车机上「蓝牙分享」把 APK 推给已配对手机；手机上蓝牙发送 APK 走系统蓝牙接收",
                9.5f, PageUi.MUTED, false);
        tips.setLineSpacing(PageUi.dp(this, 1.5f), 1f);
        tips.setPadding(0, PageUi.dp(this, 4), 0, 0);
        card.addView(tips, PageUi.matchWrap(this));
        return card;
    }

    private LinearLayout buildFilesCard() {
        LinearLayout card = PageUi.card(this, PageUi.BLUE, 14, 9);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(PageUi.text(this, "车机上的 APK", 8.5f, PageUi.BLUE, true), PageUi.wrap(this));
        View spacer = new View(this);
        head.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        linearButton(head, "刷新", PageUi.CYAN, v -> refresh());
        card.addView(head, PageUi.matchWrap(this));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        card.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listBox, PageUi.matchWrap(this));

        emptyText = PageUi.text(this, "还没有 APK：服务启动后，用手机浏览器上传即可出现在这里", 9.5f,
                PageUi.MUTED, false);
        emptyText.setPadding(0, PageUi.dp(this, 6), 0, 0);
        listBox.addView(emptyText, PageUi.matchWrap(this));
        return card;
    }

    private void addPill(LinearLayout row, String label, int accent) {
        TextView pill = PageUi.statusPill(this, label, accent);
        LinearLayout.LayoutParams lp = PageUi.wrap(this);
        lp.setMargins(0, 0, PageUi.dp(this, 5), 0);
        row.addView(pill, lp);
    }

    private LinearLayout.LayoutParams weightWithGap(float weight, float gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, PageUi.dp(this, 42), weight);
        lp.setMargins(0, 0, PageUi.dp(this, gap), 0);
        return lp;
    }

    private Button linearButton(LinearLayout row, String label, int accent, View.OnClickListener listener) {
        Button b = PageUi.button(this, label, 9.5f, accent);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                PageUi.dp(this, 62), PageUi.dp(this, 26));
        row.addView(b, lp);
        return b;
    }

    // ------------------------------------------------------------------ actions

    private void startServer() {
        if (server == null) {
            server = new FileTransferServer(root, IcarLauncherActivity.VERSION_LABEL, line -> {
                logText(line);
                runOnUiThread(this::refresh);
            });
        }
        if (server.isRunning()) {
            toast("服务已经在运行");
            refresh();
            return;
        }
        int wanted = 8090;
        if (!server.start(wanted)) {
            toast("端口被占用，稍后再试或先停止其它服务");
            return;
        }
        toast("服务已启动，手机浏览器打开 " + url());
        refresh();
    }

    private void stopServer() {
        if (server != null) server.stop();
        refresh();
    }

    private String url() {
        int port = server == null ? FileTransferServer.DEFAULT_PORT : server.port();
        return "http://" + FileTransferServer.hostAddress() + ":" + port;
    }

    private void copyAddress() {
        String value = url();
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("iCarPlay transfer", value));
            toast("已复制：" + value);
        }
    }

    private void refresh() {
        boolean running = server != null && server.isRunning();
        statePill.setText(running ? "运行中" : "已停止");
        statePill.setTextColor(running ? PageUi.GREEN : PageUi.RED);
        statePill.setBackground(PageUi.rounded(this,
                PageUi.blend(PageUi.CARD, running ? PageUi.GREEN : PageUi.RED, 0.20f),
                PageUi.blend(running ? PageUi.GREEN : PageUi.RED, PageUi.TEXT, 0.18f), 20));
        addressText.setText(running ? url() : "服务未启动");
        hintText.setText(running
                ? "手机浏览器打开上面的地址：选 APK 上传，或点列表里的文件下载到手机"
                : "点“启动服务”，再用手机浏览器打开上面的地址");
        startBtn.setEnabled(!running);
        stopBtn.setEnabled(running);
        rebuildFiles();
    }

    private void rebuildFiles() {
        listBox.removeAllViews();
        List<File> files = listApks();
        if (files.isEmpty()) {
            emptyText.setText("还没有 APK：服务启动后，用手机浏览器上传即可出现在这里");
            listBox.addView(emptyText, PageUi.matchWrap(this));
            return;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm", Locale.US);
        for (File f : files) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams rowLp = PageUi.matchWrap(this);
            PageUi.margin(rowLp, this, 0, 5, 0, 0);
            listBox.addView(row, rowLp);

            row.addView(PageUi.text(this, f.getName(), 11f, PageUi.TEXT, true), PageUi.matchWrap(this));
            row.addView(PageUi.text(this,
                    FileTransferServer.formatSize(f.length()) + " · " + fmt.format(new Date(f.lastModified())),
                    8.5f, PageUi.MUTED, false), PageUi.matchWrap(this));

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams actLp = PageUi.matchWrap(this);
            PageUi.margin(actLp, this, 0, 3, 0, 2);
            row.addView(actions, actLp);
            actions.addView(smallAction("安装", PageUi.GREEN, v -> install(f)), smallLp(6));
            actions.addView(smallAction("蓝牙分享", PageUi.PINK, v -> shareBluetooth(f)), smallLp(6));
            actions.addView(smallAction("删除", PageUi.RED, v -> deleteFile(f)), smallLp(0));
        }
    }

    private LinearLayout.LayoutParams smallLp(float gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, PageUi.dp(this, 32), 1f);
        lp.setMargins(0, 0, PageUi.dp(this, gap), 0);
        return lp;
    }

    private Button smallAction(String label, int accent, View.OnClickListener listener) {
        Button b = PageUi.button(this, label, 9.5f, accent);
        b.setOnClickListener(listener);
        return b;
    }

    private List<File> listApks() {
        List<File> out = new ArrayList<>();
        if (root == null) return out;
        collect(root, out);
        out.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return out;
    }

    private void collect(File dir, List<File> into) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isDirectory()) collect(f, into);
            else if (f.getName().toLowerCase(Locale.US).endsWith(".apk")
                    || f.getName().toLowerCase(Locale.US).endsWith(".apks")) into.add(f);
        }
    }

    /** 把当前这个包的 APK 复制进共享目录，方便手机直接把车机上的版本下载走。 */
    private void copyOwnApk() {
        new Thread(() -> {
            try {
                File self = new File(getApplicationInfo().sourceDir);
                String name = "icarplay-" + IcarLauncherActivity.VERSION_LABEL.replace("v", "") + ".apk";
                File target = new File(root, name);
                if (target.exists() && target.length() == self.length()) return;
                File part = new File(root, name + ".part");
                try (InputStream in = new FileInputStream(self);
                     OutputStream out = new FileOutputStream(part)) {
                    byte[] buf = new byte[16 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                if (target.exists()) target.delete();
                part.renameTo(target);
                runOnUiThread(this::rebuildFiles);
            } catch (Throwable ignored) {
            }
        }, "icarplay-transfer-own-apk").start();
    }

    private void install(File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            toast("已调起系统安装确认：请核对版本后再确认");
        } catch (Throwable e) {
            toast("调起安装失败：" + e.getClass().getSimpleName());
        }
    }

    private void shareBluetooth(File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apk);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("application/vnd.android.package-archive");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                send.setPackage("com.android.bluetooth");
                startActivity(send);
                toast("已交给蓝牙发送：在手机上确认接收");
            } catch (Throwable noBluetoothShare) {
                Intent chooser = Intent.createChooser(send, "发送 APK 到手机");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(chooser);
            }
        } catch (Throwable e) {
            toast("分享失败：" + e.getClass().getSimpleName());
        }
    }

    private void deleteFile(File apk) {
        if (apk.delete()) {
            toast("已删除：" + apk.getName());
            refresh();
        } else {
            toast("删除失败，文件可能被其它进程占用");
        }
    }

    private void logText(String line) {
        if (logText != null) logText.setText(line);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(tick);
        if (server != null) server.stop();
        super.onDestroy();
    }
}
