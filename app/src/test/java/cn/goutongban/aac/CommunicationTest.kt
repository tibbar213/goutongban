package cn.goutongban.aac

import cn.goutongban.aac.audio.mandarinRank
import cn.goutongban.aac.model.BodySide
import cn.goutongban.aac.model.Notice
import cn.goutongban.aac.model.PainLevel
import cn.goutongban.aac.model.PainPart
import cn.goutongban.aac.model.PainSpot
import cn.goutongban.aac.model.PhraseAction
import cn.goutongban.aac.model.Vocabulary
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CommunicationTest {
    private fun vocabulary(): Vocabulary = RuntimeEnvironment.getApplication().assets
        .open("phrases.json").bufferedReader().use { Vocabulary.parse(it.readText()) }

    @Test fun vocabularyHasSixCompleteCategories() {
        val vocab = vocabulary()
        assertEquals(listOf("body", "care", "food", "find", "mood", "other"), vocab.categories.map { it.id })
        assertTrue(vocab.phrases.size >= 150)
        assertEquals(vocab.phrases.size, vocab.byId.size)
        assertTrue(vocab.categories.all { c -> c.sections.size > 1 && c.sections.all { it.phrases.isNotEmpty() } })
        assertTrue(vocab.phrases.all { it.label.isNotBlank() && it.speech.isNotBlank() })
    }

    @Test fun essentialPhrasesSearchAndSafety() {
        val phrases = vocabulary().byId
        assertEquals(PhraseAction.PAIN, phrases.getValue("pain").action)
        assertEquals(PhraseAction.MORE, phrases.getValue("more").action)
        assertTrue(phrases.getValue("turn").matches("侧身"))
        assertEquals("请帮我翻个身。", phrases.getValue("turn").speech)
        assertEquals(Notice.SWALLOW, phrases.getValue("water").notice)
        assertEquals(Notice.URGENT, phrases.getValue("new_weakness").notice)
    }

    @Test fun leftShoulderAndPainLevelsRemainDistinct() {
        val left = PainSpot(PainPart.SHOULDER, BodySide.LEFT)
        assertEquals("左肩", left.label)
        assertEquals("我的左肩疼。", left.phrase().speech)
        assertEquals("我的左肩疼。疼得很厉害。", left.phrase(PainLevel.SEVERE).speech)
        assertNotEquals(left.phrase().id, left.phrase(PainLevel.SEVERE).id)
        assertEquals("我的右肩疼。", PainSpot(PainPart.SHOULDER, BodySide.RIGHT).phrase().speech)
        assertEquals("我的两侧肩都疼。", PainSpot(PainPart.SHOULDER, BodySide.BOTH).phrase().speech)
        assertEquals(Notice.URGENT, PainSpot(PainPart.CHEST).phrase().notice)
    }

    @Test fun offlineMandarinSelectionAvoidsCantoneseAndNonChineseVoices() {
        assertTrue(mandarinRank(Locale.SIMPLIFIED_CHINESE) > mandarinRank(Locale.TAIWAN))
        assertEquals(mandarinRank(Locale.SIMPLIFIED_CHINESE), mandarinRank(Locale.forLanguageTag("chn")))
        assertTrue(mandarinRank(Locale.TAIWAN) > 0)
        assertEquals(0, mandarinRank(Locale.forLanguageTag("zh-HK")))
        assertEquals(0, mandarinRank(Locale.forLanguageTag("yue-CN")))
        assertEquals(0, mandarinRank(Locale.US))
    }
}
