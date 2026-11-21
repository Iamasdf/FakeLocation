# PinLocation

PinLocation 是一款免 Root 的 Android 位置模拟应用，支持地图选点、地点搜索、坐标输入和悬浮摇杆，并提供历史位置的置顶、编辑与删除。当前版本 **1.12.3**（versionCode 1123）。

[English](./README.en.md)

## 1. 功能

### 1.1 首页：位置模拟

- 目标位置卡片：显示名称、城市和经纬度（BD09）。
- 历史位置列表：按置顶、时间排序，右上角搜索按钮可按名称过滤。
- 长按历史项：置顶 / 取消置顶、编辑、删除。
- 「启动模拟 / 停止模拟」一键切换；运行时显示前台服务通知。
- 左侧抽屉与右上角菜单：历史记录、设置、开发人员选项、检测更新、问题反馈、联系作者。

### 1.2 地图选点

地图使用百度地图 SDK，支持：

- 普通图与卫星图切换，双击或按钮缩放。
- 单击地图或 POI 选点并放置标记；长按地图选点并逆地理编码显示地址。
- 地点联想搜索，选中搜索结果直接跳到该位置。
- 坐标输入：支持 BD09 与 GPS（WGS84）两种坐标系，输入后自动换算到 BD09 显示。
- POI 信息窗：保存该位置（写入历史并返回首页）、复制坐标、分享坐标。
- 「返回当前位置」将地图移回设备定位点。

### 1.3 历史记录与位置编辑

- 历史记录页：关键字搜索、点击记录设为目标位置、长按改名或删除、一键删除全部。
- 位置编辑页：修改名称、详细地址、海拔和经纬度，点击页内小地图可重新选点。
- 选中位置时写入两条坐标：WGS84 与 BD09；首页展示 BD09，启动模拟时转换为 WGS84 交给系统。

### 1.4 悬浮摇杆

- 需要悬浮窗权限，在首页开关或通知栏中显示 / 隐藏。
- 两种样式：圆形摇杆、按键摇杆（设置中切换）。
- 地图面板：搜索地点、点击地图选点、「前往」把模拟位置切到该点。
- 历史面板：从已保存的历史位置中选择。
- 三档速度：步行、跑步、自行车，速度值见设置页。

### 1.5 设置

- 摇杆类型（圆形 / 按键）。
- 步行速度（默认 1.2 m/s）、跑步速度（默认 3.6 m/s）、驾驶速度（默认 10.0 m/s）。
- 海拔高度（默认 55.0 m）。
- 关闭日志、版本号、开发者信息。

> 当前版本的设置页不包含随机偏移、历史记录有效期、定位精度或更新间隔等选项；`strings.xml` 中保留了个别未使用的文案。

### 1.6 运行机制

- 通过 Android 的模拟位置接口工作，无需 Root，也不需要修改系统。
- 使用前台服务维持模拟，通知栏提供摇杆显示 / 隐藏操作。
- 日志写入应用私有目录 `Android/data/<应用包名>/files/Logs/PinLocation.log`，保留最近 3 天；「问题反馈」会把该日志文件作为附件分享出去。

### 1.7 本工程未包含的能力

线路编辑与移动、GPX 导入导出、收藏与分组、独立安装实例 / 会员、夜间地图、定位精度与更新间隔设置、高德 / 腾讯地图切换。`array_map_type` 等资源为历史残留，未接入界面。

## 2. 环境与依赖

| 项目 | 值 |
| --- | --- |
| 最低系统 | Android 8.1（API 27） |
| 编译 / 目标版本 | compileSdk 32、targetSdk 32 |
| ABI | 仅 `arm64-v8a` |
| JDK | 17 |
| Gradle / AGP | Gradle 8.13、AGP 8.12.1 |
| NDK / Build Tools | 26.2.11394342、36.0.0 |
| 地图 SDK | 百度地图 7.6.5（`app/libs/BaiduLBS_Android.jar` + `app/libs/arm64-v8a/*.so`） |

主要依赖：AndroidX AppCompat、ConstraintLayout、Preference、Material、OkHttp、XLog、Markwon。

## 3. 构建

### 3.1 准备 `local.properties`

该文件不纳入版本控制，需要在工程根目录自行创建：

```properties
sdk.dir=<Android SDK 路径>
MAPS_API_KEY=<百度地图 Android AK>
MAPS_SAFE_CODE=<百度地图安全码>
```

`MAPS_API_KEY` 由 secrets 插件生成 `BuildConfig.MAPS_API_KEY`，启动时通过 `SDKInitializer.setApiKey(...)` 传入。百度 AK 必须同时匹配包名 `com.iamasdf.pinlocation` 与签名证书的 SHA1；debug 与 release 都使用仓库内的 `keystore/PinLocation.jks`，包名或签名不一致都会导致地图鉴权失败，底图只显示网格。

### 3.2 构建命令

```bash
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/PinLocation_1.12.3_arm64-v8a_debug.apk`

```bash
./gradlew assembleRelease
```

产物：`app/build/outputs/apk/release/PinLocation_1.12.3_arm64-v8a_release.apk`（开启 R8 混淆与资源压缩）。

### 3.3 CI

- `.github/workflows/build-check.yml`：推送到 `master` 或提交 PR 时构建 debug APK 并上传；未配置 secrets 时用 `CI_PLACEHOLDER` 代替地图 Key，地图鉴权会失败。
- `.github/workflows/build-release.yml`：推送 tag 或手动触发时构建、签名并发布 Release，需要 `MAPS_API_KEY`、`MAPS_SAFE_CODE`、`SIGNING_KEY`、`ALIAS`、`KEY_STORE_PASSWORD`、`KEY_PASSWORD` 等 secrets。
- `.github/workflows/codeql-analysis.yml`：代码扫描。

## 4. 安装与首次使用

1. 安装 APK（仅支持 64 位 ARM 设备）。
2. 授予定位权限；Android 10 及以上如需后台持续模拟，请选择「始终允许」。
3. 打开系统「开发者选项」→「选择模拟位置信息应用」→ 选择 PinLocation。
4. 在首页点击 ＋ 进入地图选点，或从历史位置中选择目标，然后点「启动模拟」。
5. 需要使用悬浮摇杆时，打开首页的摇杆开关并按提示授予悬浮窗权限。

应用不设欢迎页或协议页，启动即进入首页。

## 5. 已知限制

- 百度 AK 未绑定签名、未开通相应服务或 Key 错误时，地图无法加载。
- 逆地理编码失败或返回空地址时，位置名称回退为「地图位置」。
- 是否被其他应用采纳取决于系统定位策略、坐标系和目标应用自身的逻辑。
- 仅提供 `arm64-v8a`，不支持 32 位 ARM 与 x86 设备。

## 6. 许可

本项目以 GPL-3.0-only 发布，版权与许可条款见 [LICENSE](./LICENSE)。

## 7. 致谢

感谢百度地图 SDK、XLog、OkHttp、Markwon 与 AndroidX 等开源项目。
