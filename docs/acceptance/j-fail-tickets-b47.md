# J 域 13 条 FAIL 缺陷单（批次 47，2026-11-05）

> 数据源：`docs/acceptance/ledger.md`。逐条定位时的台账为批次 46 终态
> sha256 `19ea6b57c5bbe73281232e170fb491a1360b6c1c87e0c9205e15c3f075f33e7b`；
> 本轮收口时（2026-11-05 23:35）主会话已把台账改到
> `35e0320f11abf8ecc74a9ae5c90809e683bd1b9eefb26425f259a24532e91522`
> （总盘 PASS 895 / FAIL 25），**J 域 13 条 FAIL 全部仍在**（行 942/943/961/962/
> 965/966/968/974/975/982/989/992/993 复读确认）。本文件只登记根因、复跑命令、
> 观测 exit 与证据；**不把 FAIL 改判成 PASS**，也不替主会话改台账。除 J-027 的
> 文档契约已在树内修复（待改判）外，其余 12 条维持 FAIL：缺生产签名、缺干净树
> 重建、版本源属发布裁定、AAB 陈旧。

## 0. 汇总

| ID | 台账行 | 根因类别 | 缺陷单 | 处置 | 本轮复跑 exit |
|---|---:|---|---|---|---:|
| J-001 | 942 | 共享工作树脏 | FIX-T13 | 保留 FAIL | 1 |
| J-002 | 943 | 产物落后 HEAD 58 提交 | FIX-T13 | 保留 FAIL | 1 |
| J-020 | 961 | 无生产签名环境/钥匙 | FIX-T10 | 保留 FAIL | 1 |
| J-021 | 962 | 无已登记发布指纹可对照 | FIX-T10 | 保留 FAIL | 1 |
| J-024 | 965 | APK 与 AAB 签名身份不同 | FIX-T10 | 保留 FAIL | 1 |
| J-025 | 966 | AAB 证书 2026-10-30 已过期 | FIX-T10 | 保留 FAIL | 1 |
| J-027 | 968 | 文档契约与构建代码不一致 | FIX-T10（子项） | **树内已修复，待改判** | 1（签名环境仍缺） |
| J-033 | 974 | AAB 陈旧（4 ABI ≠ abiFilters 2） | FIX-T12 | 保留 FAIL | 1 |
| J-034 | 975 | AAB 陈旧（缺 `libquickjs.so`） | FIX-T12 | 保留 FAIL | 1 |
| J-041 | 982 | AAB 陈旧（含 32 位 `.so`） | FIX-T12 | 保留 FAIL | 1 |
| J-048 | 989 | 四份产物 `versionCode` 全为 2 | FIX-T11 | 保留 FAIL | 0（判词看值） |
| J-051 | 992 | `versionCode` 仍是手写两行 | FIX-T11 | 保留 FAIL | 0（判词看值） |
| J-052 | 993 | 历史 AAB 与现 APK vc 相同 | FIX-T11 | 保留 FAIL | 0（判词看值） |

复跑证据目录：`docs/research/evidence/2026-11-06-batch-47/j-domain/`。

## 1. 逐条

### J-001 干净树前提（台账 942）

- 复跑：`python -I tools/ci/j_release_verify.py identity --root . --apk app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk --aab app/build/outputs/bundle/strictOfflineRelease/app-strictOffline-release.aab --mapping app/build/outputs/mapping/strictOfflineRelease/mapping.txt --gradle app/build.gradle.kts --metadata app/build/outputs/apk/strictOffline/release/output-metadata.json --require-clean --require-head`
- 观测：exit **1**；`head=39d8c75a…`、`dirty_paths=3883`（证据 `j-identity-b47.log`）。
- 根因：验收在共享工作树上跑，批次 46/47 的源码、工具、文档改动未提交；`-uall`
  连未跟踪的 `tools/ci/j_release_verify.py` 也计入。只要不提交，`--require-clean`
  不可能为 0。
