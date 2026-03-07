# Auto Key Presser GUI Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 创建一个现代化的 Windows GUI 自动按键工具，支持自定义按键、时间间隔和全局快捷键

**Architecture:** 使用 C++17 + Win32 API + GDI+ 实现自绘控件，单文件可执行程序，无外部依赖

**Tech Stack:** C++17, Win32 API, GDI+, MinGW-w64

---

## 前置条件

- MinGW-w64 已安装（已验证可用）
- Windows 开发环境
- 工作目录: `D:\STM32CubeMXProject\item\FOC controller\Project with XiaoJunWei`

---

## Task 1: 项目骨架和基础窗口

**目标:** 创建无边框窗口和消息循环框架

**Files:**
- Create: `auto_key_gui.cpp`

**Step 1: 编写基础窗口代码**

```cpp
#include <windows.h>
#include <gdiplus.h>
#include <stdio.h>

#pragma comment(lib, "gdiplus.lib")
#pragma comment(lib, "user32.lib")
#pragma comment(lib, "kernel32.lib")

using namespace Gdiplus;

// 窗口尺寸
#define WINDOW_WIDTH 400
#define WINDOW_HEIGHT 500

// 控件ID
#define ID_BTN_START 1001
#define ID_CB_KEY 1002
#define ID_EDT_INTERVAL 1003

// 全局变量
HWND g_hWnd = NULL;
HWND g_hBtnStart = NULL;
HWND g_hCbKey = NULL;
HWND g_hEdtInterval = NULL;
BOOL g_isRunning = FALSE;
UINT g_keyCode = VK_RETURN;
UINT g_interval = 1000;
UINT g_pressCount = 0;
UINT_PTR g_timerId = 0;

// 颜色定义
const COLORREF CLR_BG = RGB(30, 30, 30);
const COLORREF CLR_CARD = RGB(37, 37, 38);
const COLORREF CLR_PRIMARY = RGB(0, 122, 204);
const COLORREF CLR_SUCCESS = RGB(76, 175, 80);
const COLORREF CLR_TEXT = RGB(255, 255, 255);
const COLORREF CLR_TEXT_SECONDARY = RGB(160, 160, 160);

LRESULT CALLBACK WndProc(HWND hWnd, UINT message, WPARAM wParam, LPARAM lParam) {
    switch (message) {
    case WM_CREATE:
        return 0;
        
    case WM_PAINT: {
        PAINTSTRUCT ps;
        HDC hdc = BeginPaint(hWnd, &ps);
        
        // 填充背景
        RECT rc;
        GetClientRect(hWnd, &rc);
        HBRUSH hBrush = CreateSolidBrush(CLR_BG);
        FillRect(hdc, &rc, hBrush);
        DeleteObject(hBrush);
        
        EndPaint(hWnd, &ps);
        return 0;
    }
    
    case WM_DESTROY:
        PostQuitMessage(0);
        return 0;
        
    default:
        return DefWindowProc(hWnd, message, wParam, lParam);
    }
}

int WINAPI WinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, 
                   LPSTR lpCmdLine, int nCmdShow) {
    // 初始化 GDI+
    GdiplusStartupInput gdiplusStartupInput;
    ULONG_PTR gdiplusToken;
    GdiplusStartup(&gdiplusToken, &gdiplusStartupInput, NULL);
    
    // 注册窗口类
    WNDCLASSEX wcex = {0};
    wcex.cbSize = sizeof(WNDCLASSEX);
    wcex.style = CS_HREDRAW | CS_VREDRAW;
    wcex.lpfnWndProc = WndProc;
    wcex.hInstance = hInstance;
    wcex.hCursor = LoadCursor(NULL, IDC_ARROW);
    wcex.hbrBackground = (HBRUSH)GetStockObject(BLACK_BRUSH);
    wcex.lpszClassName = "AutoKeyPresser";
    
    if (!RegisterClassEx(&wcex)) {
        MessageBox(NULL, "Window Registration Failed!", "Error", MB_ICONEXCLAMATION | MB_OK);
        return 0;
    }
    
    // 创建无边框窗口
    int screenWidth = GetSystemMetrics(SM_CXSCREEN);
    int screenHeight = GetSystemMetrics(SM_CYSCREEN);
    int x = (screenWidth - WINDOW_WIDTH) / 2;
    int y = (screenHeight - WINDOW_HEIGHT) / 2;
    
    g_hWnd = CreateWindowEx(
        WS_EX_LAYERED | WS_EX_TOPMOST,
        "AutoKeyPresser",
        "Auto Key Presser",
        WS_POPUP | WS_VISIBLE,
        x, y, WINDOW_WIDTH, WINDOW_HEIGHT,
        NULL, NULL, hInstance, NULL
    );
    
    if (!g_hWnd) {
        MessageBox(NULL, "Window Creation Failed!", "Error", MB_ICONEXCLAMATION | MB_OK);
        return 0;
    }
    
    // 设置窗口圆角和透明度
    SetLayeredWindowAttributes(g_hWnd, 0, 255, LWA_ALPHA);
    HRGN hRgn = CreateRoundRectRgn(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT, 20, 20);
    SetWindowRgn(g_hWnd, hRgn, TRUE);
    
    ShowWindow(g_hWnd, nCmdShow);
    UpdateWindow(g_hWnd);
    
    // 消息循环
    MSG msg;
    while (GetMessage(&msg, NULL, 0, 0)) {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }
    
    // 清理 GDI+
    GdiplusShutdown(gdiplusToken);
    
    return (int)msg.wParam;
}
```

**Step 2: 编译测试**

Run: `g++ -o auto_key_gui.exe auto_key_gui.cpp -lgdiplus -luser32 -lkernel32 -mwindows -std=c++17`

