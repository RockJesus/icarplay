<div align="center">

# AirPlay 接收

iPhone / iPad / Mac 的屏幕镜像与音频，车机不需要 MFi，也不需要第二个 App。

`状态：已接入 · 首页启动时默认开启`　·　`优先走 5GHz / 手机热点`　·　[← 返回 README](../README.md)

</div>

---

## 协议来源

AirPlay 核心来自 [`jqssun/android-airplay-server`](https://github.com/jqssun/android-airplay-server)，它是 [UxPlay](https://github.com/FDH2/UxPlay) 的 Android 移植：

- C/C++ 原生栈负责 RAOP / AirPlay 信令、FairPlay、H.264 / H.265 解复用；
- Kotlin 层负责 MediaCodec 解码、SurfaceView 渲染、AudioTrack 输出；
- 支持 AirPlay 2、屏幕镜像、音频、视频投放、HLS。

本仓库只改了应用外壳（入口、悬浮按钮、CarPlay 入口），没有改协议核心。

## 怎么用

1. 车机与 iPhone 连**同一个网段**（推荐手机开热点让车机加入，或外接 5GHz 路由）；
2. 打开 iCarPlay 即已启动接收（首页启动时默认拉起），也可点悬浮按钮 → AirPlay；
3. iPhone 控制中心 → 屏幕镜像 → 选择 `iCarPlay`；
4. 首次可能弹 PIN（可在上游设置里关闭 / 修改）；
5. 镜像画面全屏渲染，音频走车机音频输出。

连接中心里可以看到 `AirPlay 接收` 状态与 `本机端口`；「启动 AirPlay / 停止 AirPlay」两个按钮用来确认端口是否真的在监听。

## 能做什么

- iPhone / iPad / Mac 屏幕镜像；
- AirPlay 音频（AAC-ELD / AAC-LC / ALAC）；
- AirPlay 视频投放（HLS / 远程 URL）；
- 播放 / 暂停、音量等基础控制；
- Android TV / 车机横屏。

## 不能做什么

- **没有触控回传**：车机屏幕不能反向控制 iPhone；
- **DRM 内容黑屏**：Netflix、Apple TV+ 等受保护内容不支持；
- **不是 CarPlay**：没有 CarPlay 认证 UI、方控、Siri、电话簿；
- 1920×720（8:3）上 16:9 画面会留黑边；
- 长时间稳定性、发热、音频通道仍需实车验证。

## 常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| iPhone 看不到 `iCarPlay` | 不在同一子网 / AP 隔离 / mDNS 被挡 | 换路由或改用手机热点，关掉 AP 隔离；用连接中心「mDNS 体检」确认广播 |
| 有画面没声音 | 音频通道不匹配 | 在认证设置里试“通道 9” |
| 画面卡顿 | 2.4GHz 或信号差 | 用 5GHz，靠近路由 / 热点 |
| 黑屏但有连接 | DRM 内容 | 换非 DRM 内容测试 |
| 连接后车机重启循环 | Wi‑Fi Direct / 自启 | 关闭开机自启，不要开 P2P |

## 相关文档

[`INSTALL.md`](INSTALL.md) 安装与网络准备 · [`SAFETY.md`](SAFETY.md) 开机自启边界
