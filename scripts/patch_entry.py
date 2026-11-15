# -*- coding: utf-8 -*-
"""接上「一键无线投屏」入口 + 版本号 0.7.9"""
import io
import re

NC = "app/src/main/java/com/rockjesus/icarplay/NetworkCenterActivity.java"
s = io.open(NC, encoding="utf-8").read()

OLD = '''        row2.addView(action("启动 AirPlay", PageUi.BLUE, v -> startAirPlay()), actionLp());
        row2.addView(action("停止 AirPlay", PageUi.RED, v -> stopAirPlay()), actionLp());
        actions.addView(row2, PageUi.matchWrap(this));
'''
NEW = '''        row2.addView(action("启动 AirPlay", PageUi.BLUE, v -> startAirPlay()), actionLp());
        row2.addView(action("停止 AirPlay", PageUi.RED, v -> stopAirPlay()), actionLp());
        actions.addView(row2, PageUi.matchWrap(this));

        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams row3lp = PageUi.matchWrap(this);
        PageUi.margin(row3lp, this, 0, 5, 0, 0);
        row3.addView(action("一键无线投屏向导", PageUi.CYAN, v -> openWirelessGuide()), actionLp());
        row3.addView(action("DLNA 投送接收", PageUi.PINK, v -> openDlna()), actionLp());
        row3.addView(action("连接补强", PageUi.PURPLE, v -> openLinkBoost()), actionLp());
        actions.addView(row3, row3lp);
'''
assert OLD in s
s = s.replace(OLD, NEW, 1)

ANCHOR = '''    private void openHotspotSettings() {'''
HELPERS = '''    private void openWirelessGuide() {
        try {
            startActivity(new Intent(this, WirelessGuideActivity.class));
        } catch (Exception e) {
            toast("投屏向导打不开");
        }
    }

    private void openDlna() {
        try {
            startActivity(new Intent(this, DlnaActivity.class));
        } catch (Exception e) {
            toast("DLNA 页打不开");
        }
    }

    private void openLinkBoost() {
        try {
            startActivity(new Intent(this, LinkBoostActivity.class));
        } catch (Exception e) {
            toast("连接补强打不开");
        }
    }

    private void openHotspotSettings() {'''
assert ANCHOR in s
s = s.replace(ANCHOR, HELPERS, 1)
io.open(NC, "w", encoding="utf-8").write(s)

# 版本号
G = "app/build.gradle.kts"
g = io.open(G, encoding="utf-8").read()
g = g.replace("versionCode 43", "versionCode 44").replace('versionName "0.7.8"', 'versionName "0.7.9"')
io.open(G, "w", encoding="utf-8").write(g)

L = "app/src/main/java/com/rockjesus/icarplay/IcarLauncherActivity.java"
l = io.open(L, encoding="utf-8").read()
l = l.replace('VERSION_LABEL = "v0.7.8"', 'VERSION_LABEL = "v0.7.9"')
io.open(L, "w", encoding="utf-8").write(l)

# Manifest
M = "app/src/main/AndroidManifest.xml"
m = io.open(M, encoding="utf-8").read()
NEW_ACT = '''        <activity
            android:name="com.rockjesus.icarplay.WirelessGuideActivity"
            android:exported="false"
            android:screenOrientation="landscape"
            android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden"
            android:theme="@android:style/Theme.Material.NoActionBar" />

'''
anchor = '''        <activity
            android:name="com.rockjesus.icarplay.DlnaActivity"'''
assert anchor in m
m = m.replace(anchor, NEW_ACT + anchor, 1)
io.open(M, "w", encoding="utf-8").write(m)

print("wired entry + version ok")
