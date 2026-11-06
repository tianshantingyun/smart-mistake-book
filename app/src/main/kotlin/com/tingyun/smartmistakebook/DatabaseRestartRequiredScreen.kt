package com.tingyun.smartmistakebook

import android.app.Activity
import android.os.Process
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.ui.PrimaryActionButton
import com.tingyun.smartmistakebook.core.ui.RootPageColumn
import com.tingyun.smartmistakebook.core.ui.SectionHeader

@Composable
internal fun DatabaseRestartRequiredScreen(message: String, onClose: () -> Unit) {
    BackHandler { }
    RootPageColumn(modifier = Modifier.testTag("database_restart_required")) {
        SectionHeader("需要重新打开应用")
        Text(message, modifier = Modifier.padding(top = 16.dp))
        Text("请先关闭应用，再从桌面打开，以使用更新后的本机数据。", modifier = Modifier.padding(top = 12.dp))
        PrimaryActionButton(
            text = "关闭应用",
            onClick = onClose,
            modifier = Modifier.padding(top = 24.dp).testTag("database_close_app"),
        )
    }
}

internal fun Activity.closeForDatabaseRestart() {
    finishAndRemoveTask()
    // 所有仓库都持有旧 Room。仅移走 Activity 会让桌面入口继续复用失效的进程。
    Process.killProcess(Process.myPid())
}