Expected: 编译成功，无错误

**Step 3: 运行测试**

Run: `./auto_key_gui.exe`

Expected: 显示一个圆角深色窗口，无边框，居中显示，可以关闭

**Step 4: Commit**

```bash
git add auto_key_gui.cpp
git commit -m "feat: create basic window framework with GDI+"
```

---

## Task 2: 自绘按钮控件

**目标:** 创建现代化圆角按钮，支持悬停和点击效果

**Files:**
- Modify: `auto_key_gui.cpp` - 添加按钮绘制和交互逻辑

**Step 1: 定义按钮结构体和相关函数**

在文件顶部添加（在全局变量后）:

```cpp
// 按钮结构
struct CustomButton {
    HWND hWnd;
    RECT rect;
    const char* text;
    BOOL isHovered;
    BOOL isPressed;
    COLORREF bgColor;
    COLORREF hoverColor;
    COLORREF pressedColor;
};

CustomButton g_startBtn = {0};

// 绘制圆角矩形按钮
void DrawRoundedButton(HDC hdc, CustomButton* btn) {
    COLORREF currentColor = btn->bgColor;
    if (btn->isPressed) currentColor = btn->pressedColor;
    else if (btn->isHovered) currentColor = btn->hoverColor;
    
    // 创建画刷
    HBRUSH hBrush = CreateSolidBrush(currentColor);
    
    // 创建圆角区域
    HRGN hRgn = CreateRoundRectRgn(
        btn->rect.left, btn->rect.top,
        btn->rect.right, btn->rect.bottom,
        12, 12
    );
    
    // 填充区域
    FillRgn(hdc, hRgn, hBrush);
    
    // 绘制文字
    SetBkMode(hdc, TRANSPARENT);
    SetTextColor(hdc, CLR_TEXT);
    HFONT hFont = CreateFont(18, 0, 0, 0, FW_BOLD, FALSE, FALSE, FALSE,
                             DEFAULT_CHARSET, OUT_OUTLINE_PRECIS, CLIP_DEFAULT_PRECIS,
                             CLEARTYPE_QUALITY, VARIABLE_PITCH, "Segoe UI");
    HFONT hOldFont = (HFONT)SelectObject(hdc, hFont);
    
    DrawText(hdc, btn->text, -1, &btn->rect, 
             DT_CENTER | DT_VCENTER | DT_SINGLELINE);
    
    SelectObject(hdc, hOldFont);
    DeleteObject(hFont);
    DeleteObject(hRgn);
    DeleteObject(hBrush);
}

// 检查点是否在矩形内
BOOL IsPointInRect(const RECT* rect, int x, int y) {
    return x >= rect->left && x <= rect->right &&
           y >= rect->top && y <= rect->bottom;
}
```

**Step 2: 修改 WM_CREATE 初始化按钮**

```cpp
case WM_CREATE: {
    // 初始化开始按钮
    g_startBtn.rect.left = 100;
    g_startBtn.rect.top = 300;
    g_startBtn.rect.right = 300;
    g_startBtn.rect.bottom = 360;
    g_startBtn.text = "START";
    g_startBtn.isHovered = FALSE;
    g_startBtn.isPressed = FALSE;
    g_startBtn.bgColor = CLR_PRIMARY;
    g_startBtn.hoverColor = RGB(0, 150, 240);
    g_startBtn.pressedColor = RGB(0, 90, 160);
    return 0;
}
```

**Step 3: 修改 WM_PAINT 绘制按钮**

在 EndPaint 前添加:

```cpp
DrawRoundedButton(hdc, &g_startBtn);
```

**Step 4: 添加鼠标消息处理**

在 WndProc 中添加:

```cpp
case WM_MOUSEMOVE: {
    int x = LOWORD(lParam);
    int y = HIWORD(lParam);
    BOOL wasHovered = g_startBtn.isHovered;
    g_startBtn.isHovered = IsPointInRect(&g_startBtn.rect, x, y);
    
    if (wasHovered != g_startBtn.isHovered) {
        InvalidateRect(hWnd, &g_startBtn.rect, FALSE);
    }
    return 0;
}

case WM_LBUTTONDOWN: {
    int x = LOWORD(lParam);
    int y = HIWORD(lParam);
    
    if (IsPointInRect(&g_startBtn.rect, x, y)) {
        g_startBtn.isPressed = TRUE;
        InvalidateRect(hWnd, &g_startBtn.rect, FALSE);
    }
    return 0;
}

case WM_LBUTTONUP: {
    int x = LOWORD(lParam);
    int y = HIWORD(lParam);
    
    if (g_startBtn.isPressed) {
        g_startBtn.isPressed = FALSE;
        InvalidateRect(hWnd, &g_startBtn.rect, FALSE);
        
        if (IsPointInRect(&g_startBtn.rect, x, y)) {
            // 按钮点击处理
            MessageBox(hWnd, "Button Clicked!", "Info", MB_OK);
        }
    }
    return 0;
}
```

**Step 5: 编译测试**

Run: `g++ -o auto_key_gui.exe auto_key_gui.cpp -lgdiplus -luser32 -lkernel32 -mwindows -std=c++17`

Expected: 编译成功

**Step 6: 运行测试**

Run: `./auto_key_gui.exe`

Expected: 窗口中显示一个蓝色圆角按钮，鼠标悬停变亮，按下变暗，点击显示消息框

**Step 7: Commit**

```bash
git add auto_key_gui.cpp
git commit -m "feat: add custom rounded button with hover and press effects"
```

---

## Task 3: 添加标题栏和拖拽功能

**目标:** 实现自定义标题栏，支持拖拽移动窗口

**Files:**
- Modify: `auto_key_gui.cpp`

