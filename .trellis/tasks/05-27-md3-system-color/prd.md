# MD3 System Color API Integration

## Goal

将 TrackWrite 的 Material Design 3 配色方案从硬编码颜色改为使用系统 Dynamic Color (Material You)，让应用能跟随用户在系统设置中选择的壁纸主题色。

## What I already know

* 当前 `TrackWriteTheme.kt` 定义了硬编码的 `LightColors` 和 `DarkColors` 方案
* `AppearanceMode` 枚举有 System / Light / Dark 三种模式
* minSdk = 31 (Android 12+)，原生支持 `dynamicLightColorScheme()` / `dynamicDarkColorScheme()`
* compose-bom 2024.11.00，material3 依赖已存在

## Requirements

* 当 appearance = System 时，使用 `dynamicLightColorScheme()` / `dynamicDarkColorScheme()` 获取系统壁纸主题色
* 当 appearance = Light / Dark 时，保持现有硬编码颜色作为回退
* 不添加新的 UI 开关，复用现有 AppearanceMode 逻辑

## Decision (ADR-lite)

**Context**: 用户希望应用跟随系统 Material You 主题色
**Decision**: System 模式自动使用 Dynamic Color，不添加新 UI 开关
**Consequences**: 最简实现，复用现有 AppearanceMode 逻辑；Light/Dark 保留硬编码作为回退

## Acceptance Criteria

* [ ] System 模式下应用使用系统 Dynamic Color（跟随壁纸主题）
* [ ] Light / Dark 模式保持现有硬编码颜色
* [ ] 深色/浅色切换正常工作
* [ ] 不影响现有功能

## Definition of Done

* Lint / typecheck / CI green
* 配色在 Android 12+ 设备上正确跟随系统主题

## Out of Scope

* 自定义颜色选择器
* Android 12 以下的兼容性处理

## Technical Notes

* 关键文件：`app/src/main/java/com/trackwrite/app/ui/TrackWriteTheme.kt`
* Android 12+ 使用 `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)`
* 需要在 @Composable 函数中获取 context
