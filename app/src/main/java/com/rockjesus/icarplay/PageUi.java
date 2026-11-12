package com.rockjesus.icarplay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.shilapi.xcertplay.carlife.WirelessNetwork;

/**
 * 统一车机 UI：深色玻璃面板、强调色描边、统一的缩放和不滚动单屏规则。
 * 所有页面共用同一套色板和尺寸计算，避免首页与二级页各写一套样式。
 */
public final class PageUi {

    public static final int BG = Color.parseColor("#070C14");
    public static final int BG_MID = Color.parseColor("#0D1926");
    public static final int CARD = Color.parseColor("#101B28");
    public static final int CARD_ALT = Color.parseColor("#162638");
    public static final int TEXT = Color.parseColor("#F4F8FF");
    public static final int MUTED = Color.parseColor("#91A5BC");
    public static final int BLUE = Color.parseColor("#4DA8FF");
    public static final int GREEN = Color.parseColor("#3DDC97");
    public static final int RED = Color.parseColor("#FF737D");
    public static final int BORDER = Color.parseColor("#24374D");
    public static final int GOLD = Color.parseColor("#FFC857");
    public static final int CYAN = Color.parseColor("#45D9D9");
    public static final int PURPLE = Color.parseColor("#A78BFA");

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

    /** 深色玻璃面板：自上而下的细腻渐变 + 强调色描边。 */
    public static GradientDrawable surface(Context c, int accent, float radiusDp) {
        int top = blend(CARD_ALT, accent, 0.13f);
        int bottom = blend(CARD, accent, 0.04f);
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(c, radiusDp));
        d.setStroke(dp(c, 1), blend(BORDER, accent, 0.34f));
        return d;
    }

    /** 可点击面板的按下反馈，避免所有卡片看起来都是静态图片。 */
    public static StateListDrawable surfaceState(Context c, int accent, float radiusDp) {
        StateListDrawable s = new StateListDrawable();
        GradientDrawable pressed = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{blend(CARD_ALT, accent, 0.26f), blend(CARD_ALT, accent, 0.10f)});
        pressed.setCornerRadius(dp(c, radiusDp));
        pressed.setStroke(dp(c, 1), blend(BORDER, accent, 0.65f));
        s.addState(new int[]{android.R.attr.state_pressed}, pressed);
        s.addState(new int[]{}, surface(c, accent, radiusDp));
        return s;
    }

    public static int blend(int base, int accent, float ratio) {
        float r = ratio < 0 ? 0 : Math.min(1, ratio);
        int red = (int) (Color.red(accent) * r + Color.red(base) * (1f - r));
        int green = (int) (Color.green(accent) * r + Color.green(base) * (1f - r));
        int blue = (int) (Color.blue(accent) * r + Color.blue(base) * (1f - r));
        return Color.rgb(red, green, blue);
    }

    public static LinearLayout card(Context c, int accent, int paddingX, int paddingY) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(c, paddingX), dp(c, paddingY), dp(c, paddingX), dp(c, paddingY));
        box.setBackground(surface(c, accent, 14));
        box.setElevation(dp(c, 1));
        return box;
    }

    /** 小号状态胶囊，用于“运行中 / 待连接 / 未配置”。 */
    public static TextView statusPill(Context c, String label, int accent) {
        TextView pill = text(c, label, 9.5f, accent, true);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(c, 8), dp(c, 2), dp(c, 8), dp(c, 2));
        pill.setBackground(rounded(c, blend(CARD, accent, 0.16f),
                blend(BORDER, accent, 0.55f), 20));
        return pill;
    }

    /** 方形图标底座，二级页和首页共用。 */
    public static FrameLayout iconBadge(Context c, int iconRes, int accent, float sizeDp) {
        FrameLayout badge = new FrameLayout(c);
        badge.setBackground(rounded(c, blend(CARD_ALT, accent, 0.18f),
                blend(BORDER, accent, 0.42f), 12));
        ImageView icon = new ImageView(c);
        icon.setImageResource(iconRes);
        icon.setColorFilter(accent);
        int p = dp(c, sizeDp * 0.22f);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.setMargins(p, p, p, p);
        badge.addView(icon, lp);
        return badge;
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
        StateListDrawable bg = new StateListDrawable();
        GradientDrawable pressed = rounded(c, blend(CARD_ALT, accent, 0.30f), accent, 12);
        bg.addState(new int[]{android.R.attr.state_pressed}, pressed);
        bg.addState(new int[]{android.R.attr.state_enabled},
                rounded(c, blend(CARD, accent, 0.10f), blend(BORDER, accent, 0.42f), 12));
        bg.addState(new int[]{}, rounded(c, blend(BG_MID, accent, 0.05f),
                blend(BORDER, accent, 0.25f), 12));
        b.setBackground(bg);
        return b;
    }

    /** 本机当前 Wi‑Fi / 有线 IPv4，用于在页面上直接告诉用户“填哪个网段”。 */
    public static String localNetwork(Context c) {
        try {
            String value = WirelessNetwork.INSTANCE.describe(c);
            return value == null || value.isEmpty() ? "未连接网络" : value;
        } catch (Throwable ignored) {
            return "未连接网络";
        }
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
