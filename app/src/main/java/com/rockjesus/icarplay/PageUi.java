package com.rockjesus.icarplay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
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
 * 赛博朋克车机 UI：近黑底色、霓虹强调色、彩虹渐变描边与统一字阶。
 * 所有页面共用这一套色板和绘制逻辑，避免首页与二级页各写一套样式。
 */
public final class PageUi {

    public static final int BG = Color.parseColor("#04050A");
    public static final int BG_MID = Color.parseColor("#07101C");
    public static final int CARD = Color.parseColor("#0A0E17");
    public static final int CARD_ALT = Color.parseColor("#101726");
    public static final int TEXT = Color.parseColor("#F6F8FF");
    public static final int MUTED = Color.parseColor("#8D9BB3");
    public static final int BORDER = Color.parseColor("#223047");

    /** 霓虹主色：青、紫、粉、金、薄荷绿。 */
    public static final int BLUE = Color.parseColor("#00E5FF");
    public static final int CYAN = Color.parseColor("#38F2FF");
    public static final int GREEN = Color.parseColor("#63FFA6");
    public static final int GOLD = Color.parseColor("#FFC14D");
    public static final int RED = Color.parseColor("#FF3D7F");
    public static final int PINK = Color.parseColor("#FF5FD2");
    public static final int PURPLE = Color.parseColor("#9B6CFF");

    /** 彩虹渐变顺序，只用于细描边、强调条和标题。 */
    public static final int[] RAINBOW = {BLUE, CYAN, GREEN, GOLD, PINK, PURPLE};
    public static final int[] TITLE_GRADIENT = {TEXT, CYAN, PURPLE, PINK};

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

    /** 标题字：加粗 + 轻微字距，避免中文车机字体糊成一片。 */
    public static void display(TextView view) {
        view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        view.setLetterSpacing(0.03f);
    }

    /** 技术信息用等宽字，版本号、IP、端口、指标更像仪表盘。 */
    public static void mono(TextView view) {
        view.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        view.setLetterSpacing(0.06f);
    }

    /** 给关键文字加一层很淡的霓虹辉光，不改变可读性。 */
    public static void neon(TextView view, int color) {
        view.setShadowLayer(dp(view.getContext(), 6),
                0, 0,
                Color.argb(110, Color.red(color), Color.green(color), Color.blue(color)));
    }

    /** 文字本身使用多色渐变，跟随实际宽度铺满。 */
    public static void gradientText(TextView view, int[] colors) {
        view.post(() -> {
            int width = view.getWidth();
            if (width <= 0) width = dp(view.getContext(), 240);
            view.getPaint().setShader(new LinearGradient(
                    0, 0, width, 0, colors, null, Shader.TileMode.CLAMP));
            view.invalidate();
        });
    }