- 处置：**保留 FAIL**。关闭条件（FIX-T13）：提交后干净树复跑 exit 0；且不得用
  `--no-verify`/改判绕过。关单人：主会话（唯一能做 git 写的角色）。

### J-002 产物 revision 等于 HEAD 且落后 0 提交（台账 943）

- 复跑：同上命令。
- 观测：exit **1**；APK `revision=faab17f58b01eab78d5c08a9f843b3af3df2fecf`，
  `commits_behind_head=58`；HEAD `39d8c75a…`（`git rev-list --count
  faab17f5..HEAD` = 58）。
- 根因：release APK 构建于 2026-11-03 11:10（`j-artifact-mtimes-b47.log`），之后
  HEAD 前进 58 个提交；产物未重建。
- 处置：**保留 FAIL**。关闭条件（FIX-T13）：干净树重出 release APK/AAB 后复跑
  exit 0。重出必须在串行 Gradle 门禁阶段执行。

### J-020 release APK/AAB 使用生产钥匙（台账 961）

- 复跑：`python -I tools/ci/j_release_verify.py signing --root . --apk app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk --aab app/build/outputs/bundle/strictOfflineRelease/app-strictOffline-release.aab --apksigner C:/Android/Sdk/build-tools/37.0.0/apksigner.bat`
- 观测：exit **1**；`env_missing` = `RELEASE_KEY_ALIAS/RELEASE_KEY_PASSWORD/
  RELEASE_STORE_FILE/RELEASE_STORE_PASSWORD` 4/4；`tracked_keystores=[]`。
  APK 证书 DN 为 `CN=WS8 Ephemeral Local Only, OU=NotForRelease, O=Local, C=CN`
  （digest `9dc07e9f…`），不是生产钥匙（`j-signing-b47.log`、`j-cert-details-b47.log`）。
- 根因：`app/build.gradle.kts:124-142` 只在四个 `RELEASE_*` 齐备时注入 release
  签名；当前环境全缺，因此 release 产物不可能带生产钥匙。
- 处置：**保留 FAIL**。关闭条件（FIX-T10）：由钥匙持有人配齐四变量、重建并按
  `apksigner verify --print-certs` 取指纹；本环境无此材料，**不得**用临时钥匙充数。

### J-021 签名指纹等于已登记发布指纹（台账 962）

- 复跑：同 J-020。
- 观测：exit **1**；当前 APK 指纹 `9dc07e9f…`，仓库中**没有**任何“已登记发布
  指纹”可供相等比较（`signing` 输出无 expected 字段；`tracked_keystores=[]`）。
- 根因：登记指纹属于发布方外部状态，仓库内无权威来源；在没有登记值的情况下
  任何“相等”判词都是编造。
- 处置：**保留 FAIL**。关闭条件（FIX-T10）：发布方给出登记指纹并把比较结果
  落成 `expected vs actual` 证据。

### J-024 AAB 与 APK 使用同一签名身份（台账 965）

- 复跑：同 J-020。
- 观测：exit **1**；`apk_signer.sha256=9dc07e9f…`，
  `aab_signer.sha256=a9e76cad…`，工具报
  `APK/AAB signer identity differs: apk=9dc07e9f5ce9a409 aab=a9e76cad95af11d0`。
  AAB owner 为 `CN=ZCode Release Smoke, OU=local…`（`j-cert-details-b47.log`）。
- 根因：两份产物由不同本地钥匙签出（APK 临时钥匙、AAB 冒烟钥匙），且都不是生产钥匙。
- 处置：**保留 FAIL**。关闭条件（FIX-T10）：同一生产钥匙重签/重建 APK 与 AAB 后
  `signing` 的 identity 检查不再报错。

### J-025 AAB 证书有效期覆盖发布窗口（台账 966）

- 复跑：同 J-020。
- 观测：exit **1**；AAB 证书
  `not_before=2026-09-30T00:12:34`、`not_after=2026-10-30T00:12:34`，
  工具报 `AAB certificate expired at 2026-10-30T00:12:34`（今天 2026-11-05）。
