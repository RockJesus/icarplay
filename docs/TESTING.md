# 测试台账

本文件记录 iCarPlay 每个功能**验证到什么程度**、**还缺什么**、**怎么测**。原则：有证据的写"已验证 + 证据"，没做过的一律写"未验证"，不因代码已接入就当成能用。

## 一、已验证（有实拍 / 有日志 / 有真机）

| 功能 | 验证程度 | 证据 |
|---|---|---|
| AirPlay 屏幕镜像 + 音频 | 通过 | iPhone 14,3 → 模拟器，v1.1.4 起音视频同时正常（黑屏问题由 `setZOrderOnTop` + 首帧 `glReadPixels` 自检修复） |
| AirPlay 发现（mDNS） | 通过（受网络限制） | 单播应答 `_airplay._tcp` / `_raop._tcp` 有效；模拟器环境组播不可达，故走单播 |
| CarPlay 接收（真机手机侧） | 通过 | 荣耀手机 + iPhone 实测连上并出画面 |
| CarPlay 页面与等待页品牌 | 通过 | v1.1.5 模拟器实拍：入口页、等待页均只有 iCarPlay，无第三方品牌 |
| CarLife+ 无线 | 通过（10.5 分钟） | 完整握手 → 出画面 → 触控 → 连续 10.5 分钟不断线；投屏期间零本 App 控件 |
| 一键更新（检查 / 下载 / 校验 / 安装） | 通过 | 检查到新版本 → 下载 → SHA-256 + 签名校验 → 系统安装确认 → 覆盖升级成功 |
| 无线传文件（PC 浏览器 ⇄ 车机） | 通过 | 端口 8090 上传 / 下载 `.apk`、列表刷新、删除 |
| 应用管理（列表 / 卸载入口） | 部分通过 | 列出 64 个应用（含系统预装）、点卸载能拉起系统确认框 |
| 屏幕适配 | 部分通过 | 1600×900 与 720p 首页布局；4:3 窄横屏走 3+2 两行 |
| 车机安全约束 | 通过 | `scripts/preflight-safety.sh` 两项 OK：无系统权限、无设备管理员、不抢默认桌面、无开机接管 |

## 二、未验证清单

### P0 —— 直接决定"能不能用"，必须在真车（2020 锐际 SYNC+）上测

| # | 功能 | 为什么没测 | 怎么测 | 判据 |
|---|---|---|---|---|
| 1 | **安装与签名白名单** | 只测过模拟器 + 手机 | U 盘 FAT32 放 `icarplay.apk` → 原生设置 → 存储 → U 盘 → 安装 | 装完桌面无图标时，`设置 → 应用 → iCarPlay → 打开` 能进入；若报「应用未安装」即撞签名白名单 |
| 2 | **断电重启不无限重启** | 模拟器测不出车机固件行为 | 装好后熄火锁车 2 分钟再着车，观察 3 次开机 | 车机正常进原桌面，不出现循环重启；本 App 不自启（仅悬浮层恢复） |
| 3 | **CarPlay 有线** | 需要车机 USB Host + 数据口 | 数据线接 iPhone → 车机 USB 数据口 → iPhone 解锁并允许 CarPlay | 车机出现 iCarPlay 接收画面，iPhone 端显示"已连接"；失败时抓 `logcat -s CarPlayHost` |
| 4 | **CarPlay 无线** | 需要车机蓝牙可给第三方 App 用 + 5GHz 热点 | 车机开蓝牙 + 开热点（5GHz）→ 手机与车机蓝牙配对 → 点「开始 CarPlay 接收」 | 等待页文案从「请打开蓝牙」变为「正在准备 CarPlay…」后出画面；失败判据见等待页提示语本身 |
| 5 | **AirPlay 在真车网络下** | 车机 Wi-Fi 与组播行为未知 | 车机连手机热点 → iPhone 控制中心选 `iCarPlay` | 3 秒内出画面，声音从车机出来；失败先看「连接中心 → mDNS 体检」是否看到 iPhone 广播 |
| 6 | **CarLife+ 有线** | 无线通了，USB 未测 | 手机装 CarLife+ → 数据线接车机 USB 数据口 → 点「有线连接」 | 30 秒内进 CarLife 界面，触控可用 |
| 7 | **应用管理卸载 CarLife / 小白点** | 需要车机上真有这两个 App | 应用管理 → 找到目标 → 卸载 → 系统确认框点确定 | 卸载后原生设置里不再闪退；若系统框不弹，页面会给出原因（设备管理员 / 系统预装 / 安装包缺失） |

### P1 —— 功能已接入但没在实际链路上跑过

