package cn.goutongban.aac.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.goutongban.aac.BoardModel
import cn.goutongban.aac.BuildConfig

@Composable
internal fun AppUpdatePanel(model: BoardModel) {
    val state by model.update.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var installMessage by remember(state.file) { mutableStateOf("") }
    Panel {
        Heading("应用更新")
        BodyText("当前版本 ${BuildConfig.VERSION_NAME}")
        if (state.message.isNotEmpty()) {
            BodyText(state.message, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (installMessage.isNotEmpty()) BodyText(installMessage)
        val file = state.file
        when {
            file?.isFile == true -> ActionButton("安装更新", Modifier.fillMaxWidth(), primary = true) {
                try {
                    if (!context.packageManager.canRequestPackageInstalls()) {
                        installMessage = "允许沟通板安装应用后，返回这里再点「安装更新」。"
                        context.startActivity(Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")
                        ))
                    } else {
                        installMessage = ""
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
                        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        })
                    }
                } catch (_: ActivityNotFoundException) {
                    installMessage = "系统无法打开安装界面，可从 GitHub 发布页安装。"
                } catch (_: SecurityException) {
                    installMessage = "系统阻止了安装，请检查沟通板的安装应用权限。"
                }
            }
            state.release != null -> ActionButton(
                "下载更新", Modifier.fillMaxWidth(), primary = true, enabled = !state.busy, click = model::downloadUpdate
            )
        }
        ActionButton("检查更新", Modifier.fillMaxWidth(), enabled = !state.busy) { model.checkForUpdate() }
    }
}
