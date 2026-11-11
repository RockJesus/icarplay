package com.rockjesus.icarplay;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 认证与连接设置：单屏三列全部显示，不滚动。
 * 三组选项分别是认证后端、音频通道、无线网络模式。
 */
public class AuthSettingsActivity extends Activity {

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("icarplay", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PageUi.BG);
        root.setPadding(PageUi.dp(this, 18), PageUi.dp(this, 10),
                PageUi.dp(this, 18), PageUi.dp(this, 8));
        setContentView(root);

        root.addView(PageUi.header(this, "认证与连接设置", "v0.4.6"), PageUi.matchWrap(this));

        TextView sub = PageUi.text(this,
                "CarPlay 能不能连取决于认证后端；有没有声音取决于音频通道。",
                10.5f, PageUi.MUTED, false);
        LinearLayout.LayoutParams subLp = PageUi.matchWrap(this);
        PageUi.margin(subLp, this, 0, 3, 0, 6);
        root.addView(sub, subLp);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(content, contentLp);

        content.addView(column("CarPlay 认证后端",
                new String[]{"未配置", "本地证书（.p7b + .pk8）",
                        "MFi 芯片（I2C / CH341）", "远程 MFI 服务"},
                prefs.getInt("auth_backend", 0), "auth_backend"), columnLp());
        content.addView(column("音频通道",
                new String[]{"默认通道", "通道 9（实车验证）"},
                prefs.getInt("audio_channel", 0), "audio_channel"), columnLp());
        content.addView(column("无线网络模式",
                new String[]{"现有 Wi-Fi / 同一局域网", "车机热点（可能不可用）"},
                prefs.getInt("network_mode", 0), "network_mode"), columnLp());

        TextView hint = PageUi.text(this,
                "本地证书目录：" + CarPlayCore.identityHint(this) + "\n"
                        + "证书用 DER 编码 PKCS#7（.p7b），私钥用未加密 DER PKCS#8（.pk8）。\n"
                        + "MFi 芯片需要硬件接线或 CH341 USB 桥；远程 MFI 使用 /mfi/certificate、/mfi/sign、/mfi/reset。",
                9.5f, PageUi.MUTED, false);
        hint.setPadding(PageUi.dp(this, 10), PageUi.dp(this, 6),
                PageUi.dp(this, 10), PageUi.dp(this, 6));
        hint.setBackground(PageUi.rounded(this, PageUi.CARD, PageUi.BORDER, 8));
        LinearLayout.LayoutParams hintLp = PageUi.matchWrap(this);
        PageUi.margin(hintLp, this, 0, 6, 0, 0);
        root.addView(hint, hintLp);

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams toolsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, PageUi.dp(this, 42));
        PageUi.margin(toolsLp, this, 0, 8, 0, 0);
        root.addView(tools, toolsLp);

        tools.addView(tool("保存并返回", PageUi.BLUE, v -> {
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
            finish();
        }));
        tools.addView(tool("重置为未配置", PageUi.RED, v -> {
            prefs.edit().putInt("auth_backend", 0).apply();
            Toast.makeText(this, "已重置认证后端", Toast.LENGTH_SHORT).show();
            finish();
        }));
        tools.addView(tool("返回主界面", PageUi.GREEN, v -> finish()));
    }

    private LinearLayout.LayoutParams columnLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        PageUi.margin(lp, this, 4, 0, 4, 0);
        return lp;
    }

    private LinearLayout column(String title, String[] labels, int checked, String key) {
        LinearLayout box = PageUi.card(this, PageUi.BORDER, 9, 8);
        TextView t = PageUi.text(this, title, 11f, PageUi.GREEN, true);
        box.addView(t, PageUi.matchWrap(this));

        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        LinearLayout.LayoutParams groupLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        PageUi.margin(groupLp, this, 0, 4, 0, 0);
        box.addView(group, groupLp);

        for (int i = 0; i < labels.length; i++) {
            RadioButton b = new RadioButton(this);
            b.setId(1000 + i);
            b.setText(labels[i]);
            b.setTextColor(PageUi.TEXT);
            PageUi.textSize(b, 10.5f);
            b.setPadding(0, PageUi.dp(this, 1), 0, PageUi.dp(this, 1));
            group.addView(b, PageUi.matchWrap(this));
        }
        group.check(1000 + Math.min(Math.max(checked, 0), labels.length - 1));
        group.setOnCheckedChangeListener((g, id) ->
                prefs.edit().putInt(key, id - 1000).apply());
        return box;
    }

    private Button tool(String label, int accent, android.view.View.OnClickListener l) {
        Button b = PageUi.button(this, label, 11.5f, accent);
        b.setOnClickListener(l);
        b.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        PageUi.margin(lp, this, 4, 0, 4, 0);
        b.setLayoutParams(lp);
        return b;
    }
}
