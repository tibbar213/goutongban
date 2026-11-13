package cn.goutongban.aac

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class SmallScreenTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test
    @Config(sdk = [26], qualifiers = "zh-rCN-w300dp-h620dp-port-mdpi")
    fun narrowAndroidEightWithLargeSystemFontKeepsButtonsReadable() {
        RuntimeEnvironment.setFontScale(2f)
        ui.activityRule.scenario.recreate()
        ui.waitForIdle()
        assertEquals(2f, ui.activity.resources.configuration.fontScale, 0f)
        ui.onNodeWithTag("ai-prediction").assertIsDisplayed()
        ui.onNodeWithContentDescription("添加常用词").assertIsDisplayed()
        ui.onNodeWithTag("board-page").performScrollToNode(hasText("你"))
        val first = ui.onNodeWithText("我").getUnclippedBoundsInRoot()
        val second = ui.onNodeWithText("你").getUnclippedBoundsInRoot()
        assertEquals(first.left, second.left)
        assertTrue(first.bottom <= second.top)
        val category = hasText("什么")
        ui.onNodeWithTag("board-page").performScrollToNode(category)
        ui.onNode(category).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "zh-rCN-w430dp-h820dp-port-mdpi")
    fun defaultLargePhoneShowsFourOpeningWordsInFirstRow() = assertFourColumns()

    @Test
    fun default360DpPhoneShowsFourOpeningWordsInFirstRow() = assertFourColumns()

    private fun assertFourColumns() {
        val box = ui.onNodeWithTag("sentence-box").getUnclippedBoundsInRoot()
        val add = ui.onNodeWithContentDescription("添加常用词").assertIsDisplayed().getUnclippedBoundsInRoot()
        val cloud = ui.onNodeWithTag("ai-prediction").assertIsDisplayed().getUnclippedBoundsInRoot()
        val read = ui.onNodeWithContentDescription("读整句").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(box.bottom - box.top <= 112.dp)
        assertTrue(add.right <= cloud.left && add.bottom <= box.top)
        assertTrue(read.left > box.left + (box.right - box.left) / 2)
        assertTrue(read.right <= box.right && read.bottom <= box.bottom)
        val first = ui.onNodeWithText("我").assertIsDisplayed().getUnclippedBoundsInRoot()
        val second = ui.onNodeWithText("你").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(first.right <= second.left)
        assertTrue(first.top == second.top)
        val fourth = ui.onNodeWithText("那个").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertEquals(first.top, fourth.top)
        assertTrue(first.right - first.left >= 72.dp)
        assertTrue(first.bottom - first.top >= 48.dp)
    }
}