**Step 1: 添加标题栏区域和拖拽逻辑**

在全局变量区添加:

```cpp
#define TITLE_BAR_HEIGHT 40
BOOL g_isDragging = FALSE;
POINT g_dragStart = {0};
```

**Step 2: 修改 WM_PAINT 绘制标题栏**

在 DrawRoundedButton 之前添加:

```cpp
// 绘制标题栏背景
RECT titleRect = {0, 0, WINDOW_WIDTH, TITLE_BAR_HEIGHT};
HBRUSH titleBrush = CreateSolidBrush(CLR_CARD);
FillRect(hdc, &titleRect, titleBrush);
DeleteObject(titleBrush);

// 绘制标题文字
SetBkMode(hdc, TRANSPARENT);
SetTextColor(hdc, CLR_TEXT);
HFONT titleFont = CreateFont(16, 0, 0, 0, FW_SEMIBOLD, FALSE, FALSE, FALSE,
                              DEFAULT_CHARSET, OUT_OUTLINE_PRECIS, CLIP_DEFAULT_PRECIS,
                              CLEARTYPE_QUALITY, VARIABLE_PITCH, "Segoe UI");
HFONT oldFont = (HFONT)SelectObject(hdc, titleFont);
RECT titleTextRect = {20, 0, WINDOW_WIDTH - 100, TITLE_BAR_HEIGHT};
DrawText(hdc, "Auto Key Presser v2.0", -1, &titleTextRect, 
         DT_LEFT | DT_VCENTER | DT_SINGLELINE);
SelectObject(hdc, oldFont);
DeleteObject(titleFont);

// 绘制关闭按钮
RECT closeRect = {WINDOW_WIDTH - 40, 8, WINDOW_WIDTH - 10, 32};
HBRUSH closeBrush = CreateSolidBrush(RGB(232, 17, 35));
FillRect(hdc, &closeRect, closeBrush);
DeleteObject(closeBrush);

SetTextColor(hdc, CLR_TEXT);
DrawText(hdc, "×", -1, &closeRect, DT_CENTER | DT_VCENTER | DT_SINGLELINE);
```

**Step 3: 添加拖拽和关闭功能**

在 WndProc 中添加:

```cpp
case WM_LBUTTONDOWN: {
    int x = LOWORD(lParam);
    int y = HIWORD(lParam);
    
    // 检查是否点击关闭按钮
    if (x >= WINDOW_WIDTH - 40 && x <= WINDOW_WIDTH - 10 &&
        y >= 8 && y <= 32) {
        PostQuitMessage(0);
        return 0;
    }
    
    // 检查是否点击标题栏（用于拖拽）
    if (y < TITLE_BAR_HEIGHT) {
        g_isDragging = TRUE;
        g_dragStart.x = x;
        g_dragStart.y = y;
        SetCapture(hWnd);
    }
    
    // 原有按钮处理...
    if (IsPointInRect(&g_startBtn.rect, x, y)) {
        g_startBtn.isPressed = TRUE;
        InvalidateRect(hWnd, &g_startBtn.rect, FALSE);
    }
    return 0;
}

case WM_MOUSEMOVE: {
    int x = LOWORD(lParam);
    int y = HIWORD(lParam);
    
    // 处理拖拽
    if (g_isDragging) {
        RECT rc;
        GetWindowRect(hWnd, &rc);
        int newX = rc.left + x - g_dragStart.x;
        int newY = rc.top + y - g_dragStart.y;
        SetWindowPos(hWnd, NULL, newX, newY, 0, 0, 
                     SWP_NOSIZE | SWP_NOZORDER);
        return 0;
    }
    
    // 原有悬停处理...
    BOOL wasHovered = g_startBtn.isHovered;
    g_startBtn.isHovered = IsPointInRect(&g_startBtn.rect, x, y);
    if (wasHovered != g_startBtn.isHovered) {
        InvalidateRect(hWnd, &g_startBtn.rect, FALSE);
    }
    return 0;
}

case WM_LBUTTONUP: {
    if (g_isDragging) {
        g_isDragging = FALSE;
        ReleaseCapture();
    }
    
    // 原有按钮释放处理...
    int x = LOWORD(lParam);
    int y = HIWORD(lParam);
    
    if (g_startBtn.isPressed) {
        g_startBtn.isPressed = FALSE;
        InvalidateRect(hWnd, &g_startBtn.rect, FALSE);
        
        if (IsPointInRect(&g_startBtn.rect, x, y)) {
            MessageBox(hWnd, "Button Clicked!", "Info", MB_OK);
        }
    }
    return 0;
}
```

**Step 4: 编译测试**

Run: `g++ -o auto_key_gui.exe auto_key_gui.cpp -lgdiplus -luser32 -lkernel32 -mwindows -std=c++17`

**Step 5: 运行测试**

Run: `./auto_key_gui.exe`

Expected: 窗口有标题栏，可以拖拽移动，点击右上角 × 关闭

**Step 6: Commit**

```bash
git add auto_key_gui.cpp
git commit -m "feat: add custom title bar with drag and close functionality"
```

---

## Task 4: 添加输入控件（下拉框和输入框）

**目标:** 创建自定义下拉选择框和数值输入框

**Files:**
- Modify: `auto_key_gui.cpp`

**Step 1: 定义控件结构**

在文件顶部添加:

