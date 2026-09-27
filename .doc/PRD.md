# Jetpack Compose 离线正计时 App 完整开发计划

## 一、产品目标

开发一个纯离线的 Android 正计时 App。

用户可以创建一个计时器，填写：

- 计时主题或名称
- 开始日期
- 开始时间

App 根据“当前时间 - 开始时间”实时计算累计时长，并显示：

```text
戒糖坚持

12天 08小时 35分钟 42秒

开始时间
2026年9月15日 08:30
```

支持：

- 创建计时器
- 修改主题名称
- 修改开始时间
- 每秒刷新计时
- App 关闭后继续准确计算
- 导出 JSON 到剪贴板
- 完全离线运行

第一版只支持一个计时器。

---

## 二、技术方案

### 基础技术

- Kotlin
- Jetpack Compose
- Material 3
- Android SDK
- ViewModel
- Kotlin Coroutines
- Preferences DataStore
- Kotlin Serialization
- `java.time`

### 建议版本

- 最低支持 Android 8.0，API 26
- 编译版本使用当前 Android Studio 默认稳定版本
- 使用 Compose BOM 管理 Compose 依赖
- 使用 Kotlin Serialization 生成 JSON

### 权限

不需要申请网络权限，也不需要定位、存储、通讯录等权限。

剪贴板属于系统能力，不需要额外权限。

---

## 三、功能范围

### 1. 首次创建页面

用户首次打开 App 时进入创建页面。

页面包含：

- 标题：创建正计时
- 主题名称输入框
- 日期选择按钮
- 时间选择按钮
- 开始计时按钮

默认值：

- 名称为空
- 日期为当前日期
- 时间为当前时间

名称校验：

- 不能为空
- 去除首尾空格后再判断
- 长度限制为 1～50 个字符
- 允许中文、英文、数字和常见符号

开始时间校验：

- 不能晚于当前时间
- 可以设置为当前时间
- 选择未来时间时显示错误提示

保存成功后进入主页面。

---

### 2. 主计时页面

主页面显示：

- 主题名称
- 累计时间
- 开始时间
- 当前时间
- 编辑按钮
- 复制 JSON 按钮

建议布局：

```text
顶部标题栏
    正计时

主题区域
    戒糖坚持

时间区域
    12
    天

    08:35:42

信息区域
    开始时间
    2026年9月15日 08:30

    当前时间
    2026年9月27日 17:05:42

操作区域
    [编辑] [复制 JSON]
```

累计时长格式：

```text
12天 08小时 35分钟 42秒
```

如果时长较短：

```text
0天 00小时 03分钟 12秒
```

时间统一使用两位显示：

```text
08小时 05分钟 09秒
```

主题名称过长时：

- 最多显示两行
- 超出部分使用省略号
- 不允许撑破页面
- 编辑框中仍可完整查看和修改

---

### 3. 编辑页面

用户点击编辑后进入编辑页面。

可编辑内容：

- 主题名称
- 开始日期
- 开始时间

页面操作：

- 保存
- 取消

保存时再次执行校验：

- 名称不能为空
- 名称不能超过 50 个字符
- 开始时间不能晚于当前时间

保存成功后返回主页面，并立即重新计算累计时间。

第一版不保留修改记录。

---

### 4. JSON 导出

点击“复制 JSON”按钮后，生成格式化 JSON：

```json
{
  "name": "戒糖坚持",
  "startTime": "2026-09-15T08:30:00+08:00",
  "currentTime": "2026-09-27T17:05:42+08:00",
  "elapsedSeconds": 10658142,
  "elapsed": {
    "days": 12,
    "hours": 8,
    "minutes": 35,
    "seconds": 42
  }
}
```

字段说明：

