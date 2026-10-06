package com.maibill.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.maibill.app.auto.PendingAutoRecord
import com.maibill.app.ui.AppRoot
import com.maibill.app.ui.theme.MaiBillTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAutoIntent(intent)
        setContent {
            MaiBillTheme {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAutoIntent(intent)
    }

    /** 记账气泡 / 桌面快捷方式带入：置入预填数据，AppRoot 观察后跳转记账页 */
    private fun handleAutoIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("goto_record", false) == true) {
            PendingAutoRecord.post(null, null)
            return
        }
        if (intent?.getBooleanExtra("auto_record", false) == true) {
            val fen = if (intent.hasExtra("auto_amount_fen")) {
                intent.getLongExtra("auto_amount_fen", 0L)
            } else null
            PendingAutoRecord.post(
                fen?.takeIf { it > 0 },
                intent.getStringExtra("auto_source"),
            )
        }
    }
}
