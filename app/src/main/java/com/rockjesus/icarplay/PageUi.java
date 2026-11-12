package com.rockjesus.icarplay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 统一二级页的单屏 UI：与首页同一套配色、圆角和缩放规则。
 * 所有页面不放进 ScrollView，靠 weight + 最小高度把内容压进一屏。
 */
public final class PageUi {

    public static final int BG = Color.parseColor("#0A0F17");
    public static final int BG_MID = Color.parseColor("#111C2B");
    public static final int CARD = Color.parseColor("#121A25");
    public static final int CARD_ALT = Color.parseColor("#182537");
    public static final int TEXT = Color.parseColor("#F2F6FC");
    public static final int MUTED = Color.parseColor("#93A4B8");
    public static final int BLUE = Color.parseColor("#5CB8FF");
    public static final int GREEN = Color.parseColor("#3DD68C");
    public static final int RED = Color.parseColor("#FF6B6B");
    public static final int BORDER = Color.parseColor("#27364A");
    public static final int GOLD = Color.parseColor("#F5C451");

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

    /** 页面背景：深色纵向渐变，避免大面积纯黑。 */
    public static GradientDrawable background(Context c) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#0B111B"), BG_MID, Color.parseColor("#080C12")});
        d.setShape(GradientDrawable.RECTANGLE);
        return d;
    }

    public static void applyBackground(View view) {
        view.setBackground(background(view.getContext()));
    }

    /** 半透明强调色，用于功能卡片的轻量着色。 */
    public static int tint(int accent, float ratio) {
        int r = (int) (Color.red(accent) * ratio + Color.red(CARD) * (1f - ratio));
        int g = (int) (Color.green(accent) * ratio + Color.green(CARD) * (1f - ratio));
        int b = (int) (Color.blue(accent) * ratio + Color.blue(CARD) * (1f - ratio));
        return Color.rgb(r, g, b);
    }

    public static LinearLayout card(Context c, int accent, int paddingX, int paddingY) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(c, paddingX), dp(c, paddingY), dp(c, paddingX), dp(c, paddingY));
        box.setBackground(rounded(c, tint(accent, 0.10f), tint(accent, 0.42f), 14));
        box.setElevation(dp(c, 1));
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
        b.setPadding(dp(c, 8), 0, dp(c, 8), 0);
        b.setStateListAnimator(null);
        b.setBackground(rounded(c, tint(accent, 0.08f), tint(accent, 0.38f), 12));
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

    /** 顶部标题栏：强调竖条 + 标题 + 右侧版本胶囊。 */
    public static LinearLayout header(Context c, String title, String version) {
        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        View accent = new View(c);
        accent.setBackground(rounded(c, BLUE, 0, 3));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(c, 3), dp(c, 20));
        alp.setMargins(0, 0, dp(c, 9), 0);
        header.addView(accent, alp);

        TextView t = text(c, title, 22f, TEXT, true);
        header.addView(t, wrap(c));

        View spacer = new View(c);
        header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        if (version != null && !version.isEmpty()) {
            TextView pill = text(c, version, 10f, MUTED, false);
            pill.setPadding(dp(c, 9), dp(c, 3), dp(c, 9), dp(c, 3));
            pill.setBackground(rounded(c, CARD_ALT, BORDER, 20));
            header.addView(pill, wrap(c));
        }
        return header;
    }

    /** 统一底部返回按钮。 */
    public static Button back(Context c) {
        Button b = button(c, "←  返回主界面", 13f, BLUE);
        b.setTypeface(null, Typeface.BOLD);
        b.setBackground(rounded(c, tint(BLUE, 0.14f), BLUE, 12));
        return b;
    }
}
