# 第三方组件

本仓库以 GPLv3 发布，下列组件的许可证与本仓库相容，并在此说明来源与构建方式。

## DiPlay CarPlay 引擎（GPLv3）

- 上游：<https://github.com/shihabal3amri/DiPlay>，版本 `v0.2.22`
- 位置：`app/libs/diplay-shared.aar`、`app/libs/diplay-common.aar`
- 使用方式：作为 Android Library 依赖编入同一 APK，不单独分发

### 重新构建 AAR

```bash
curl -sL -o DiPlay-0.2.22-source.zip \
  https://github.com/shihabal3amri/DiPlay/releases/download/v0.2.22/DiPlay-0.2.22-source.zip
unzip -q DiPlay-0.2.22-source.zip && cd DiPlay-0.2.22
# 本机只装 NDK 27，把 shared 的 ndkVersion 改成本机版本后编译
sed -i "s/ndkVersion = '28.2.13676358'/ndkVersion = '27.0.12077973'/" shared/build.gradle
./gradlew :shared:assembleRelease :common:assembleRelease
cp shared/build/outputs/aar/shared-release.aar  <本仓库>/app/libs/diplay-shared.aar
cp common/build/outputs/aar/common-release.aar  <本仓库>/app/libs/diplay-common.aar
```

### 我们改了什么

- 合并进本包时用 `tools:node="remove"` 剥掉：`WRITE_SETTINGS`、`REQUEST_INSTALL_PACKAGES`、`PACKAGE_USAGE_STATS`、`WRITE_EXTERNAL_STORAGE`，以及引擎自带的 `com.shilapi.xcertplay.BootReceiver` 开机自启。
- 移除 `DiPlayActivity` 的 `MAIN/LAUNCHER` 入口，避免 APK 出现第二个启动图标；只由 iCarPlay 的 CarPlay 页拉起。
- 因此车机热点「自动开启」与引擎自更新属于**已知降级**：热点仍需在系统设置里手动开，更新由 iCarPlay 自己的更新器负责。
- 界面品牌做**资源覆盖**（不改引擎代码、不改 dex）：`app/src/main/res/values/strings_icarplay_brand.xml`、`values-zh-rCN/strings_icarplay_brand.xml` 覆盖引擎两个 AAR 里值含 `DiPlay` 的全部字符串（en 97 条 / zh-rCN 96 条），把品牌字样换成 `iCarPlay`；`app/src/main/res/drawable/ic_carplay.png` 覆盖引擎等待页的图标（192×192，青→紫渐变）。覆盖走 Android 资源合并规则（app 模块同名资源优先），引擎行为不变。
- 若上游升级版本，需按同一份清单重新生成覆盖：用 `zipfile` 读 `diplay-common.aar` 的 `res/values/values.xml`、`res/values-zh-rCN/values-zh-rCN.xml`，筛出值含 `DiPlay` 的 `<string>` 后替换品牌词即可。

### 身份材料

引擎需要 `assets/offline-mfi/{certificate.p7b,identity.pk8}`（离线 MFi 身份），上游源码构建默认不包含，仅在其 release APK 中提供。本仓库的 `app/src/main/assets/offline-mfi/` 即取自上游 `v0.2.22` release 包，未做任何修改。
