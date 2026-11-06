# 批次 46 FAIL 缺陷/修复单（32 行）

> 来源：`docs/acceptance/ledger.md`（sha256 `19ea6b57…`）中 verdict=FAIL 的 32 行。
> 本批对 30 条旧 FAIL 逐条重判：28 条重跑/复核证据，F-055/F-057 因 2h soak
> 不可行保留 FAIL；其中 6 条真绿改 PASS、24 条保留 FAIL。另 7 条原 PASS 因复核矛盾
> 改 FAIL、G-089 由 UNVERIFIED 判 FAIL。合计 32 行。修复单不是通过声明。

| # | ID | 严重度 | 缺陷 | 出口判据 |
|---:|---|---|---|---|
| 1 | F-019 | P0 | 首帧基线写死上界 2150ms，实测 p50 含 2350ms | 按 gfxinfo 重算并改正基线/上界；或证明该轮 p50 不含 2350 |
| 2 | F-028 | P0 | 导出基线产品单题 487/597/820ms 与日志 7.97/7.77/8.12s 矛盾 | 重录导出耗时并统一单位/场景；删除无源数字 |
| 3 | F-029 | P1 | MistakeBatchPerf 516/553/538ms 无源；仅 <5000ms 断言有证据 | 补 MistakeBatchPerf 原始日志或删除该断言，只保留有证据阈值 |
| 4 | F-034 | P1 | 导出日志声称含 MistakeBatchPerf，instr.txt 未出现 | 重录 logcat/instr 并保留 MistakeBatchPerf 行 |
| 5 | F-036 | P1 | 回归基线 518/545/569ms 与 p95=127ms 无源 | 补批次43/本轮同设备同构建基线与 DB 门原始输出 |
| 6 | F-055 | P0 | 2h soak 在 2504s FATAL（滑杆端点），未完成 2h | 修复后设备/长时复跑 ≥2h 无 FATAL/ANR；保留首崩证据 |
| 7 | F-057 | P0 | 2h 内 FATAL 超限路径命中 | 同 F-055；复跑 2h 并确认超限路径不再命中 |
| 8 | F-064 | P1 | dropbox app 条目声称 12，实测 6 | 改正基线为 6 并说明窗口/过滤口径 |
| 9 | F-067 | P1 | 重复运行 dropbox 计数声称稳定 12，实测 6 | 同 F-064；重跑后按实测计数 |
| 10 | G-089 | P1 | gradle/verification-metadata.xml 不存在，供应链校验未启用 | 落地依赖校验元数据并跑 verify；或产品裁定不启用并降级为 BLOCKED |
| 11 | H-018 | P0 | DOCX 无文档级 /AIGC；KD-84 部分页丢标；KD-85 模型改卷漏标 | 落地 docProps/子集/模型改卷三处 /AIGC 并独立读回 |
| 12 | H-061 | P0 | KD-84 open：writeSubset 逐页重绘丢文档级 /AIGC | 改 writeSubset 保留 Info 字典；独立读回 |
| 13 | H-062 | P0 | KD-85 open：模型改卷文本未纳入 /AIGC | toDraft 透传 hasModelParsedText 并纳入判据 |
| 14 | J-001 | P0 | 工作树 4730 脏路径，干净树前提不成立 | 冻结/清理工作树后重跑 identity --require-clean |
| 15 | J-002 | P0 | release APK revision 落后 HEAD 53 提交 | 干净树重建 release APK/AAB 并重跑 --require-head |
| 16 | J-020 | P0 | RELEASE_* 4/4 缺失，release 未用生产钥匙 | 配置生产签名环境变量并重签 |
| 17 | J-021 | P0 | 签名指纹未等于已登记发布指纹 | 登记发布指纹并用同一钥匙重签 |
| 18 | J-024 | P0 | AAB 与 APK 签名身份无法证明一致 | 同钥匙重出 APK/AAB 并比对证书 |
| 19 | J-025 | P0 | AAB 证书有效期无法证明覆盖发布窗口 | 用生产证书重出并读有效期 |
| 20 | J-027 | P1 | 签名文档契约缺环境变量约定（doc_mentions_env_contract=false） | 补文档契约或改 signing 检查口径 |
| 21 | J-033 | P0 | AAB ABI 4 个（含 32 位），abiFilters 仅 2 个 | 重出 AAB 仅 arm64-v8a/x86_64 |
| 22 | J-034 | P0 | AAB 缺 libquickjs.so | 重出 AAB 并确认 native 库齐全 |
| 23 | J-041 | P0 | AAB 含 32 位 .so（负向未过） | 同 J-033 |
| 24 | J-048 | P0 | versionCode 恒 2，跨构建不单调 | 引入版本源并验证两次构建 vc 单调 |
| 25 | J-051 | P0 | versionCode/versionName 手写常量，无生成机制 | 同 J-048 |
| 26 | J-052 | P1 | 历史 AAB 与现 APK 的 vc 不可比 | 同 J-048；保留历史产物对照 |
| 27 | GATE-032 | P0 | acceptance_report 终态 exit 1（state=FAIL） | 清零 FAIL/BLOCKED 后复跑；不得改规则换绿 |
| 28 | GATE-038 | P0 | --require-complete exit 1（NOT_ACCEPTANCE_COMPLETE） | 同 GATE-032 |
| 29 | GATE-042 | P1 | strict lint exit 1（144 条 EVIDENCE_REUSED） | 降低证据复用或经裁定接受；不得放宽阈值 |
| 30 | GATE-091 | P0 | AUD-08 countExactRate 4/7=0.5714 < 0.9 | 提升切分金标准确率后复跑 |
| 31 | GATE-092 | P0 | AUD-08 门整体判红（fragmentRate 单条过） | 同 GATE-091 |
| 32 | GATE-099 | P0 | AUD-08 维持 FAIL，未转绿 | 同 GATE-091 |

## 关闭规则

- 只有对应行的重跑证据（cmd/test/device）转绿并写入行级复核后，才可改 PASS；
- 不得以“已修代码/已写文档”替代设备或独立读回证据；
- 7 条 PASS→FAIL 的矛盾行（F-019/F-028/F-029/F-034/F-036/F-064/F-067）
  必须先改正证据或数字，再重审。