- 根因：AAB 是 2026-09-30 的旧构建，证书只到 2026-10-30；旧工具用
  `encoding="utf-8", errors="replace"` 读中文 keytool 输出，owner/有效期被读成
  乱码/null，所以“已过期”此前看不见。
- 处置：**保留 FAIL**。工具侧已修（见 §2）；关闭条件（FIX-T10）：生产证书重建后
  复跑 exit 0。

### J-027 文档契约一致：keystore.properties 与环境变量（台账 968）

- 复跑：同 J-020。
- 修复前（批次 46 归档 `j-signing.log`）：`doc_mentions_keystore_properties=true`、
  `doc_mentions_env_contract=false`；而 `app/build.gradle.kts:124-142` 读的是
  四个 `RELEASE_*` 环境变量 → 文档与代码相反。
- 树内修复：`RELEASE.md` 的签名段改为明确写出四个环境变量，并写明构建**不读**
  `keystore.properties`；同段写明 `validateReleaseSigning` 与 `signing` 的
  fail-closed 判据。
- 修复后复跑：exit **1**（签名环境仍缺，属 J-019/J-020 的条件缺失）；但
  `doc_mentions_env_contract` 已变为 **true**（`j-signing-b47.log`）。
- 处置：**树内已修复，判定仍保留 FAIL**（台账未改；改动会改变 ledger sha，需
  主会话改判并重绑 920 份行级 JSON）。改判建议：以 `j-signing-b47.log` 的
  `doc_mentions_env_contract=true` + `RELEASE.md` 当前字节 + `app/build.gradle.kts:124-142`
  三项为证据，判 PASS；不得把签名环境缺失混进本行。

### J-033 AAB 的 ABI 集合等于 abiFilters（台账 974）

- 复跑：`python -I tools/ci/j_release_verify.py aab16k --aab app/build/outputs/bundle/strictOfflineRelease/app-strictOffline-release.aab`
- 观测：exit **1**；`abi_set=[arm64-v8a, armeabi-v7a, x86, x86_64]` ≠
  `abiFilters=[arm64-v8a, x86_64]`（`j-aab16k-b47.log`）。
- 根因：AAB mtime **2026-09-30 02:34**（sha `d0734105…`，与批次 46 相同，字节未变），
  早于 `abiFilters` 引入提交 `29b46f21`（2026-10-25 22:30）；AAB 是陈旧产物，
  不是当前配置的结果（`j-aab-freshness-b47.log`、`j-artifact-mtimes-b47.log`）。
- 处置：**保留 FAIL**。关闭条件（FIX-T12）：干净树重出 AAB 后 `aab16k` exit 0。

### J-034 AAB 含 libquickjs.so（台账 975）

- 复跑：同 J-033。
- 观测：exit **1**；`native_libs` 六项中无 `libquickjs.so`，工具报
  `required native library missing: libquickjs.so`。
- 根因：quickjs AAR 依赖引入于 `1a6bf486`（2026-10-26 05:43），
  `core/data/build.gradle.kts:228-234`；AAB 构建于 2026-09-30，早于该依赖。
  预期（未重建验证）：新 AAB 会带 AAR 的 `jni/<abi>/libquickjs.so`。
- 处置：**保留 FAIL**。关闭条件（FIX-T12）：重出 AAB 后 `aab16k` exit 0。

### J-041 负向：AAB 不含 32 位 .so（台账 982）

- 复跑：同 J-033。
- 观测：exit **1**；`abi_set` 含 `armeabi-v7a`、`x86`，即存在 32 位 `.so`。
- 根因：同 J-033 的陈旧产物（2026-09-30 AAB 早于 16KB `abiFilters` 收紧）。
- 处置：**保留 FAIL**。关闭条件（FIX-T12）：重出 AAB 后 `aab16k` exit 0。

### J-048 versionCode 跨构建单调（台账 989）

