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

## 可发现性兜底（v1.1.2 起）

系统 `NsdManager` 依赖系统守护进程 `mdnsd`。部分车机 / 模拟器上 `mdnsd` 起不来（日志里表现为
`Unable to set property "ctl.start" to "mdnsd"`、`DNSServiceRegister -65563`），
这时 `_airplay._tcp` 注册失败，iPhone 控制中心的「屏幕镜像」就永远看不到本机。

v1.1.2 起在 `AirPlayService` 启动时额外拉起进程内应答器
`io.github.jqssun.airplay.discovery.MiniMdnsResponder`：

| 项 | 说明 |
|---|---|
| 监听 | UDP 5353，加入组播 `224.0.0.251` |
| 应答 | `_airplay._tcp` / `_raop._tcp` 的 PTR、SRV、TXT，以及主机名 A 记录 |
| TXT | 直接复用 native 层 `nativeGetRaopTxtRecords` / `nativeGetAirplayTxtRecords`，与 UxPlay 一致 |
| 宣告 | 启动后主动组播三遍，提高 iPhone 缓存命中 |
| 主机名 | `iCarPlay-XXXX.local`，XXXX 取 `wlan0` MAC 末四位 |
| 与系统 NSD 关系 | 并存，不替代；系统 `mdnsd` 正常时两者同时应答 |

实测（MuMu，1600×900）：服务监听 `7000`，独立主机用 DNS-SD 查询 `192.168.1.213:5353`，
回包 862 字节、7 条记录，全部字段解析通过：

```
PTR _raop._tcp.local -> 1A20FF212288@iCarPlay._raop._tcp.local
SRV 1A20FF212288@iCarPlay._raop._tcp.local -> iCarPlay-A7C.local:7000
TXT （UxPlay 原生键值，含 am / pk / vv / features / pw …）
PTR _airplay._tcp.local -> iCarPlay._airplay._tcp.local
SRV iCarPlay._airplay._tcp.local -> iCarPlay-A7C.local:7000
TXT features=0x5A7FFEF7,0x400  pw=false …
A    iCarPlay-A7C.local -> 192.168.1.213
```

限制：模拟器（MuMu）网络是 NAT，主机发往 `224.0.0.251` 的组播进不到模拟器内部，
所以 MuMu 上 iPhone 仍然发现不到，只能在真车机 / 真 Android 设备上验证组播路径。

## 镜像画面承载（v1.1.3 起）

音频能听、镜像没画面的根因不在协议：日志里 `mirror running: 1`、`video_set_codec`、
`Video codec started video/avc 416x900` 全都正常，解码器已经在出帧。
问题是 `AirPlayService.setVideoSurface()` 没有任何地方调用——帧解出来没有地方落地。

v1.1.3 新增 `MirrorOverlay`：镜像一起来就把一块全屏 `SurfaceView` 铺在最上层，
把原页面整块隐藏，画面上不叠任何本 App 的控件、不弹窗、不留状态条；
按返回键结束这次投屏并回到接收页。首页与 AirPlay 接收页都装了这层，
所以在哪一页开始镜像都能出画面（首页只用 `bindService(..., 0)` 观察，不会替你启动服务）。

同一版还修了比例：镜像多为竖屏（416×900），渲染管线原本把画面硬拉满整个屏幕，
横屏车机上会被压扁。现在按视频宽高比等比缩放，两侧留黑边。
