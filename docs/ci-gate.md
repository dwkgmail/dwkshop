# CI/CD 门禁

GitHub Actions 工作流 `.github/workflows/ci.yml` 会在 Pull Request、推送到
`main` 以及手动触发时运行以下检查：

- `Backend tests`：使用 Java 21 执行根目录 `mvn --settings .github/maven-settings.xml test`
- `Admin frontend build`：使用 Node.js 20 执行 `npm ci` 和 `npm run build`
- `Mobile frontend build`：使用 Node.js 20 执行 `npm ci` 和 `npm run build`

任一检查失败，工作流即不通过。相同分支有新提交时，旧的未完成运行会自动取消。

## 启用合并门禁

工作流首次成功运行后，在 GitHub 仓库的分支规则或 Ruleset 中保护 `main`，开启
“Require status checks to pass before merging”，并将以下检查设为必需：

- `Backend tests`
- `Admin frontend build`
- `Mobile frontend build`

建议同时开启“Require branches to be up to date before merging”，确保门禁针对最新的
`main` 执行。分支保护属于 GitHub 仓库设置，不能仅通过工作流文件自动生效。

## 本地执行同一套检查

```bash
mvn --settings .github/maven-settings.xml test
npm --prefix frontend-admin ci
npm --prefix frontend-admin run build
npm --prefix frontend-mobile ci
npm --prefix frontend-mobile run build
```

## Maven 依赖解析

CI 通过 `actions/setup-java` 启用 Maven 缓存，缓存路径为 `~/.m2/repository`，缓存 key 由仓库内所有 `pom.xml` 计算。后端测试显式使用 `.github/maven-settings.xml`，避免不同 Runner 使用隐式全局 settings。

如果 GitHub Actions 需要走公司 Nexus/Artifactory，请不要把真实凭据提交到仓库。可参考 `docs/maven-settings.example.xml` 在 CI 中由 Secret 生成临时 settings，或将 Runner 预置为只能访问内网镜像；本仓库只保留无凭据模板。
