package cn.goutongban.aac.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.goutongban.aac.BoardModel
import cn.goutongban.aac.model.ModelPrediction

@Composable
internal fun ModelSettingsScreen(model: BoardModel) {
    val config by model.modelSettings.collectAsStateWithLifecycle()
    val busy by model.modelBusy.collectAsStateWithLifecycle()
    val message by model.modelMessage.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    var url by remember(config.baseUrl) { mutableStateOf(config.baseUrl) }
    var name by remember(config.model) { mutableStateOf(config.model) }
    var key by remember { mutableStateOf("") }
    var clearKey by remember { mutableStateOf(false) }
    val saved = ModelPrediction.endpoint(config.baseUrl) != null && config.model.isNotBlank()
    val edited = url.trim() != config.baseUrl || name.trim() != config.model || key.isNotBlank() || clearKey

    LaunchedEffect(message) {
        if (message == "模型设置已保存") {
            key = ""
            clearKey = false
        }
    }
    PageList {
        item {
            Panel {
                BodyText("开启后，已选文字会发送给你配置的服务，生成后续候选。服务不可用时使用内置字词。")
                if (!saved || edited) {
                    BodyText("保存接口地址和模型名后，可在沟通页开启 AI 联想。")
                }
            }
        }
        item {
            OutlinedTextField(
                url, { url = it.take(512) }, label = { Text("HTTPS 接口地址") },
                placeholder = { Text("https://api.example.com/v1") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
        }
        item {
            OutlinedTextField(
                name, { name = it.take(200) }, label = { Text("模型名") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy
            )
        }
        item {
            val label = if (config.encryptedKey.isEmpty() || url.trim() != config.baseUrl) {
                "API Key（按服务要求填写）"
            } else {
                "API Key（已保存，可替换）"
            }
            OutlinedTextField(
                key, { key = it.take(512); clearKey = false }, label = { Text(label) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
        }
        if (config.encryptedKey.isNotEmpty()) item {
            ActionButton(if (clearKey) "取消移除密钥" else "移除已保存密钥", Modifier.fillMaxWidth(), enabled = !busy) {
                clearKey = !clearKey
                if (clearKey) key = ""
            }
        }
        item {
            ActionButton("保存", Modifier.fillMaxWidth(), primary = true, enabled = !busy && edited) {
                focus.clearFocus()
                model.saveModel(url, name, key, clearKey)
            }
        }
        item {
            ActionButton("测试连接", Modifier.fillMaxWidth(), enabled = saved && !busy && !edited) {
                focus.clearFocus()
                model.testModel()
            }
        }
        if (message.isNotBlank()) item {
            BodyText(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}