- 复跑：`aapt2 dump badging` 三份 APK + AAB `versionCode`：
  - release APK（2026-11-03）：`versionCode='2'`
  - strictOffline debug（2026-11-04）：`versionCode='2'`
  - localFirst debug（2026-11-05）：`versionCode='2'`
  - AAB（2026-09-30）：`VERSION_CODE=2`（归档 `2026-11-05-batch-44/j-domain/
    j-bundletool-manifest.log`；AAB sha 与本轮相同，字节未变；bundletool 本轮未重跑）
- 观测：命令 exit 0，但**判词看值**：四份不同日期的构建全为 2，跨构建不单调。
- 根因：`app/build.gradle.kts:33` 手写 `versionCode = 2`，从 09-30 至今未递增。
- 处置：**保留 FAIL**。关闭条件（FIX-T11）：引入版本源并在两次构建上验证 N → N+1；
  属发布裁定，需主会话/用户批准。

### J-051 版本号生成或注入机制（台账 992）

- 复跑：全树 gradle 脚本 grep + 版本源文件枚举（`j-version-source-b47.log`）。
- 观测：唯一命中是 `app/build.gradle.kts:33-34` 两行字面量；没有任何
  `version.properties`/`version.txt`；gradle 脚本无 `git describe`/`rev-list`/
  `commitCount` 派生。`j-version-b47.log` exit 0 只证明 gradle 与 output-metadata
  一致，不证明“有机制”。
- 根因：版本号是手改常量（`app/build.gradle.kts:33-34`）。
- 处置：**保留 FAIL**。关闭条件（FIX-T11）：落地单一版本源并以测试/两次构建证明
  非手改；属发布裁定。

### J-052 回归：历史 AAB 与现 APK 的 vc 可比（台账 993）

- 复跑：见 J-048 的 aapt2 + 归档 bundletool manifest + `j-artifact-mtimes-b47.log`。
- 观测：历史 AAB（2026-09-30）vc=2，现 release APK（2026-11-03）vc=2。相隔五周的
  两次构建 vc 相同，无法构成“上一版 → 现版”的可比升级路径。
- 根因：同 J-048/J-051 的手写常量，从未递增。
- 处置：**保留 FAIL**。关闭条件（FIX-T11）：历史 AAB vc 与现 APK vc 形成可判定
  的新旧关系（历史 < 现），并在成对产物上复跑。

## 2. 本轮树内修复（不是洗绿）

1. `tools/ci/j_release_verify.py`：`signing` 由“永远 exit 0”改为 fail-closed。
   新增 `signing_errors()`：环境变量缺失、APK/AAB 指纹缺失或不同、AAB 证书
   不可读或已过期，任一成立即 exit 1。消灭的具体失败：批次 46 的
   `j-signing.log` 在 4/4 环境变量缺失 + 身份不同 + 证书过期时仍 exit 0。
2. 同一工具的 keytool 解析：新增 `parse_keytool_cert()` 支持中文
   `所有者/生效时间/失效时间` 标签；`run_tool()` 改为先 UTF-8、再 locale/GBK
   解码。消灭的具体失败：中文 keytool 输出被 `errors="replace"` 读成乱码，
   owner=null、到期时间完全读不到。
3. `tools/tests/test_j_release_verify.py`：新增 6 条用例（中文证书解析、
   fail-closed 三类红、完整证成绿、CLI 无环境变量 exit 1、GBK 解码）。
   先红后绿：修复前 `6 failed, 19 passed`；修复后 `26 passed`（exit 0）。
4. `RELEASE.md`：签名段改为与 `app/build.gradle.kts:124-142` 一致的四变量契约
   （见 J-027）。改动前 `doc_mentions_env_contract=false`，改动后 true。

## 2.1 本轮连带重绑（复核 JSON 与被引文件的字节同步）

- `RELEASE.md` 改动使 J-064（PASS，回滚文档）的 `file_sha256`/`evidence_sha256`
  失配：重绑到新字节 `8546b843…`；`## Rollback` 断言复跑仍命中 `RELEASE.md:51`；
  verdict 保持 PASS（`verdicts/rows/J-064.json` notes 批次47重绑段）。