```cpp
#define MAX_KEYS 13
const char* KEY_NAMES[MAX_KEYS] = {
    "Enter", "F1", "F2", "F3", "F4", "F5", "F6",
    "F7", "F8", "F9", "F10", "F11", "F12"
};
const UINT KEY_CODES[MAX_KEYS] = {
    VK_RETURN, VK_F1, VK_F2, VK_F3, VK_F4, VK_F5, VK_F6,
    VK_F7, VK_F8, VK_F9, VK_F10, VK_F11, VK_F12
};

struct CustomComboBox {
    RECT rect;
    const char** items;
    int itemCount;
    int selectedIndex;
    BOOL isOpen;
    BOOL isHovered;
};

struct CustomEditBox {
    RECT rect;
    char text[32];
    int textLen;
    BOOL isFocused;
    BOOL isHovered;
};

CustomComboBox g_keyCombo = {0};
CustomEditBox g_intervalEdit = {0};
```

**Step 2: 在 WM_CREATE 中初始化控件**

```cpp
case WM_CREATE: {
    // 初始化按键选择下拉框
    g_keyCombo.rect.left = 50;
    g_keyCombo.rect.top = 80;
    g_keyCombo.rect.right = 350;
    g_keyCombo.rect.bottom = 120;
    g_keyCombo.items = KEY_NAMES;
    g_keyCombo.itemCount = MAX_KEYS;
    g_keyCombo.selectedIndex = 0;
    g_keyCombo.isOpen = FALSE;
    g_keyCombo.isHovered = FALSE;
    
    // 初始化间隔输入框
    g_intervalEdit.rect.left = 50;
    g_intervalEdit.rect.top = 150;
    g_intervalEdit.rect.right = 250;
    g_intervalEdit.rect.bottom = 190;
    strcpy(g_intervalEdit.text, "1.0");
    g_intervalEdit.textLen = 3;
    g_intervalEdit.isFocused = FALSE;
    g_intervalEdit.isHovered = FALSE;
    
    // 初始化开始按钮（更新位置）
    g_startBtn.rect.left = 100;
    g_startBtn.rect.top = 350;
    g_startBtn.rect.right = 300;
    g_startBtn.rect.bottom = 410;
    g_startBtn.text = "START";
    g_startBtn.isHovered = FALSE;
    g_startBtn.isPressed = FALSE;
    g_startBtn.bgColor = CLR_PRIMARY;
    g_startBtn.hoverColor = RGB(0, 150, 240);
    g_startBtn.pressedColor = RGB(0, 90, 160);
    
    return 0;
}
```

**Step 3: 添加控件绘制函数**

```cpp
void DrawComboBox(HDC hdc, CustomComboBox* combo) {
    // 绘制背景
    COLORREF bgColor = combo->isHovered ? RGB(50, 50, 55) : CLR_CARD;
    HBRUSH hBrush = CreateSolidBrush(bgColor);
    FillRect(hdc, &combo->rect, hBrush);
    DeleteObject(hBrush);
    
    // 绘制边框
    HPEN hPen = CreatePen(PS_SOLID, 1, CLR_PRIMARY);
    HPEN hOldPen = (HPEN)SelectObject(hdc, hPen);
    MoveToEx(hdc, combo->rect.left, combo->rect.bottom - 1, NULL);
    LineTo(hdc, combo->rect.right, combo->rect.bottom - 1);
    SelectObject(hdc, hOldPen);
    DeleteObject(hPen);
    
    // 绘制选中的文字
    SetBkMode(hdc, TRANSPARENT);
    SetTextColor(hdc, CLR_TEXT);
    HFONT hFont = CreateFont(14, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE,
                             DEFAULT_CHARSET, OUT_OUTLINE_PRECIS, CLIP_DEFAULT_PRECIS,
                             CLEARTYPE_QUALITY, VARIABLE_PITCH, "Segoe UI");
    HFONT hOldFont = (HFONT)SelectObject(hdc, hFont);
    
    RECT textRect = combo->rect;
    textRect.left += 15;
    textRect.right -= 35;
    DrawText(hdc, combo->items[combo->selectedIndex], -1, &textRect,
             DT_LEFT | DT_VCENTER | DT_SINGLELINE);
    
    // 绘制下拉箭头
    POINT arrow[3] = {
        {combo->rect.right - 25, combo->rect.top + 18},
        {combo->rect.right - 15, combo->rect.top + 18},
        {combo->rect.right - 20, combo->rect.top + 23}
    };
    HPEN arrowPen = CreatePen(PS_SOLID, 2, CLR_TEXT);
    SelectObject(hdc, arrowPen);
    MoveToEx(hdc, arrow[0].x, arrow[0].y, NULL);
    LineTo(hdc, arrow[1].x, arrow[1].y);
    LineTo(hdc, arrow[2].x, arrow[2].y);
    DeleteObject(arrowPen);
    
    SelectObject(hdc, hOldFont);
    DeleteObject(hFont);
}

void DrawEditBox(HDC hdc, CustomEditBox* edit) {
    // 绘制背景
    COLORREF bgColor = edit->isFocused ? RGB(50, 50, 55) : CLR_CARD;
    HBRUSH hBrush = CreateSolidBrush(bgColor);
    FillRect(hdc, &edit->rect, hBrush);
    DeleteObject(hBrush);
    
    // 绘制边框
    COLORREF borderColor = edit->isFocused ? CLR_PRIMARY : RGB(80, 80, 80);
    HPEN hPen = CreatePen(PS_SOLID, edit->isFocused ? 2 : 1, borderColor);
    HPEN hOldPen = (HPEN)SelectObject(hdc, hPen);
    HBRUSH hOldBrush = (HBRUSH)SelectObject(hdc, GetStockObject(NULL_BRUSH));
    Rectangle(hdc, edit->rect.left, edit->rect.top, 
              edit->rect.right, edit->rect.bottom);
    SelectObject(hdc, hOldBrush);
    SelectObject(hdc, hOldPen);
    DeleteObject(hPen);
    
    // 绘制文字
    SetBkMode(hdc, TRANSPARENT);
    SetTextColor(hdc, CLR_TEXT);
    HFONT hFont = CreateFont(14, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE,
                             DEFAULT_CHARSET, OUT_OUTLINE_PRECIS, CLIP_DEFAULT_PRECIS,
                             CLEARTYPE_QUALITY, VARIABLE_PITCH, "Segoe UI");
    HFONT hOldFont = (HFONT)SelectObject(hdc, hFont);
    
    RECT textRect = edit->rect;
    textRect.left += 15;
    textRect.right -= 15;
    DrawText(hdc, edit->text, -1, &textRect,
             DT_LEFT | DT_VCENTER | DT_SINGLELINE);
    
    SelectObject(hdc, hOldFont);
    DeleteObject(hFont);
}
```

