package com.rockjesus.icarplay;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.security.MessageDigest;

/**
 * 版本更新页：查 GitHub Releases 最新版 → 下载 → 校验 → 交给系统安装框。
 *
 * <p>下载完先比字节数与 Release 声明的 SHA-256，再比签名指纹，任一不符就把文件删掉不装；
 * 装包本身走系统安装确认，应用不做静默安装。
 */
public class UpdateActivity extends FitActivity {

    private TextView statePill;
    private TextView versionText;
    private TextView notesText;
    private TextView logText;
    private Button checkBtn;
    private Button updateBtn;
    private ProgressBar bar;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile UpdateChecker.Info info;
    private volatile boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackground(PageUi.background(this));
        int pad = PageUi.dp(this, 18);
        shell.setPadding(pad, PageUi.dp(this, 10), pad, PageUi.dp(this, 8));
        setFitContentView(shell);

        shell.addView(PageUi.header(this, "版本更新", IcarLauncherActivity.VERSION_LABEL),
                PageUi.matchWrap(this));

        TextView sub = PageUi.text(this,
                "一键检查并安装新版本 · 来源 GitHub Releases · 下载后校验 SHA-256 与签名",
                10.5f, PageUi.MUTED, false);
        sub.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams subLp = PageUi.matchWrap(this);
        PageUi.margin(subLp, this, 0, 3, 0, 7);
        shell.addView(sub, subLp);

        LinearLayout main = buildMainCard();
        LinearLayout.LayoutParams mainLp = PageUi.matchWrap(this);
        PageUi.margin(mainLp, this, 0, 0, 0, 8);
        shell.addView(main, mainLp);

        LinearLayout notes = buildNotesCard();
        LinearLayout.LayoutParams notesLp = PageUi.weight(this, 1);
        PageUi.margin(notesLp, this, 0, 0, 0, 8);
        shell.addView(notes, notesLp);

        shell.addView(buildFoot(), PageUi.matchWrap(this));

