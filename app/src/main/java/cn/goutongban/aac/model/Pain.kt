package cn.goutongban.aac.model

enum class BodyView(val label: String) { FRONT("正面"), BACK("背面") }
enum class BodySide(val label: String) { LEFT("左"), RIGHT("右"), BOTH("两侧") }

enum class PainPart(
    val label: String,
    val word: String,
    val bilateral: Boolean = false
) {
    HEAD("头部", "头"),
    NECK("颈部", "脖子"),
    CHEST("胸部", "胸口"),
    ABDOMEN("腹部", "肚子"),
    SHOULDER("肩膀", "肩", true),
    ARM("手臂", "手臂", true),
    PALM("手掌", "手掌", true),
    BACK("腰背", "腰背"),
    HIP("臀部", "臀部", true),
    THIGH("大腿", "大腿", true),
    CALF("小腿", "小腿", true),
    FOOT("脚部", "脚", true)
}

enum class PainLevel(val label: String, val sentence: String) {
    MILD("轻微", "有点疼。"),
    MODERATE("一般", "疼得比较明显。"),
    SEVERE("严重", "疼得很厉害。"),
    UNKNOWN("不确定", "我不确定有多疼。")
}

data class PainSpot(val part: PainPart, val side: BodySide? = null) {
    init { require(part.bilateral == (side != null)) }

    val label = when (side) {
        BodySide.BOTH -> "两侧" + part.label
        null -> part.label
        else -> side.label + if (part == PainPart.SHOULDER) "肩" else part.label
    }

    fun phrase(level: PainLevel? = null): Phrase {
        val location = when (side) {
            BodySide.BOTH -> "两侧" + part.word + "都"
            null -> part.word
            else -> side.label + part.word
        }
        return Phrase(
            id = listOf("pain", part.name, side?.name ?: "center", level?.name ?: "unspecified")
                .joinToString(":"),
            label = "我的${location}疼",
            speech = "我的${location}疼。" + (level?.sentence ?: ""),
            categoryId = "body",
            icon = "heart",
            notice = if (part == PainPart.CHEST) Notice.URGENT else null
        )
    }

    companion object {
        fun forPart(part: PainPart): List<PainSpot> = if (part.bilateral) {
            BodySide.entries.map { PainSpot(part, it) }
        } else {
            listOf(PainSpot(part))
        }
    }
}
