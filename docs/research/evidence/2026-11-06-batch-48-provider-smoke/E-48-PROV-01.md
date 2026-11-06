# 真实 Provider 冒烟（DeepSeek）——E-48-PROV-01

| 字段 | 值 |
|---|---|
| evidence-id | `E-48-PROV-01` |
| executed-at | 2026-11-06 |
| head | `7e1c03ac`（工作树） |
| 密钥 | 用户书面提供并粘贴于对话；已写入 gitignored `local.properties`（`git check-ignore` 实证 ignore、`git status` 干净）；证据只记 sha256 前 8 位 **`cd10efbc`**，本文件与任何入库文件均不含密钥 |
| 判定 | **协议级连通 PASS**：`GET /models` 200（2 个模型 id）；`POST /chat/completions` ×3 全部 200，`finish_reason=stop`，usage 正常，时延 672–764 ms |
| 模型 | 平台实际 id 为 **`deepseek-flash`** / `deepseek-v4-pro`；用户口述 "DeepSeek V4.1 flash" 无此 id，按档位取 `deepseek-flash`（`local.properties` 已改） |
| 响应结构 | `choices[0].message` 含 `content` + `reasoning_content`（推理型）；答案在 `content`（实测 `'2'`）；网关按 OpenAI 兼容 `content` 解析不受影响，`reasoning_content` 为额外字段 |
| 逐字命令 | `curl -s -H "Authorization: Bearer $KEY" "$BASE/models"`（KEY 取自 local.properties，未回显）；python urllib `/chat/completions` ×3（max_tokens 8/16/32，temperature 0） |
| exit | `SMOKE_EXIT=0` |
| 未覆盖 | ①产品路径（App 网关→真实端点）未验证——需设备/模拟器侧配置凭据后走应用链路；②生成质量未评（不是本轮判据）；③图像输入能力未测（DeepSeek /models 未声明 vision，D 域图像任务维持 BLOCKED） |

## 对验收台账的影响（本轮登记，行级改判留下一轮）

- D 域「真实 Provider」相关 BLOCKED 行的 blocked_by 从「无 key」更新为「已具备 key + 协议级冒烟 PASS；产品路径需设备侧配置凭据」。
- 发版阻塞清单相应缩小；**不据此把任何行改 PASS**（行级改判需按行重跑并写行级复核，属下一轮）。
