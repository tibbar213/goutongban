package cn.goutongban.aac

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.goutongban.aac.audio.VoiceStatus
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WordSpeechDeviceTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun readsEachPickedCharacterAndTheCompletedSentence() {
        lateinit var model: BoardModel
        ui.runOnIdle { model = ViewModelProvider(ui.activity)[BoardModel::class.java] }
        ui.waitUntil(35_000) { model.speech.state.value.status != VoiceStatus.CHECKING }
        assumeTrue(model.speech.state.value.status == VoiceStatus.READY)
        val completed = model.speech.state.value.completed

        ui.onNodeWithTag("board-page").performScrollToNode(hasText("我想"))
        ui.onNodeWithText("我想").performClick()
        ui.onNodeWithText("翻身").performClick()
        ui.waitUntil(12_000) { model.speech.state.value.completed >= completed + 2 }
        assertEquals("我想翻身", model.draft.value.text)
        ui.onNodeWithText("读整句").performClick()
        ui.waitUntil(12_000) { model.speech.state.value.completed >= completed + 3 }
        assertEquals(VoiceStatus.READY, model.speech.state.value.status)
    }
}
