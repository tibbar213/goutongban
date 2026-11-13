package cn.goutongban.aac.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import cn.goutongban.aac.data.BoardSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal val LocalBoardSettings = staticCompositionLocalOf { BoardSettings() }
internal val Shape = RoundedCornerShape(16.dp)
private val Ink = Color(0xFF192C3D)
private val Blue = Color(0xFF164779)
private val Danger = Color(0xFFA02B29)

@Composable
internal fun BoardTheme(settings: BoardSettings, content: @Composable () -> Unit) {
    val palette = lightColorScheme(
        primary = if (settings.highContrast) Color(0xFF002E61) else Blue,
        onPrimary = Color.White,
        background = if (settings.highContrast) Color.White else Color(0xFFF7F5EE),
        onBackground = if (settings.highContrast) Color.Black else Ink,
        surface = Color.White,
        onSurface = if (settings.highContrast) Color.Black else Ink,
        surfaceVariant = Color(0xFFE9EFF6),
        onSurfaceVariant = if (settings.highContrast) Color.Black else Color(0xFF3C5062),
        outline = if (settings.highContrast) Color.Black else Color(0xFF8395A2),
        outlineVariant = if (settings.highContrast) Color.Black else Color(0xFFD7DEE2),
        error = Danger,
        errorContainer = Color(0xFFFFE9E4),
        onErrorContainer = Color(0xFF742219)
    )
    CompositionLocalProvider(LocalBoardSettings provides settings) {
        MaterialTheme(colorScheme = palette, content = content)
    }
}

@Composable
internal fun Heading(label: String, modifier: Modifier = Modifier, major: Boolean = false) {
    val size = if (major) 26 else 20
    Text(
        label, modifier.semantics { heading() },
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = size.sp, lineHeight = (size + 8).sp, fontWeight = FontWeight.Bold
    )
}

@Composable
internal fun BodyText(label: String, modifier: Modifier = Modifier, strong: Boolean = false) {
    Text(
        label, modifier, fontSize = if (strong) 20.sp else 16.sp,
        lineHeight = if (strong) 30.sp else 25.sp,
        fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal
    )
}

@Composable
internal fun ActionButton(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    click: () -> Unit
) {
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val text: @Composable () -> Unit = {
        Text(
            label, fontSize = if (largeText) 14.sp else 16.sp,
            lineHeight = if (largeText) 20.sp else 22.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
    val sized = modifier.heightIn(min = 52.dp)
    val padding = PaddingValues(horizontal = if (largeText) 5.dp else 10.dp, vertical = 8.dp)
    if (primary) {
        Button(
            onClick = click, modifier = sized, enabled = enabled, shape = RoundedCornerShape(12.dp),
            contentPadding = padding
        ) { text() }
    } else {
        OutlinedButton(
            onClick = click, modifier = sized, enabled = enabled, shape = RoundedCornerShape(12.dp),
            contentPadding = padding
        ) { text() }
    }
}

internal fun <T> LazyListScope.grid(
    values: List<T>,
    columns: Int,
    key: (T) -> String,
    spacing: Dp = 10.dp,
    tile: @Composable (T, Modifier) -> Unit
) {
    items(values.chunked(columns), key = { "grid:" + key(it.first()) }) { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
            row.forEach { tile(it, Modifier.weight(1f)) }
            if (row.size < columns) Spacer(Modifier.weight((columns - row.size).toFloat()))
        }
    }
}

@Composable
internal fun AdaptiveActions(content: @Composable (Boolean) -> Unit) {
    val scale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) { content(maxWidth.value >= 280f * scale.coerceAtLeast(1f)) }
}

@Composable
internal fun ActionPair(
    first: String, second: String,
    firstClick: () -> Unit, secondClick: () -> Unit,
    primaryFirst: Boolean = false, secondEnabled: Boolean = true
) = AdaptiveActions { row ->
    if (row) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ActionButton(first, Modifier.weight(1f), primary = primaryFirst, click = firstClick)
            ActionButton(second, Modifier.weight(1f), enabled = secondEnabled, click = secondClick)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ActionButton(first, Modifier.fillMaxWidth(), primary = primaryFirst, click = firstClick)
            ActionButton(second, Modifier.fillMaxWidth(), enabled = secondEnabled, click = secondClick)
        }
    }
}

@Composable
internal fun Tile(
    label: String,
    modifier: Modifier = Modifier,
    icon: String? = null,
    group: String = "",
    photo: File? = null,
    large: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
    click: () -> Unit
) {
    val config = LocalBoardSettings.current
    val colors = MaterialTheme.colorScheme
    val font = listOf(20, 24, 28, 32)[config.font]
    val size = listOf(83, 105, 127)[config.tile] + if (large) 14 else 0
    val background = when {
        selected -> colors.surfaceVariant
        config.highContrast -> Color.White
        else -> when (group) {
            "body" -> Color(0xFFF8E6DE)
            "care" -> Color(0xFFE5EDF9)
            "food" -> Color(0xFFFCEDCB)
            "find" -> Color(0xFFDFF0EA)
            "mood" -> Color(0xFFEEE7F5)
            "other" -> Color(0xFFEDEDE4)
            else -> colors.surface
        }
    }
    val edge = if (selected || config.highContrast) colors.primary else colors.outlineVariant
    Surface(
        modifier = modifier
            .clip(Shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = click)
            .then(if (selected) Modifier.semantics { this.selected = true } else Modifier),
        shape = Shape, color = background,
        contentColor = if (enabled) colors.onBackground else colors.onSurfaceVariant,
        border = BorderStroke(if (selected || config.highContrast) 2.dp else 1.dp, edge)
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = size.dp).padding(horizontal = 8.dp, vertical = 11.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
        ) {
            if (config.showImages) {
                var bitmap by remember(photo) { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(photo) {
                    if (photo != null) bitmap = withContext(Dispatchers.IO) {
                        BitmapFactory.decodeFile(photo.path, BitmapFactory.Options().apply { inSampleSize = 2 })
                            ?.asImageBitmap()
                    }
                }
                val preview = bitmap
                if (preview != null) {
                    Image(
                        preview, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(78.dp).clip(RoundedCornerShape(8.dp))
                    )
                } else if (icon != null) {
                    Text(symbol(icon), fontSize = 22.sp, lineHeight = 28.sp)
                }
            }
            Text(
                label, fontSize = font.sp, lineHeight = (font + 8).sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

internal fun symbol(icon: String): String = when (icon) {
    "heart" -> "♥"
    "care" -> "✋"
    "food" -> "🍚"
    "person" -> "👤"
    "mood" -> "☺"
    "other" -> "💬"
    "water" -> "💧"
    "phone" -> "☎"
    "tv" -> "📺"
    "music" -> "♫"
    "photo" -> "▣"
    "check" -> "✓"
    "close" -> "✕"
    "snow" -> "❄"
    "sun" -> "☀"
    "warning" -> "⚠"
    "bell" -> "🔔"
    "glasses" -> "👓"
    "moon" -> "☾"
    "bed" -> "🛏"
    else -> "💬"
}

@Composable
internal fun Panel(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier.fillMaxWidth(), shape = Shape, color = color,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
internal fun BoardDialog(title: String, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = dismiss) {
        Surface(shape = Shape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Heading(title, major = true)
                content()
            }
        }
    }
}