| # | 功能 | 怎么测 | 判据 |
|---|---|---|---|
| 8 | Android Auto 有线（AOA） | 安卓手机开 Android Auto → 数据线接车机 USB → 车机点 Android Auto | 手机切到「已连接车载」状态，车机出 AA 画面 |
| 9 | Android Auto 无线（Headunit Server） | 手机开发者选项开「Headunit Server」→ 同 Wi-Fi → 车机页填 `手机IP:5277` | 连接成功并出画面；失败看是否被手机端拒绝 |
| 10 | HiCar | 华为 / 荣耀鸿蒙手机靠近车机 → 悬浮菜单或 HiCar 页点「开始连接」 | 手机弹 HiCar 连接提示，输入六位码后出画面 |
| 11 | 车机自建热点 | 连接中心点「开启车机热点」→ 手机搜该 SSID | 手机能连上并拿到 IP；不支持 AP 模式时会明确报失败 |
| 12 | 蓝牙分享通道 | 传文件页选 APK →「蓝牙分享」→ 选目标设备 | 系统蓝牙传输界面出现且能发完；车机无蓝牙 OPP 时会提示"没有应用可执行此操作"（属系统限制） |
| 13 | 悬浮球在车机桌面 | 系统无障碍里开启 `iCarPlay 悬浮按钮` → 回原桌面 | 悬浮球显示在原车机桌面上、可拖动、点外部自动收起 |
| 14 | 并发场景 | AirPlay 投屏中来 CarLife 请求；CarPlay 中来电 | 不崩、不黑屏、可恢复；来电后画面能回来 |
| 15 | 长时间稳定性 | AirPlay 镜像连续 30 分钟；CarPlay 连续 30 分钟 | 无断连、无内存暴涨（`dumpsys meminfo` 前后对比） |

### P2 —— 边界与负向测试

| # | 功能 | 怎么测 | 判据 |
|---|---|---|---|
| 16 | 更新包校验失败的处理 | 手动构造一个 SHA 不符 / 签名不符的包，让更新器下载 | 明确拒绝并提示原因，绝不进入安装 |
| 17 | 多源下载与自动换源 | 断开 GitHub 直连，只留镜像源 | 能自动换源并下载完成 |
| 18 | 断点续传 | 下载中拔网 30 秒再恢复 | 续传而非重下，最终校验通过 |
| 19 | 其他语言品牌残留 | 车机语言切日语 / 韩语 / 俄语 | 引擎等待页仍会出现第三方品牌（当前只覆盖 en 与 zh-rCN，属已知残留） |
| 20 | 引擎内部设置页品牌 | CarPlay 里三指下滑进快捷菜单 → 设置 / 诊断 | 少量品牌词仍在（已知残留，路径不在主链路） |
| 21 | DLNA 真机投送 | 手机相册 / 视频 App 选「投屏」→ 选 iCarPlay | 图片 / 视频上屏，停止后画面卡自动清空 |

## 三、模拟器 / ADB 能自动跑的（无需实车）

```bash
# 多分辨率适配：改模拟器分辨率后逐页截图
adb shell wm size 1280x720 && adb shell wm density 160
adb shell am start -n com.rockjesus.icarplay/.IcarLauncherActivity
adb exec-out screencap -p > home-1280x720.png
adb shell wm size reset && adb shell wm density reset

# 品牌残留扫描：进引擎等待页与设置页，截图比对是否还有第三方字样
adb shell am start -n com.rockjesus.icarplay/.CarPlayActivity
adb shell input tap 420 473          # 开始 CarPlay 接收
adb exec-out screencap -p > carplay-wait.png

# 更新全流程：装旧版包 → 开更新页 → 观察检查 / 下载 / 校验 / 安装四段
adb install -r icarplay-<旧版本>.apk
adb shell am start -n com.rockjesus.icarplay/.UpdateActivity

# 应用管理卸载：先装一个测试 APK，再从应用管理卸掉
adb install -r test-app.apk
adb shell am start -n com.rockjesus.icarplay/.AppManageActivity
adb shell pm list packages | grep test

# 传文件：Windows 浏览器打 http://<车机IP>:8090 上传 / 下载
# DLNA：PC 上起一个 DLNA 客户端投送图片 / 视频
# 稳定性：投屏中每 5 分钟抓一次内存与帧率
adb shell dumpsys meminfo com.rockjesus.icarplay | Select-String "TOTAL"
```

判定记录要落到本目录 `docs/` 下的验证笔记，附截图文件名与时间，避免"好像测过"。

## 四、需要实车的手工顺序（建议一次上车跑完）

1. 装包 → 进 App → 走完权限引导（悬浮窗 / 麦克风 / 位置 / 蓝牙 / 通知 / 电池优化）；
2. 连接中心截图（Wi-Fi、IP、热点、USB、蓝牙状态一并留档）；
3. AirPlay：iPhone 控制中心选 `iCarPlay` → 截图 + 连续 3 分钟；
4. CarLife+ 有线 → 截图 + 触控确认；
5. CarPlay 有线 → 截图；再试无线（蓝牙 + 5GHz 热点）；
6. Android Auto 有线 / 无线（有安卓手机时）；
7. 应用管理卸载 CarLife / 小白点 → 回原生设置确认不再闪退；
8. 熄火锁车 2 分钟 → 着车 → 确认不无限重启、本 App 不自启；
9. 全程 `adb logcat -v time > car-test.log`（能量车机 ADB 时），失败点单独标注时间戳。

## 五、取证规范

- 每个结论必须带：截图 / 日志片段 / 时间戳，三者至少两个；
- 失败时同时记录：页面提示语原文、`adb logcat` 相关 tag、当时的网络状态（SSID、频段、IP）；
- 不在仓库里留真实 SSID、IP、VIN、手机号；截图打码或替换为示例值。
