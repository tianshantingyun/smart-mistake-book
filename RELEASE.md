# Release Guide

## Dry-run gate

Before tagging a release:

1. Run both debug flavors: assemble, unit tests, lint.
2. Run `:app:assembleRelease` / bundle dry run with R8 enabled.
3. Verify the merged release manifest has no debug-only component (activity,
   receiver, service or provider declared under app/src/debug) and no
   over-broad FileProvider path. `python tools/ci/check_release_manifest.py`
   automates both checks.
4. Run the complete Room migration matrix for every historical schema.
5. Run backup create/validate/restore round trip on a device.
6. Confirm no API key, Keystore secret, lease, or user content appears in
   backup archives or release logs.
7. Confirm the version name and code are bumped and the release branch only
   receives blocking fixes.

Release signing is intentionally not committed. The build does **not** read a
`keystore.properties` file: `app/build.gradle.kts` populates the release signing
config from four environment variables at evaluation time — `RELEASE_KEY_ALIAS`,
`RELEASE_KEY_PASSWORD`, `RELEASE_STORE_FILE` and `RELEASE_STORE_PASSWORD`. Keep
the keystore outside the repository. `validateReleaseSigning` fails the build
when any of the four is missing, and
`python -I tools/ci/j_release_verify.py signing --root . --apk <apk> --aab <aab> --apksigner <apksigner>`
fails closed (exit 1) until the APK and AAB share one signer identity and the AAB
certificate is inside its validity window.

## Known migration debt: v16 reconstructed schema

Schema `16.json` carries a reconstructed identity hash
(`00000000000000000000000000000000`) because the original v16 export was not
retained; its DDL was rebuilt from the v15/v17 exports and
`ExportedSchemaContractTest` scopes this exception to v16 only.

Risk boundary, verified 2026-08-30:

- Runtime migration is version-driven, so the placeholder never participates in
  the hash check of a live device; after migrating to the current version Room
  revalidates the final schema and rewrites the hash.
  `FullMigrationMatrixInstrumentedTest` proves the whole 1..current chain,
  including v16, migrates without destructive fallback.
- What cannot be proven: the reconstructed v16 DDL byte-matched whatever a real
  v16-era APK wrote. If any device still runs that vintage, a migration failure
  would surface as a Room `IllegalStateException` on that device only.
- Mitigation before shipping to such a device: run a backup export on it, do a
  fresh install, then restore. If no v16-era install exists (all development
  stayed on newer builds), the debt is archival only.

## Rollback

Each release must be installable over the previous version with no destructive
database migration. If a migration cannot be made non-destructive, do not ship
it.

## 1.0.0 发布候选

`app/build.gradle.kts` 的基础版本号为 `1.0.0`，两种变体分别显示 `1.0.0-localfirst` 与 `1.0.0-offline`。本轮验证状态见 [1.0.0 发布准备记录](docs/current/release-1.0.0.md)。

先在完整 Git 历史上生成 versionCode，算号器会拒绝浅克隆。

```powershell
New-Item -ItemType Directory -Path build -Force | Out-Null
python tools/ci/release_version_code.py --out build/release-version.json
if ($LASTEXITCODE -ne 0) { throw 'versionCode derivation failed' }
$releaseVersion = Get-Content build/release-version.json -Raw | ConvertFrom-Json
$releaseCode = "-Psmbk.versionCode=$($releaseVersion.version_code)"
```

由签名密钥持有人在当前进程设置前文四个 `RELEASE_*` 环境变量后执行。

```powershell
.\gradlew.bat :app:assembleLocalFirstRelease :app:assembleStrictOfflineRelease `
  :app:bundleLocalFirstRelease :app:bundleStrictOfflineRelease $releaseCode --console=plain
python tools/ci/check_release_manifest.py
```

Windows 受控环境入口为 `tools/run-gradle.ps1`，其环境要求见 `tools/README.md`。APK 位于 `app/build/outputs/apk/<flavor>/release/`，AAB 位于 `app/build/outputs/bundle/<flavor>Release/`。CI 使用临时测试签名执行同样的四个打包任务。

签名检查可对每种变体分别运行。

```powershell
python -I tools/ci/j_release_verify.py signing --root . --apk <apk> --aab <aab> --apksigner <apksigner>
python -I tools/ci/j_release_verify.py version --help
python -I tools/ci/j_release_verify.py apk16k --help
python -I tools/ci/j_release_verify.py aab16k --help
```

正式标记版本前，将当前提交、算号输出、APK/AAB SHA-256、签名证书与当前发布门验收结果绑定留证。`docs/current/release-gates.md` 定义既有验收要求，`docs/research/evidence/2026-11-12-residual-dispositions.md` 记录了尚未通过的 3D、3E、MVA、AUD-08 项。