| 字段 | 类型 | 说明 |
|---|---|---|
| `name` | String | 计时器名称 |
| `startTime` | String | ISO-8601 格式的开始时间 |
| `currentTime` | String | 导出时的当前时间 |
| `elapsedSeconds` | Long | 累计秒数 |
| `elapsed.days` | Int | 累计天数 |
| `elapsed.hours` | Int | 去除天数后的小时 |
| `elapsed.minutes` | Int | 去除小时后的分钟 |
| `elapsed.seconds` | Int | 去除分钟后的秒数 |

导出流程：

1. 读取本地名称和开始时间。
2. 获取当前时间。
3. 计算累计毫秒数和累计秒数。
4. 分解为天、时、分、秒。
5. 生成格式化 JSON。
6. 写入系统剪贴板。
7. 显示 Snackbar：

```text
JSON 已复制到剪贴板
```

如果没有计时器数据，则不显示导出按钮。

---

## 四、数据存储设计

使用 Preferences DataStore。

保存两个字段：

```text
timer_name: String
start_time_epoch_millis: Long
```

示例：

```text
timer_name = 戒糖坚持
start_time_epoch_millis = 1757896200000
```

不保存累计秒数。

累计时间永远通过以下公式计算：

```text
当前时间 - 开始时间
```

这样可以保证：

- App 关闭后再次打开仍然准确
- App 被系统回收后数据不丢失
- 不需要后台服务
- 不需要定时任务
- 不需要电池常驻权限

---

## 五、时间处理规则

使用：

```kotlin
java.time.Instant
java.time.ZoneId
java.time.ZonedDateTime
```

存储时使用 UTC 时间戳：

```kotlin
Instant.toEpochMilli()
```

显示时使用设备当前时区：

```kotlin
ZoneId.systemDefault()
```

### 计算逻辑

```kotlin
val elapsedMillis = now.toEpochMilli() - startTime.toEpochMilli()
val totalSeconds = elapsedMillis / 1000

val days = totalSeconds / 86_400
val hours = (totalSeconds % 86_400) / 3_600
val minutes = (totalSeconds % 3_600) / 60
val seconds = totalSeconds % 60
```

### 边界处理

如果开始时间晚于当前时间：

- 不保存
- 显示错误提示
- 累计时间不显示负数

如果系统时间被用户手动调回：

- 依据系统当前时间重新计算
- 如果当前时间早于开始时间，显示时间异常提示

跨以下情况必须正确：

- 跨午夜
- 跨天
- 跨月份
- 跨年份
- 闰年
- 不同设备时区

---

## 六、推荐项目结构

```text
app/
└── src/
    └── main/
        ├── AndroidManifest.xml
        ├── java/com/example/positivecounter/
        │   ├── MainActivity.kt
        │   │
        │   ├── data/
        │   │   ├── TimerPreferences.kt
        │   │   └── TimerRepository.kt
        │   │
        │   ├── model/
        │   │   ├── TimerState.kt
        │   │   ├── ElapsedTime.kt
        │   │   └── TimerExport.kt
        │   │
        │   ├── viewmodel/
        │   │   └── TimerViewModel.kt
        │   │
        │   ├── ui/
        │   │   ├── TimerApp.kt
        │   │   ├── CreateTimerScreen.kt
        │   │   ├── TimerScreen.kt
        │   │   ├── EditTimerScreen.kt
        │   │   └── components/
        │   │       ├── ElapsedTimeText.kt
        │   │       ├── TimerNameField.kt
        │   │       └── DateTimePicker.kt
        │   │
        │   └── util/
        │       ├── TimeFormatter.kt
        │       └── JsonExporter.kt
        │
        └── res/
            ├── values/
            │   ├── strings.xml
            │   ├── colors.xml
            │   └── themes.xml
            └── drawable/
```

---

## 七、数据模型

### TimerUiState

```kotlin
data class TimerUiState(
    val name: String = "",
    val startTime: Instant? = null,
    val now: Instant = Instant.now(),
    val elapsed: ElapsedTime = ElapsedTime.zero(),
    val isFirstLaunch: Boolean = true,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)
```

### ElapsedTime

