# HA 面板（内置 WebView）

> 适用版本 v0.9.7

## 为什么不用官方 HA App

2020 款锐际 SYNC+ 车机是 Android 8.1（API 27）。官方 Home Assistant 伴侣应用要求 Android 6.0+，版本门槛本身通过，但车机**没有 GMS**，而伴侣应用的推送、定位、部分传感器都走 Google 服务，装上去能用面板、其余基本是空的；加上 8.1 的 System WebView 往往停留在旧版本，HA 前端可能白屏。

本应用内置的 HA 面板只做一件事：**用系统 WebView 打开你自己的 HA 地址**，不依赖 Google 服务、不申请额外权限、不常驻后台。

## 使用

1. 首页悬浮按钮菜单 → **HA 面板**（或连接中心 → `HA 面板 ›`）
2. 首次进入填写完整地址，例如 `http://192.168.1.10:8123`
3. 保存后自动进入面板；顶部细工具条 6 秒后自动淡出，点顶部 24dp 区域可再次唤出
4. 工具条：**刷新** / **首页**（回到配置的地址）/ **设置**（改地址、切 kiosk）/ **返回**

## 已做的适配

| 能力 | 做法 |
|---|---|
| 横屏铺满 | `screenOrientation="landscape"` + 沉浸式全屏（隐藏状态栏与导航栏） |
| 屏幕常亮 | `FLAG_KEEP_SCREEN_ON`，车上不会因为没操作黑屏 |
| 隐藏侧边栏 | 默认在地址后追加 `?kiosk`（HA 官方支持的 kiosk 模式），可在设置里关闭 |
| 记住地址 | 存 `SharedPreferences("icarplay")` 的 `ha_url` / `ha_kiosk` |
| 本地 HTTP | 应用声明 `usesCleartextTraffic="true"`，局域网 `http://` 能直接打开 |
| 后退 | 物理返回键先走 WebView `goBack()`，退到底再关闭页面 |
| 安全 | `allowFileAccess=false`、`allowContentAccess=false`，只能联网不能读本地文件 |

## 建议

- 在 HA 里单独做一个**宽屏 dashboard**（视图布局选 `panel` 或 `horizontal-stack` 多列），1920×720 横屏才排得好看；手机用的 `sections` 视图在 8:3 上会空很多。
- 车机 Wi‑Fi 和 HA 服务器要在同一网段；如果 HA 走 HTTPS 自签证书，WebView 会拦，建议局域网内用 `http://` 或给 HA 配可信证书。
- 若面板卡顿，先在车机浏览器打开同一地址对比：浏览器也卡说明是 WebView 版本问题，不是本应用的问题。

## 与官方 App 的取舍

| 场景 | 建议 |
|---|---|
| 车上只看状态、控灯控空调 | 用本应用的 HA 面板 |
| 需要手机推送通知、精确位置上报 | 手机装官方 App，车机不需要 |
| 想把车机当常驻壁挂屏（kiosk） | 本应用的 HA 面板 + HA 宽屏 dashboard 更合适 |
