# Auto Key Presser GUI 设计文档

**日期:** 2025-02-22
**项目:** Auto Key Presser with Modern GUI

---

## 1. 概述

创建一个现代化的 Windows GUI 应用程序，实现自动按键功能，具备美观的界面和实用的快捷操作。

### 核心功能
- 可配置按键类型（Enter, F1-F12）
- 可配置时间间隔（0.1-60秒）
- 全局快捷键（Ctrl+Alt+S）开始/停止
- 现代化扁平化 UI 设计
- 实时状态显示

---

## 2. 界面设计

### 2.1 布局结构

```
┌─────────────────────────────────────┐
│       Auto Key Presser v2.0         │
├─────────────────────────────────────┤
│                                     │
│  [Key Selection Dropdown]           │
│     Enter / F1 / F2 / ... / F12    │
│                                     │
│  [Interval Input]  [ 5.0 ] seconds  │
│                                     │
│  ┌─────────────────────────────┐    │
│  │                             │    │
│  │    [START / STOP]           │    │
│  │                             │    │
│  └─────────────────────────────┘    │
│                                     │
│  Status: [● Running] / [○ Stopped]  │
│  Presses: 42                        │
│                                     │
│  Hotkey: Ctrl+Alt+S                 │
│                                     │
└─────────────────────────────────────┘
```

### 2.2 视觉风格

**主题:** 深色现代扁平化

**颜色方案:**
- 背景: #1E1E1E (深灰)
- 卡片背景: #252526
- 主色调: #007ACC (蓝色)
- 成功色: #4CAF50 (绿色)
- 文字: #FFFFFF (白色)
- 次要文字: #A0A0A0

**控件样式:**
- 圆角按钮 (8px radius)
- 悬停效果 (颜色变亮)
- 下拉菜单自定义样式
- 输入框发光边框效果

---

## 3. 技术架构

### 3.1 技术栈
- **语言:** C++17
- **框架:** Win32 API + GDI+
- **编译器:** MinGW-w64
- **无外部依赖:** 仅使用 Windows 系统库

### 3.2 组件结构

```
MainWindow
├── TitleBar (自定义标题栏)
├── KeySelector (下拉选择框 - 自绘)
├── IntervalInput (数值输入框 - 自绘)
├── StartButton (主按钮 - 自绘)
├── StatusIndicator (状态指示器)
├── CounterDisplay (计数显示)
└── HotkeyLabel (快捷键提示)
```

### 3.3 核心模块

1. **UI Renderer** - GDI+ 绘制控件
2. **Input Handler** - 处理鼠标/键盘输入
3. **Key Presser** - 定时发送按键事件
4. **Hotkey Manager** - 注册全局快捷键
5. **Config Manager** - 保存/加载配置（可选）

---

## 4. 交互流程

### 4.1 启动流程
1. 注册窗口类
2. 创建无边框窗口
3. 初始化 GDI+
4. 注册全局快捷键 (Ctrl+Alt+S)
5. 加载默认配置
6. 显示窗口

### 4.2 操作流程
```
用户选择按键 → 更新配置
用户输入间隔 → 验证范围 (0.1-60)
用户点击开始 → 禁用输入控件
            → 启动定时器
            → 状态变为"运行中"(绿色)
            → 按钮变为"停止"(红色)
            
用户点击停止 → 停止定时器
            → 启用输入控件
            → 状态变为"已停止"(灰色)
            → 按钮变为"开始"(蓝色)
            
用户按快捷键 → 切换开始/停止状态
```

### 4.3 按键发送流程
```
定时器触发
  ↓
验证窗口焦点
  ↓
发送 keybd_event (按下)
  ↓
延迟 50ms
  ↓
发送 keybd_event (释放)
  ↓
更新计数器
  ↓
等待间隔时间
```

---

## 5. 错误处理

### 5.1 输入验证
- 间隔时间 < 0.1秒 → 自动设为 0.1秒
- 间隔时间 > 60秒 → 自动设为 60秒
- 无效输入 → 显示错误提示 (红色边框)

### 5.2 运行时错误
- 快捷键注册失败 → 显示警告但继续运行
- 定时器创建失败 → 显示错误对话框

---

## 6. 文件结构

```
auto_key_gui.cpp      - 主程序入口
├── ui/
│   ├── window.cpp    - 窗口管理
│   ├── controls.cpp  - 自定义控件
│   └── theme.cpp     - 主题配置
├── core/
│   ├── keypresser.cpp - 按键发送逻辑
│   └── hotkey.cpp    - 全局快捷键
└── resources/
    └── manifest.xml  - DPI 感知配置
```

---

## 7. 实现要点

### 7.1 自绘控件
- 处理 WM_PAINT 消息
- 使用 GDI+ 绘制圆角矩形
- 实现渐变背景效果
- 处理鼠标悬停/按下状态

### 7.2 全局快捷键
```cpp
RegisterHotKey(hwnd, ID_HOTKEY_START, MOD_CONTROL | MOD_ALT, 'S');
// 在 WM_HOTKEY 消息中处理
```

### 7.3 定时器实现
```cpp
SetTimer(hwnd, ID_TIMER_KEYPRESS, interval_ms, NULL);
// 在 WM_TIMER 消息中发送按键
```

### 7.4 无边框窗口
```cpp
CreateWindowEx(WS_EX_LAYERED | WS_EX_TOPMOST, ...);
// 移除 WS_CAPTION 和 WS_THICKFRAME 样式
```

---

## 8. 扩展性考虑

**未来可能添加的功能:**
- 配置文件保存/加载
- 多种按键组合支持
- 随机间隔功能
- 多键序列支持
- 系统托盘最小化

---

## 9. 构建命令

```bash
g++ -o auto_key_gui.exe auto_key_gui.cpp \
    -lgdiplus -luser32 -lkernel32 \
    -mwindows -std=c++17
```

---

**设计批准:** 待确认
**下一步:** 使用 writing-plans 技能创建详细实现计划
