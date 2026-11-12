# CarLife+ 接收（单 APK 内置）

iCarPlay v0.6.1 起，首页第五张卡 **CarLife+** 在同一个 `icarplay.apk` 内启动接收端，不再需要第二个 CarLife 接收 APK，也不再需要 `bdcf` 配置文件。

## 来源与边界

- 接收协议来自 GPL-3.0 项目 **DiPlay / Limo CarLife**：
  <https://github.com/FF1239/Limo-carlife>
- 该实现已在真实 Android 手机上与 **百度 CarLife+ 手机端** 做过联调，支持有线 AOA 与同一 Wi‑Fi 下的无线连接；协议细节见上游 `docs/live-testing-findings.md`。
- 这不是百度官方 `CLServerSDK` / `CLClientSDK` 车机端 SDK。百度未公开 CarLife+ 车机端源码或 SDK；需要用官方 SDK、量产 channel ID 和认证材料的方案，必须向百度申请授权后另行集成。
- 因此本卡属于 **CarLife+ 手机端兼容接收**：CarLife+ App 能连、能投屏、能反向触控；CarLife+ 宣传的“无感连接”、车机原生小程序、部分深度车控能力不保证可用。

## 使用

1. 手机安装并打开 **CarLife+**（或手机系统自带的 CarLife+ / CarWith 入口）。
2. **有线**：USB 数据线连接车机，在 iCarPlay 首页点 `CarLife+`，再点“有线连接”。
3. **无线**：手机和车机连同一个 Wi‑Fi；手机 CarLife+ 点“无线连接”后，在 iCarPlay 的 CarLife+ 页点“无线连接”，程序会扫描手机并连接。
4. 投屏后，车机触摸会反传到手机；顶部 `← iCarPlay` 返回首页，底部可“停止”后切换有线/无线。

## 与 Android Auto 的 USB 冲突

CarLife+ 有线使用 USB AOA，Android Auto 有线也使用 USB AOA，同一时刻只能有一个接收端占用 USB。切换前先在当前接收页点“停止”，再回首页进另一个接收端。

## 权限

- `RECORD_AUDIO`：CarLife+ 语音/麦克风上行；不授权时仍可投屏，只是手机侧听不到车机麦克风。
- USB Host：手机切 AOA 配件模式。
- 同一 Wi‑Fi / 本地网络：无线模式扫描手机端口。

## 开源合规

本仓库整体为 GPL-3.0。CarLife+ 接收核心来自 `FF1239/Limo-carlife`（GPL-3.0），保留其协议实现与版权声明；修改后的完整源码随本仓库发布。
