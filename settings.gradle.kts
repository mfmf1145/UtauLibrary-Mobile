pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin") { name = "AliyunGradlePlugin" }
        maven("https://maven.aliyun.com/repository/google") { name = "AliyunGoogle" }
        maven("https://maven.aliyun.com/repository/public") { name = "AliyunPublic" }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        maven("https://maven.aliyun.com/repository/google") { name = "AliyunGoogle" }
        maven("https://maven.aliyun.com/repository/public") { name = "AliyunPublic" }
        maven("https://maven.aliyun.com/repository/gradle-plugin") { name = "AliyunGradlePlugin" }
        mavenCentral()
        maven("https://jitpack.io") { name = "JitPack" }
    }
}

rootProject.name = "UtauMarketMobile"
include(":app")