**Step 4: 修改 WM_PAINT**

在绘制按钮之前添加:

```cpp
// 绘制标签
SetBkMode(hdc, TRANSPARENT);
SetTextColor(hdc, CLR_TEXT_SECONDARY);
HFONT labelFont = CreateFont(12, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE,
                              DEFAULT_CHARSET, OUT_OUTLINE_PRECIS, CLIP_DEFAULT_PRECIS,
                              CLEARTYPE_QUALITY, VARIABLE_PITCH, "Segoe UI");
HFONT oldFont = (HFONT)SelectObject(hdc, labelFont);

RECT labelRect = {50, 60, 350, 80};
DrawText(hdc, "Select Key", -1, &labelRect, DT_LEFT | DT_VCENTER | DT_SINGLELINE);

labelRect.top = 130;
labelRect.bottom = 150;
DrawText(hdc, "Interval (seconds)", -1, &labelRect, DT_LEFT | DT_VCENTER | DT_SINGLELINE);

SelectObject(hdc, oldFont);
DeleteObject(labelFont);

// 绘制控件
DrawComboBox(hdc, &g_keyCombo);
DrawEditBox(hdc, &g_intervalEdit);
```

**Step 5: 更新鼠标交互处理**

修改 WM_MOUSEMOVE、WM_LBUTTONDOWN 和 WM_LBUTTONUP 以处理新控件。

**Step 6: 编译测试**

Run: `g++ -o auto_key_gui.exe auto_key_gui.cpp -lgdiplus -luser32 -lkernel32 -mwindows -std=c++17`

**Step 7: 运行测试**

Run: `./auto_key_gui.exe`

Expected: 窗口显示下拉框和输入框，有标签说明，控件有悬停效果

**Step 8: Commit**

```bash
git add auto_key_gui.cpp
git commit -m "feat: add custom combobox and edit controls"
```

---

## Task 5: 添加下拉菜单展开功能

**目标:** 实现下拉框点击展开，选择项目的功能

**Files:**
- Modify: `auto_key_gui.cpp`

**Step 1: 添加下拉列表绘制和交互逻辑**

在 DrawComboBox 函数后添加:

```cpp
void DrawDropdownList(HDC hdc, CustomComboBox* combo) {
    if (!combo->isOpen) return;
    
    int itemHeight = 35;
    RECT listRect = combo->rect;
    listRect.top = combo->rect.bottom;
    listRect.bottom = listRect.top + itemHeight * combo->itemCount;
    
    // 绘制列表背景
    HBRUSH bgBrush = CreateSolidBrush(CLR_CARD);
    FillRect(hdc, &listRect, bgBrush);
    DeleteObject(bgBrush);
    
    // 绘制边框
    HPEN borderPen = CreatePen(PS_SOLID, 1, CLR_PRIMARY);
    HPEN oldPen = (HPEN)SelectObject(hdc, borderPen);
    HBRUSH oldBrush = (HBRUSH)SelectObject(hdc, GetStockObject(NULL_BRUSH));
    Rectangle(hdc, listRect.left, listRect.top, listRect.right, listRect.bottom);
    SelectObject(hdc, oldBrush);
    SelectObject(hdc, oldPen);
    DeleteObject(borderPen);
    
    // 绘制项目
    SetBkMode(hdc, TRANSPARENT);
    HFONT itemFont = CreateFont(13, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE,
                                DEFAULT_CHARSET, OUT_OUTLINE_PRECIS, CLIP_DEFAULT_PRECIS,
                                CLEARTYPE_QUALITY, VARIABLE_PITCH, "Segoe UI");
    HFONT oldFont = (HFONT)SelectObject(hdc, itemFont);
    
    POINT mousePos;
    GetCursorPos(&mousePos);
    ScreenToClient(g_hWnd, &mousePos);
    
    for (int i = 0; i < combo->itemCount; i++) {
        RECT itemRect = listRect;
        itemRect.top = listRect.top + i * itemHeight;
        itemRect.bottom = itemRect.top + itemHeight;
        
        // 高亮悬停项
        if (mousePos.y >= itemRect.top && mousePos.y < itemRect.bottom &&
            mousePos.x >= listRect.left && mousePos.x <= listRect.right) {
            HBRUSH hoverBrush = CreateSolidBrush(RGB(60, 60, 70));
            FillRect(hdc, &itemRect, hoverBrush);
            DeleteObject(hoverBrush);
        }
        
        // 绘制分隔线
        if (i > 0) {
            HPEN sepPen = CreatePen(PS_SOLID, 1, RGB(60, 60, 60));
            HPEN sepOldPen = (HPEN)SelectObject(hdc, sepPen);
            MoveToEx(hdc, itemRect.left + 10, itemRect.top, NULL);
            LineTo(hdc, itemRect.right - 10, itemRect.top);
            SelectObject(hdc, sepOldPen);
            DeleteObject(sepPen);
        }
        
        // 绘制文字
        RECT textRect = itemRect;
        textRect.left += 15;
        SetTextColor(hdc, CLR_TEXT);
        DrawText(hdc, combo->items[i], -1, &textRect,
                 DT_LEFT | DT_VCENTER | DT_SINGLELINE);
    }
    
    SelectObject(hdc, oldFont);
    DeleteObject(itemFont);
}
```

