<div align="center">

# Huawei HiCar 接收

华为手机把画面、声音、触控投到车机，手机端不装第二个 APK、不需要 root。

`状态：实验路径 · 未在 8.1 实车验证`　·　`上游 MIT · 0.11.1 Preview`　·　[← 返回 README](../README.md)

</div>

---

## 这是什么

iCarPlay 内置的 **Huawei HiCar 接收端**来自开源项目 [`huducat586/hicar-android-receiver`](https://github.com/huducat586/hicar-android-receiver)（MIT，0.11.1 Preview）。

本接收端是非官方社区实现，不是华为官方产品，也未获得 HiCar 认证；HiCar 名称与商标归其权利人所有。

## 在目标车机上的可用边界

2020 款 SYNC+ 1.0 是 **Android 8.1 / API 27**。上游接收端的无线连接按 **Android 10+** 设计，Manifest 的 `minSdkVersion 26` 并不等于 Android 8 / 9 可以完成无线连接。本仓库为 Android 8.1 增加了旧版 Wi‑Fi Direct 兼容分支，但这条路径**没有在目标车机实车上验证**，属于实验功能。

可能的结果：

- 手机能在蓝牙里发现车机，但 Wi‑Fi 热点 / 直连建立失败；
- 热点建立成功，但华为手机不接受 2.4GHz 频道，或拿不到可用的 BSSID；
- 首次能连上，重启车机后不能自动回连（上游已知问题）。

> **不要把它当作稳定可用的驾驶功能。** 首次测试请停车操作，先手动连接，不要开启“开机自动接收”。

## 使用步骤

1. 确认华为手机系统自带 **HiCar**（当前上游只在 HarmonyOS 4 发送端做过实测，不能据此承诺 HarmonyOS 5 / 6）；
2. 车机与手机都打开蓝牙和 WLAN；
3. 先在**系统蓝牙设置**里完成一次普通配对（部分设备上这是连接成功的必要步骤）；
4. 打开 iCarPlay → Huawei HiCar → 点「开始连接」；
5. 允许附近设备 / 蓝牙 / 位置权限；华为手机出现 HiCar 提示后按提示连接；
6. 首次连接输入接收画面显示的六位连接码——连接码是本机私有数据，不要截图外传；
7. 出画面后顶部连接信息默认隐藏；结束点「停止连接」。

## 已实现的能力（上游口径）

| 能力 | 状态 |
|---|---|
| H.264 视频接收 | 上游真机实测（800×480） |
| AAC 音频 | 上游真机实测 |
| 点按 / 返回 / 主页 | 上游真机实测 |
| 横屏、全屏、深色 | 上游真机实测 |
| 麦克风语音 | 实验，默认关闭 |
| 开机自动接收 | 实验，默认关闭；需弹窗二次确认；安全模式会自动关闭；重启回连未解决 |
| 32 位 ARM 车机 | 原生库已提供，尚未实测 |
| Android 8.1 车机 | **未验证**；本仓库只增加了旧版 Wi‑Fi Direct 兼容路径 |

## 与其他入口的关系

| 入口 | 适合谁 | 现状 |
|---|---|---|
| **AirPlay** | 苹果手机 / 平板，控制中心“屏幕镜像”，不需要 MFi | 已可用 |
| **Android Auto** | 安卓手机，USB AOA 有线，或手机开 Headunit Server 后手动无线 | 协议已接通 |
| **HiCar** | 华为手机，本文档 | 实验路径 |
| **CarPlay** | 苹果手机，需要 MFi 材料 | 只有入口与骨架 |

## 排障

先看 HiCar 页面里的「设置与诊断」事件日志，再检查：

1. 手机是否真的支持原生 HiCar（不是所有华为 / 荣耀机型都有）；
2. 系统蓝牙里是否已完成普通配对；
3. 车机 Wi‑Fi 是否能创建热点 / 直连，是否被 SYNC+ 的其他无线功能占用；
4. 是否授予了蓝牙、位置 / 附近设备、WLAN 权限；
5. 是否在停车状态、未开启“开机自动接收”的情况下手动测试。

如果车机在 Android 8.1 上始终无法建立热点，这是系统 API 限制，不是换一个 APK 就能绕过；此时可改用**手机开热点让车机加入**（连接中心 / 一键无线投屏向导），或使用外接 HiCar 盒子。

## 合规与来源

- 适配层与上游应用代码：MIT，见 [`LICENSE-HICAR-MIT.txt`](../LICENSE-HICAR-MIT.txt)。
- 原生库 `libcarlink_hichain.so` 取自上游 0.11.1 Preview 官方 release APK；其静态链接的 OpenHarmony DeviceAuth / HUKS / securec、mbedTLS、OpenSSL、cJSON 各自许可证随包放在 `app/src/main/assets/hicar-licenses/`。
- 上游第三方声明见 [`THIRD_PARTY_NOTICES-HICAR.txt`](../THIRD_PARTY_NOTICES-HICAR.txt)。
- 本项目不上传手机配对信息、连接码或画面；接收数据只在本机局域网内处理。
- 本项目与 Huawei、Apple、Ford、Baidu 均无关联。

## 相关文档

[`SAFETY.md`](SAFETY.md) 开机自启与安全模式 · [`INSTALL.md`](INSTALL.md) 网络准备
