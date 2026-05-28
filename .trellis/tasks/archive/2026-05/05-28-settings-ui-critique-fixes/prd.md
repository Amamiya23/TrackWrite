# 设置页面评审问题修复

## Goal

修复 UX 评审发现的 6 个问题（2xP1, 2xP2, 2xP3），提升设置页面的信息架构和交互效率。

## What I already know

* 设置页面刚完成 M3 视觉重做（commit f152893）
* 组件结构：SettingsGroup, SettingNavigationRow, SettingStepper, SettingSwitchRow, ExportModeSelector
* 当前问题清单见下方 Requirements

## Assumptions (temporary)

* 保持现有功能不变，仅修复评审问题
* 遵循 M3 规范
* 不新增设置项

## Open Questions

* (none - 评审已明确所有修复方向)

## Requirements (evolving)

### P1: 结构性碎片化
* 合并外观（Appearance）和录制频率（Recording Frequency）为一个 "通用设置" SettingsGroup
* 两行之间用 HorizontalDivider 分隔
* 移除各自的独立 SettingsGroup 包裹

### P1: 录制频率图标语义错误
* 将 `Icons.Default.Refresh` 替换为更匹配的图标
* 候选：`Icons.Default.Speed`（速度/频率）或移除图标

### P2: 导出设置标题重复
* 移除 `ExportModeSelector` 内部的 `Text("Export Settings")` 标题
* 仅保留分段按钮，外部 SectionHeader 已提供上下文

### P2: BottomSheet 交互过重
* Appearance（3 选项）和 Frequency（3 选项）改用 inline 展开
* 点击行后展开 Radio 列表在卡片内，而非弹出 BottomSheet
* 移除 SettingsBottomSheet 组件（如不再使用）

### P3: 步进器缺少单位
* 在数值后添加单位文本（"min" 或本地化的 "分钟"）
* 通过 stringResource 实现，确保中英文一致

### P3: 设置项缺少描述文案
* 外观和录制频率行添加简短描述作为 subtitle
* 步进器项考虑添加范围说明

## Acceptance Criteria (evolving)

* [ ] 外观和录制频率在同一个卡片内
* [ ] 录制频率图标语义正确
* [ ] Export Settings 标题只出现一次
* [ ] 3 选项选择器不再使用 BottomSheet
* [ ] 步进器数值带单位
* [ ] 编译通过、lint 通过、测试通过

## Definition of Done

* 代码通过 compileDebugKotlin + lintDebug + testDebugUnitTest
* 视觉验证（亮色/暗色）

## Out of Scope (explicit)

* 新增设置项
* 其他页面 UI 修改
* i18n 变更（仅添加必要的单位字符串）

## Technical Notes

* MainActivity.kt lines 1646-2019 包含所有 Settings 组件
* TrackWriteTheme.kt 色板无需修改
* strings.xml 需添加单位字符串

## Expansion Sweep

### Future Evolution
* 更多设置项可能加入通用设置分组

### Related Scenarios
* 无

### Failure & Edge Cases
* inline 展开时卡片高度变化需平滑
* 步进器单位在多语言下的显示
