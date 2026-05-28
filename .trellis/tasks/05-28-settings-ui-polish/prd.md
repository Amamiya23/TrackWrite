# 优化设置页面UI审美

## Goal

将 TrackWrite 应用的设置页面从当前功能性 UI 提升到产品级审美水平，解决视觉层次不清、间距不一致、交互反馈弱等问题。

## What I already know

* 设置页面位于 `app/src/main/java/com/trackwrite/app/MainActivity.kt` (line 1590+)
* 使用 Jetpack Compose + Material Design 3
* 主题定义在 `app/src/main/java/com/trackwrite/app/ui/TrackWriteTheme.kt`
* 当前设置项：外观模式、录制频率、照片匹配参数、导出设置
* 近期已有的 UI 改进：紧凑步进器、对话框选择器、分段按钮、底部弹窗

## Assumptions (temporary)

* 保持现有功能不变，仅优化视觉呈现
* 遵循 Material Design 3 规范
* 支持亮色/暗色主题

## Open Questions

* [x] 用户对优化范围的具体期望？→ 整体重做，强调专业UI设计感，避免AI感
* [x] 参考的设计风格？→ Material 3 风格（大圆角卡片、宽松间距、强调色突出、呼吸感强）
* [x] 卡片内项目分隔方式？→ 细分割线（HorizontalDivider）
* [x] 设置分组标题样式？→ 正文加粗（titleSmall + SemiBold）
* [x] 设置项是否需要图标？→ 仅关键项配图标（外观、录制频率、导出、相机偏移）
* [x] 步进器的设计风格？→ 紧凑加减按钮，优化按钮样式和间距
* [x] 对话框的设计风格？→ Bottom Sheet（从底部弹出的模态表单）
* [x] 间距系统的具体数值？→ M3标准间距（圆角16dp、内边距16dp、组间距24dp）
* [x] 导出模式选择器的设计风格？→ 保留分段按钮，优化样式和间距
* [x] 主题色板调整？→ 微调色板以增强M3风格

## Requirements (evolving)

* 整体重做设置页面UI，强调专业设计感
* 避免AI生成的generic风格（过度圆角、过度阴影、过度动画）
* 采用 Material 3 设计语言（大圆角卡片、宽松间距、强调色突出）
* 优化视觉层次（标题与内容区分）
* 统一间距系统（M3标准：圆角16dp、内边距16dp、组间距24dp）
* 增强交互反馈（涟漪效果、状态过渡）
* 改进组件设计（步进器、选择器、对话框）
* 确保颜色对比度符合 WCAG 标准
* 细分割线分隔卡片内项目
* 分组标题使用正文加粗样式（titleSmall + SemiBold）
* 关键设置项添加图标（外观、录制频率、导出、相机偏移）
* 步进器优化按钮样式和间距
* 选择器改用 Bottom Sheet
* 导出模式选择器保留分段按钮，优化样式和间距
* 微调主题色板以增强M3风格
* 预留组件扩展性，方便后续添加新设置项
* 预留组件扩展性，方便后续添加新设置项
* 预留组件扩展性，方便后续添加新设置项
* 选择器改用 Bottom Sheet

## Acceptance Criteria (evolving)

* [ ] 设置页各分组视觉层次清晰
* [ ] 所有间距遵循 M3 标准（圆角16dp、内边距16dp、组间距24dp）
* [ ] 交互元素有明确的点击反馈
* [ ] 文本与背景对比度达标
* [ ] 亮色/暗色主题下视觉一致
* [ ] 关键设置项有图标
* [ ] 对话框改为 Bottom Sheet
* [ ] 导出模式选择器样式优化
* [ ] 主题色板微调完成

## Definition of Done

* 代码通过 lint / typecheck
* 视觉效果在不同主题下验证
* 无功能回退

## Out of Scope (explicit)

* 设置项功能变更
* 新增设置项
* 本地化（i18n）变更
* 其他页面的UI优化

## Technical Notes

* 当前代码结构：`SettingsScreen` 组件包含所有设置 UI
* 主题色板定义在 `TrackWriteTheme.kt`
* 组件：`SettingsGroup`、`SettingNavigationRow`、`SettingStepper`、`SettingSwitchRow`、`ExportModeSelector`

## Expansion Sweep

### Future Evolution
* 设置项可能增加（如通知设置、隐私设置等）
* 需要保持组件可扩展性

### Related Scenarios
* 其他页面的设置相关组件（如主页的设置入口）
* 需要保持视觉一致性

### Failure & Edge Cases
* 长文本设置项的处理
* 暗色主题下的对比度问题
* Bottom Sheet 的键盘遮挡问题
