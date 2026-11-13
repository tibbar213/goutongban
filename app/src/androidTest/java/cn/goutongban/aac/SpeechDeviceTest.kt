package cn.goutongban.aac

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.goutongban.aac.audio.BoardSpeech
import cn.goutongban.aac.audio.VoiceStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeechDeviceTest {
    @Test fun installedOfflineMandarinEngineCompletesPlayback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val expectedEngine = InstrumentationRegistry.getArguments().getString("expectedSpeechEngine")
        lateinit var speech: BoardSpeech
        instrumentation.runOnMainSync { speech = BoardSpeech(instrumentation.targetContext.applicationContext) }
        try {
            val initial = runBlocking { withTimeout(35_000) { speech.state.first { it.status != VoiceStatus.CHECKING } } }
            if (expectedEngine == null) {
                assumeTrue("此项需要测试手机已安装普通话离线语音", initial.status == VoiceStatus.READY)
            } else {
                assertEquals(VoiceStatus.READY, initial.status)
                assertEquals(expectedEngine, initial.enginePackage)
            }
            listOf("我的左肩疼。", "请帮我翻个身。", "请把枕头抬高一点。").forEach { text ->
                instrumentation.runOnMainSync { speech.speak(text, 0.9f, 0.8f) }
                assertNotNull("语音引擎没有开始播报", speech.state.value.activeId)
                val finished = runBlocking { withTimeout(12_000) { speech.state.first { it.activeId == null } } }
                assertEquals(VoiceStatus.READY, finished.status)
                assertEquals(expectedEngine ?: initial.enginePackage, finished.enginePackage)
            }
        } finally {
            instrumentation.runOnMainSync(speech::close)
        }
    }
}
