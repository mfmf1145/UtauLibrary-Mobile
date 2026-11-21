# 构建与发布说明

本项目使用 **Kotlin + Jetpack Compose**，产出**自包含 APK**：运行时、Compose、解压库全部打进 APK，
用户安装即用，不需要额外安装任何库、运行时或插件。

---

## 1. 环境要求

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17 | AGP 8.5 要求；`gradle.properties` 中 `org.gradle.java.home` 指定 |
| Gradle | 8.7 | 或直接用项目 `gradlew`（需联网下载发行包） |
| Android SDK Platform | `platforms;android-34` | `compileSdk = 34` |
| Android SDK Build-Tools | `build-tools;34.0.0` | 需要 `aapt2`、`zipalign`、`core-lambda-stubs.jar` |

### 1.1 标准流程（x86_64 主机）

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
yes | sdkmanager --licenses
sdkmanager "platforms;android-34" "build-tools;34.0.0"
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug
```

### 1.2 arm64 / aarch64 主机（本项目实际使用的构建方式）

Google 的 SDK 包里的 `aapt2`/`zipalign` 是 x86_64 二进制，在 arm64 Linux 上不能直接跑。
可行的做法是**用发行版自带的原生 arm64 Android 工具替换它们**：

```bash
# Ubuntu/Debian 提供原生 arm64 的 aapt2 / zipalign / apksigner
sudo apt-get install -y android-sdk-build-tools zipalign apksigner openjdk-17-jdk-headless unzip zip

# 取 SDK 的 android.jar 与 build-tools（可用镜像）
curl -LO https://mirrors.cloud.tencent.com/AndroidSDK/platform-34-ext7_r03.zip
curl -LO https://mirrors.cloud.tencent.com/AndroidSDK/build-tools_r34-linux.zip
mkdir -p "$ANDROID_HOME/platforms" "$ANDROID_HOME/build-tools"
unzip -q platform-34-ext7_r03.zip -d /tmp/pf && mv /tmp/pf/android-34 "$ANDROID_HOME/platforms/"
unzip -q build-tools_r34-linux.zip -d /tmp/bt && mv /tmp/bt/android-14 "$ANDROID_HOME/build-tools/34.0.0"

# 用原生 arm64 工具覆盖 x86_64 二进制
cp -f /usr/bin/aapt2    "$ANDROID_HOME/build-tools/34.0.0/aapt2"
cp -f /usr/bin/zipalign "$ANDROID_HOME/build-tools/34.0.0/zipalign"

# Gradle 8.7（镜像）
curl -LO https://mirrors.cloud.tencent.com/gradle/gradle-8.7-bin.zip && unzip -q gradle-8.7-bin.zip

echo "sdk.dir=$ANDROID_HOME" > local.properties
gradle-8.7/bin/gradle :app:assembleDebug
```

`gradle.properties` 里的这一行让 AGP 使用我们自己指定的 aapt2：

```properties
android.aapt2FromMavenOverride=/opt/android-tools/aapt2
```

若你的环境已有可用的 `aapt2`，把该值改成对应路径，或删除这一行让 AGP 自行从 Maven 下载。

### 1.3 国内网络下的 Maven 镜像

`settings.gradle.kts` 已经按顺序配置了阿里云镜像（google / public / gradle-plugin），
再回退到 Maven Central 与 JitPack。若某个镜像失效，替换其中的 URL 即可。

---

## 2. 构建命令

```bash
# 调试包（未签名，可 adb install）
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# 发布包（R8 混淆 + 资源压缩 + 签名）
bash tools/make-keystore.sh          # 首次生成 keystore/utaumarket.jks
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

签名口令可用环境变量覆盖：

```bash
export UTM_STORE_PASSWORD='你的口令'
export UTM_KEY_ALIAS='utaumarket'
export UTM_KEY_PASSWORD='你的口令'
```

> 没有 `keystore/utaumarket.jks` 时，`assembleRelease` 仍会产出未签名 APK，需要自行用 `apksigner` 签名后才能安装。

---

## 3. 校验产物

```bash
# 查看 APK 基本信息
aapt2 dump badging app/build/outputs/apk/release/app-release.apk | head

# 校验签名
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk

# 安装到设备
adb install -r app/build/outputs/apk/release/app-release.apk
```

自包含校验要点：
- `lib/` 下**不应**有本项目的 native 库（解压全部是纯 JVM 实现）。
- APK 内应包含 `assets/resource_manifest.json` 与 `assets/openutau/theme.json`。
- 安装后无需任何额外运行时即可启动。

---

## 4. 重新生成图标

```bash
python3 tools/generate_icons.py
```

脚本从 `microsoft/fluentui-system-icons` 拉取 regular/24px 的 SVG，转换成
`app/src/main/res/drawable/ou_ic_*.xml`（Android VectorDrawable）。

---

## 5. 维护内置资源清单

编辑 `app/src/main/assets/resource_manifest.json`：

```jsonc
{
  "voicebanks": [
    {
      "id": "vb-example",
      "name": "声库原名",
      "name_translated": "译名",
      "engine": "utau",            // utau | diffsinger | other
      "family": "utau",            // utau | diffsinger | other（二级分类）
      "author": "作者 / 制作方",
      "language": "ja",            // ja | zh | en | ko | multi | other
      "format": "zip",
      "file_size": 12345678,
      "url": "https://...",        // 必须有官方直链
      "mirror_url": "",            // 可选镜像
      "network": "green",          // green | yellow | red
      "version": "v1.0",
      "release_date": "2025-01-01",
      "source_platform": "GitHub",
      "recording_type": "CVVC",    // CV | VCV | CVVC | CVC
      "range": "A3-E5",
      "scale_count": "3",
      "license": "允许商用 / 禁止修改",
      "install_rule": "voicebank_auto",
      "description": "简介",
      "remark": "备注",
      "detail_url": "https://..."
    }
  ],
  "plugins_manual": [
    {
      "id": "pl-example",
      "name": "插件名",
      "category": "lyrics",        // lyrics | pitch | marker | sound | utility
      "author": "作者",
      "format": "zip",
      "url": "https://github.com/.../releases/download/...",
      "network": "green",
      "version": "1.0",
      "mobile_warning": "安卓无法运行 .exe 等提示",
      "install_rule": "plugin_auto"
    }
  ]
}
```

缺失的字段可以留空，界面会显示占位符并保持布局一致。

---

## 6. 发布检查清单

- [ ] `./gradlew :app:assembleRelease` 成功
- [ ] `apksigner verify` 通过
- [ ] 在 Android 5.0 模拟器 / 真机上能启动（minSdk 21）
- [ ] 深色 / 浅色 / 跟随系统三种模式切换正常
- [ ] SAF 授权后重启应用仍然有效（持久化授权）
- [ ] 下载 zip / rar / 7z / uar 各一次，均能自动解压并安装
- [ ] 下载中返回桌面，通知栏进度持续更新，完成后提示
- [ ] 已安装页删除前有「将从设备移除，不可恢复」确认
- [ ] 重复下载已安装资源时弹出覆盖确认