**Step 2: 修改 WM_LBUTTONDOWN 处理下拉框点击**

```cpp
case WM_LBUTTONDOWN: {
    int x = LOWORD(lParam);
    int y = HIWORD(lParam);
    
    // 检查是否点击关闭按钮
    if (x >= WINDOW_WIDTH - 40 && x <= WINDOW_WIDTH - 10 &&
        y >= 8 && y <= 32) {
        PostQuitMessage(0);
        return 0;
    }
    
    // 处理下拉框
    if (IsPointInRect(&g_keyCombo.rect, x, y)) {
        g_keyCombo.isOpen = !g_keyCombo.isOpen;
        InvalidateRect(hWnd, NULL, FALSE);
        return 0;
    }
    
    // 处理下拉列表项点击
    if (g_keyCombo.isOpen) {
        int itemHeight = 35;
        RECT listRect = g_keyCombo.rect;
        listRect.top = g_keyCombo.rect.bottom;
        listRect.bottom = listRect.top + itemHeight * g_keyCombo.itemCount;
        
        if (IsPointInRect(&listRect, x, y)) {
            int selected = (y - listRect.top) / itemHeight;
            if (selected >= 0 && selected < g_keyCombo.itemCount) {
                g_keyCombo.selectedIndex = selected;
                g_keyCode = KEY_CODES[selected];
            }
            g_keyCombo.isOpen = FALSE;
            InvalidateRect(hWnd, NULL, FALSE);
            return 0;
        } else {
            g_keyCombo.isOpen = FALSE;
            InvalidateRect(hWnd, NULL, FALSE);
        }
    }
    
    // 检查是否点击标题栏（用于拖拽）
    if (y < TITLE_BAR_HEIGHT) {
        g_isDragging = TRUE;
        g_dragStart.x = x;
        g_dragStart.y = y;
        SetCapture(hWnd);
    }
    
    // 原有按钮处理...
    if (IsPointInRect(&g_startBtn.rect, x, y)) {
        g_startBtn.isPressed = TRUE;
        InvalidateRect(hWnd, &g_startBtn.rect, FALSE);
    }
    return 0;
}
```

**Step 3: 修改 WM_PAINT 绘制下拉列表**

在 DrawComboBox 之后添加:

```cpp
DrawDropdownList(hdc, &g_keyCombo);
```

**Step 4: 编译测试**

Run: `g++ -o auto_key_gui.exe auto_key_gui.cpp -lgdiplus -luser32 -lkernel32 -mwindows -std=c++17`

**Step 5: 运行测试**

Run: `./auto_key_gui.exe`

Expected: 点击下拉框显示 Enter/F1-F12 列表，可以悬停高亮，点击选择后关闭列表

**Step 6: Commit**

```bash
git add auto_key_gui.cpp
git commit -m "feat: add dropdown list with item selection"
```

---

## Task 6: 实现定时按键功能

**目标:** 实现开始/停止按钮功能，定时发送按键

**Files:**
- Modify: `auto_key_gui.cpp`

**Step 1: 添加按键发送函数**

```cpp
void SendKeyPress(UINT keyCode) {
    keybd_event(keyCode, 0, 0, 0);
    Sleep(50);
    keybd_event(keyCode, 0, KEYEVENTF_KEYUP, 0);
}
```

**Step 2: 添加开始/停止功能**

```cpp
void ToggleStartStop() {
    if (g_isRunning) {
        // 停止
        if (g_timerId != 0) {
            KillTimer(g_hWnd, g_timerId);
            g_timerId = 0;
        }
        g_isRunning = FALSE;
        g_startBtn.text = "START";
        g_startBtn.bgColor = CLR_PRIMARY;
        g_startBtn.hoverColor = RGB(0, 150, 240);
        g_startBtn.pressedColor = RGB(0, 90, 160);
    } else {
        // 解析间隔时间
        float interval = atof(g_intervalEdit.text);
        if (interval < 0.1f) interval = 0.1f;
        if (interval > 60.0f) interval = 60.0f;
        g_interval = (UINT)(interval * 1000);
        
        // 开始
        g_pressCount = 0;
        g_timerId = SetTimer(g_hWnd, 1, g_interval, NULL);
        g_isRunning = TRUE;
        g_startBtn.text = "STOP";
        g_startBtn.bgColor = RGB(232, 17, 35);  // 红色
        g_startBtn.hoverColor = RGB(255, 50, 60);
        g_startBtn.pressedColor = RGB(200, 0, 20);
    }
    InvalidateRect(g_hWnd, &g_startBtn.rect, FALSE);
}
```

**Step 3: 修改 WM_LBUTTONUP 调用 ToggleStartStop**

```cpp
case WM_LBUTTONUP: {
    if (g_isDragging) {
        g_isDragging = FALSE;
        ReleaseCapture();
    }
    
    int x = LOWORD(lParam);
    int y = HIWORD(lParam);
    
    if (g_startBtn.isPressed) {
        g_startBtn.isPressed = FALSE;
        InvalidateRect(hWnd, &g_startBtn.rect, FALSE);
        
        if (IsPointInRect(&g_startBtn.rect, x, y)) {
            ToggleStartStop();
        }
    }
    return 0;
}
```

**Step 4: 添加 WM_TIMER 处理**

