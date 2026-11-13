package cn.goutongban.aac

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NarrowScreenDeviceTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun sentenceAndControlsRemainUsableAtDoubleFontSize() {
        val configuration = ui.activity.resources.configuration
        assumeTrue(configuration.screenWidthDp <= 310 && configuration.fontScale >= 1.8f)

        listOf("请帮我", "翻身", "请", "慢一点").forEach { word ->
            val button = hasText(word) and hasClickAction()
            ui.onNodeWithTag("board-page").performScrollToNode(button)
            ui.onNode(button).assertIsDisplayed().performClick()
        }
        val sentence = "请帮我翻身，请慢一点"
        val layouts = mutableListOf<TextLayoutResult>()
        ui.onNodeWithText(sentence, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getLayout -> getLayout(layouts) }
        val layout = layouts.single()
        assertTrue(layout.lineCount > 1)
        assertFalse("句子有横向溢出", layout.didOverflowWidth)
        ui.onNodeWithContentDescription("读整句").assertIsDisplayed().performClick()
        ui.onNodeWithContentDescription("撤销").performClick()
        ui.onNodeWithText("请帮我翻身，请").assertIsDisplayed()
    }
}
