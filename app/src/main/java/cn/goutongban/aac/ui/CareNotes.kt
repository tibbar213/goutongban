package cn.goutongban.aac.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
internal fun UrgentNote() {
    Panel(color = MaterialTheme.colorScheme.errorContainer) {
        BodyText("突发或持续胸痛、严重呼吸困难、突发意识改变、新发一侧肢体无力时，请家属立即联系当地急救。中国大陆拨打 120。", strong = true)
        BodyText("沟通板用于表达需求。请由医护人员判断病情。")
    }
}

@Composable
internal fun SwallowNote() {
    Panel(color = MaterialTheme.colorScheme.surfaceVariant) {
        BodyText("有吞咽困难时，请按医护人员给出的饮食和饮水指导照护。听到「想喝水」后，先确认能否安全饮水。")
    }
}

@Composable
internal fun GuideScreen() {
    PageList {
        item { Heading("何时联系急救"); UrgentNote() }
        item { Heading("吃饭与喝水"); SwallowNote() }
    }
}