- 批次 47 WS3 对 `docs/acceptance/runbook.md` 的改写（本轮实测 21:11 定稿，
  sha `4ea28905…`）使 J-009/J-045/J-054/J-072 四条 PASS 行的 `file_sha256`
  失配：四条全部重绑到 `4ea28905…`，四条判据 grep 复跑仍命中
  （J-003→runbook.md:286、arm64→164/165/173 等 6 处、versionCode→366、
  回滚/Rollback→291/370；复跑证据 `j-runbook-rebind-b47.log`），verdict 保持 PASS。
- 上述重绑只改 `file_sha256`/`evidence_sha256` 的字节值与 notes 标注；
  verdict、run_id、ledger_sha256 均未动。台账 sha 变更后的 920 份全量重绑
  归主会话（见 §3）。

## 3. 本轮未做（不得读作通过）

- 未跑 Gradle（`:app:assembleStrictOfflineRelease`/`bundleStrictOfflineRelease`），
  因此 J-002/J-033/J-034/J-041 的“重建后应变绿”只是预期，未验证。
- 未取得生产签名材料，J-020/J-021/J-024/J-025 无法关闭。
- 未改台账 verdict，也未重绑 920 份行级 JSON：J-027 的改判需主会话执行
  （改台账会使 `ledger_sha256` 变化，全部复核 JSON 的 `ledger_sha256` 与
  `ledger_row` 值必须同步重绑）。本轮收口时主会话已把台账改到
  `35e0320f…`（23:35），全量重绑在收口时点仍待执行：实测
  `python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md`
  对全部 920 份 JSON 报 `REVIEW_LEDGER_SHA_MISMATCH`（declared=19ea6b57…,
  actual=35e0320f…），这是台账先行、复核未跟的正常中间态，不是本域新增缺陷。
- 未跑设备（J 域本轮无设备项需要重跑；J-035/J-037–J-043 仍是 BLOCKED）。
- `tools/ci/j_release_verify.py` 与 `tools/tests/test_j_release_verify.py` 在
  HEAD 中未被跟踪（`git status` 显示 `??`），证据的可复算性依赖工作树字节。

## 4. 证据哈希（sha256）

| 文件 | sha256 |
|---|---|
| `j-identity-b47.log` | `32d8072921e2538201329e8a827f2bf56dbde7d667971a304d1ba7450c390251` |
| `j-signing-b47.log` | `82d017a2b05696d2a898ac2efa0295904b6521c25441ea41ac5c4524e1bb7edc` |
| `j-cert-details-b47.log` | `895c7ca51a223997c30769b2ecae294e7ca24152482ee71f7415eadf0de3978a` |
| `j-aab16k-b47.log` | `6173293fc57f3d67742263871719e0a87f24490e9ffa8ef1506d47c59a1dda95` |
| `j-aab-freshness-b47.log` | `08910cd8de5005caaf6959b7dbeba9d168d6645ed1af7eb9271d3ba502536863` |
| `j-version-b47.log` | `96e5f38403939d04007158fdc8bfd9f5a496da4c587c16a8d7202f7667f616df` |
| `j-version-artifacts-b47.log` | `7223fe529551d22495c1ec040b0d4ca407dd3a57d310483002cf0957f2deb53d` |
| `j-version-source-b47.log` | `7f0fc03b8a984f048a39c7d2812555771f0be43fd5749b2cdfc29128fda8f646` |
| `j-artifact-mtimes-b47.log` | `39780e67bb66b1bfa9496754bf91052fcee60ce6a3387cc2fffe442f27cee22f` |
| `j-unit-b47.log` | `100c4358c0d54cfe6846de20034608ae93301bb55842a61d99a6f2ebf2ed8dc8` |
| `j-unit-b47.xml` | `d80896f0c6020b0888f430b115fb3a940173227cb4a3097a1def49ab8a54bdde` |
| `j-runbook-rebind-b47.log` | `8415f783bad9c1a4fd891f9041a7b013772e0332728f252957d16ce448dff31c` |
