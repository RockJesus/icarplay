package com.rockjesus.icarplay;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 认证后端 / 音频通道 / 网络模式。 */
public class AuthSettingsActivity extends Activity {

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("icarplay", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0E1216"));
        int pad = dp(22);
        root.setPadding(pad, dp(16), pad, dp(16));

        TextView title = new TextView(this);
        title.setText("认证与连接设置");
        title.setTextColor(Color.parseColor("#E6EDF3"));
        title.setTextSize(20);
        root.addView(title, wrap());

        TextView sub = new TextView(this);
        sub.setText("CarPlay 能不能连，取决于认证后端；有没有声音，取决于音频通道。");
        sub.setTextColor(Color.parseColor("#8B949E"));
        sub.setTextSize(12);
        sub.setPadding(0, dp(6), 0, dp(12));
        root.addView(sub, wrap());

        root.addView(section("CarPlay 认证后端"), wrap());
        RadioGroup auth = group(prefs.getInt("auth_backend", 0),
                new String[]{"未配置", "本地证书（.p7b + .pk8）",
                        "MFi 芯片（板载 I2C / CH341）", "远程 MFI 服务"});
        auth.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup g, int id) {
                prefs.edit().putInt("auth_backend", id - 1000).apply();
            }
        });
        root.addView(auth, wrap());

        root.addView(section("音频通道"), wrap());
        RadioGroup audio = group(prefs.getInt("audio_channel", 0),
                new String[]{"默认通道", "通道 9（车机实车验证）"});
        audio.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup g, int id) {
                prefs.edit().putInt("audio_channel", id - 1000).apply();
            }
        });
        root.addView(audio, wrap());

        root.addView(section("无线网络模式"), wrap());
        RadioGroup net = group(prefs.getInt("network_mode", 0),
                new String[]{"现有 Wi-Fi / 同一局域网（推荐）", "车机热点（部分车机上大概率不可用）"});
        net.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup g, int id) {
                prefs.edit().putInt("network_mode", id - 1000).apply();
            }
        });
        root.addView(net, wrap());

        TextView hint = new TextView(this);
        hint.setText("\n本地证书放这里：\n"
                + CarPlayCore.identityHint(this) + "\n\n"
                + "· 证书：DER 编码 PKCS#7 (.p7b)\n"
                + "· 私钥：未加密 DER PKCS#8 (.pk8)\n"
                + "· MFi 芯片：需要硬件接线或 CH341 USB 桥\n"
                + "· 远程 MFI：/mfi/certificate、/mfi/sign、/mfi/reset");
        hint.setTextColor(Color.parseColor("#8B949E"));
        hint.setTextSize(11);
        root.addView(hint, wrap());

        Button save = new Button(this);
        save.setText("保存并返回");
        save.setAllCaps(false);
        save.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Toast.makeText(AuthSettingsActivity.this, "已保存", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
        LinearLayout.LayoutParams slp = wrap();
        slp.topMargin = dp(14);
        root.addView(save, slp);

        Button reset = new Button(this);
        reset.setText("重置为未配置");
        reset.setAllCaps(false);
        reset.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                prefs.edit().putInt("auth_backend", 0).apply();
                Toast.makeText(AuthSettingsActivity.this, "已重置认证后端", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
        root.addView(reset, wrap());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    private TextView section(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.parseColor("#3DDC84"));
        t.setTextSize(14);
        t.setPadding(0, dp(12), 0, dp(4));
        return t;
    }

    private RadioGroup group(int checked, String[] labels) {
        RadioGroup g = new RadioGroup(this);
        g.setOrientation(RadioGroup.VERTICAL);
        for (int i = 0; i < labels.length; i++) {
            RadioButton b = new RadioButton(this);
            b.setId(1000 + i);
            b.setText(labels[i]);
            b.setTextColor(Color.parseColor("#C9D1D9"));
            b.setTextSize(13);
            b.setPadding(0, dp(4), 0, dp(4));
            g.addView(b);
        }
        g.check(1000 + Math.min(Math.max(checked, 0), labels.length - 1));
        return g;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
