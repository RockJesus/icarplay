package com.rockjesus.icarplay;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

/**
 * 屏幕自适应基类。
 *
 * <p>车机分辨率跨度很大（1920×720 / 1600×720 / 1280×720 / 1024×600，密度还各不相同），
 * 所以这里做两件事：
 * <ol>
 *   <li><b>归一化密度</b>：把逻辑宽度收敛到 960–1280dp 区间，避免同一份 dp 布局在
 *       高密度车机上被压扁、在低密度车机上被拉散；同时把字体缩放固定为 1.0，
 *       防止系统字体大小设置把卡片撑破。</li>
 *   <li><b>自动滚动兜底</b>：页面内容超过一屏（比如 8:3 超宽屏只有 480dp 高）时，
 *       整页可上下滚动，不会出现按钮被裁掉、点不到的情况。</li>
 * </ol>
 */
public class FitActivity extends Activity {

    /**
     * 只在密度明显异常时才归一化：低于 720dp 太挤、高于 1500dp 太空。
     * 常见的 853–1280dp 车机（1280×720@240、1920×720@300）保持原样，
     * 避免和 PageUi.scale 的二次缩放打架。
     */
    private static final float TARGET_MIN_DP = 720f;
    private static final float NORMAL_MIN_DP = 960f;
    private static final float TARGET_MAX_DP = 1500f;
    private static final float NORMAL_MAX_DP = 1280f;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(fit(base));
    }

    /** 计算归一化后的 Context；异常时原样返回，绝不因为适配把页面搞崩。 */
    static Context fit(Context base) {
        try {
            DisplayMetrics dm = base.getResources().getDisplayMetrics();
            if (dm == null || dm.density <= 0) return base;
            float widthDp = dm.widthPixels / dm.density;
            float ratio = 1f;
            if (widthDp < TARGET_MIN_DP) {
                ratio = NORMAL_MIN_DP / widthDp;
            } else if (widthDp > TARGET_MAX_DP) {
                ratio = NORMAL_MAX_DP / widthDp;
            } else {
                // 已在合理区间：只统一字体缩放，不动密度
                Configuration c = new Configuration(base.getResources().getConfiguration());
                c.fontScale = 1.0f;
                return base.createConfigurationContext(c);
            }
            ratio = Math.max(0.75f, Math.min(1.6f, ratio));
            int dpi = Math.round(dm.densityDpi * ratio);
            dpi = Math.max(120, Math.min(320, dpi));

            Configuration c = new Configuration(base.getResources().getConfiguration());
            c.densityDpi = dpi;
            c.fontScale = 1.0f;
            return base.createConfigurationContext(c);
        } catch (Throwable ignored) {
            return base;
        }
    }

    /** 显示主内容；内容高于视口时可滚动，低于视口时按原布局权重铺满。 */
    protected void setFitContentView(View root) {
        try {
            if (root instanceof ScrollView) {
                setContentView(root);
                return;
            }
            int[] pad = {root.getPaddingLeft(), root.getPaddingTop(), root.getPaddingRight(),
                    root.getPaddingBottom()};
            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            root.setPadding(0, 0, 0, 0);
            ViewGroup.LayoutParams lp = root.getLayoutParams();
            scroll.addView(root, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            scroll.setPadding(pad[0], pad[1], pad[2], pad[3]);
            if (lp == null) {
                lp = new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            }
            setContentView(scroll, lp);
        } catch (Throwable e) {
            // 兜底：适配失败就直接按原样显示，不让页面起不来
            setContentView(root);
        }
    }
}
