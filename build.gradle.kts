// UtauMarket Mobile - root build script
// SPDX-License-Identifier: MIT
buildscript {
    repositories {
        maven("https://maven.aliyun.com/repository/google") { name = "AliyunGoogle" }
        maven("https://maven.aliyun.com/repository/public") { name = "AliyunPublic" }
        maven("https://maven.aliyun.com/repository/gradle-plugin") { name = "AliyunGradlePlugin" }
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.5.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.24")
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
