package com.rockjesus.icarplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Home Assistant Companion 的上报侧：把 HaSensors 采到的车机状态推到 HA。
 *
 * <p>走官方 mobile_app 集成：先 {@code POST /api/mobile_app/registrations}
 * 拿到 webhook，之后所有数据都往 webhook 发，等于一个自签的 Companion App；
 * 注册失败时降级为 REST 直写 {@code POST /api/states/<entity_id>}，保证有数据。</p>
 */
public final class HaCompanion {

    /** 状态上报总开关 */
    public static final String P_ENABLED = "ha_sync_enabled";
    /** 上报间隔（秒） */
    public static final String P_INTERVAL = "ha_sync_interval";
    /** webhook 完整地址（cloudhook 优先，否则本地 /api/webhook/xxx） */
    public static final String P_WEBHOOK = "ha_sync_webhook";
    /** mobile_app | states */
    public static final String P_MODE = "ha_sync_mode";
    public static final String P_REG_AT = "ha_sync_reg_at";
    /** 传感器定义是否已经 register_sensor 过 */
    public static final String P_DEFINED = "ha_sync_defined";
    public static final String P_LAST_AT = "ha_sync_last_at";
    public static final String P_LAST_MSG = "ha_sync_last_msg";

    private static final String APP_ID = "com.rockjesus.icarplay";
    private static final String APP_NAME = "iCarPlay";

    private HaCompanion() {
    }

    public static boolean enabled(Context ctx) {
        return HaApi.prefs(ctx).getBoolean(P_ENABLED, false);
    }

    public static int intervalSec(Context ctx) {
        int v = HaApi.prefs(ctx).getInt(P_INTERVAL, 60);
        return v < 30 ? 30 : v;
    }

    public static boolean registered(Context ctx) {
        return !HaApi.prefs(ctx).getString(P_WEBHOOK, "").isEmpty();
    }

    public static String lastText(Context ctx) {
        SharedPreferences p = HaApi.prefs(ctx);
        String at = p.getString(P_LAST_AT, "");
        String msg = p.getString(P_LAST_MSG, "");
        if (at.isEmpty()) return "尚未上报";
        return "上次 " + at + (msg.isEmpty() ? " · 正常" : " · " + msg);
    }

    /** 清掉注册信息（换 HA 地址 / 换设备名之后重来）。 */
    public static void reset(Context ctx) {
        HaApi.prefs(ctx).edit()
                .remove(P_WEBHOOK).remove(P_MODE).remove(P_REG_AT).remove(P_DEFINED)
                .apply();
    }