```kotlin
data class ElapsedTime(
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val seconds: Long
) {
    companion object {
        fun zero() = ElapsedTime(0, 0, 0, 0)
    }
}
```

### TimerExport

```kotlin
@Serializable
data class TimerExport(
    val name: String,
    val startTime: String,
    val currentTime: String,
    val elapsedSeconds: Long,
    val elapsed: ElapsedExport
)

@Serializable
data class ElapsedExport(
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val seconds: Long
)
```

---

## 八、Repository 设计

`TimerRepository` 负责和 DataStore 交互。

建议接口：

```kotlin
interface TimerRepository {
    val timerData: Flow<TimerData?>
    suspend fun saveTimer(name: String, startTime: Instant)
    suspend fun clearTimer()
}
```

数据结构：

```kotlin
data class TimerData(
    val name: String,
    val startTime: Instant
)
```

职责：

- 读取本地计时器
- 保存计时器
- 修改计时器
- 清除计时器
- 处理 DataStore 异常

UI 不直接操作 DataStore。

---

## 九、ViewModel 设计

`TimerViewModel` 负责：

- 加载本地数据
- 判断是否首次打开
- 每秒刷新当前时间
- 计算累计时长
- 保存新计时器
- 修改计时器
- 校验输入
- 生成 JSON
- 处理错误提示

### 每秒刷新

使用协程：

```kotlin
viewModelScope.launch {
    while (true) {
        updateCurrentTime()
        delay(1000)
    }
}
```

也可以使用 `ticker` 或基于 `Flow` 的定时器。

页面进入后台时不需要继续计算，因为恢复后会根据当前时间重新计算。

### 主要方法

```kotlin
fun saveTimer(name: String, date: LocalDate, time: LocalTime)
fun updateTimer(name: String, date: LocalDate, time: LocalTime)
fun clearError()
fun exportJson(): String?
```

---

## 十、Compose 页面设计

### TimerApp

负责：

- 读取 ViewModel 状态
- 判断显示创建页还是主页面
- 管理页面导航状态
- 显示全局 Snackbar

第一版可以不引入 Navigation Compose，使用简单的页面状态：

```kotlin
sealed class AppScreen {
    data object Create : AppScreen()
    data object Timer : AppScreen()
    data object Edit : AppScreen()
}
```

如果后续页面增加，再引入 Navigation Compose。

### CreateTimerScreen

组件：

- `OutlinedTextField`
- 日期选择按钮
- 时间选择按钮
- `Button`
- 错误提示文字

### TimerScreen

组件：

- `TopAppBar`
- 主题名称
- `ElapsedTimeText`
- 开始时间信息卡片
- 当前时间信息卡片
- 编辑按钮
- 复制 JSON 按钮

### EditTimerScreen

复用创建页面的大部分输入组件，但初始值来自已有计时器。

### DateTimePicker

Android Material 3 中可使用：

- `DatePickerDialog`
- `TimePickerDialog`

日期时间组合为：

```kotlin
LocalDateTime.of(selectedDate, selectedTime)
    .atZone(ZoneId.systemDefault())
    .toInstant()
```

---

## 十一、主题与视觉设计

采用 Material 3。

界面重点突出两个内容：

1. 主题名称
2. 已计时时长

建议：

- 使用大字号展示累计天数
- 使用卡片展示日期和时间
- 使用圆角按钮
- 支持系统深色模式
- 使用单一主色，避免界面复杂
- 名称使用较大字号并支持换行

建议主页面视觉层级：

```text
主题名称：24sp～28sp
累计天数：48sp～64sp
小时分钟秒：22sp～28sp
辅助信息：14sp～16sp
```

---

## 十二、Gradle 依赖

需要加入：

- Compose BOM
- Compose Material 3
- Lifecycle ViewModel Compose
- DataStore Preferences
- Kotlin Serialization JSON
- Coroutines
- Activity Compose

