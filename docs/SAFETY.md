# 车机安全边界

本项目的目标是在 **2020 款 SYNC+ 1.0（Android 8.1）** 上增加第三方接收端，同时明确一条硬约束：

> **APK 不能破坏车机原有系统，不能导致无法启动或无限重启。**

## 这个 APK 不会做什么

- 不 root，不申请 `su`；
- 不写 `/system`、`/vendor`、boot、recovery 等系统分区；
- 不修改系统 framework、不替换系统 launcher；
- 不申请设备管理员（`BIND_DEVICE_ADMIN` / DeviceAdminReceiver）；
- 不申请 `WRITE_SETTINGS` / `WRITE_SECURE_SETTINGS`；
- 不申请 `REBOOT`、`FACTORY_TEST`、`MASTER_CLEAR`、`INSTALL_PACKAGES` 等系统级权限；
- 只申请用户可见的悬浮窗、麦克风、位置、蓝牙、通知和电池优化白名单；电池优化只是系统设置页的一次性确认，不改系统分区；
- 不通过 `Runtime.exec` 或 `ProcessBuilder` 调特权命令；
- 不把 `android.intent.category.HOME` 加进 Manifest，因此不会被系统当作“桌面”候选；
- 不自动注册开机自启；HiCar 的 `BootReceiver` 在 Manifest 中默认 `android:enabled="false"`。

`bash scripts/preflight-safety.sh` 会在 CI 构建前检查上述条件，任何一项不满足都会让构建失败。

## 仍然存在的现实风险

虽然 APK 不会写坏引导链，但它仍然是一个会申请悬浮窗、蓝牙、Wi‑Fi Direct、前台服务的普通应用。可能出现的异常是“**看起来起不来**”，而不是系统被刷坏：

- 应用被用户手动设为默认桌面后自身崩溃；
- 开机自启的接收服务异常；
- 悬浮窗或 Wi‑Fi Direct 占用资源导致界面卡住。

这些情况通常可以通过卸载 APK、关闭悬浮窗或切回原桌面恢复。

## v0.5.7 的防护措施（自 v0.4.0 引入）

| 约束 | 实现 |
|---|---|
| 不抢占默认桌面 | Manifest 只保留 `LAUNCHER`，没有 `HOME` / `LEANBACK_LAUNCHER`；主界面只提供“系统桌面设置（手动确认）”入口 |
| 开机自启默认关闭 | `local.hicarlab.BootReceiver` 默认 `enabled=false`；HiCar 页面开启时弹窗二次确认，确认后才启用组件 |
| 崩溃隔离 | 全局 `UncaughtExceptionHandler` 记录崩溃；5 分钟内连续两次崩溃进入安全模式并关闭开机自启 |
| 看门狗 | HiCar 服务主线程 60 秒无响应时停止服务并回到主界面；全局主线程 90 秒无响应时打开主界面、关闭自启并结束本应用进程 |
| 热点退出释放 | 停止连接、服务销毁、任务被移除时关闭本地热点 / `removeGroup`，不保留持久热点配置 |

安全模式不会自动恢复开机自启。确认稳定后，需要用户自己到 HiCar 页面重新手动开启。

## 建议的首次测试步骤

1. **停车、挂 P 挡**，不要在行驶中测试；
2. 先确认车机仍能进入安卓原生设置；
3. 从 U 盘安装 `icarplay-v0.5.7.apk`，不要把它设为默认桌面；
4. 打开主界面，先只测试 AirPlay / Android Auto；
5. 华为 HiCar 单独测试，首次在系统蓝牙里完成六位码配对；
6. 若出现异常，先在原生设置里卸载 APK；不要反复断电重启；
7. 若已经进不了原生设置，使用 SYNC+ 的恢复方式或联系 4S，具体按键组合以车辆手册为准。

## 不承诺的部分

- 目标车机 SYNC+ 是 Android 8.1；HiCar 上游接收端按 Android 10+ 设计，8.1 上属于实验路径，可能连不上或重启后不回连；
- CarPlay 仍需要 Apple MFi 认证材料，当前只保留入口和骨架；
- 不同 SYNC+ 软件版本、不同 U 盘、不同华为机型的结果可能不同。任何“稳定可用”的结论都必须以实车测试为准。
