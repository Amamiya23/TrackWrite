# UI Polish: Navigation, Metrics, Sections, Remove AI-style Labels

## Goal

修复设计评审中发现的三个问题，并移除应用中不必要的解释性标题，使 UI 更自然、不像 AI 生成的。

## Requirements

### 1. NavigationItem 使用标准 M3 组件
- 将自定义 `NavigationItem`（Surface + clickable）替换为标准 `NavigationBarItem`
- 保留现有的图标和标签

### 2. MetricGrid 布局优化
- 改进 2x2 刚性布局，使视觉层次更清晰
- 考虑将 Active Track 名称更突出显示
- 统计数据（Points、Duration、Distance）可以水平排列

### 3. SectionBlock / SurfaceCard 一致性
- 建立清晰的使用规则：
  - `SectionBlock`：主要内容区域（RecordingPanel、TrackHistoryPanel）
  - `SurfaceCard`：次要/包含信息（SettingsSection、PhotoMatchRow）
- 确保同一屏幕内的使用一致

### 4. 移除 AI 风格的解释性标题
- 审查所有 `SectionHeader` 调用
- 移除不必要的、过于解释性的标题
- 保留功能性标题（如 Track history count）
- 目标：UI 更简洁，不像 AI 生成的模板

## Acceptance Criteria

- [ ] NavigationItem 使用 NavigationBarItem 标准组件
- [ ] MetricGrid 布局更灵活，视觉层次更清晰
- [ ] SectionBlock 和 SurfaceCard 使用场景一致
- [ ] 移除不必要的解释性标题
- [ ] 编译和 lint 通过
- [ ] 不影响现有功能

## Definition of Done

- `./gradlew :app:compileDebugKotlin` 通过
- `./gradlew :app:lintDebug` 通过
- UI 更自然、更像手工设计

## Out of Scope

- 添加新功能
- 修改颜色方案（已在 Dynamic Color 任务中完成）
- 修改设置页面结构

## Technical Notes

- 关键文件：`app/src/main/java/com/trackwrite/app/MainActivity.kt`
- 使用 Material 3 标准组件
- 遵循 PRODUCT.md 中的 "Reliable, light, quiet" 品牌个性
