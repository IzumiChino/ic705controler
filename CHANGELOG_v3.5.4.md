# IC-705 Controller v3.5.4 更新日志

**发布日期**: 2026年4月8日  
**版本号**: v3.5.4

---

## 修复与改进

### 1. 卫星过境显示问题修复

**问题描述**: 用户从卫星跟踪界面返回主界面时，正在过境的卫星会消失（特别是在无网络状态下）

**根本原因**:
- NTP 时间缓存过期后，无网络时回退到系统时间
- UI 使用系统时间过滤，而过境计算使用 NTP 时间
- 时间基准不一致导致过境被错误过滤

**修复内容**:
- **NtpTimeManager.kt**: 无网络时仍使用过期缓存时间，避免时间基准突变
- **MainActivity.kt**: UI 过滤改用 NTP 校准时间，与过境计算保持一致
- **PassNotificationDataStore.kt**: 仰角过滤器设置现在持久化保存

---

### 2. NTP 同步优化

**改进内容**:
- 添加多个备用 NTP 服务器（阿里云、Google、Windows）
- 添加同步重试间隔限制（1分钟），避免频繁尝试
- 优化错误日志，记录每个服务器的失败原因

**文件变更**:
```kotlin
// 备用 NTP 服务器列表
private val NTP_SERVERS = listOf(
    "ntp1.aliyun.com",
    "ntp2.aliyun.com", 
    "time.google.com",
    "time.windows.com"
)
```

---

### 3. 地面站管理增强

#### 3.1 地面站列表功能
**新增文件**: `StationListDialog.kt`

**功能**:
- 主界面长按 GPS 信息栏快速切换地面站
- 显示所有已保存的地面站列表
- 支持删除地面站
- 当前默认地面站显示勾选标记

#### 3.2 两种保存模式
**文件**: `StationSettingsDialog.kt`

**新增选项**:
- **设为默认地面站**: 替换当前默认位置，立即用于卫星跟踪
- **添加到地面站库**: 保存到库中备用，方便后续切换

#### 3.3 修复名称重复崩溃
**文件**: `DatabaseHelper.kt`

**修复内容**:
- 数据库版本升级至 v11
- 移除 `name` 字段的 `UNIQUE` 约束
- 新增 `isStationNameExists()` 检查方法
- 保存前检查名称是否被其他地面站使用

---

### 4. 界面优化

#### 4.1 移除深色模式
**文件**: `Theme.kt`

**变更**:
- 移除 `isSystemInDarkTheme` 相关代码
- 删除 `DarkColorScheme` 和动态颜色适配
- 应用统一使用浅色主题

#### 4.2 卫星跟踪界面禁止熄屏
**文件**: `SatelliteTrackingActivity.kt`

**变更**:
- 添加 `FLAG_KEEP_SCREEN_ON` 保持屏幕常亮
- 在 `onResume()` 中启用
- 在 `onPause()` 中清除

---

## 文件变更列表

### 新增文件
```
app/src/main/java/com/bh6aap/ic705Cter/ui/components/StationListDialog.kt
```

### 修改文件
```
app/src/main/java/com/bh6aap/ic705Cter/ui/theme/Theme.kt
app/src/main/java/com/bh6aap/ic705Cter/SatelliteTrackingActivity.kt
app/src/main/java/com/bh6aap/ic705Cter/data/database/DatabaseHelper.kt
app/src/main/java/com/bh6aap/ic705Cter/ui/components/StationSettingsDialog.kt
app/src/main/java/com/bh6aap/ic705Cter/MainActivity.kt
app/src/main/java/com/bh6aap/ic705Cter/data/PassNotificationDataStore.kt
app/src/main/java/com/bh6aap/ic705Cter/data/time/NtpTimeManager.kt
```

---

## 使用指南

### 快速切换地面站
1. 在主界面长按 GPS 信息栏
2. 在弹出的列表中选择要使用的地面站
3. 点击删除按钮可移除不需要的地面站

### 保存新地面站
1. 点击 GPS 栏左侧的设置按钮
2. 填写地面站信息（名称、QTH定位符、经纬度等）
3. 选择保存方式：
   - 设为默认：立即用于卫星跟踪
   - 添加到库：保存备用，后续可切换
4. 点击保存

### 卫星跟踪
1. 进入卫星跟踪界面后屏幕将保持常亮
2. 支持 VFO 避让功能（检测到手动调频时自动暂停跟踪）
3. 支持自定义模式和默认模式切换

---

## 技术细节

### NTP 时间同步策略
```
缓存有效（5分钟内）
    ↓
使用缓存时间

缓存过期
    ↓
距离上次尝试 < 1分钟？
    ├─ 是 → 使用过期缓存（无日志）
    └─ 否 → 尝试同步
              ├─ 成功 → 更新缓存
              └─ 失败 → 使用过期缓存（打印日志）
```

### 过境时间计算
- 计算时使用 `NtpTimeManager.getCachedAccurateTime()`
- UI 过滤使用相同的时间基准
- 确保无网络时时间基准保持一致

---

## 测试建议

1. **无网络场景测试**
   - 关闭网络，进入卫星跟踪界面
   - 返回主界面，检查过境卫星是否正常显示

2. **地面站切换测试**
   - 保存多个地面站
   - 长按 GPS 栏切换，检查是否正确更新

3. **NTP 同步测试**
   - 在无网络环境下观察日志
   - 确认不会频繁打印同步失败日志

---

**Full Changelog**: https://github.com/zhangyulu8023/ic705controler/compare/v3.5.3...v3.5.4
