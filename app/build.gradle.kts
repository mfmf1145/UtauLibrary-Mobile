plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.utaumarket.mobile"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.utaumarket.mobile"
        minSdk = 21
        targetSdk = 34
        // 当前是测试版：显示版本 1.0.0。
        // versionCode 必须**单调递增**，否则系统拒绝覆盖安装
        // （曾经回退到 1 导致无法升级、只能卸载重装，会把用户设置全清掉）。
        versionCode = 10
        versionName = "1.0.0"
        multiDexEnabled = true
        // 只保留实际提供翻译的语言。之前写的是 listOf("zh", "en")，
        // 结果 values-ja 和 values-zh-rTW 在构建时被剔除 —— 选「日本語」只能回退到英文，
        // 界面语言和实际显示完全对不上。
        resourceConfigurations += listOf("zh", "en", "ja", "zh-rTW")
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            // 自包含发布：keystore 由 tools/make-keystore.sh 生成
            val ks = rootProject.file("keystore/utaumarket.jks")
            if (ks.exists()) {
                storeFile = ks
                storePassword = System.getenv("UTM_STORE_PASSWORD") ?: "utaumarket"
                keyAlias = System.getenv("UTM_KEY_ALIAS") ?: "utaumarket"
                keyPassword = System.getenv("UTM_KEY_PASSWORD") ?: "utaumarket"
            }
        }
    }

    buildTypes {
        release {
            // 诊断构建：暂时关闭 R8 与资源压缩，用来定位「一打开就闪退」是否由混淆/压缩引起。
            // 确认可用后再重新打开（proguard-rules.pro 里的规则已经准备好）。
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val ks = rootProject.file("keystore/utaumarket.jks")
            if (ks.exists()) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/*.kotlin_module",
                "**/module-info.class"
            )
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // 纯 JVM 单元测试（语言判定 / 名字回退 / 体积格式化这些不含 Android 依赖的逻辑）
    testImplementation("junit:junit:4.13.2")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-service:2.8.3")
    implementation("androidx.documentfile:documentfile:1.0.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // 自包含解压能力（纯 JVM，无需 native .so）
    implementation("org.apache.commons:commons-compress:1.26.1")
    implementation("com.github.junrar:junrar:7.5.5")
    implementation("org.tukaani:xz:1.9")
    implementation("commons-io:commons-io:2.16.1")
}
