package com.maibill.app

import android.app.Application
import com.maibill.app.data.Backup
import com.maibill.app.data.Graph
import com.maibill.app.data.Repository
import com.maibill.app.ui.theme.ThemePrefs
import com.maibill.app.ui.theme.ThemeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MaiBillApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        ThemeState.member = ThemePrefs.load(this)
        ThemeState.mode = ThemePrefs.loadMode(this)
        appScope.launch { Graph.seedIfEmpty() }
        // 回收站 30 天自动清除
        appScope.launch {
            try { Repository().purgeOldTx() } catch (_: Exception) {}
        }
        // 每 24 小时自动备份一次数据库到系统下载目录
        appScope.launch {
            delay(5000)
            try { Backup.maybeAutoBackup(this@MaiBillApplication) } catch (_: Exception) {}
        }
    }
}

