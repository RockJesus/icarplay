# CarLife+ 接收（单 APK 内置）

iCarPlay v0.6.2 起，首页第五张卡 **CarLife+** 在同一个 `icarplay.apk` 内启动接收端，不再需要第二个 CarLife 接收 APK，也不再需要 `bdcf` 配置文件。

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

## v0.6.2 稳定性修正

- **握手顺序按真实 CarLife+ 收敛**：`HU_AUTHEN_RESULT` 之后立刻回 `FEATURE_CONFIG_RESPONSE` 并提议编码器；不再插入 `HU_BT_OOB_INFO`。上游实测该消息会让真机停在 `ENCODER_INIT`。
- **GNSS 延后**：`CAR_DATA_SUBSCRIBE` 改到编码器协商完成、首帧开始之后发送，不再夹在认证和编码器之间。
- **无线发现**：只扫描当前 Wi‑Fi / 有线 LAN 的 IPv4 网段，不再遍历蜂窝和 VPN 网卡；两轮探测覆盖“手机晚 1–2 秒才开端口”的情况。
- **优先复用上次 IP**：连接成功后记住手机地址，下次无线先探测该地址，失败再全网段扫描。
- **手动 IP**：自动扫描失败时点“手动 IP”，输入手机 CarLife+ 无线页显示的地址直连。
- **超时与重连**：无线 CMD/VIDEO 增加 20 秒读超时；握手 20 秒无进展会主动断开并进入退避重连；“停止”会取消正在进行的扫描，旧扫描回调不会再把接收端拉起来。
- **车机端日志**：长按顶部状态文字，或“手动 IP”弹窗里的“最近日志”，可查看本会话最近 80 行日志和文件路径。

## 真实手机联调清单

1. 车机端先进入 CarLife+ 页；手机端 CarLife+ 先 force-stop，再全新打开（避免半死会话显示“已连接”但不再推流）。
2. 手机与车机接同一 Wi‑Fi；手机点 CarLife+ 的“无线连接”，确认手机端进入等待车机状态。
3. 车机点“无线连接”：正常应在 2–5 秒内找到手机并进入 `phase:encoder_init`；若扫描失败，点“手动 IP”输入手机地址。
4. 检查日志出现 `connected` → `phase:authen` → `phase:encoder_init` → `phase:streaming`，随后有 H.264 帧；画面应出现，触摸应能反向操作手机。
5. 手机锁屏/亮屏、车机 Wi‑Fi 断连各一次，确认自动重连从 1s 开始退避，最长 15s，不需要重启 APK。
6. 有线与无线各测一次；切换前必须点“停止”，因为有线与 Android Auto 都占用 USB AOA。

> CarWith（小米 `com.miui.carlink`）有独立车机身份校验，不在本兼容接收端的保证范围内。

## 与 Android Auto 的 USB 冲突

CarLife+ 有线使用 USB AOA，Android Auto 有线也使用 USB AOA，同一时刻只能有一个接收端占用 USB。切换前先在当前接收页点“停止”，再回首页进另一个接收端。

## 权限

- `RECORD_AUDIO`：CarLife+ 语音/麦克风上行；不授权时仍可投屏，只是手机侧听不到车机麦克风。
- USB Host：手机切 AOA 配件模式。
- 同一 Wi‑Fi / 本地网络：无线模式扫描手机端口。

## 开源合规

本仓库整体为 GPL-3.0。CarLife+ 接收核心来自 `FF1239/Limo-carlife`（GPL-3.0），保留其协议实现与版权声明；修改后的完整源码随本仓库发布。