        startCheck();
    }

    private LinearLayout buildMainCard() {
        LinearLayout card = PageUi.card(this, PageUi.CYAN, 14, 12);

        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.HORIZONTAL);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.addView(PageUi.text(this, "当前版本", 11f, PageUi.MUTED, false));

        versionText = PageUi.text(this, IcarLauncherActivity.VERSION_LABEL + "  →  检查中…",
                13f, PageUi.TEXT, true);
        PageUi.mono(versionText);
        LinearLayout.LayoutParams vlp = PageUi.wrap(this);
        PageUi.margin(vlp, this, 8, 0, 0, 0);
        title.addView(versionText, vlp);

        statePill = PageUi.statusPill(this, "检查中", PageUi.MUTED);
        LinearLayout.LayoutParams plp = PageUi.wrap(this);
        PageUi.margin(plp, this, 8, 0, 0, 0);
        title.addView(statePill, plp);
        card.addView(title, PageUi.matchWrap(this));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = PageUi.matchWrap(this);
        PageUi.margin(rowLp, this, 0, 8, 0, 0);

        checkBtn = PageUi.button(this, "检查更新", 11f, PageUi.CYAN);
        checkBtn.setOnClickListener(v -> startCheck());
        row.addView(checkBtn, PageUi.weight(this, 1));

        updateBtn = PageUi.button(this, "一键下载并安装", 11f, PageUi.GREEN);
        updateBtn.setEnabled(false);
        updateBtn.setOnClickListener(v -> startUpdate());
        LinearLayout.LayoutParams ulp = PageUi.weight(this, 1.6f);
        PageUi.margin(ulp, this, 8, 0, 0, 0);
        row.addView(updateBtn, ulp);
        card.addView(row, rowLp);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgress(0);
        bar.setVisibility(View.GONE);
        LinearLayout.LayoutParams blp = PageUi.matchWrap(this);
        PageUi.margin(blp, this, 0, 8, 0, 0);
        card.addView(bar, blp);

        logText = PageUi.text(this, "点「检查更新」读取 GitHub 上最新的 Release", 9.5f, PageUi.MUTED, false);
        LinearLayout.LayoutParams llp = PageUi.matchWrap(this);
        PageUi.margin(llp, this, 0, 6, 0, 0);
        card.addView(logText, llp);
        return card;
    }

    private LinearLayout buildNotesCard() {
        LinearLayout card = PageUi.card(this, PageUi.PURPLE, 14, 10);

        TextView head = PageUi.text(this, "更新内容", 11f, PageUi.PURPLE, true);
        card.addView(head, PageUi.matchWrap(this));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        notesText = PageUi.text(this, "等待检查结果…", 10f, PageUi.MUTED, false);
        notesText.setLineSpacing(PageUi.dp(this, 2), 1f);
        scroll.addView(notesText, PageUi.matchWrap(this));
        LinearLayout.LayoutParams slp = PageUi.weight(this, 1);
        PageUi.margin(slp, this, 0, 6, 0, 0);
        card.addView(scroll, slp);
        return card;
    }

    private View buildFoot() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);

        box.addView(PageUi.text(this, "安装由系统确认框完成；若装不上，先允许本应用「安装未知应用」。",
                9f, PageUi.MUTED, false), PageUi.weight(this, 1));

        Button allow = PageUi.button(this, "允许安装", 9.5f, PageUi.GOLD);
        allow.setOnClickListener(v -> {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception e) {
                toast("这个系统没有开放该项设置");
            }
        });
        box.addView(allow, PageUi.wrap(this));

        Button back = PageUi.back(this);
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams blp = PageUi.wrap(this);
        PageUi.margin(blp, this, 8, 0, 0, 0);
        box.addView(back, blp);
        return box;
    }

    private void startCheck() {
        if (busy) return;
        busy = true;
        updateBtn.setEnabled(false);
        statePill.setText("检查中");
        statePill.setTextColor(PageUi.MUTED);
        log("正在读取 GitHub Releases …");
        new Thread(() -> {
            UpdateChecker.Info got = null;
            String err = null;
            try {
                got = UpdateChecker.latest(12000);
            } catch (Throwable e) {
                err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final UpdateChecker.Info result = got;
            final String failure = err;
            ui.post(() -> onChecked(result, failure));
        }, "update-check").start();
    }

    private void onChecked(UpdateChecker.Info got, String failure) {
        busy = false;
        if (got == null || got.tag.isEmpty()) {
            statePill.setText("检查失败");
            statePill.setTextColor(PageUi.RED);
            log("连不上 GitHub（" + (failure == null ? "无返回" : failure) + "），稍后再试或改用手机传 APK。");
            return;
        }
        info = got;
        boolean newer = UpdateChecker.isNewer(IcarLauncherActivity.VERSION_LABEL, got.tag);
        versionText.setText(IcarLauncherActivity.VERSION_LABEL + "  →  " + got.tag);
        if (newer) {
            statePill.setText("有新版本");
            statePill.setTextColor(PageUi.GREEN);
            updateBtn.setEnabled(got.hasApk);
            log("发现 " + got.tag + "：" + fmtSize(got.size) + "，点「一键下载并安装」开始。");
        } else {
            statePill.setText("已是最新");
            statePill.setTextColor(PageUi.CYAN);
            updateBtn.setEnabled(false);
            log("当前已是最新版本，无需更新。");
        }
        notesText.setText(got.notes.isEmpty() ? "（这版没有写更新说明）" : got.notes);
    }

    private void startUpdate() {
        UpdateChecker.Info got = info;
        if (got == null || !got.hasApk) {
            toast("先点「检查更新」拿到新版本信息");
            return;
        }
        if (busy) return;
        busy = true;
        updateBtn.setEnabled(false);
        checkBtn.setEnabled(false);
        bar.setVisibility(View.VISIBLE);
        bar.setProgress(0);
        log("开始下载 " + got.tag + " …");

        new Thread(() -> {
            File apk = null;
            String err = null;
            try {
                File dir = new File(getExternalFilesDir(null), "update");
                apk = UpdateChecker.download(got, dir, (done, total) -> ui.post(() -> {
                    if (total > 0) bar.setProgress((int) (done * 1000 / total));
                    log("下载中 " + fmtSize(done) + " / " + fmtSize(total));
                }), (idx, count, host, tryIndex) -> ui.post(() ->
                        log("下载源 " + idx + "/" + count + " · " + host + " · 第 " + tryIndex + " 次")));
            } catch (Throwable e) {
                err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final File file = apk;
            final String failure = err;
            ui.post(() -> onDownloaded(got, file, failure));
        }, "update-download").start();
    }

    private void onDownloaded(UpdateChecker.Info got, File apk, String failure) {
        busy = false;
        checkBtn.setEnabled(true);
        bar.setVisibility(View.GONE);
        if (apk == null) {
            log("下载失败：" + (failure == null ? "未知原因" : failure));
            updateBtn.setEnabled(true);
            return;
        }
        if (!sameSignature(apk)) {
            apk.delete();
            log("签名与当前安装包不一致，已删除下载文件（防止装到被篡改的包）。");
            updateBtn.setEnabled(true);
            return;
        }
        log("下载完成并校验通过，正在打开系统安装框…");
        install(apk);
    }

    private void install(File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            updateBtn.setEnabled(true);
        } catch (Throwable e) {
            log("打不开安装框：" + e.getClass().getSimpleName() + "，去「允许安装」里开启后再试。");
        }
    }

    /** 下载包的签名必须和当前安装的包一致，否则拒绝安装。 */
    private boolean sameSignature(File apk) {
        try {
            PackageManager pm = getPackageManager();
            PackageInfo mine = pm.getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES);
            PackageInfo target = pm.getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNATURES);
            if (mine.signatures == null || target == null || target.signatures == null) return false;
            String a = fingerprint(mine.signatures);
            String b = fingerprint(target.signatures);
            return a != null && a.equals(b);
        } catch (Throwable e) {
            return false;
        }
    }

    private String fingerprint(Signature[] sigs) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (Signature s : sigs) sb.append(UpdateChecker.hex(md.digest(s.toByteArray())));
            return sb.toString();
        } catch (Throwable e) {
            return null;
        }
    }

    private void log(String line) {
        logText.setText(line);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private String fmtSize(long bytes) {
        if (bytes <= 0) return "—";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0);
    }
}
