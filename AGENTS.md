# SKRoot Pro Compose 开发与发布规范

本文件适用于仓库根目录及全部子目录。后续 Agent 在同步上游、修改 UI、构建 APK、检查更新或发布 Release 时应遵循以下规则。

## 1. 版本号来源

- 唯一版本源位于根目录 `gradle.properties`：
  - `SKROOT_CORE_VERSION`：上游 SKRoot 核心版本，格式为 `major.minor.patch`。
  - `SKROOT_UI_REVISION`：本项目 UI/管理器修订号 `X`，范围 `1..999`。
- Android `versionName` 固定生成成 `<SKROOT_CORE_VERSION>.<SKROOT_UI_REVISION>`，例如 `4.5.4.10`。
- Android `versionCode` 固定按以下公式生成，确保升级顺序稳定：

  ```text
  major * 10,000,000 + minor * 100,000 + patch * 1,000 + X
  ```

- 每次发布本项目改动时，`SKROOT_UI_REVISION` 加 1。
- 上游核心版本变化时，先更新 `SKROOT_CORE_VERSION`，再将 `SKROOT_UI_REVISION` 重置为 `1`。
- 历史 `v4.5.4-compose.X` Tag 仅作为旧版本记录；新版本统一使用四段版本号。

## 2. 更新渠道边界

- 管理器更新只读取本仓库 GitHub Releases：

  ```text
  https://api.github.com/repos/dreamcolor123/SKRoot-Pro-Compose/releases/latest
  ```

- 管理器更新检测默认关闭；用户启用后才发起请求。
- Release Tag 使用 `v<versionName>`，更新解析器同时兼容历史 `v<core>-compose.X` Tag。
- 管理器下载地址必须取自 Release 的 APK asset，更新日志取 Release body。
- 模块市场、模块 `update_json`、模块 ZIP 与模块更新 JSON 协议继续使用 SKRoot 上游规则。管理器更新迁移不得修改 `ModuleRepository.MARKET_URL`、`JsonParsers.moduleUpdate` 或模块提供的更新地址。

## 3. APK 与 Release 命名

- 官方 Release 的包名固定为 `com.linux.compose`，应用名称固定为 `SKRoot Pro`。
- 官方 APK 文件名固定为：

  ```text
  v4.5.4.X-UI重构版-SKRoot Pro.apk
  ```

  实际构建时使用当前版本，即 `v<versionName>-UI重构版-SKRoot Pro.apk`。
- 官方 Tag 固定为 `v<versionName>`，Release 标题使用 `SKRoot Pro v<versionName>`。
- 自助品牌构建沿用同一版本前缀，文件名为 `v<versionName>-UI重构版-<应用名称>.apk`。
- GitHub 对 URL 中的中文或空格可能进行转义或清理；Release asset 的显示标签仍应保持上述完整文件名。

## 4. 上游同步规则

- 同步上游时先区分核心/Native 更新与管理器 UI 源码更新。
- Native 库、JNI、AIDL、JSON 字段及调用顺序保持上游语义。
- 上游核心版本发生变化后按第 1 节更新核心版本并重置 UI 修订号。
- 仅同步二进制 Native 库时，也应校验三项 ARM64 库均被打包：
  - `libmagica.so`
  - `libpermissionmanager.so`
  - `libresetprop.so`
- UI、主题、本地定制器和导航改动不得顺带改变 Root、授权、模块或 Native 行为。

## 5. 发布前验证

依次执行：

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./gradlew clean :app:assembleRelease \
  -PPUBLISH_APPLICATION_ID=com.linux.compose \
  -PPUBLISH_APP_NAME="SKRoot Pro" \
  -PRELEASE_STORE_FILE=... \
  -PRELEASE_STORE_PASSWORD=... \
  -PRELEASE_KEY_ALIAS=... \
  -PRELEASE_KEY_PASSWORD=...
```

发布 APK 必须确认：

- `aapt2 dump badging` 中包名、应用名称、`versionName` 和 `versionCode` 正确。
- APK v2 签名有效，正式签名 SHA-256 保持为 `ec4d3bc1cc054ad2d52b671785c265e8bd66530f8ed6502179e423fdea8fe6dc`。
- `zipalign -c -p 4` 通过。
- 三项 ARM64 Native 库完整。
- 本地定制器依赖的 17 项 launcher 资源完整。
- 文件名严格等于 `v<versionName>-UI重构版-SKRoot Pro.apk`。
- README 中的当前版本、下载链接和 SHA-256 已同步。
- Tag 指向包含最终 README 的提交，Release 状态为非草稿、非预发布。

## 6. Release 内容

- Release 附件默认只放官方签名 APK；调试包用于本地验证。
- Release notes 应列出本轮 UI/功能修复、上游核心版本、应用版本和 APK SHA-256。
- 发布后通过 GitHub API再次核对 Tag、asset 数量、asset 大小与 SHA-256，并保持工作区干净。
