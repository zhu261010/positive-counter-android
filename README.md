# 正计时 Android App

当前版本：**0.1.1**

根据 `.doc/PRD.md` 实现的离线单计时器 Android 应用，使用 Kotlin、Jetpack Compose、Material 3 和 Preferences DataStore。

## 当前实现

- 首次打开创建计时器，支持名称、日期和时间校验
- 每秒刷新累计天数、时、分、秒
- 本地持久化，关闭应用后继续按当前时间计算
- 编辑已有计时器
- 复制格式化 JSON 到系统剪贴板
- 支持系统深色模式的 Material 主题基础

## 构建

使用 Android Studio 打开此目录，等待 Gradle 同步后运行 `app`。需要 JDK 17、Android SDK 35 和 Android Gradle Plugin 8.6.1。
