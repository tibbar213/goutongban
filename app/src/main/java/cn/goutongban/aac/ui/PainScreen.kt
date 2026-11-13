package cn.goutongban.aac.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.goutongban.aac.BoardModel
import cn.goutongban.aac.model.BodySide
import cn.goutongban.aac.model.BodyView
import cn.goutongban.aac.model.PainLevel
import cn.goutongban.aac.model.PainPart
import cn.goutongban.aac.model.PainSpot

@Composable
internal fun PainScreen(model: BoardModel) {
    val choice by model.pain.collectAsStateWithLifecycle()
    val notice by model.notice.collectAsStateWithLifecycle()
    var diagram by rememberSaveable { mutableStateOf(false) }
    var backView by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    val spot = choice.spot
    val choose: (PainSpot) -> Unit = {
        model.chooseSpot(it)
        details = true
    }
    PageList { columns ->
        item {
            ActionPair("大按钮列表", "人体图", { diagram = false }, { diagram = true }, primaryFirst = !diagram)
        }
        if (diagram) {
            item {
                ActionPair("正面", "背面", { backView = false }, { backView = true }, primaryFirst = !backView)
            }
            item {
                BodyMap(if (backView) BodyView.BACK else BodyView.FRONT, spot, choose)
            }
            if (spot != null) item { PainSummary(spot, choice.level) { details = true } }
        } else {
            if (spot != null) item { PainSummary(spot, choice.level) { details = true } }
            item { BodyText("左右按老人自己的左手和右手区分。") }
            val order = listOf(PainPart.SHOULDER) + PainPart.entries.filter { it != PainPart.SHOULDER }
            val spots = order.flatMap(PainSpot::forPart)
            grid(spots.filter { it.side != BodySide.BOTH } + spots.filter { it.side == BodySide.BOTH },
                columns, { it.label }) { part, tile ->
                Tile(part.label, tile, group = "body", selected = part == spot) { choose(part) }
            }
        }
    }
    if (details && spot != null && notice == null) {
        val phrase = spot.phrase(choice.level)
        BoardDialog("疼痛表达", { details = false }) {
            BodyText(phrase.speech, strong = true)
            ActionButton("朗读这句话", Modifier.fillMaxWidth(), primary = true) { model.speak(phrase.speech) }
            Heading("疼痛程度")
            PainLevel.entries.chunked(2).forEach { levels ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    levels.forEach { level ->
                        ActionButton(level.label, Modifier.weight(1f), primary = level == choice.level) {
                            model.chooseLevel(level)
                        }
                    }
                }
            }
            ActionButton("继续选择部位", Modifier.fillMaxWidth()) { details = false }
        }
    }
}

@Composable
private fun PainSummary(spot: PainSpot, level: PainLevel?, details: () -> Unit) {
    Panel {
        BodyText(spot.phrase(level).speech, strong = true)
        ActionButton("疼痛程度与重播", Modifier.fillMaxWidth(), click = details)
    }
}

private data class Marker(val spot: PainSpot, val x: Float, val y: Float)

private fun markers(view: BodyView): List<Marker> = buildList {
    fun middle(part: PainPart, y: Float) { add(Marker(PainSpot(part), 0.50f, y)) }
    fun pair(part: PainPart, x: Float, y: Float) {
        val leftOnScreen = if (view == BodyView.FRONT) BodySide.RIGHT else BodySide.LEFT
        val rightOnScreen = if (leftOnScreen == BodySide.RIGHT) BodySide.LEFT else BodySide.RIGHT
        add(Marker(PainSpot(part, leftOnScreen), x, y))
        add(Marker(PainSpot(part, rightOnScreen), 1f - x, y))
    }
    middle(PainPart.HEAD, 0.08f)
    middle(PainPart.NECK, 0.22f)
    if (view == BodyView.FRONT) {
        middle(PainPart.CHEST, 0.36f)
        middle(PainPart.ABDOMEN, 0.50f)
    } else {
        middle(PainPart.BACK, 0.46f)
        pair(PainPart.HIP, 0.39f, 0.60f)
    }
    pair(PainPart.SHOULDER, 0.32f, 0.28f)
    pair(PainPart.ARM, 0.22f, 0.43f)
    pair(PainPart.PALM, 0.12f, 0.61f)
    pair(PainPart.THIGH, 0.39f, 0.71f)
    pair(PainPart.CALF, 0.36f, 0.83f)
    pair(PainPart.FOOT, 0.33f, 0.94f)
}

