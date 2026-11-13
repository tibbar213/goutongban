package cn.goutongban.aac

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import cn.goutongban.aac.ui.BoardApp

class MainActivity : ComponentActivity() {
    private val model: BoardModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(0xFFF7F5EE.toInt(), 0xFFF7F5EE.toInt()),
            navigationBarStyle = SystemBarStyle.light(0xFFF7F5EE.toInt(), 0xFFEAE7DE.toInt())
        )
        setContent { BoardApp(model, ::setFullScreen) }
    }

    override fun onResume() {
        super.onResume()
        model.resumed(true)
    }

    override fun onPause() {
        model.resumed(false)
        super.onPause()
    }

    private fun setFullScreen(enabled: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (enabled) controller.hide(WindowInsetsCompat.Type.systemBars())
        else controller.show(WindowInsetsCompat.Type.systemBars())
    }
}