依赖版本应根据现有项目的 Kotlin 和 AGP 版本统一，不要单独混用不兼容版本。

---

## 十三、测试计划

用户没有要求额外测试代码时，至少进行手动验证。

### 创建功能

- 首次打开显示创建页面
- 名称为空不能提交
- 名称超过 50 个字符不能提交
- 未来时间不能提交
- 当前时间可以提交
- 中文名称可以保存

### 显示功能

- 主页面显示正确名称
- 时间每秒更新
- 天、时、分、秒格式正确
- 名称很长时页面不溢出
- 屏幕旋转后数据仍然存在

### 持久化功能

- 关闭 App 后重新打开
- App 被系统回收后重新打开
- 名称和开始时间仍然存在

### 时间计算

- 开始时间为几秒前
- 开始时间为昨天
- 跨午夜
- 跨月份
- 跨年份
- 闰年
- 修改系统时间后的行为

### 编辑功能

- 能修改名称
- 能修改日期
- 能修改时间
- 取消编辑不会保存
- 保存后主页面立即更新

### JSON 功能

- JSON 包含 `name`
- JSON 包含正确的开始时间
- JSON 包含当前时间
- `elapsedSeconds` 正确
- `elapsed` 的天、时、分、秒正确
- JSON 可以被标准 JSON 工具解析
- 复制后能从剪贴板粘贴

---

## 十四、开发顺序

### 第一步：确认工程

- 检查现有 Gradle 配置
- 确认包名
- 确认最低 SDK
- 确认当前 Compose 版本
- 确认是否已有主题和 MainActivity

### 第二步：加入依赖

- DataStore
- Kotlin Serialization
- Coroutines
- ViewModel Compose

### 第三步：实现模型和时间工具

- `TimerData`
- `ElapsedTime`
- `TimerExport`
- 时间计算
- 时间格式化
- JSON 生成

### 第四步：实现本地存储

- DataStore keys
- Repository
- 读取和保存计时器

### 第五步：实现 ViewModel

- 加载状态
- 每秒刷新
- 创建
- 编辑
- 校验
- 导出

### 第六步：实现创建页面

- 名称输入
- 日期选择
- 时间选择
- 错误提示
- 保存按钮

### 第七步：实现主页面

- 名称显示
- 累计时长显示
- 日期时间显示
- 编辑按钮
- JSON 按钮

### 第八步：实现编辑页面

- 加载已有值
- 修改并保存
- 取消返回

### 第九步：处理剪贴板

使用：

```kotlin
val clipboard = LocalClipboardManager.current
clipboard.setText(AnnotatedString(json))
```

根据项目 Compose 版本，也可以使用 Android `ClipboardManager`。

### 第十步：构建和验证

- 编译 Debug APK
- 安装到模拟器或真机
- 完成测试清单
- 修复布局问题
- 生成最终 APK

---

## 十五、验收标准

满足以下条件即可视为第一版完成：

- App 可以离线运行
- 首次打开可以创建计时器
- 必须填写主题名称
- 可以选择开始日期和时间
- 未来时间不能保存
- 主页面显示正确名称
- 主页面显示天、时、分、秒
- 每秒自动更新
- App 重启后数据仍存在
- 可以编辑名称和开始时间
- 可以复制格式化 JSON
- JSON 包含名称、时间和累计时长
- 中文名称正常显示
- 长名称不会导致布局溢出
- Debug APK 可以成功构建和安装

---

## 十六、后续版本规划

第一版完成后可以增加：

- 多个计时器
- 删除计时器
- 暂停和继续
- 计时器分类
- 自定义颜色
- 自定义背景
- 桌面小组件
- 通知栏显示
- 导出 JSON 文件
- Android 系统分享
- JSON 导入
- 计时历史记录
- 计时目标和提醒
- 总小时数、总分钟数显示
- 完成目标后的纪念日模式

第一版建议保持单计时器和本地数据结构，后续再将数据模型扩展成计时器列表。