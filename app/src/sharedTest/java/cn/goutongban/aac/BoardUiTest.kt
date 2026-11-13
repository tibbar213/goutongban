package cn.goutongban.aac

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.goutongban.aac.data.PreferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BoardUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    private fun reveal(matcher: SemanticsMatcher) {
        ui.waitUntil(8_000) {
            try {
                try { ui.onNode(matcher).assertIsDisplayed() }
                catch (_: AssertionError) { ui.onNodeWithTag("board-page").performScrollToNode(matcher) }
                ui.onNode(matcher).assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    private fun click(text: String) {
        reveal(hasText(text))
        ui.onNodeWithText(text).performClick()
    }

    private fun assertDialogText(text: String) {
        ui.onNode(hasText(text) and hasAnyAncestor(isDialog())).assertIsDisplayed()
    }

    @Test fun selectWordsUndoAndStartAgain() {
        ui.onNodeWithText("点下方字词").assertIsDisplayed()
        click("我")
        click("想")
        click("翻身")
        click("向左边")
        ui.onNodeWithText("我想翻身向左边").assertIsDisplayed()
        ui.onNode(hasText("读整句") and hasAnyAncestor(
            androidx.compose.ui.test.hasTestTag("sentence-box")
        )).assertIsDisplayed().performClick()
        click("撤销")
        ui.onNodeWithText("我想翻身").assertIsDisplayed()
        click("向右边")
        ui.onNodeWithText("我想翻身向右边").assertIsDisplayed()
        click("重来")
        ui.onNodeWithText("点下方字词").assertIsDisplayed()
        ui.onNodeWithText("家属").assertDoesNotExist()
    }

    @Test fun expandsBuiltInAlternativesWithoutNetwork() {
        click("请帮我")
        click("拿东西")
        click("我要")
        click("手机")
        ui.onNodeWithText("请帮我拿东西，我要手机").assertIsDisplayed()
    }

    @Test fun canSpellIndividualCharactersWithoutOpeningAnotherPage() {
        click("我")
        click("想")
        click("喝")
        click("水")
        ui.onNodeWithText("我想喝水").assertIsDisplayed()
        listOf("你", "这", "谁", "我们", "什", "她", "哪", "怎", "他", "那", "为").forEach {
            ui.onNodeWithText(it).assertDoesNotExist()
        }
        click("请帮我")
        click("拿水杯")
        ui.onNodeWithText("我想喝水，请帮我拿水杯").assertIsDisplayed()
    }

    @Test fun offlineInputMethodSuggestsWordsOutsideTheCarePhrases() {
        click("我")
        click("想")
        click("去")
        reveal(hasText("哪里"))
    }

    @Test fun canMixWholeWordsWithSingleCharacters() {
        click("我想")
        click("喝")
        click("水")
        ui.onNodeWithText("我想喝水").assertIsDisplayed()
        click("重来")
        click("我")
        click("想")
        click("喝水")
        ui.onNodeWithText("我想喝水").assertIsDisplayed()
    }

    @Test fun quickPhrasesStillReachLeftShoulderAndSeverity() {
        ui.onNodeWithTag("tab-phrases").performClick()
        click("疼痛部位")
        click("左肩")
        assertDialogText("我的左肩疼。")
        ui.onNodeWithText("严重").performClick()
        assertDialogText("我的左肩疼。疼得很厉害。")
    }

    @Test fun diagramUsesPatientLeftOnBothViews() {
        ui.onNodeWithTag("tab-phrases").performClick()
        click("疼痛部位")
        click("人体图")
        ui.onNodeWithContentDescription("左肩").performScrollTo().assertIsDisplayed().performClick()
        assertDialogText("我的左肩疼。")
        ui.onNodeWithText("继续选择部位").performClick()
        click("背面")
        ui.onNodeWithContentDescription("右肩").performScrollTo().assertIsDisplayed().performClick()
        assertDialogText("我的右肩疼。")
    }

    @Test fun titleAndBottomTabsKeepCommunicationPhrasesHistoryAndSettings() {
        ui.onNodeWithText("沟通板").assertIsDisplayed()
        ui.onNodeWithText("我要说").assertDoesNotExist()
        ui.onNodeWithTag("tab-help").assertDoesNotExist()
        ui.onNodeWithTag("tab-phrases").assertIsDisplayed()
        ui.onNodeWithTag("tab-history").assertIsDisplayed()
        ui.onNodeWithTag("tab-settings").performClick()
        ui.onNodeWithText("模型设置").performClick()
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithText("模型设置").assertIsDisplayed()
        ui.onNodeWithTag("tab-home").performClick()
        ui.onNodeWithText("点下方字词").assertIsDisplayed()
    }

    @Test fun historyRecordsFullSentencesAndAllowsReplayAndClear() {
        click("我们")
        click("一起")
        click("看电视")
        click("读整句")
        click("读整句")
        ui.onNodeWithTag("tab-history").performClick()
        val item = hasText("我们一起看电视") and hasClickAction()
        ui.waitUntil(8_000) { ui.onAllNodes(item).fetchSemanticsNodes().size == 1 }
        ui.onNode(item).assertIsDisplayed().performClick()
        click("清空历史")
        ui.onNodeWithText("清空").performClick()
        ui.waitUntil(8_000) { ui.onAllNodes(item).fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithText("朗读过的话会显示在这里。").assertIsDisplayed()
    }

    @Test fun favoritesStayAfterHistoryIsClearedAndCanBeRemoved() {
        val sentence = "我想听音乐"
        listOf("我", "想", "听音乐", "读整句").forEach(::click)
        ui.onNodeWithTag("tab-history").performClick()
        reveal(androidx.compose.ui.test.hasContentDescription("收藏：$sentence"))
        ui.onNodeWithContentDescription("收藏：$sentence").assertIsDisplayed().performClick()
        ui.waitUntil(8_000) {
            ui.onAllNodes(androidx.compose.ui.test.hasContentDescription("取消收藏：$sentence"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        click("清空历史")
        ui.onNodeWithText("清空").performClick()
        ui.onNodeWithText("朗读过的话会显示在这里。").assertIsDisplayed()
        ui.onNodeWithTag("tab-phrases").performClick()
        reveal(hasText(sentence) and hasClickAction())
        ui.onNode(hasText(sentence) and hasClickAction()).assertIsDisplayed().performClick()
        ui.onNodeWithContentDescription("取消收藏：$sentence").performClick()
        ui.waitUntil(8_000) { ui.onAllNodes(hasText(sentence)).fetchSemanticsNodes().isEmpty() }
    }

    @Test fun repeatedExpressionsTeachAWordFromIndividualCharacters() {
        ui.onNodeWithTag("tab-settings").performClick()
        click("清除学习记录")
        ui.onNode(hasText("清除") and hasAnyAncestor(isDialog())).performClick()
        ui.onNodeWithTag("tab-home").performClick()
        ui.onNodeWithText("点下方字词").assertIsDisplayed()

        repeat(2) {
            click("我想")
            click("听")
            click("戏")
            click("读整句")
            click("重来")
            ui.onNodeWithText("点下方字词").assertIsDisplayed()
        }
        click("我想")
        ui.waitUntil(8_000) { ui.onAllNodes(hasText("听戏")).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("听戏").assertIsDisplayed().performClick()
        ui.onNodeWithText("我想听戏").assertIsDisplayed()
    }

    @Test fun questionsAndPronounsAreAvailableAsOpeningWords() {
        listOf("我", "你", "我们", "那个", "什么", "哪里", "谁", "为什么").forEach {
            reveal(hasText(it))
        }
        ui.onNodeWithText("更多字").assertDoesNotExist()
        ui.onNodeWithText("展开其他字词").assertDoesNotExist()
        click("为什么")
        click("要")
        click("翻身")
        click("？")
        ui.onNodeWithText("为什么要翻身？").assertIsDisplayed()
    }

    @Test fun everydayOpeningPhrasesLeadToRelevantWords() {
        ui.onNodeWithText("我不舒服").assertDoesNotExist()
        ui.onNodeWithText("我这里疼").assertDoesNotExist()
        ui.onNodeWithText("我").assertIsDisplayed()
        click("我需要")
        click("上厕所")
        ui.onNodeWithText("我需要上厕所").assertIsDisplayed()
        click("重来")
        click("我想找")
        click("眼镜")
        ui.onNodeWithText("我想找眼镜").assertIsDisplayed()
        click("重来")
        click("请把")
        click("枕头")
        click("垫高一点")
        ui.onNodeWithText("请把枕头垫高一点").assertIsDisplayed()
        click("重来")
        click("有没有")
        click("纸巾")
        click("？")
        ui.onNodeWithText("有没有纸巾？").assertIsDisplayed()
    }

    @Test fun aiPredictionInHeaderCanOpenSettingsAndToggleWithoutLosingDraft() {
        val prefs = PreferencesStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val previous = runBlocking { prefs.model.first() }
        try {
            click("我")
            click("想")
            ui.onNodeWithTag("ai-prediction").assertIsDisplayed().performClick()
            ui.onNodeWithText("HTTPS 接口地址").assertIsDisplayed()
                .performTextReplacement("https://example.invalid/v1")
            ui.onNodeWithText("模型名").assertIsDisplayed().performTextReplacement("demo")
            click("保存")
            ui.waitUntil(8_000) { ui.onAllNodes(hasText("模型设置已保存")).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithContentDescription("返回").performClick()
            ui.onNodeWithText("我想").assertIsDisplayed()
            ui.onNodeWithText("AI 联想 · 关").performClick()
            ui.waitUntil(8_000) { ui.onAllNodes(hasText("AI 联想 · 开")).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("喝水").assertIsDisplayed()
            ui.onNodeWithText("AI 联想 · 开").performClick()
            ui.waitUntil(8_000) { ui.onAllNodes(hasText("AI 联想 · 关")).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("我想").assertIsDisplayed()
            reveal(hasText("喝水"))
        } finally {
            runBlocking { prefs.saveModel(previous) }
        }
    }

    @Test fun longCandidateListLoadsMoreRowsWhileScrolling() {
        click("我")
        val page = ui.onNodeWithTag("board-page")
        page.performScrollToIndex(4)
        page.performScrollToIndex(10)
        reveal(hasText("胸"))
        ui.onNodeWithTag("ai-prediction").assertIsDisplayed()
    }

    @Test fun customPhrasesAndWordsAreAddedAndUsedOnTheirRespectivePages() {
        val phrase = "请把蓝色水杯拿过来"
        val word = "老花镜"
        ui.onNodeWithTag("tab-phrases").performClick()
        click("添加整句")
        ui.onNodeWithText("词语内容").assertDoesNotExist()
        ui.onNodeWithText("整句内容").performTextInput(phrase)
        click("保存")
        ui.waitUntil(8_000) { ui.onAllNodes(hasText("常用句")).fetchSemanticsNodes().isNotEmpty() }
        reveal(hasText(phrase))
        click(phrase)
        ui.onNodeWithText("刚才的话").assertDoesNotExist()
        ui.onNodeWithText("重播").assertDoesNotExist()
        ui.onNodeWithTag("tab-home").performClick()
        click("添加词语")
        ui.onNodeWithText("整句内容").assertDoesNotExist()
        ui.onNodeWithText("词语内容").performTextInput(word)
        click("保存")
        ui.waitUntil(8_000) { ui.onAllNodes(hasText("我的词语")).fetchSemanticsNodes().isNotEmpty() }
        click("我想找")
        click("我的词语")
        click(word)
        ui.onNodeWithText("我想找老花镜").assertIsDisplayed()
        click("撤销")
        ui.onNodeWithTag("sentence-bar").assertIsDisplayed()
        ui.onNodeWithText("我想找老花镜").assertDoesNotExist()
    }
}
