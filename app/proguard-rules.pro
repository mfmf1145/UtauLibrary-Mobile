# UtauMarket Mobile - R8 rules
# 解压相关库全部是纯 JVM 实现，R8 需要放宽反射/可选依赖的缺失类

-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-dontwarn com.github.junrar.**
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
-dontwarn java.awt.**
-dontwarn org.osgi.**

-keep class org.apache.commons.compress.** { *; }
-keep class com.github.junrar.** { *; }
-keep class org.tukaani.xz.** { *; }
-keep class org.apache.commons.io.** { *; }

-keepattributes *Annotation*, InnerClasses, Signature, Exceptions

# kotlinx-coroutines / OkHttp 的常规放宽
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Application / Service / Activity 由 Manifest 引用，R8 自动保留，这里显式声明更稳
-keep class com.utaumarket.mobile.UtauMarketApp { *; }
-keep class com.utaumarket.mobile.MainActivity { *; }
-keep class com.utaumarket.mobile.download.DownloadService { *; }
