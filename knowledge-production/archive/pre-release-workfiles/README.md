# 发布前知识库工作文件归档

本目录保存从仓库根目录迁入的 `.agent_*` 判定切片、CSV、JSON 和 `..._probe.json`。迁移日期为 2026-11-12，文件内容与文件名均保留。

`tools/kb_coverage/apply_text_fixes.py` 与 `merge_text_judgments.py` 自动读取此目录，并继续支持根目录新生成的同名输入。同名文件优先使用根目录版本。历史记录中单独出现的 `.agent_*` 文件名可在此目录查找。
