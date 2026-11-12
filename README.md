# Smart Mistake Book

智能错题本 Android 客户端，使用 Kotlin、Jetpack Compose 和 Room。当前发布候选版本为 **1.0.0**。

## 开发与构建

需要 JDK 21、Android SDK Platform 37 和 Build Tools 37.0.0。在本地 `local.properties` 设置 `sdk.dir`。

```powershell
.\gradlew.bat :app:assembleLocalFirstDebug :app:assembleStrictOfflineDebug
```

Windows 工作区环境与受控构建入口见 [tools/README.md](tools/README.md)，完整验证步骤见 [DEVELOPMENT.md](DEVELOPMENT.md)。

## 仓库导航

| 目录 | 内容 |
| --- | --- |
| `app/` | 应用入口、导航、构建变体与发布配置 |
| `core/` | 模型、领域逻辑、数据、数据库、导出与共享 UI |
| `feature/` | 拍题、复习、辅导、题库与个人设置 |
| `benchmark/` | 性能与 Baseline Profile |
| `gradle/` | Gradle wrapper、版本目录与依赖校验 |
| `tools/` | 开发工具、知识库流水线与 CI 校验 |
| `knowledge-production/` | 知识库生产数据与归档输入 |
| `docs/current/` | 当前产品、架构、数据与发布契约 |
| `docs/research/`、`docs/acceptance/` | 历史研究、验收记录与证据 |
| `design/` | 设计资料 |

## 版本与发布

- `localFirst` 提供网络与模型服务入口，包名后缀为 `.localfirst`。
- `strictOffline` 提供离线变体，包名后缀为 `.offline`。
- 发布步骤、签名变量与验证命令见 [RELEASE.md](RELEASE.md)。
- 1.0.0 的验证状态见 [发布准备记录](docs/current/release-1.0.0.md)。
- 当前契约从 [docs/current/README.md](docs/current/README.md) 进入。

本地 SDK 配置、签名文件、构建缓存和产物由 `.gitignore` 管理。早期根目录 `.agent_*` 输入已迁入 `knowledge-production/archive/pre-release-workfiles/`，原文件名保留供历史证据追溯。
