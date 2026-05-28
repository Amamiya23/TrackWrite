# 设置页返回手势+记录页历史改为Bottom Sheet+匹配页多项UI改进

## Goal

改进TrackWrite的多个UI交互问题：设置页返回手势、记录页轨迹历史操作流程、匹配页轨迹选择和照片选择交互、写入按钮位置和确认文案。

## Requirements

### 1. 设置页返回手势返回主页面
- 当前：设置页通过TopAppBar的"Back"按钮返回，手势返回直接退出app
- 目标：手势返回时应回到主页面（Record/Match），而非退出app
- 实现：在SettingsScreen显示时添加`BackHandler`，调用`onCloseSettings`

### 2. 记录页轨迹历史改为Modal Bottom Sheet
- 当前：TrackHistoryPanel是可折叠区域，展开后显示轨迹列表和底部的Export/Rename/Delete按钮
- 目标：点击后弹出ModalBottomSheet，滚动查看列表，每行有重命名、导出、删除按钮
- 实现：
  - 移除TrackHistoryPanel的展开/折叠逻辑
  - 改为一个按钮显示"Track history (N)"，点击弹出ModalBottomSheet
  - Sheet内每行显示轨迹名称，右侧有重命名、导出、删除IconButton
  - 选中轨迹的行高亮显示

### 3. 匹配页轨迹选择改为Modal Bottom Sheet
- 当前：MatchTrackSourcePanel是可折叠区域，展开后显示轨迹列表和Import GPX按钮
- 目标：改为和"选择照片"一样的按钮，点击后用ModalBottomSheet选择轨迹，Sheet内有Import GPX按钮
- 实现：
  - 移除MatchTrackSourcePanel的展开/折叠逻辑
  - 改为按钮显示当前选中轨迹名称，点击弹出ModalBottomSheet
  - Sheet内显示轨迹列表，每行可点击选择
  - Sheet底部有"Import GPX"按钮

### 4. 匹配页移除说明卡片+照片选择改为Bottom Sheet
- 当前：MatchSettingsSummary显示相机时间偏移和最大运行时间差的说明卡片
- 目标：移除这些说明卡片
- 当前：PhotoBatchSummary是可折叠区域
- 目标：改为ModalBottomSheet
- 实现：
  - 删除MatchSettingsSummary组件及其调用
  - PhotoBatchSummary改为按钮显示照片统计，点击弹出ModalBottomSheet
  - Sheet内显示照片列表（匹配状态、缩略图等）

### 5. 写入按钮改为悬浮+确认文案修改
- 当前：写入按钮在页面底部的ReviewWritePanel中
- 目标：悬浮在右下角（FloatingActionButton风格）
- 当前确认文案："写入原始照片？这会直接修改源文件。导出副本更安全。"
- 目标确认文案："写入原始照片？这会直接修改源文件。"
- 实现：
  - 将写入按钮从ReviewWritePanel中提取出来
  - 使用Box包裹MatchScreen内容，在右下角放置FloatingActionButton
  - 修改strings.xml中的write_originals_message

## Acceptance Criteria

- [ ] 设置页手势返回时回到主页面，不退出app
- [ ] 记录页点击轨迹历史按钮弹出ModalBottomSheet，每行有重命名/导出/删除按钮
- [ ] 匹配页点击轨迹选择按钮弹出ModalBottomSheet，底部有Import GPX按钮
- [ ] 匹配页不再显示相机时间偏移和最大运行时间差的说明卡片
- [ ] 匹配页照片选择改为ModalBottomSheet
- [ ] 写入按钮悬浮在右下角
- [ ] 写入确认对话框只显示"写入原始照片？这会直接修改源文件。"

## Technical Notes

- 已有ModalBottomSheet使用示例：`WriteResultSheet`（line 1937-1987）
- 已有ExperimentalMaterial3Api注解
- TrackHistoryPanel: lines 835-896
- MatchTrackSourcePanel: lines 981-1047
- PhotoBatchSummary: lines 1050-1089
- MatchSettingsSummary: lines 1804-1821
- ReviewWritePanel: lines 1268-1289
- WriteOriginalsDialog: lines 1912-1933
- strings.xml中的相关字符串资源
