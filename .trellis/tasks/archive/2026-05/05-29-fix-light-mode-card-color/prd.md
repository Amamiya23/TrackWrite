# Fix Card Color in Light Mode - Use Near-White for Cards

## Goal

在浅色模式下，设置页的卡片（Card）和背景颜色过于接近，缺乏视觉区分度。需要将卡片调整为类白色，增强层次感。

## What I Already Know

- 项目使用 Material 3 主题系统
- `SurfaceCard` 组件默认使用 `MaterialTheme.colorScheme.surface` 作为卡片颜色
- 浅色模式颜色配置：
  - `background = Color(0xFFF8FAFC)` - 非常浅的灰色
  - `surface = Color(0xFFFFFFFF)` - 纯白色
  - `surfaceVariant = Color(0xFFF1F5F9)` - 稍深一点的灰色
- 设置页面使用 `SurfaceCard` 组件展示内容
- 卡片有 1dp 的 `outline` 边框，但视觉上仍然缺乏足够的对比度

## Assumptions (Temporary)

- 需要调整的是 `surface` 或 `surfaceVariant` 颜色值，使其与 `background` 有更明显的区分
- 可能也需要考虑 `surfaceContainer` 等相关表面色

## Decision (ADR-lite)

**Context**: 浅色模式下卡片（surface=#FFFFFF）与背景（background=#F8FAFC）区分度不足，用户希望卡片呈现类白色效果
**Decision**: 加深浅色模式 `background` 颜色（如 #F8FAFC → #E2E8F0 或类似值），保持 `surface`（卡片）为纯白 #FFFFFF，形成白卡片+深底的对比效果
**Consequences**: 卡片在浅色模式下呈现纯白效果，背景略深提供衬托；深色模式不受影响

## Requirements (Evolving)

- 浅色模式下卡片颜色应为类白色，与背景形成清晰但不刺眼的对比
- 保持深色模式不受影响
- 符合 Material Design 3 的色彩规范

## Acceptance Criteria (Evolving)

- [ ] 浅色模式下卡片与背景有明显视觉区分
- [ ] 卡片颜色仍为类白色（不是纯白或过深的灰色）
- [ ] 深色模式视觉效果不受影响
- [ ] 整体界面协调性保持良好

## Definition of Done

- Lint / typecheck 通过
- 视觉测试验证浅色模式效果

## Out of Scope (Explicit)

- 不修改深色模式配色
- 不改变卡片形状、边框、阴影等其他样式
- 不调整文字颜色或其他 UI 元素颜色

## Technical Notes

相关文件：
- `/home/cat/Project/TrackWrite/app/src/main/java/com/trackwrite/app/ui/TrackWriteTheme.kt` - 主题颜色定义
- `/home/cat/Project/TrackWrite/app/src/main/java/com/trackwrite/app/MainActivity.kt` - SurfaceCard 组件定义（第 1537-1553 行）
