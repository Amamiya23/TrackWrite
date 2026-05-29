# Settings Appearance & Frequency: Inline Expand → MD3 AlertDialog

## Goal

将设置页中"外观"和"记录频率"两个选项从当前的内联展开样式改为 Material Design 3 标准的 AlertDialog + RadioButton 弹窗。

## Requirements

- 点击设置行时弹出 MD3 AlertDialog，而非在卡片内联展开选项
- Dialog 内使用 RadioButton 列表展示所有选项
- 选中后关闭 dialog 并更新设置
- 移除 `expandedAppearance` / `expandedFrequency` 内联展开逻辑

## Acceptance Criteria

- [ ] 点击"外观"行弹出 AlertDialog，包含 3 个 RadioButton 选项（系统/浅色/深色）
- [ ] 点击"记录频率"行弹出 AlertDialog，包含频率 RadioButton 选项
- [ ] 选中后 dialog 关闭，设置生效
- [ ] 编译通过

## Out of Scope

- 不改变其他设置项（如开关、步进器）的交互方式
- 不修改颜色/主题相关配置