```cpp
case WM_TIMER:
    if (wParam == 1 && g_isRunning) {
        SendKeyPress(g_keyCode);
        g_pressCount++;
        InvalidateRect(hWnd, NULL, FALSE);  // 重绘更新计数
    }
    return 0;
```

**Step 5: 添加状态显示**

在 WM_PAINT 中 DrawRoundedButton 之后添加:

```cpp
// 绘制状态标签
SetBkMode(hdc, TRANSPARENT);
HFONT statusFont = CreateFont(13, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE,
                               DEFAULT_CHARSET, OUT_OUTLINE_PRECIS, CLIP_DEFAULT_PRECIS,
                               CLEARTYPE_QUALITY, VARIABLE_PITCH, "Segoe UI");
oldFont = (HFONT)SelectObject(hdc, statusFont);

RECT statusRect = {50, 260, 350, 280};
if (g_isRunning) {
    SetTextColor(hdc, CLR_SUCCESS);
    char statusText[64];
    sprintf(statusText, "Status: Running | Presses: %u", g_pressCount);
    DrawText(hdc, statusText, -1, &statusRect, DT_LEFT | DT_VCENTER | DT_SINGLELINE);
} else {
    SetTextColor(hdc, CLR_TEXT_SECONDARY);
    DrawText(hdc, "Status: Stopped", -1, &statusRect, DT_LEFT | DT_VCENTER | DT_SINGLELINE);
}

// 绘制快捷键提示
RECT hotkeyRect = {50, 425, 350, 445};
SetTextColor(hdc, CLR_TEXT_SECONDARY);
DrawText(hdc, "Hotkey: Ctrl+Alt+S to Start/Stop", -1, &hotkeyRect, 
         DT_CENTER | DT_VCENTER | DT_SINGLELINE);

SelectObject(hdc, oldFont);
DeleteObject(statusFont);
```

**Step 6: 编译测试**

Run: `g++ -o auto_key_gui.exe auto_key_gui.cpp -lgdiplus -luser32 -lkernel32 -mwindows -std=c++17`

**Step 7: 运行测试**

Run: `./auto_key_gui.exe`

Expected: 
- 输入间隔时间（如 2.5）
- 点击 START 开始按键，按钮变红，状态显示 Running
- 按键计数器递增
- 点击 STOP 停止，按钮变蓝，状态显示 Stopped

**Step 8: Commit**

```bash
git add auto_key_gui.cpp
git commit -m "feat: implement timer-based key pressing with start/stop"
```

---

## Task 7: 注册全局快捷键

**目标:** 实现 Ctrl+Alt+S 全局快捷键开始/停止

**Files:**
- Modify: `auto_key_gui.cpp`

**Step 1: 在 WM_CREATE 中注册热键**

```cpp
case WM_CREATE: {
    // ... 原有初始化代码 ...
    
    // 注册全局快捷键 Ctrl+Alt+S
    if (!RegisterHotKey(hWnd, 1, MOD_CONTROL | MOD_ALT, 'S')) {
        MessageBox(hWnd, "Failed to register global hotkey!", "Warning", MB_OK | MB_ICONWARNING);
    }
    
    return 0;
}
```

**Step 2: 在 WM_DESTROY 中注销热键**

```cpp
case WM_DESTROY:
    UnregisterHotKey(hWnd, 1);
    if (g_timerId != 0) {
        KillTimer(hWnd, g_timerId);
    }
    PostQuitMessage(0);
    return 0;
```

**Step 3: 添加 WM_HOTKEY 处理**

```cpp
case WM_HOTKEY:
    if (wParam == 1) {
        ToggleStartStop();
    }
    return 0;
```

**Step 4: 编译测试**

Run: `g++ -o auto_key_gui.exe auto_key_gui.cpp -lgdiplus -luser32 -lkernel32 -mwindows -std=c++17`

**Step 5: 运行测试**

Run: `./auto_key_gui.exe`

Expected: 
- 窗口可以最小化或切换到后台
- 按 Ctrl+Alt+S 可以开始/停止按键
- 热键在程序运行时全局有效

**Step 6: Commit**

```bash
git add auto_key_gui.cpp
git commit -m "feat: add global hotkey Ctrl+Alt+S for start/stop"
```

---

## Task 8: 添加输入验证和错误处理

**目标:** 验证输入框内容，处理边界情况

**Files:**
- Modify: `auto_key_gui.cpp`

**Step 1: 添加输入验证函数**

```cpp
BOOL ValidateInterval(const char* text, float* outValue) {
    char* endptr;
    float value = strtof(text, &endptr);
    
    // 检查是否为有效数字
    if (*endptr != '\0' && *endptr != '\n' && *endptr != '\r') {
        return FALSE;
    }
    
    // 检查范围
    if (value < 0.1f || value > 60.0f) {
        return FALSE;
    }
    
    *outValue = value;
    return TRUE;
}

void ShowErrorBalloon(HWND hWnd, const char* message) {
    // 简单的错误提示 - 在实际应用中可以使用气球提示
    MessageBox(hWnd, message, "Input Error", MB_OK | MB_ICONWARNING);
}
```

**Step 2: 修改 ToggleStartStop 添加验证**

