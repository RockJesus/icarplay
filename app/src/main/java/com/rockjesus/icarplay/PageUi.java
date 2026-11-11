package com.rockjesus.icarplay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 统一二级页的单屏 UI：与首页同一套配色、圆角和缩放规则。
 * 所有页面不放进 ScrollView，靠 weight + 最小高度把内容压进一屏。
 */
public final class PageUi {

    public static final int BG = Color.parseColor("#0E1216");
    public static final int CARD = Color.parseColor("#161B22");
    public static final int CARD_ALT = Color.parseColor("#1B2230");
    public static final int TEXT = Color.parseColor("#E6EDF3");
    public static final int MUTED = Color.parseColor("#8B949E");
    public static final int BLUE = Color.parseColor("#58A6FF");
    public static final int GREEN = Color.parseColor("#3FB950");
    public static final int RED = Color.parseColor("#F85149");
    public static final int BORDER = Color.parseColor("#30363D");

    private PageUi() {
    }

    /** 与首页相同的缩放：1000dp / 560dp 基准，限制在 0.9~1.45。 */
    public static float scale(Context c) {
        android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
        float widthDp = dm.widthPixels / dm.density;
        float heightDp = dm.heightPixels / dm.density;
        float raw = Math.min(widthDp / 1000f, heightDp / 560f);
        return Math.max(0.9f, Math.min(1.45f, raw));
    }

    public static boolean narrow(Context c) {
        return c.getResources().getDisplayMetrics().widthPixels
                / c.getResources().getDisplayMetrics().density < 760f;
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density * scale(c));
    }

    public static void textSize(TextView view, float base) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, base * scale(view.getContext()));
    }

    public static TextView text(Context c, String value, float size, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextColor(color);
        textSize(t, size);
        t.setLineSpacing(dp(c, 1.5f), 1f);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    public static GradientDrawable rounded(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) d.setStroke(dp(c, 1), stroke);
        return d;
    }

    public static LinearLayout card(Context c, int accent, int paddingX, int paddingY) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(c, paddingX), dp(c, paddingY), dp(c, paddingX), dp(c, paddingY));
        box.setBackground(rounded(c, CARD, accent, 10));
        return box;
    }

    public static Button button(Context c, String label, float size, int accent) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        textSize(b, size);
        b.setTextColor(TEXT);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(c, 6), 0, dp(c, 6), 0);
        b.setStateListAnimator(null);
        b.setBackground(rounded(c, CARD, accent, 8));
        return b;
    }

    public static LinearLayout.LayoutParams matchWrap(Context c) {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams wrap(Context c) {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams weight(Context c, float weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight);
    }

    public static LinearLayout.LayoutParams fixed(Context c, float heightDp) {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(c, heightDp));
    }

    public static void margin(LinearLayout.LayoutParams lp, Context c, float left, float top,
                              float right, float bottom) {
        lp.setMargins(dp(c, left), dp(c, top), dp(c, right), dp(c, bottom));
    }

    /** 顶部标题栏：标题 + 右侧版本号。 */
    public static LinearLayout header(Context c, String title, String version) {
        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView t = text(c, title, 20f, TEXT, true);
        header.addView(t, wrap(c));

        android.view.View spacer = new android.view.View(c);
        header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        if (version != null && !version.isEmpty()) {
            header.addView(text(c, version, 11f, MUTED, false), wrap(c));
        }
        return header;
    }

    /** 统一底部返回按钮。 */
    public static Button back(Context c) {
        Button b = button(c, "←  返回主界面", 13f, BLUE);
        b.setTypeface(null, Typeface.BOLD);
        b.setBackground(rounded(c, CARD_ALT, BLUE, 10));
        return b;
    }
}
