# HA 面板（内置 WebView + 内置客户端）

> 适用版本 v1.3.1

## 为什么不用官方 HA App

2020 款锐际 SYNC+ 车机是 Android 8.1（API 27），**没有 GMS**：官方伴侣应用的推送、定位、部分传感器都走 Google 服务，装上去能用面板、其余基本是空的；还要额外装一个几十 MB 的 APK，升级和登录态都得管两份。所以本应用自己把「连上 HA」这件事做掉：**不需要另外安装任何 HA 应用**。

## 使用

1. 首页第六卡 **Home Assistant** 直接进面板（悬浮球菜单、连接中心同样有入口）
2. 首次进入填写完整地址，例如 `http://192.168.1.10:8123`，点「测试连接」确认通
3. 保存后自动进入面板，在里面登录 HA 一次；本应用会自动从页面 `localStorage.hassTokens` 取到长期令牌
4. 工具条：**刷新** / **首页**（回到配置的地址与路径）/ **设置**（地址、令牌、路径、kiosk、证书）/ **返回**

## 已做的适配

| 能力 | 做法 |
|---|---|
| 横屏铺满 | `screenOrientation="landscape"` + 沉浸式全屏（隐藏状态栏与导航栏） |
| 屏幕常亮 | `FLAG_KEEP_SCREEN_ON`，车上不会因为没操作黑屏 |
| 隐藏侧边栏 | 默认在地址后追加 `?kiosk`（HA 官方支持的 kiosk 模式），可在设置里关闭 |
| 记住地址 | 存 `SharedPreferences("icarplay")` 的 `ha_url` / `ha_kiosk` |
| 默认落点 | `ha_path` 指定 dashboard 路径（如 `/lovelace/car`），进面板直达 |
| 登录态 | 页面登录一次后自动提取 `access_token` 存 `ha_token`；也可手工粘贴长期令牌 |
| 连通性 | 首页卡直连 `GET /api/` 探测，显示 未配置 / 连接中 / 已连接 / 未连上 |
| 自签证书 | 设置里可开「忽略证书错误」，默认严格校验（`ha_insecure` 默认 false） |
| 本地 HTTP | 应用声明 `usesCleartextTraffic="true"`，局域网 `http://` 能直接打开 |
| 后退 | 物理返回键先走 WebView `goBack()`，退到底再关闭页面 |
| 安全 | `allowFileAccess=false`、`allowContentAccess=false`，只能联网不能读本地文件 |

## 说明

- **面板不由本应用绘制**：所有卡片、布局都在你自己的 HA Lovelace 里做，本应用只提供登录态、连通性、全屏与常亮。
- 令牌只存在本机 preferences（`SharedPreferences("icarplay")`），不出网、不下发。

## 建议

- 在 HA 里单独做一个**宽屏 dashboard**（视图布局选 `panel` 或 `horizontal-stack` 多列），1920×720 横屏才排得好看；手机用的 `sections` 视图在 8:3 上会空很多。
- 车机 Wi‑Fi 和 HA 服务器要在同一网段；如果 HA 走 HTTPS 自签证书，可在设置里打开「忽略证书错误」，或给 HA 配可信证书。
- 若面板卡顿，先在车机浏览器打开同一地址对比：浏览器也卡说明是 WebView 版本问题，不是本应用的问题。

## 与官方 App 的取舍

| 场景 | 建议 |
|---|---|
| 车上看状态、控灯控空调、控空调与场景 | 用本应用的 HA 面板（自建 Lovelace） |
| 需要手机端推送通知、精确定位上报 | 手机装官方 App；车机不需要，也不应该依赖它 |
| 想把车机当常驻壁挂屏（kiosk） | 本应用的 HA 面板 + 宽屏 dashboard 最合适 |
