# system
和安卓系统打交道的部分。依赖 data、logic 和 remote。

- `ScreenshotStore`：收到截图马上存一份到应用文件夹（files/screenshots，JPEG，最宽 1440 像素），读、删、清理没人用的副本。
- `TextScanner`：ML Kit 文字识别（韩文、中文两个模型，打包在 App 里，不联网）。
- `ScreenshotReader`：把上面两个和 logic 的规则串起来：识别 → 拼行 → 挑一份 → 解析成一笔账。
- `RateUpdater`：按 logic 的 `RatePlanner` 算出缺哪几段汇率，用 remote 下载，存进数据库；断网时什么也不存。
- `ReviewReminder` / `ReviewReminderWorker`：每月 1 号上午 10 点提醒看上个月的回顾（WorkManager 一次性任务，到点发通知再排下个月）。Worker 由 WorkManager 用反射创建，所以从 `LedgerApplication` 取共用的对象。
- `ImageSharer`：把图片存成 PNG 放在缓存的 shared/ 里，通过 FileProvider 交给系统的分享面板。
- 以后：定位。
