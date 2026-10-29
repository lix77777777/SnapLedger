# remote
网络接口：Frankfurter 汇率（免费、不需要密钥）。
只依赖 model。

- `RateSource`：取一段时间每个工作日的汇率（测试时换成假的）。
- `FrankfurterApi`：用 `HttpURLConnection` 请求 `api.frankfurter.dev/v1/开始..结束`，解析 JSON；汇率按原样的文字转成 BigDecimal。
