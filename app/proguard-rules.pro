# 正式版 R8 的保留规则。库自己带的规则会自动加进来，这里只补它们漏掉的。

# ML Kit 按清单里的 meta-data 用反射创建这些注册类（比如 VisionCommonRegistrar），代码里看不到调用。
# firebase-components 16.1.0 自带的规则只保留了类名；R8 完整模式下不写构造函数就会被删掉，识别时报
# NoSuchMethodException: VisionCommonRegistrar.<init>，文字一个也读不出来。
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}