    /**
     * 注册为 HA 的 mobile_app 设备。成功返回 null，失败返回错误文案。
     * 必须在工作线程调用。
     */
    public static String register(Context ctx) {
        if (HaApi.baseUrl(ctx).isEmpty()) return "未设置 HA 地址";
        if (HaApi.token(ctx).isEmpty()) return "缺少访问令牌";
        JSONObject body = new JSONObject();
        try {
            body.put("app_id", APP_ID);
            body.put("app_name", APP_NAME);
            body.put("app_version", IcarLauncherActivity.VERSION_LABEL);
            body.put("device_name", deviceName());
            body.put("manufacturer", Build.MANUFACTURER == null ? "unknown" : Build.MANUFACTURER);
            body.put("model", Build.MODEL == null ? "unknown" : Build.MODEL);
            body.put("os_name", "Android");
            body.put("os_version", Build.VERSION.RELEASE == null ? "" : Build.VERSION.RELEASE);
            body.put("supports_encryption", false);
        } catch (JSONException ignored) {
            return "组装注册信息失败";
        }
        HaApi.Resp r = HaApi.send(ctx, "POST", "/api/mobile_app/registrations",
                body.toString(), 12000);
        if (r.code < 200 || r.code >= 300) {
            if (r.code == 401 || r.code == 403) {
                return "令牌无效或已过期：请到 HA「个人资料 → 长期访问令牌」新建一条粘贴保存"
                        + "（网页登录自动取到的令牌只有 30 分钟）";
            }
            if (r.code == 0) return "连不上 HA：检查地址、网络或证书设置";
            if (r.code == 400) return "注册被拒（400）：HA 的 mobile_app 集成可能被禁用";
            // 接口不存在 / 未启用 mobile_app：降级为 REST 直写，仍然把状态送进 HA
            HaApi.prefs(ctx).edit()
                    .putString(P_MODE, "states")
                    .putString(P_WEBHOOK, HaApi.baseUrl(ctx) + "/api/states")
                    .putString(P_REG_AT, nowText())
                    .remove(P_DEFINED)
                    .apply();
            return null;
        }
        try {
            JSONObject o = new JSONObject(r.body);
            String hookId = opt(o, "webhook_id");
            String cloud = opt(o, "cloudhook_url");
            String hook;
            if (cloud.startsWith("http")) {
                hook = cloud;
            } else if (!hookId.isEmpty()) {
                hook = HaApi.baseUrl(ctx) + "/api/webhook/" + hookId;
            } else {
                return "HA 返回里没有 webhook";
            }
            HaApi.prefs(ctx).edit()
                    .putString(P_MODE, "mobile_app")
                    .putString(P_WEBHOOK, hook)
                    .putString(P_REG_AT, nowText())
                    .remove(P_DEFINED)
                    .apply();
            return null;
        } catch (Throwable t) {
            return "解析注册响应失败";
        }
    }

    /**
     * 采一遍并上报。返回 null 表示成功，否则是给人看的错误文案。
     * 必须在工作线程调用。
     */
    public static String push(Context ctx) {
        if (HaApi.baseUrl(ctx).isEmpty()) return "未设置 HA 地址";
        List<HaSensors.Item> items = HaSensors.collect(ctx);
        if (items.isEmpty()) return "没有可上报的状态";

        if (!registered(ctx)) {
            String err = register(ctx);
            if (err != null) {
                mark(ctx, err);
                return err;
            }
        }
        String mode = HaApi.prefs(ctx).getString(P_MODE, "mobile_app");
        String err = "states".equals(mode) ? pushStates(ctx, items) : pushWebhook(ctx, items, true);
        mark(ctx, err);
        return err;
    }

    private static String pushWebhook(Context ctx, List<HaSensors.Item> items, boolean retry) {
        String hook = HaApi.prefs(ctx).getString(P_WEBHOOK, "");
        if (hook.isEmpty()) return "没有 webhook";
        SharedPreferences p = HaApi.prefs(ctx);

        // 第一次先登记传感器定义（名字 / 单位 / device_class），之后只发状态。
        // 注意：HA 的 register_sensor 只接受「单个」传感器对象，一次发数组会被
        // payload schema 拒掉且仍返回 200 空响应，所以必须逐条注册。
        if (!p.getBoolean(P_DEFINED, false)) {
            for (HaSensors.Item item : items) {
                JSONObject reg = new JSONObject();
                try {
                    reg.put("type", "register_sensor");
                    reg.put("data", item.toJson());
                } catch (JSONException ignored) {
                    return "组装传感器定义失败";
                }
                String rr = HaApi.postAbsolute(ctx, hook, reg.toString(), 10000);
                if (rr == null) {
                    if (retry) {
                        reset(ctx);
                        if (register(ctx) == null) return pushWebhook(ctx, items, false);
                        return "webhook 失效且重注册失败";
                    }
                    return "webhook 不可用";
                }
                if (!okBody(rr)) return "传感器注册被拒：" + item.uniqueId;
            }
            p.edit().putBoolean(P_DEFINED, true).apply();
        }

        JSONObject upd = new JSONObject();
        try {
            upd.put("type", "update_sensor_states");
            upd.put("data", toArray(items));
        } catch (JSONException ignored) {
            return "组装状态失败";
        }
        String r = HaApi.postAbsolute(ctx, hook, upd.toString(), 10000);
        if (r == null) {
            if (retry) {
                // webhook 失效（HA 重装 / 集成重载）：重注册再试一次
                reset(ctx);
                if (register(ctx) == null) return pushWebhook(ctx, items, false);
                return "webhook 失效且重注册失败";
            }
            return "webhook 上报失败";
        }
        if (r.contains("\"success\":false") || r.contains("\"success\": false")) {
            return "状态被拒：" + brief(r);
        }
        return null;
    }

