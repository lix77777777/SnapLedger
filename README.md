# 截图记账（SnapLedger）

付款以后把截图分享给应用，自动识别成一笔账；不同货币按付款那天的汇率换算；每个月生成一组故事卡片，回顾钱花在了哪里。

프로그램설계방법론（CSE2016）IC-PBL 3팀「코드밭」기말 프로젝트.

## 功能

- **截图记账**：从相册选图，或者在别的应用里「分享」过来；ML Kit 在手机上识别金额、日期、商家（韩文、中文），确认后保存。银行流水这类列表截图可以一次记好几笔，记过的会提醒。
- **多币种**：韩元、人民币、美元、日元、欧元，按付款那天的汇率换算成主货币（Frankfurter，欧洲央行参考汇率）；断网时用存下的汇率。
- **统计**：分类圆环、每天的柱状图、消费日历。
- **月度回顾**：8 张故事卡片（花得最多的类别、最大的一笔、常去的店、消费性格……），总结卡可以分享成图片；每月 1 号提醒。
- **消费地图**（OpenStreetMap）、**每月预算提醒**、**导出 CSV**。
- 中文、韩文、英文三种语言，深色模式。

## 构建

用 Android Studio 打开这个文件夹（JDK 用 Android Studio 自带的）。也可以用命令行：

```
./gradlew assembleDebug        # 调试版，设置里有「演示数据」
./gradlew assembleRelease      # 正式版：只打包 arm64，开 R8，16MB
./gradlew testDebugUnitTest    # 单元测试（106 个）
./gradlew connectedDebugAndroidTest   # 界面测试（7 个，要开着模拟器）
```

界面测试跑完后 Gradle 会把 App 卸载，设备上的账会一起删掉，所以请用模拟器跑，别用自己记账的手机。想保留 App 的话加上 `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`。

minSdk 26（Android 8.0），用到 Kotlin、Jetpack Compose、Room、WorkManager、ML Kit 文字识别、osmdroid、Coil、kotlinx.serialization。

## 文件夹

- `app/src/main/java/com/codefield/snapledger/`：`model` 数据类、`logic` 记账规则（纯 Kotlin）、`data` 数据库和设置、`remote` 汇率接口、`system` 识别/定位/通知/分享、`ui` 界面。除了 `ui`，每个文件夹里都有一个 README 说明它管什么。
- `app/src/test/`：规则的单元测试；`app/src/androidTest/`：界面测试。
- `docs/开发步骤.md`：步骤、定下的事、文件夹分工、和课程内容的对应。
- `docs/质量指标.md`：测试、识别准确度、速度、APK 大小。
