# SKRoot Pro Compose

Kotlin + Jetpack Compose UI 重构版的 SKRoot Pro 权限管理器源码。

本仓库基于 [SKRoot-linuxKernelRoot](https://github.com/abcz316/SKRoot-linuxKernelRoot) 中的 Pro `PermissionManager` 工程整理，重点重做管理器的 UI、状态流和导航层，采用 Material 3 / Compose 实现 KSU 风格的管理界面。

> 本项目保留原 SKRoot Pro 的 Native/JNI、AIDL、Magica 服务、JSON 协议、应用 ID、权限和 ARM64 Native 构建结构。源码中的上游实现与资源归属原项目作者。

## 当前版本

- 管理器版本：`4.5.3`
- Compose UI 发布版本：`v4.5.3-compose.2`
- 默认 Application ID：`com.linux.permissionmanager`
- `minSdk`：26
- `targetSdk`：31
- ABI：`arm64-v8a`

## 主要内容

- Material 3 Compose 单 Activity 架构
- 自适应手机底部导航与平板 NavigationRail
- 主页环境状态、Root 命令终端、系统状态卡片
- SU 授权管理与应用选择器
- 已安装模块、模块市场、更新和下载进度
- 设置、诊断、日志查看与导出
- Root Key、Boot / 热启动配置和重启选项
- `StateFlow` UI 状态、`SharedFlow` 一次性事件和 Repository 数据层
- 浅色/深色主题、动态强调色和纯白浅色背景

## 构建环境

- JDK 17
- Gradle 8.9（使用仓库中的 Gradle Wrapper）
- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21
- compileSdk 35 / Build Tools 35.0.0
- NDK `26.3.11579264`
- CMake 3.18.1

## 构建命令

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

### 品牌 Release 构建

构建参数可以覆盖 Application ID 和桌面应用名称。Android Application ID 不接受 `*` 字符，因此请求中的 `com.linux.**compose` 使用规范化后的 `com.linux.compose`。

```bash
# SKRoot Bro
./gradlew clean :app:assembleRelease \
  -PPUBLISH_APPLICATION_ID=com.linux.compose \
  -PPUBLISH_APP_NAME="SKRoot Bro"

# 顺丰速运
./gradlew clean :app:assembleRelease \
  -PPUBLISH_APPLICATION_ID=com.sf.activity \
  -PPUBLISH_APP_NAME="顺丰速运"
```

正式分发时通过以下 Gradle 参数注入签名密钥；密钥文件和密码不应提交到仓库：

```text
-PRELEASE_STORE_FILE=... \
-PRELEASE_STORE_PASSWORD=... \
-PRELEASE_KEY_ALIAS=... \
-PRELEASE_KEY_PASSWORD=...
```

Debug APK 输出路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

项目只配置 `arm64-v8a` Native ABI。安装和运行前，请确认设备、Root Key 与 SKRoot 环境配置符合上游项目要求。

本次公开 Release 提供两个签名 APK：

- `com.linux.compose` / `SKRoot Bro`
- `com.sf.activity` / `顺丰速运`

## 上游项目与致谢

- 上游项目：[abcz316/SKRoot-linuxKernelRoot](https://github.com/abcz316/SKRoot-linuxKernelRoot)
- UI 视觉参考：[tiann/KernelSU](https://github.com/tiann/KernelSU)

本仓库不复制 KernelSU 的页面源码或资源，仅参考其 Material 管理器视觉理念并进行独立 Compose 实现。

## 变更范围

本版本主要包含 UI、状态管理、导航、主题和构建迁移。Native C/C++ 函数体、JNI 声明、AIDL 接口、Magica 服务和现有业务协议保持原语义。
