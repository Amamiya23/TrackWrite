# 修复 GPS 写入成功后的误报与错误弹窗尺寸

## Goal

让照片位置写入结果与实际生成的副本保持一致：当副本已经包含目标 GPS 坐标时不得因 Android 隐私脱敏而误报失败；真正失败时仍应给出清晰、紧凑且可关闭的结果反馈。

## Background

- 用户在为照片写入位置后看到错误：`Screenshot_2026-07-19-12-20-01-459_com.microsoft.emmx.jpg: Could not verify written GPS coordinates.`
- 相册中保存的副本实际已经写入位置。`PhotoGeotagging.exportCopies(...)` 在 `app/src/main/java/com/trackwrite/app/media/PhotoGeotagging.kt:111` 创建并写入副本，随后在同文件 `:201` 到 `:208` 读回最终 URI 并执行严格 GPS 校验；失败文本来自 `:328` 到 `:330`。
- 原图写入入口已经通过 `requestMediaLocationPermissionThen` 申请 `ACCESS_MEDIA_LOCATION`（`app/src/main/java/com/trackwrite/app/MainActivity.kt:406`），副本写入入口没有申请该权限（同文件 `:665` 到 `:684`）。Android 10 及以上读取未脱敏 EXIF 位置需要该运行时权限，因此副本可能实际写入成功，但校验读到被移除 GPS 的流。
- 用户已确认：允许导出副本在首次写入前申请照片位置信息权限，以保留严格的最终文件校验。
- 写入结果底部当前使用全宽 `PrimaryActionButton`（`app/src/main/java/com/trackwrite/app/MainActivity.kt:4651`），导致“关闭”按钮视觉尺寸过大。

## Requirements

- R1：导出副本开始前必须复用现有 `ACCESS_MEDIA_LOCATION` 运行时权限流程；权限仅在尚未授予时请求，授予后继续原有副本目录选择与写入流程。
- R2：用户拒绝照片位置信息权限时不得开始副本写入，并继续使用现有本地化权限提示；不得生成无法严格读回校验、却被错误标记为成功的副本。
- R3：保留对最终输出 URI 的图片完整性、GPS 坐标、方向引用和海拔校验；不得移除校验、吞掉校验错误或放宽坐标容差来规避误报。
- R4：原图写入的权限、预检、替换和写后校验行为保持不变。
- R5：写入结果 Sheet 的“关闭”操作改为尾部对齐的紧凑 Material 文字按钮，复用现有 `close` 字符串和主题排版；视觉宽度不再填满容器，同时保持至少 48dp 的可访问触控目标。
- R6：保持批量进度、成功/跳过/失败统计、失败明细及中英文资源行为不变。

## Acceptance Criteria

- [x] AC1（R1）：副本模式首次写入且权限未授予时显示系统照片位置信息权限请求；授予后继续选择/复用导出目录并完成写入。
- [x] AC2（R2）：拒绝权限后不启动 `BulkOperation.WritingCopies`，不创建输出副本，并显示现有本地化权限提示。
- [x] AC3（R3）：权限已授予时，已成功写入目标 GPS 的副本通过最终 URI 读回校验，不再出现 `Could not verify written GPS coordinates.`。
- [x] AC4（R3）：最终副本无法读回、坐标明显不符或图片损坏时仍返回失败和原有可诊断原因。
- [x] AC5（R4）：原图写入仍在确认后申请同一权限，并保持当前严格校验链路。
- [x] AC6（R5）：结果 Sheet 的“关闭”按钮视觉宽度由文字与标准内容内边距决定，尾部对齐，不再全宽；触控高度至少 48dp。
- [x] AC7（R6）：结果统计、失败列表、进度弹窗和默认/简体中文资源无回归。
- [x] AC8：`./gradlew testDebugUnitTest`、`./gradlew :app:compileDebugKotlin` 和 `git diff --check` 通过；若连接设备可用，再实测一次首次授权、拒绝和已授权副本写入。

## Out of Scope

- 改变照片匹配算法或轨迹插值规则。
- 重做整个写入结果界面。
- 移除写入后的 GPS 校验。
- 为已有照片批量补授权或改变 Android 系统权限文案。
- 改造 SAF / MediaStore 存储架构或导出目录模型。

## Verification

- `./gradlew testDebugUnitTest :app:compileDebugKotlin :app:lintDebug` passed on 2026-07-19.
- `git diff --check` passed.
- Static flow review confirmed that copy mode reaches the existing permission launcher before folder selection or `BulkOperation.WritingCopies`; denial invokes the existing localized error path and does not run the pending action.
- `PhotoGeotagging` and the original-write permission/validation flow were unchanged.
- No Android device was attached to ADB, so the optional real-provider permission grant/deny and gallery readback matrix remains for user/device smoke testing.