@Composable
private fun BodyMap(view: BodyView, chosen: PainSpot?, select: (PainSpot) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Panel {
        BodyText("点圆点选部位。图中标注的是老人自己的左右。")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BodyText(if (view == BodyView.FRONT) "本人右侧" else "本人左侧")
            BodyText(if (view == BodyView.FRONT) "本人左侧" else "本人右侧")
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(440.dp)) {
            val width = maxWidth
            val height = maxHeight
            Canvas(Modifier.fillMaxSize()) {
                fun x(value: Float) = value * size.width
                fun y(value: Float) = value * size.height
                val outline = Path().apply {
                    moveTo(x(.44f), y(.17f))
                    lineTo(x(.44f), y(.21f))
                    cubicTo(x(.40f), y(.23f), x(.34f), y(.23f), x(.31f), y(.26f))
                    cubicTo(x(.27f), y(.29f), x(.22f), y(.37f), x(.20f), y(.44f))
                    lineTo(x(.13f), y(.56f))
                    cubicTo(x(.07f), y(.57f), x(.06f), y(.65f), x(.10f), y(.66f))
                    cubicTo(x(.13f), y(.67f), x(.17f), y(.64f), x(.17f), y(.60f))
                    lineTo(x(.27f), y(.46f))
                    lineTo(x(.34f), y(.36f))
                    cubicTo(x(.35f), y(.46f), x(.36f), y(.48f), x(.33f), y(.55f))
                    cubicTo(x(.30f), y(.63f), x(.34f), y(.68f), x(.33f), y(.75f))
                    lineTo(x(.30f), y(.91f))
                    cubicTo(x(.25f), y(.94f), x(.24f), y(.97f), x(.30f), y(.97f))
                    cubicTo(x(.36f), y(.97f), x(.41f), y(.97f), x(.42f), y(.94f))
                    lineTo(x(.45f), y(.76f))
                    lineTo(x(.49f), y(.63f))
                    lineTo(x(.51f), y(.63f))
                    lineTo(x(.55f), y(.76f))
                    lineTo(x(.58f), y(.94f))
                    cubicTo(x(.59f), y(.97f), x(.64f), y(.97f), x(.70f), y(.97f))
                    cubicTo(x(.76f), y(.97f), x(.75f), y(.94f), x(.70f), y(.91f))
                    lineTo(x(.67f), y(.75f))
                    cubicTo(x(.66f), y(.68f), x(.70f), y(.63f), x(.67f), y(.55f))
                    cubicTo(x(.64f), y(.48f), x(.65f), y(.46f), x(.66f), y(.36f))
                    lineTo(x(.73f), y(.46f))
                    lineTo(x(.83f), y(.60f))
                    cubicTo(x(.83f), y(.64f), x(.87f), y(.67f), x(.90f), y(.66f))
                    cubicTo(x(.94f), y(.65f), x(.93f), y(.57f), x(.87f), y(.56f))
                    lineTo(x(.80f), y(.44f))
                    cubicTo(x(.78f), y(.37f), x(.73f), y(.29f), x(.69f), y(.26f))
                    cubicTo(x(.66f), y(.23f), x(.60f), y(.23f), x(.56f), y(.21f))
                    lineTo(x(.56f), y(.17f))
                    close()
                }
                val bodyFill = colors.surfaceVariant
                val bodyLine = colors.primary
                drawPath(outline, bodyFill)
                drawPath(outline, bodyLine, style = Stroke(2.dp.toPx()))
                val topLeft = Offset(x(.38f), y(.02f))
                val head = Size(x(.24f), y(.17f))
                drawOval(bodyFill, topLeft, head)
                drawOval(bodyLine, topLeft, head, style = Stroke(2.dp.toPx()))
                if (view == BodyView.BACK) {
                    drawLine(bodyLine, Offset(x(.50f), y(.25f)), Offset(x(.50f), y(.58f)), 2.dp.toPx())
                } else {
                    drawCircle(bodyLine, 2.dp.toPx(), Offset(x(.46f), y(.115f)))
                    drawCircle(bodyLine, 2.dp.toPx(), Offset(x(.54f), y(.115f)))
                    drawLine(bodyLine, Offset(x(.47f), y(.149f)), Offset(x(.53f), y(.149f)), 2.dp.toPx())
                }
            }
            markers(view).forEach { marker ->
                val active = marker.spot == chosen
                Box(
                    modifier = Modifier
                        .offset(width * marker.x - 24.dp, height * marker.y - 24.dp)
                        .size(48.dp).clip(CircleShape)
                        .clickable(role = Role.Button) { select(marker.spot) }
                        .semantics { contentDescription = marker.spot.label; selected = active },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.size(if (active) 31.dp else 24.dp),
                        shape = CircleShape, color = if (active) colors.error else colors.primary,
                        border = BorderStroke(2.dp, colors.surface)
                    ) {}
                }
            }
        }
    }
}
