package com.maibill.app.ui.mine

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.maibill.app.data.Backup
import com.maibill.app.data.Repository
import com.maibill.app.data.SettingKeys
import com.maibill.app.ui.components.BudgetDialog
import com.maibill.app.ui.components.MutsumiAvatar
import com.maibill.app.ui.theme.MemberThemes
import com.maibill.app.ui.theme.ThemeMode
import com.maibill.app.ui.theme.ThemePrefs
import com.maibill.app.ui.theme.ThemeState
import com.maibill.app.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class MineViewModel : ViewModel() {
    private val repo = Repository()

    val budgetFen: StateFlow<Long> = repo.stringFlow(SettingKeys.MONTHLY_BUDGET)
        .map { it?.toLongOrNull() ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    fun setBudget(fen: Long) { viewModelScope.launch { repo.setSetting(SettingKeys.MONTHLY_BUDGET, fen.toString()) } }
    suspend fun exportCsv(): String = repo.exportCsv()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory { initializer { MineViewModel() } }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MineScreen(
    vm: MineViewModel = viewModel(factory = MineViewModel.Factory),
    onGotoCategories: () -> Unit,
    onGotoAuto: () -> Unit = {},
    onGotoTrash: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val budget by vm.budgetFen.collectAsState()
    var showBudget by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var lastAuto by remember { mutableStateOf(Backup.lastAuto(ctx)) }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val err = Backup.restoreFrom(ctx, uri)
                if (err == null) {
                    android.widget.Toast.makeText(ctx, "恢复成功，正在重启…", android.widget.Toast.LENGTH_SHORT).show()
                    kotlinx.coroutines.delay(900)
                    val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    if (intent != null) ctx.startActivity(intent)
                    Runtime.getRuntime().exit(0)
                } else {
                    android.widget.Toast.makeText(ctx, "恢复失败：$err", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val csv = vm.exportCsv()
                        ctx.contentResolver.openOutputStream(uri)?.use { os ->
                            os.write("\uFEFF".toByteArray(Charsets.UTF_8))
                            os.write(csv.toByteArray(Charsets.UTF_8))
                        } != null
                    }
                    if (ok) {
                        android.widget.Toast.makeText(ctx, "已导出 ✓", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(ctx, "导出失败：无法写入所选位置", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(ctx, "导出失败：${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "我的", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp)
        ) {
            RowItem("🗂️", "分类管理", "自定义图标与颜色", onGotoCategories)
            RowDivider()
            RowItem("⚡", "自动记账", "支付时弹出气泡快速记录", onGotoAuto)
            RowDivider()
            RowItem("🌗", "外观模式", "${ThemeMode.label(ThemeState.mode)}（${ThemeMode.sub(ThemeState.mode)}）", onClick = { showAppearance = true })
            RowDivider()
            RowItem("🎨", "主题色", ThemeState.theme.name, onClick = { showTheme = true })
            RowDivider()
            RowItem("🗑️", "回收站", "误删 30 天内可恢复", onGotoTrash)
            RowDivider()
            RowItem(
                "📊", "每月预算",
                if (budget > 0) "¥${Money.txt(budget)} / 月" else "未设置",
                onClick = { showBudget = true },
            )
            RowDivider()
            RowItem(
                "📤", "导出账单 CSV", "备份全部账单到手机存储",
                onClick = { exportLauncher.launch("小睦记账账单_${LocalDate.now()}.csv") },
            )
        }

        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Text("数据备份", fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "每 24 小时自动备份一次到系统「下载/小睦记账备份/」。\n恢复会覆盖当前全部数据并重启应用。",
                fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp),
            )
            Row(
                Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable {
                            scope.launch {
                                val name = Backup.backupNow(ctx, auto = false)
                                lastAuto = Backup.lastAuto(ctx)
                                android.widget.Toast.makeText(
                                    ctx,
                                    name?.let { "已备份 ✓ $name" } ?: "备份失败",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("立即备份", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.4.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .clickable { restoreLauncher.launch(arrayOf("*/*")) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("从备份恢复", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                if (lastAuto > 0) "上次自动备份：" + java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.CHINA)
                    .format(java.util.Date(lastAuto)) else "尚未自动备份过",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("关于小睦记账", fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "小睦记账 v${com.maibill.app.BuildConfig.VERSION_NAME} · 个人自用\n数据仅保存在本机（Room），无网络权限\n存钱进度、预算与统计全部离线可用\n「嗯。」—— 若叶睦",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp),
                    )
                }
                // 彩蛋：长按试试
                MutsumiAvatar(
                    com.maibill.app.R.drawable.mutsumi_art,
                    56.dp,
                    Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = {
                            android.widget.Toast.makeText(ctx, "……Mortis？", android.widget.Toast.LENGTH_SHORT).show()
                        },
                    ),
                )
            }
        }
        Spacer(Modifier.height(120.dp))
    }

    if (showBudget) {
        BudgetDialog(currentFen = budget, onSave = { vm.setBudget(it) }, onDismiss = { showBudget = false })
    }

    if (showAppearance) {
        AlertDialog(
            onDismissRequest = { showAppearance = false },
            title = { Text("外观模式", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column {
                    ThemeMode.all.forEach { (key, label) ->
                        val selected = ThemeState.mode == key
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    ThemeState.mode = key
                                    ThemePrefs.saveMode(ctx, key)
                                    showAppearance = false
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    label, fontSize = 13.5.sp,
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                )
                                Text(
                                    ThemeMode.detail(key), fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            if (selected) {
                                Text("✓", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAppearance = false }) { Text("关闭") } },
        )
    }

    if (showTheme) {
        AlertDialog(
            onDismissRequest = { showTheme = false },
            title = { Text("应援色主题", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column {
                    MemberThemes.all.forEach { m ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    ThemeState.member = m.key
                                    ThemePrefs.save(ctx, m.key)
                                    showTheme = false
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(14.dp).clip(CircleShape).background(m.light))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                m.name, fontSize = 13.5.sp,
                                fontWeight = if (ThemeState.member == m.key) FontWeight.ExtraBold else FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            if (ThemeState.member == m.key) {
                                Text("✓", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showTheme = false }) { Text("关闭") } },
        )
    }
}

@Composable
private fun RowItem(emoji: String, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
        Text("›", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}
