# 详情图片直接显示

用户在查看详情模块效果后明确要求“详情效果不需要缩略了”。本批据此替代上一批原图/讲解图默认收起和单图缩略高度的规则，继续保留已实现的视觉模块。

- 原图、重绘/题图、讲解图直接显示在各自模块中，移除“查看原图/讲解图”展开步骤；角色、来源顺序和历史版本标识保持。
- 单张图片按正文宽度及原始宽高比完整呈现，不裁图，不再受240/520dp缩略高度限制；点击图片或“查看大图”进入原有缩放查看器，关闭后回到详情。
- 图片仅在进入可见区域后读取，离屏释放采样位图；保留有界解码，不把直接显示实现为全量原尺寸位图常驻。
- 正常图片去掉重复的“点击收起/放大”提示，加载/不可读取/解码失败使用图片状态，不误称讲解图为原图。图片读取不写入备注、收藏、归档或生图任务。

## 验证

- [x] library 73项单元测试零失败/错误/跳过，lint零错误（保留8项警告和3项提示）；测试包及localFirst/strictOffline主包构建通过，`final-build.log`为BUILD SUCCESSFUL，2m24s。
- [x] 独立emulator-5562运行3类19项设备回归全部通过，无遗漏/跳过；覆盖题目/讲解角色隔离、无须展开、状态恢复、历史版本、备注编辑/归档。
- [x] 真实本地PNG的全宽/自然高度、点击大图后返回、文件摘要不变；12页长图仅解码可见页并可到达末页。全宽800×1600夹具实测高度超过原520dp上限，保持2:1高宽比。
- [x] 当前主包和library测试包已安装，设备APK摘要与构建产物相符；3个源码/测试文件摘要、前置字节和本轮差异留证，角色筛选/排序及备注处理未变。计划和进度已同步。

设备结果为`device.log`及`probe-summary.json`，单元/lint统计为`unit-lint-summary.json`。首轮新测试因多余import和DpRect宽高API用法不匹配而编译失败；已修正并完整重跑，保留`build.log`，不以失败构建冒充通过。

[当前原生详情截图](../../.jez/artifacts/detail-images-expanded-2026-10-26/mistake-detail-full-image.png)使用合成本地PNG，已实际查看。截图用于说明原图内容与自然显示高度，不是用户照片；摘要及尺寸见`screenshot.json`。

```text
./gradlew.bat :feature:library:testDebugUnitTest :feature:library:lintDebug :feature:library:assembleDebugAndroidTest :app:assembleLocalFirstDebug :app:assembleStrictOfflineDebug -Pksp.incremental=false --continue --console=plain --no-daemon --no-parallel --max-workers=1
python -X utf8 .jez/artifacts/detail-images-expanded-2026-10-26/run_detail_suites.py --select MistakeDetailRoleSectionsInstrumentedTest,MistakeNoteAndArchiveInstrumentedTest,MistakeRelationAndRevisionInstrumentedTest
```

证据目录：`.jez/artifacts/detail-images-expanded-2026-10-26/`。未提交推送。本批只修订详情图片呈现，原性能/真机/人工发布门保持开放。
