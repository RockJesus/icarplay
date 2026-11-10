# Android Auto 接收（iCarPlay）

iCarPlay 在同一个 APK 里增加了 Android Auto 接收端。协议层来自
[`harryjph/android-auto-headunit`](https://github.com/harryjph/android-auto-headunit)
（AGPL-3.0），去掉了它原有的旧 UI 和开机自启，保留：

- USB AOA（Android Open Accessory）切换；
- AAP 消息编解码（protobuf-lite，离线生成，不依赖 Gradle protoc 插件）；
- TLSv1.2 握手（使用开源项目内置的 Google Automotive Link 测试身份）；
- H.264 MediaCodec 解码 / 音频输出 / 触控与按键回传 / 定位发送。

## 入口

主界面和内置小白点都有 **Android Auto 接收**。界面提供：

1. **连接 USB 手机（推荐）**：列 USB 设备 → 请求权限 → 发送 AOA 切换 →
   手机重新枚举为 `0x18D1:0x2D00/0x2D01` → 启动 `AapService`；
2. **无线连接（手机 Headunit Server）**：手机 Android Auto 开发者选项里
   `Start headunit server`，车机与手机同一 Wi-Fi，输入手机 IP，端口 `5277`；
3. **打开投影画面**：进入 `AapProjectionActivity`，显示手机画面并回传触控；
4. **停止 Android Auto**：停止 `AapService`。

手机切到配件模式后，`AndroidAutoUsbReceiver` 也会直接启动服务，所以插线后
不必每次手动点“连接 USB”。

## 必须满足

- 车机 USB 口必须支持 **USB Host + 数据**，只供电的口不行；
- 手机必须安装 **Android Auto**（部分国行/定制系统会裁剪或限制）；
- 手机需允许 USB 调试/文件传输，首次连接要在手机上确认 USB 权限；
- 有线是主路径；无线依赖手机端 `Headunit Server`，不是原生无线 Android Auto
  的蓝牙/Wi-Fi Direct 自动握手。

## 已知边界

- 没有在 2020 款锐际 SYNC+ 真车上验证过；本机没有模拟器/NDK/adb，无法代替实车；
- 车机没有 Google 服务（GMS）不影响“接收端”，但手机端 Android Auto 被厂商
  裁剪时仍然连不上；
- 无线只做手动 IP 的 TCP 5277，不做蓝牙引导 + Wi-Fi Direct；后者在 SYNC+
  上曾出现 Wi-Fi Direct 重启循环，而且 Android Auto 17.4+ 对传统无线触发有限制；
- 1920×720 是 8:3，Android Auto 画面按 16:9 输出时会左右留黑边；
- 音频/麦克风需要 `RECORD_AUDIO`，定位需要 `ACCESS_FINE_LOCATION`；拒绝授权
  只影响对应通道，不影响视频和触控。

## 构建说明

仓库已经把 7 个 protobuf-lite Java 类离线生成并提交到：

```
app/src/main/java/info/anodsplace/headunit/aap/protocol/proto/
```

所以 CI 不需要 `com.google.protobuf` Gradle 插件和 `protoc`，只依赖运行期
`com.google.protobuf:protobuf-lite:3.0.1`。如需重新生成：

```bash
protoc -I app/src/main/proto \
  --plugin=protoc-gen-javalite=/path/to/protoc-gen-javalite \
  --javalite_out=app/src/main/java \
  app/src/main/proto/*.proto
```

## 许可

Android Auto 模块为 AGPL-3.0；与 iCarPlay 其余部分组合后按 AGPL-3.0 分发。
完整文本见仓库根目录 `LICENSE-AGPL-3.0.txt`，来源与版权见 `NOTICE`。
