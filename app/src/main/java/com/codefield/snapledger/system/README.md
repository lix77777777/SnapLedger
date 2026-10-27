# system
和安卓系统打交道的部分。依赖 data 和 logic。

- `ScreenshotStore`：收到截图马上存一份到应用文件夹（files/screenshots，JPEG，最宽 1440 像素），读、删、清理没人用的副本。
- `TextScanner`：ML Kit 文字识别（韩文、中文两个模型，打包在 App 里，不联网）。
- `ScreenshotReader`：把上面两个和 logic 的规则串起来：识别 → 拼行 → 挑一份 → 解析成一笔账。
- 以后：定位、通知和定时任务（WorkManager）、分享图片。