    public static GradientDrawable rounded(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) d.setStroke(dp(c, 1), stroke);
        return d;
    }

    /** 彩虹强调条：给页头竖条、卡片下划线、进度线使用。 */
    public static GradientDrawable rainbow(Context c, float radiusDp) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, RAINBOW);
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    /** 页面背景：近黑底上叠一层极暗的青紫极光，避免大面积死黑。 */
    public static GradientDrawable background(Context c) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.parseColor("#04050A"),
                        Color.parseColor("#06111D"),
                        Color.parseColor("#090A18"),
                        Color.parseColor("#14091C"),
                        Color.parseColor("#04050A")
                });
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

    /**
     * 彩虹描边 + 深色渐变内芯。描边只有 1dp 多一点，负责“霓虹感”；
     * 内芯仍是近黑玻璃，保证文字对比度和车机可读性。
     */
    private static Drawable frame(Context c, int accent, float radiusDp, boolean pressed) {
        float radius = dp(c, radiusDp);
        int inset = Math.max(1, dp(c, 1.3f));

        GradientDrawable rim = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        accent,
                        blend(accent, CYAN, 0.55f),
                        blend(accent, GREEN, 0.35f),
                        blend(accent, GOLD, 0.35f),
                        blend(accent, PINK, 0.55f),
                        blend(accent, PURPLE, 0.55f),
                        accent
                });
        rim.setShape(GradientDrawable.RECTANGLE);
        rim.setCornerRadius(radius);

        GradientDrawable fill = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        blend(CARD_ALT, accent, pressed ? 0.38f : 0.17f),
                        blend(CARD, accent, pressed ? 0.18f : 0.06f),
                        BG
                });
        fill.setShape(GradientDrawable.RECTANGLE);
        fill.setCornerRadius(Math.max(0f, radius - inset));

        LayerDrawable layers = new LayerDrawable(new Drawable[]{rim, fill});
        layers.setLayerInset(1, inset, inset, inset, inset);
        return layers;
    }

    /** 深色玻璃面板，带彩虹细描边。 */
    public static Drawable surface(Context c, int accent, float radiusDp) {
        return frame(c, accent, radiusDp, false);
    }

    /** 可点击面板的按下反馈：内芯提亮、描边保持霓虹。 */
    public static StateListDrawable surfaceState(Context c, int accent, float radiusDp) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, frame(c, accent, radiusDp, true));
        s.addState(new int[]{}, frame(c, accent, radiusDp, false));
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
        box.setElevation(dp(c, 2));
        return box;
    }

    /** 状态胶囊：深色底 + 霓虹描边 + 同色文字。 */
    public static TextView statusPill(Context c, String label, int accent) {
        TextView pill = text(c, label, 9.5f, accent, true);
        pill.setGravity(Gravity.CENTER);
        pill.setLetterSpacing(0.04f);
        pill.setPadding(dp(c, 9), dp(c, 3), dp(c, 9), dp(c, 3));
        pill.setBackground(rounded(c, blend(CARD, accent, 0.20f),
                blend(accent, TEXT, 0.18f), 20));
        return pill;
    }

    /** 方形图标底座：深色玻璃 + 霓虹描边 + 同色图标。 */
    public static FrameLayout iconBadge(Context c, int iconRes, int accent, float sizeDp) {
        FrameLayout badge = new FrameLayout(c);
        badge.setBackground(rounded(c, blend(CARD_ALT, accent, 0.22f),
                blend(accent, TEXT, 0.12f), 12));
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
        b.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        b.setLetterSpacing(0.04f);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(c, 8), 0, dp(c, 8), 0);
        b.setStateListAnimator(null);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{-android.R.attr.state_enabled},
                rounded(c, BG, blend(BORDER, accent, 0.35f), 12));
        bg.addState(new int[]{android.R.attr.state_pressed}, frame(c, accent, 12, true));
        bg.addState(new int[]{}, frame(c, accent, 12, false));
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

    /** 顶部标题栏：彩虹竖条 + 渐变标题 + 右侧等宽版本胶囊。 */
    public static LinearLayout header(Context c, String title, String version) {
        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        View accent = new View(c);
        accent.setBackground(rainbow(c, 3));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(c, 3), dp(c, 24));
        alp.setMargins(0, 0, dp(c, 9), 0);
        header.addView(accent, alp);

        TextView t = text(c, title, 22f, TEXT, true);
        display(t);
        gradientText(t, TITLE_GRADIENT);
        header.addView(t, wrap(c));

        View spacer = new View(c);
        header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        if (version != null && !version.isEmpty()) {
            TextView pill = text(c, version, 9.5f, CYAN, true);
            mono(pill);
            pill.setPadding(dp(c, 9), dp(c, 3), dp(c, 9), dp(c, 3));
            pill.setBackground(rounded(c, blend(CARD, PURPLE, 0.16f),
                    blend(PURPLE, CYAN, 0.45f), 20));
            header.addView(pill, wrap(c));
        }
        return header;
    }

    /** 统一底部返回按钮：彩虹描边 + 整行宽度。 */
    public static Button back(Context c) {
        Button b = button(c, "←  返回主界面", 13f, BLUE);
        b.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        b.setLetterSpacing(0.02f);
        return b;
    }
}