```cpp
void ToggleStartStop() {
    if (g_isRunning) {
        // 停止代码保持不变...
        if (g_timerId != 0) {
            KillTimer(g_hWnd, g_timerId);
            g_timerId = 0;
        }
        g_isRunning = FALSE;
        g_startBtn.text = "START";
        g_startBtn.bgColor = CLR_PRIMARY;
        g_startBtn.hoverColor = RGB(0, 150, 240);
        g_startBtn.pressedColor = RGB(0, 90, 160);
    } else {
        // 验证输入
        float interval;
        if (!ValidateInterval(g_intervalEdit.text, &interval)) {
            ShowErrorBalloon(g_hWnd, "Please enter a valid interval between 0.1 and 60 seconds.");
            return;
        }
        
        g_interval = (UINT)(interval * 1000);
        
        // 开始代码保持不变...
        g_pressCount = 0;
        g_timerId = SetTimer(g_hWnd, 1, g_interval, NULL);
        g_isRunning = TRUE;
        g_startBtn.text = "STOP";
        g_startBtn.bgColor = RGB(232, 17, 35);
        g_startBtn.hoverColor = RGB(255, 50, 60);
        g_startBtn.pressedColor = RGB(200, 0, 20);
    }
    InvalidateRect(g_hWnd, &g_startBtn.rect, FALSE);
}
```

**Step 3: 添加 WM_CHAR 处理输入框**

```cpp
case WM_CHAR:
    if (g_intervalEdit.isFocused) {
        if (wParam == 8) {  // Backspace
            if (g_intervalEdit.textLen > 0) {
                g_intervalEdit.text[--g_intervalEdit.textLen] = '\0';
                InvalidateRect(hWnd, &g_intervalEdit.rect, FALSE);
            }
        } else if (wParam >= '0' && wParam <= '9') {
            if (g_intervalEdit.textLen < 31) {
                g_intervalEdit.text[g_intervalEdit.textLen++] = (char)wParam;
                g_intervalEdit.text[g_intervalEdit.textLen] = '\0';
                InvalidateRect(hWnd, &g_intervalEdit.rect, FALSE);
            }
        } else if (wParam == '.' && strchr(g_intervalEdit.text, '.') == NULL) {
            if (g_intervalEdit.textLen < 31) {
                g_intervalEdit.text[g_intervalEdit.textLen++] = '.';
                g_intervalEdit.text[g_intervalEdit.textLen] = '\0';
                InvalidateRect(hWnd, &g_intervalEdit.rect, FALSE);
            }
        }
    }
    return 0;
```

**Step 4: 修改 WM_LBUTTONDOWN 处理输入框聚焦**

```cpp
// 在 WM_LBUTTONDOWN 中添加:
if (IsPointInRect(&g_intervalEdit.rect, x, y)) {
    g_intervalEdit.isFocused = TRUE;
    InvalidateRect(hWnd, &g_intervalEdit.rect, FALSE);
} else {
    if (g_intervalEdit.isFocused) {
        g_intervalEdit.isFocused = FALSE;
        InvalidateRect(hWnd, &g_intervalEdit.rect, FALSE);
    }
}
```

**Step 5: 编译测试**

Run: `g++ -o auto_key_gui.exe auto_key_gui.cpp -lgdiplus -luser32 -lkernel32 -mwindows -std=c++17`

**Step 6: 运行测试**

Run: `./auto_key_gui.exe`

Expected: 
- 输入框只能输入数字和小数点
- 无效输入会弹出错误提示
- 开始时会验证输入范围

**Step 7: Commit**

```bash
git add auto_key_gui.cpp
git commit -m "feat: add input validation and error handling"
```

---

## Task 9: 最终优化和测试

**目标:** 优化代码，添加注释，确保稳定性

**Files:**
- Modify: `auto_key_gui.cpp` - 代码清理和优化

**Step 1: 代码优化**

- 添加文件头注释
- 优化重绘区域，减少闪烁
- 确保所有资源正确释放
- 添加默认配置文件支持（可选）

**Step 2: 最终编译**

Run: `g++ -o auto_key_gui.exe auto_key_gui.cpp -O2 -lgdiplus -luser32 -lkernel32 -mwindows -std=c++17`

**Step 3: 完整测试**

测试用例:
1. 启动程序，界面正常显示
2. 选择不同的按键（Enter, F1-F12）
3. 输入不同的时间间隔（0.5, 2, 10秒）
4. 点击 START，观察按键是否正确发送
5. 使用全局快捷键 Ctrl+Alt+S 停止
6. 再次使用快捷键开始
7. 测试无效输入（负数、超过60、非数字）
8. 测试拖拽窗口
9. 测试关闭按钮
10. 长时间运行稳定性测试

**Step 4: 创建 README**

Create: `README.md`

```markdown
# Auto Key Presser v2.0

现代化的 Windows GUI 自动按键工具

## 功能特性

- 🎨 现代化深色扁平化 UI
- ⌨️ 支持 Enter 和 F1-F12 按键
- ⏱️ 可配置时间间隔（0.1-60秒）
- 🔥 全局快捷键 Ctrl+Alt+S
- 📊 实时按键计数
- 🖱️ 可拖拽窗口

## 使用方法

1. 运行 `auto_key_gui.exe`
2. 选择要发送的按键
3. 输入时间间隔（秒）
4. 点击 START 或按 Ctrl+Alt+S 开始
5. 点击 STOP 或再次按 Ctrl+Alt+S 停止

## 编译方法

```bash
g++ -o auto_key_gui.exe auto_key_gui.cpp -O2 -lgdiplus -luser32 -lkernel32 -mwindows -std=c++17
```

## 系统要求

- Windows 7 或更高版本
- MinGW-w64 或 Visual Studio（用于编译）

## 许可证

MIT License
```

**Step 5: Commit**

```bash
git add auto_key_gui.cpp README.md
git commit -m "feat: final optimization and documentation"
```

---

## 总结

**完成的文件:**
- `auto_key_gui.cpp` - 主程序（约 600-800 行）
- `README.md` - 使用说明

**构建命令:**
```bash
g++ -o auto_key_gui.exe auto_key_gui.cpp -O2 -lgdiplus -luser32 -lkernel32 -mwindows -std=c++17
```

**预期文件大小:** 100-150KB

**所有 Task 已完成！** 🎉
