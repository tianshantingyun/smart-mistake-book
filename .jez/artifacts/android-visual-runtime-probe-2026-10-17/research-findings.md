# Android 自由 JS 绘图接入复核（2026-10-18）

## 已确定的结论

正式路线：当前讲题模型通过 GENERATE_FIGURE 返回 JS 程序；Android isolatedProcess Service 执行；仅将受限场景数据交回原生校验、度量与 Canvas 回放。不引入额外视觉模型任务，不将简单图转发图像模型。

研究探针是独立包 com.tingyun.visualprobe，位于本目录；没有修改正式 App。当前计划文件在研究完成前未改。

## 本轮实测

- AGP 9.3.0、Kotlin 2.3.21、coroutines 1.11.0、minSdk 23，消费 quickjs-kt-android 1.0.15：assembleDebug 成功。
- 第一轮未显式对齐 Kotlin、AGP 默认编译器 2.2 时，因依赖 metadata 2.4 而失败；对齐项目 2.3.21 后成功。没有使用跳过 metadata 校验参数，没有升级正式项目。
- emulator-5556：API 34、x86_64、4 KB 页。instrumentation 共 6 项 PASS，完整结果见 probe-result.json。
- 宿主 UID 10197；隔离进程 UID 99000。独立 PID；INTERNET 权限拒绝；宿主私有文件不可读；Application 业务初始化被 UID guard 跳过。
- QuickJS 可计算 101 点正弦曲线并往返中文/emoji；fetch/document/window/process/require 均不存在。
- 无限循环返回 interrupted，8 MiB 堆预算触发 out of memory；大原生操作受到独立墙钟 watchdog 终止。
- 人为阻塞工作线程后，独立 watchdog 自杀服务进程；重新绑定后得到新 PID，宿主 PID 未变。
- APK zipalign -c -P 16 4 成功；这只证明 APK 打包对齐，不证明内部 ELF 或设备加载符合 16 KB。

命令：

~~~powershell
.\gradlew.bat -p .jez/artifacts/android-visual-runtime-probe-2026-10-17 :probe:assembleDebug --console=plain
C:\Android\Sdk\platform-tools\adb.exe -s emulator-5556 shell am instrument -w -r com.tingyun.visualprobe/.ProbeInstrumentation
C:\Android\Sdk\build-tools\37.0.0\zipalign.exe -c -P 16 4 .jez/artifacts/android-visual-runtime-probe-2026-10-17/probe/build/outputs/apk/debug/probe-debug.apk
~~~

## 正式运行库决定与门槛

固定 quickjs-kt v1.0.15 源码，commit a081755bcdb18ee6afa7ffee25b8fa976f06d31f；MIT，含 Bellard QuickJS。正式产物从固定源码用 NDK r28+ 重建。发布 AAR 只用于本次可行性实验，不直接作为已满足上线兼容的成品。

发布 AAR SHA256：406c3efb80d5fa3495df4a7f2663be8cc30c974fe5c2368effa929de77cf0ed8。四 ABI 的 PT_LOAD 对齐均为 16384，但 GNU_RELRO 结束未对齐：arm64 余 4096、x86_64 余 12288，32 位两项亦非零。Android 官方要求同时检查 RELRO，单独设置 max-page-size 或通过 zipalign 不够。

目前没有 NDK/16 KB 设备，未验证源码重建、API 23、arm64、16 KB 真正加载、R8、整 App 集成。以上属于正式实施第一批的阻断门；不写成已通过。

## 工程接入复核

1. Application 在任何数据库、文件维护、恢复、Provider 初始化之前，用 Process.myUid() != applicationInfo.uid 短路隔离进程。API 23 不依赖 ActivityManager 查询进程名或隐藏 API。正式实现还需审计自动启动 ContentProvider/AndroidX Startup。
2. evaluate 是 suspend 不等于自动后台执行；使用专用单线程 dispatcher。引擎 timeout 需要 interrupt 回调运行，只是协作中断；close 可能等锁，不能充当硬终止。
3. 独立 watchdog 与 Binder terminate 控制方法由服务自己 killProcess(myPid())。宿主不能假定可杀不同 UID；超时先废弃代号，要求自杀/解绑，确认旧 Binder 死亡后限次重建。
4. Messenger 探针只证明小消息可行；正式用 AIDL 控制和有界 ParcelFileDescriptor 管道，避免共享 Binder 事务上限。
5. 当前模型 request schema 17、Room 66。GENERATE_FIGURE 仅 kind/description，无 JS；无 TUTOR_VISUAL_GENERATE/REVIEW 任务。JS 源码直接由现有 TUTOR_PLAN/RESPOND 工具参数生成。
6. 现有工具循环并未真的撤掉最后一轮工具；需同时收敛 native tools 与兼容 JSON 工具广告，修复仍计入原 6 次派发上限。
7. 当前 MathBox 字形测量和上下标布局有简化，Compose/PDF 几何有重复；新增共享内容布局与显示列表，不能声称现有公式引擎已精确。

## 一手资料与失败记录

- [quickjs-kt 固定源码](https://github.com/dokar3/quickjs-kt/tree/a081755bcdb18ee6afa7ffee25b8fa976f06d31f)
- [Android 16 KB 与 RELRO 检查](https://developer.android.com/guide/practices/page-sizes#check-relro)
- [Android NDK 修订历史](https://developer.android.com/ndk/downloads/revision_history)
- [Service isolatedProcess](https://developer.android.com/guide/topics/manifest/service-element#isolated)
- 浏览工具打开 GitHub tag/属性页失败；改用 git ls-remote 与 Maven 实物检查。猜测的 raw CMake 路径返回 404，未作为证据。
- Android 可行性研究子代理在给出 AAR、ELF、watchdog 等核查后触发额度限制；最终构建与设备实验由主代理完成。
