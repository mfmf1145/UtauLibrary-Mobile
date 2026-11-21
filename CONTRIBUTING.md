# 参与贡献

感谢你愿意改进 UtauLibrary Mobile。下面是最小必要流程。

## 提交问题

- 使用 Issue 模板：缺陷用 *Bug report*，功能建议用 *Feature request*。
- 缺陷请附：Android 版本 / 机型、应用版本、复现步骤、以及相关日志：
  - `Download/UtauMarketMobile-download.log`
  - `Download/UtauMarketMobile-write.txt`
  - `Download/UtauMarketMobile-extract.txt`

## 提交代码

1. Fork 本仓库并从 `main` 拉分支：`feat/xxx` 或 `fix/xxx`。
2. 保持现有代码风格：Kotlin + Compose，注释写「为什么」而不是「做了什么」。
3. 本地验证：

   ```bash
   ./gradlew :app:assembleDebug
   ```

4. 提交信息使用 Conventional Commits：`fix(download): 修复…`。
5. 开 Pull Request，说明改了什么、为什么、如何验证。

## 不要提交

- `keystore/`、`local.properties`、任何签名文件或密码。
- `build/`、`.gradle/`（已在 `.gitignore` 中）。

## 资源清单

`app/src/main/assets/resource_manifest.json` 是全部收录条目的数据源。新增条目请：

- `name` 用作者给出的正式名称，不要用文件名；
- `url` 填可直接下载的直链，落地页请填到 `detail_url`；
- 境外网盘（MediaFire / Dropbox / MEGA / Google Drive）请把 `network` 设为 `red` 并在 `remark` 注明需要代理。
