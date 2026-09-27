# IP 跳跃警报器

Android 应用：持续监控设备出口 IP。IP 一旦变化（VPN 重连、Wi-Fi 切蜂窝等），立刻发出系统通知。用来避免跨境账号因 IP 突变被平台风控。

最低系统：Android 6.0（API 23）

## 功能

- 前台服务 + WorkManager 双保险，退到后台后继续检测
- 默认定时 30 秒查一次出口 IP，配置页可自定义间隔（5–86400 秒）
- 监听 Wi-Fi、蜂窝、VPN 网络切换并立刻复查
- 通知内容包含旧 IP、新 IP、地理位置、变化时间，带声音和振动
- 可忽略 VPN 切换、仅在 Wi-Fi ↔ 蜂窝切换时警报、设置静默时段
- Room 保存历次变化记录，可查看详情
- 无网络时跳过检测；IP API 失败时自动换备用接口

## 界面

- 首页：当前 IP、基准 IP、网络类型、监控开关、立即检测
- 配置页：自定义检测间隔、VPN 忽略、Wi-Fi/蜂窝开关、静默时段、手动/重置基准 IP
- 历史页：时间线列表与详情

## 构建

需要 JDK 17 与 Android SDK（compileSdk 34 / targetSdk 34 / minSdk 23）。

推荐直接用 Android Studio 打开项目构建。命令行方式：

```bash
# 指向本机的 Android SDK 与 JDK（也可写进 local.properties 的 sdk.dir）
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk-17

# 首次需安装 SDK 组件并接受许可
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --sdk_root=$ANDROID_HOME --licenses
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --sdk_root=$ANDROID_HOME \
  "platform-tools" "platforms;android-34" "build-tools;34.0.0"

./gradlew :app:assembleDebug        # Windows 下改用 gradlew.bat
```

产物路径：`app/build/outputs/apk/debug/app-debug.apk`

安装到设备：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 使用说明

1. 首次打开会查询当前出口 IP，并自动设为基准
2. 点「启动监控」，建议同时关闭电池优化
3. 出口 IP 变化时会弹出通知，历史页可回看
4. 需要改基准时，到配置页手动填写或点「将当前 IP 设为基准」

应用不需要 ROOT，不创建 VPN Service，没有账号和广告。
