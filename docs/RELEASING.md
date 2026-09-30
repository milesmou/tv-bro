# GitHub 发布流程

正式版本使用独立包名 `com.milesmou.tvbrowser`，发布经过签名的 WebView 通用 APK。

## 一次性配置

在仓库的 Settings → Secrets and variables → Actions 中配置：

| Secret | 内容 |
| --- | --- |
| `KEYSTORE_BASE64` | 发布签名文件的 Base64 内容 |
| `KEYSTORE_PASSWORD` | 签名文件密码 |
| `KEY_ALIAS` | 签名密钥别名 |
| `KEY_PASSWORD` | 签名密钥密码 |

按项目维护者要求，本项目的签名文件与密码已公开保存在 [`signing/`](../signing/README.md) 目录中。后续覆盖更新必须使用同一密钥。其他密钥和凭据不得提交到 Git；本机环境缓存仍保存在被忽略的 `.gradle` 目录。

## 发布新版本

1. 修改 `app/build.gradle.kts` 的 `versionName`，并递增 `versionCode`。
2. 更新 `RELEASE_NOTES.md` 和 README 中的当前版本。
3. 提交并推送代码，然后创建与版本一致的标签：

```bash
git tag -a v1.0.0 -m "浏览器 1.0.0"
git push origin v1.0.0
```

标签推送会触发「发布浏览器」工作流。也可在 Actions 手动运行，输入与代码一致的版本号，并选择是否发布为预览版。

工作流会检查版本与包名、校验 Gradle Wrapper、运行单元测试、构建并校验正式签名 APK、生成 SHA-256，上传 APK 和校验和后再公开 Release。失败时可修复原因后重新运行；已公开的版本禁止覆盖，应使用新版本号发布。

APK 命名为 `browser-版本号-webview.apk`，下载地址在仓库的 Releases 页面。构建产物和 APK 元数据也会保存为 Actions Artifact，保留 30 天。
