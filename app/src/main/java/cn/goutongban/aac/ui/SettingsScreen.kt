package cn.goutongban.aac.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.goutongban.aac.BoardModel
import cn.goutongban.aac.Page
import cn.goutongban.aac.audio.VoiceStatus
import cn.goutongban.aac.data.PreferencesStore
import kotlin.math.roundToInt

@Composable
internal fun SettingsScreen(model: BoardModel) {
    val config = LocalBoardSettings.current
    val voice by model.speech.state.collectAsStateWithLifecycle()
    var unavailable by rememberSaveable { mutableStateOf(false) }
    var clearLearning by rememberSaveable { mutableStateOf(false) }
    var speed by remember(config.speed) { mutableFloatStateOf(config.speed) }
    var volume by remember(config.volume) { mutableFloatStateOf(config.volume) }
    val systemVoice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        model.speech.recheck()
    }
    PageList {
        item { ActionButton("模型设置", Modifier.fillMaxWidth()) { model.open(Page.MODEL) } }
        item { ActionButton("紧急就医与吞咽指引", Modifier.fillMaxWidth()) { model.open(Page.GUIDE) } }
        item {
            Panel {
                Heading("常用字词学习")
                SettingToggle("学习常用字词", config.learnWords) {
                    model.setting(PreferencesStore.LearnWords, it)
                }
                BodyText("同一接词在两次整句朗读中用过后，会优先显示。学习记录保存在本机。")
                ActionButton("清除学习记录", Modifier.fillMaxWidth()) { clearLearning = true }
            }
        }
        item {
            Panel {
                Heading("普通话离线语音")
                SettingToggle("点选时朗读字词", config.speakEachWord) {
                    model.setting(PreferencesStore.SpeakEachWord, it)
                }
                BodyText(when (voice.status) {
                    VoiceStatus.CHECKING -> "正在检查手机的普通话语音。"
                    VoiceStatus.READY -> "已检测到可用的普通话离线语音。"
                    VoiceStatus.ERROR -> "语音播报失败。请检查语音引擎、媒体音量后重新检测。"
                    VoiceStatus.MISSING -> "这台手机还没有可用的普通话离线语音。请在系统语音设置中选择普通话并下载离线语音包，然后重新检测。"
                })
                if (unavailable) {
                    BodyText("可手动在手机设置中搜索「文字转语音」或「文本转语音」。")
                }
                ActionButton("打开系统语音设置", Modifier.fillMaxWidth()) {
                    try {
                        systemVoice.launch(Intent("com.android.settings.TTS_SETTINGS"))
                    } catch (_: ActivityNotFoundException) {
                        try { systemVoice.launch(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
                        catch (_: ActivityNotFoundException) { unavailable = true }
                    }
                }
                ActionPair("重新检测", "试听普通话", model.speech::recheck, {
                    model.speak("你好，这里是沟通板。")
                }, secondEnabled = voice.status == VoiceStatus.READY)
                BodyText("语速 ${(speed * 100).roundToInt()}%")
                Slider(speed, { speed = it }, valueRange = 0.55f..1.35f, steps = 7,
                    onValueChangeFinished = { model.setting(PreferencesStore.Speed, speed) })
                BodyText("播报音量 ${(volume * 100).roundToInt()}%")
                Slider(volume, { volume = it }, valueRange = 0.1f..1f, steps = 8,
                    onValueChangeFinished = { model.setting(PreferencesStore.Volume, volume) })
                BodyText("语音使用手机的媒体音量。")
            }
        }
        item {
            Panel {
                Heading("沟通按钮字号")
                ChoiceRow(listOf("较大", "大", "更大", "特大"), config.font) {
                    model.setting(PreferencesStore.Font, it)
                }
                Heading("沟通按钮尺寸")
                ChoiceRow(listOf("大", "加高", "特高"), config.tile) {
                    model.setting(PreferencesStore.Tile, it)
                }
                Tile("帮我翻身", Modifier.fillMaxWidth(), icon = "care", group = "care") {
                    model.speak("请帮我翻个身。")
                }
            }
        }
        item {
            Panel {
                Heading("显示与触控")
                SettingToggle("显示图标和照片", config.showImages) { model.setting(PreferencesStore.Images, it) }
                SettingToggle("高对比度", config.highContrast) { model.setting(PreferencesStore.Contrast, it) }
                SettingToggle("沟通页全屏", config.fullScreen) { model.setting(PreferencesStore.FullScreen, it) }
                SettingToggle("防止同一按钮连续误触", config.guardTaps) { model.setting(PreferencesStore.GuardTaps, it) }
            }
        }
        item { AppUpdatePanel(model) }
    }
    if (clearLearning) {
        BoardDialog("清除学习记录？", { clearLearning = false }) {
            BodyText("清除后，接词排序会重新开始学习。")
            ActionPair("清除", "取消", {
                model.clearLearning()
                clearLearning = false
            }, { clearLearning = false })
        }
    }
}

@Composable
private fun ChoiceRow(options: List<String>, chosen: Int, select: (Int) -> Unit) {
    options.indices.chunked(2).forEach { indexes ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            indexes.forEach { index ->
                ActionButton(options[index], Modifier.weight(1f), primary = chosen == index) { select(index) }
            }
        }
    }
}

@Composable
internal fun SettingToggle(label: String, active: Boolean, change: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 55.dp)
            .toggleable(value = active, role = Role.Switch, onValueChange = change)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BodyText(label, Modifier.weight(1f))
        Switch(checked = active, onCheckedChange = null)
    }
}