    /** HA 的 register_sensor 正常返回 {"success":true}；其它都当失败。 */
    private static boolean okBody(String body) {
        String b = body.replace(" ", "");
        return b.contains("\"success\":true");
    }

    /** 截一段响应体给 UI 显示。 */
    private static String brief(String body) {
        String b = body == null ? "" : body.trim();
        if (b.length() > 60) b = b.substring(0, 60) + "…";
        return b.isEmpty() ? "空响应" : b;
    }

    /** 降级路径：逐条 POST /api/states/<entity_id>。 */
    private static String pushStates(Context ctx, List<HaSensors.Item> items) {
        String base = HaApi.baseUrl(ctx);
        if (base.isEmpty()) return "未设置 HA 地址";
        int ok = 0;
        int fail = 0;
        for (HaSensors.Item item : items) {
            try {
                boolean binary = "binary_sensor".equals(item.type);
                JSONObject body = new JSONObject();
                body.put("state", item.state);
                JSONObject attrs = new JSONObject();
                if (item.unit != null) attrs.put("unit_of_measurement", item.unit);
                if (item.deviceClass != null) attrs.put("device_class", item.deviceClass);
                attrs.put("friendly_name", item.name);
                body.put("attributes", attrs);
                String path = "/api/states/" + (binary ? "binary_sensor.icarplay_"
                        : "sensor.icarplay_") + item.uniqueId;
                HaApi.Resp r = HaApi.send(ctx, "POST", path, body.toString(), 6000);
                if (r.code >= 200 && r.code < 300) {
                    ok++;
                    continue;
                }
                // 第一条就 401 / 连不上，没必要把十几条都撞一遍墙
                if (r.code == 401 || r.code == 403) {
                    return ok == 0
                            ? "令牌无效或已过期：请粘贴长期访问令牌后再试"
                            : ("上报了 " + ok + " 项后令牌失效");
                }
                if (r.code == 0 && ok == 0) return "连不上 HA：检查地址、网络或证书设置";
                fail++;
            } catch (Throwable ignored) {
                fail++;
            }
        }
        if (ok == 0) return "直写 REST 失败（" + fail + " 项）";
        return fail == 0 ? null : ("部分失败 " + fail + "/" + (ok + fail));
    }

    private static JSONArray toArray(List<HaSensors.Item> items) throws JSONException {
        JSONArray arr = new JSONArray();
        for (HaSensors.Item item : items) {
            arr.put(item.toJson());
        }
        return arr;
    }

    private static void mark(Context ctx, String err) {
        HaApi.prefs(ctx).edit()
                .putString(P_LAST_AT, nowText())
                .putString(P_LAST_MSG, err == null ? "" : err)
                .apply();
    }

    private static String nowText() {
        return new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    private static String deviceName() {
        String model = Build.MODEL == null ? "" : Build.MODEL.trim();
        if (model.isEmpty()) return "iCarPlay 车机";
        return "iCarPlay " + model;
    }

    private static String opt(JSONObject o, String key) {
        String v = o.optString(key, "");
        if (v == null) return "";
        v = v.trim();
        // HA 会把不存在的 cloudhook_url 返回成 JSON null，optString 会变成字符串 "null"
        if ("null".equalsIgnoreCase(v) || "undefined".equalsIgnoreCase(v)) return "";
        return v;
    }
}



