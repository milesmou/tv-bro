# 公开签名配置

按项目维护者要求，本目录公开保存浏览器的发布签名私钥及密码，供本项目构建使用。

- `browser-release.jks`：RSA 4096 位发布签名密钥。
- `config.json`：签名文件路径、文件密码、密钥别名和密钥密码。
- 证书 SHA-256：`7a170cfddb900e2ba688d13449de53bb256e546b537c365c993c56086b8b3848`。

构建前将配置中的四项设置为同名环境变量。`KEYSTORE_PATH` 相对于仓库根目录，建议转换为绝对路径后传入 Gradle。然后运行：

```bash
./gradlew :app:assembleGenericGeckoExcludedRelease
```

该密钥已公开，任何人均可签署使用相同应用包名的 APK，因此签名不能用来证明 APK 来自本项目维护者。请从本仓库的 Releases 获取正式安装包。不要将此密钥或密码用于其他应用、账户或服务。

GitHub Actions 仍通过仓库 Secrets 读取签名配置。后续版本使用相同密钥，以支持覆盖安装。
