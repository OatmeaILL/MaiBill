package com.maibill.app.ui.savings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.maibill.app.data.Repository
import com.maibill.app.data.SavingsGoalEntity
import com.maibill.app.data.TransactionEntity
import com.maibill.app.data.TxType
import com.maibill.app.ui.components.AmountInput
import com.maibill.app.ui.components.AmountKeypad
import com.maibill.app.ui.components.CategoryIcon
import com.maibill.app.ui.components.ColorSwatches
import com.maibill.app.ui.components.ConfirmDialog
import com.maibill.app.ui.components.DatePickDialog
import com.maibill.app.ui.components.EmptyHint
import com.maibill.app.ui.components.MoneyText
import com.maibill.app.ui.components.SectionRow
import com.maibill.app.ui.components.SimpleField
import com.maibill.app.ui.components.StatBar
import com.maibill.app.ui.components.TwoSeg
import com.maibill.app.ui.components.secondaryCard
import com.maibill.app.ui.theme.appIsDark
import com.maibill.app.ui.theme.paletteBase
import com.maibill.app.ui.theme.paletteContainer
import com.maibill.app.util.Money
import com.maibill.app.util.TimeUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SavingsViewModel : ViewModel() {
    data class GoalView(val goal: SavingsGoalEntity, val savedFen: Long, val progress: Float)
    data class Row(val tx: TransactionEntity, val goalName: String?)
    data class Ui(val goals: List<GoalView>, val history: List<Row>, val monthSavedFen: Long)

    private val repo = Repository()
    private val monthTick = MutableStateFlow(0)

    val data: StateFlow<Ui?> = monthTick.flatMapLatest {
        // 每次 refreshMonth 都取"当前"月份，避免进程跨月后"本月存入"停留在旧月份
        val range = TimeUtil.monthRange(java.time.YearMonth.now())
        combine(
            repo.goals(),
            repo.goalSums(),
            repo.recentSavings(30),
            repo.netSavedInMonth(range.first, range.last),
        ) { goals, sums, txs, monthSaved ->
            val sumMap = sums.associate { it.goalId to it.total }
            val goalMap = goals.associateBy { it.id }
            Ui(
                goals = goals.map { g ->
                    val s = sumMap[g.id] ?: 0L
                    GoalView(g, s, if (g.targetFen > 0) (s.toFloat() / g.targetFen).coerceIn(0f, 1f) else 0f)
                },
                history = txs.map { Row(it, it.goalId?.let { id -> goalMap[id]?.name }) },
                monthSavedFen = monthSaved,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun refreshMonth() { monthTick.value++ }

    fun deposit(goal: SavingsGoalEntity, save: Boolean, fen: Long) {
        viewModelScope.launch { repo.deposit(goal, save, fen) }
    }

    fun saveGoal(name: String, emoji: String, colorIdx: Int, targetFen: Long, deadline: Long?, id: Long? = null) {
        viewModelScope.launch {
            if (id == null) {
                repo.addGoal(SavingsGoalEntity(name = name, emoji = emoji, colorIndex = colorIdx, targetFen = targetFen, deadline = deadline))
            } else {
                repo.goalById(id)?.let {
                    repo.updateGoal(it.copy(name = name, emoji = emoji, colorIndex = colorIdx, targetFen = targetFen, deadline = deadline))
                }
            }
        }
    }

    fun deleteGoal(g: SavingsGoalEntity, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val n = repo.countByGoal(g.id)
            if (n > 0) onResult(false, "该目标下有 $n 笔存取记录，请先删除对应账单")
            else { repo.deleteGoal(g); onResult(true, "已删除目标") }
        }
    }

    fun deleteTx(t: TransactionEntity) { viewModelScope.launch { repo.deleteTx(t) } }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory { initializer { SavingsViewModel() } }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavingsScreen(vm: SavingsViewModel = viewModel(factory = SavingsViewModel.Factory)) {
    val ui by vm.data.collectAsState()
    val ctx = LocalContext.current
    // 页面常驻组合（Pager 保活），改在每次回到前台时刷新月份，避免跨月后"本月存入"停留旧月份
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        vm.refreshMonth()
    }
    var depositTarget by remember { mutableStateOf<Pair<SavingsGoalEntity, Boolean>?>(null) }
    var editGoal by remember { mutableStateOf<SavingsGoalEntity?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var deleteGoalTarget by remember { mutableStateOf<SavingsGoalEntity?>(null) }
    var deleteTxTarget by remember { mutableStateOf<TransactionEntity?>(null) }

    fun toast(msg: String) = android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("存钱", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            val ms = ui?.monthSavedFen ?: 0L
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    when {
                        ms > 0 -> "本月存入 ¥${Money.txt(ms)}"
                        ms < 0 -> "本月净取出 ¥${Money.txt(-ms)}"
                        else -> "本月净存入 ¥0.00"
                    },
                    fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        ui?.let { u ->
            if (u.goals.isEmpty()) {
                com.maibill.app.ui.components.MutsumiEmpty(
                    artRes = com.maibill.app.R.drawable.mutsumi_lay_art,
                    title = "想存钱的话……嗯，建一个目标吧。",
                    sub = "目标会一直在存钱页等你。",
                    mascotSize = 150.dp,
                    circle = false,
                )
            }
            u.goals.forEach { g ->
                GoalCard(
                    g,
                    onDeposit = { depositTarget = g.goal to true },
                    onWithdraw = { depositTarget = g.goal to false },
                    onEdit = { editGoal = g.goal },
                    onDelete = { deleteGoalTarget = g.goal },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                NewGoalButton(onClick = { showNew = true })
            }
            if (u.goals.isNotEmpty()) {
                SectionRow("最近动态", "近 30 天 · 自动同步到账单", Modifier.padding(horizontal = 10.dp))
                Column(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .secondaryCard()
                        .padding(horizontal = 14.dp)
                ) {
                    if (u.history.isEmpty()) {
                        // 一条记录都没有时也留着这张卡：空白交给小睦，而不是让页面下半截空着
                        Text(
                            "「还没存过一笔。慢慢来。」",
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 15.dp),
                        )
                    } else {
                        u.history.forEachIndexed { i, r ->
                            if (i > 0) HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            HistoryRow(r, onDelete = { deleteTxTarget = r.tx })
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(120.dp))
    }

    depositTarget?.let { (g, initialSave) ->
        DepositDialog(
            goal = g,
            initialSaveMode = initialSave,
            onDone = { save, fen -> vm.deposit(g, save, fen); depositTarget = null },
            onDismiss = { depositTarget = null },
        )
    }
    if (showNew) {
        GoalDialog(
            initial = null,
            onSave = { n, e, c, t, d -> vm.saveGoal(n, e, c, t, d); showNew = false },
            onDismiss = { showNew = false },
        )
    }
    editGoal?.let { g ->
        GoalDialog(
            initial = g,
            onSave = { n, e, c, t, d -> vm.saveGoal(n, e, c, t, d, g.id); editGoal = null },
            onDismiss = { editGoal = null },
        )
    }
    deleteGoalTarget?.let { g ->
        ConfirmDialog(
            title = "删除目标",
            message = "要删除「${g.name}」吗？已生成的存取账单会保留。",
            onConfirm = { vm.deleteGoal(g) { ok, msg -> toast(msg) } },
            onDismiss = { deleteGoalTarget = null },
        )
    }
    deleteTxTarget?.let { t ->
        ConfirmDialog(
            title = "删除记录",
            message = "要删除这笔存取记录吗？目标进度会同步变化。",
            onConfirm = { vm.deleteTx(t) },
            onDismiss = { deleteTxTarget = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GoalCard(
    g: SavingsViewModel.GoalView,
    onDeposit: () -> Unit,
    onWithdraw: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = appIsDark()
    val base = paletteBase(g.goal.colorIndex)
    val reached = g.savedFen >= g.goal.targetFen
    val pct = if (g.goal.targetFen > 0) ((g.savedFen * 100 / g.goal.targetFen).toInt()).coerceIn(0, 100) else 0
    val lack = (g.goal.targetFen - g.savedFen).coerceAtLeast(0)

    Column(
        modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onEdit, onLongClick = onDelete)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(paletteContainer(g.goal.colorIndex, dark)),
                contentAlignment = Alignment.Center,
            ) { Text(g.goal.emoji, fontSize = 20.sp) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(g.goal.name, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    when {
                        reached -> "🎉 嗯，做到了。"
                        else -> "还差 ¥${Money.txt(lack)}" + (g.goal.deadline?.let { " · 目标 ${TimeUtil.dateText(it)}" } ?: " · 不设期限")
                    },
                    fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // 已存金额 + 占比并成一行：百分比不再孤零零飘在角上
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            MoneyText(g.savedFen.coerceAtLeast(0), fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                " / ¥${Money.txt(g.goal.targetFen)}",
                fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .padding(bottom = 2.dp)
                    .clip(RoundedCornerShape(50))
                    .background(paletteContainer(g.goal.colorIndex, dark))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text("$pct%", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = base)
            }
        }
        Spacer(Modifier.height(10.dp))
        StatBar(progress = g.progress, color = base, height = 9.dp)
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                Modifier
                    .weight(1.45f)
                    .shadow(6.dp, RoundedCornerShape(13.dp))
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onDeposit)
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) { Text("＋ 存入", fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(13.dp))
                    .border(1.4.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(13.dp))
                    .clickable(onClick = onWithdraw)
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) { Text("取出", fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun NewGoalButton(onClick: () -> Unit) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 9f))
    val primary = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(primary.copy(alpha = 0.05f))
            .drawBehind {
                drawRoundRect(
                    color = primary.copy(alpha = 0.5f),
                    style = Stroke(width = 2.2f, pathEffect = dash),
                    cornerRadius = CornerRadius(22.dp.toPx(), 22.dp.toPx()),
                )
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("＋ 新建存钱目标", fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = primary)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(r: SavingsViewModel.Row, onDelete: () -> Unit) {
    val isSave = r.tx.type == TxType.SAVE
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onDelete)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(if (isSave) "💰" else "📤", if (isSave) 9 else 1)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${if (isSave) "存入" else "取出"}「${r.goalName ?: "已删目标"}」",
                fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${TimeUtil.dateText(r.tx.date)} · 已同步到账单",
                fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(
            (if (isSave) "+" else "-") + "¥" + Money.txt(r.tx.amount),
            fontSize = 15.sp, fontWeight = FontWeight.Bold,
            color = if (isSave) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

/** 存入 / 取出弹窗（initialSaveMode：true=存入，false=取出） */
@Composable
private fun DepositDialog(
    goal: SavingsGoalEntity,
    initialSaveMode: Boolean,
    onDone: (Boolean, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var saveMode by remember { mutableStateOf(initialSaveMode) }
    val input = remember { AmountInput() }
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${goal.emoji} ${goal.name}", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TwoSeg(listOf("存入", "取出"), if (saveMode) 0 else 1) { saveMode = it == 0 }
                input.hint()?.let { hint ->
                    Text(
                        hint,
                        fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("¥", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        input.text.ifEmpty { "0" },
                        fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                AmountKeypad(
                    onKey = { input.onKey(it) },
                    onSave = {
                        val fen = input.result()
                        if (fen != null && fen > 0) onDone(saveMode, fen)
                        else android.widget.Toast.makeText(ctx, "请输入有效金额", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    keyHeight = 44.dp,
                    saveLabel = "确认",
                    navBarInset = false,
                )
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}

/** 新建 / 编辑存钱目标弹窗 */
@Composable
private fun GoalDialog(
    initial: SavingsGoalEntity?,
    onSave: (name: String, emoji: String, colorIdx: Int, targetFen: Long, deadline: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "🎯") }
    var target by remember { mutableStateOf(initial?.let { Money.plain(it.targetFen) } ?: "") }
    var colorIdx by remember { mutableStateOf(initial?.colorIndex ?: 9) }
    var deadline by remember { mutableStateOf(initial?.deadline) }
    var showPicker by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建存钱目标" else "编辑目标", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SimpleField(value = name, onValueChange = { name = it }, label = "目标名称", maxLength = 12)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SimpleField(
                        value = emoji, onValueChange = { if (it.length <= 4) emoji = it },
                        label = "图标", modifier = Modifier.width(92.dp), maxLength = 4,
                    )
                    SimpleField(
                        value = target,
                        onValueChange = { s ->
                            if (s.length <= 10 && s.count { it == '.' } <= 1 && s.all { it.isDigit() || it == '.' }) target = s
                        },
                        label = "目标金额（元）", number = true, maxLength = 10,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text("颜色", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ColorSwatches(selected = colorIdx, onSelect = { colorIdx = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showPicker = true }) {
                        Text(
                            deadline?.let { "目标日期：${TimeUtil.dateText(it)}" } ?: "＋ 设置目标日期（可选）",
                            fontSize = 12.5.sp,
                        )
                    }
                    if (deadline != null) TextButton(onClick = { deadline = null }) { Text("清除", fontSize = 12.5.sp) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val fen = Money.parseFen(target)
                if (name.isNotBlank() && fen != null) {
                    onSave(name.trim(), emoji.ifBlank { "🎯" }, colorIdx, fen, deadline)
                } else {
                    android.widget.Toast.makeText(ctx, "请填写目标名称和有效金额", android.widget.Toast.LENGTH_SHORT).show()
                }
            }) { Text("保存", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )

    if (showPicker) {
        DatePickDialog(
            initial = deadline?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now().plusMonths(3),
            onPick = { deadline = it.toEpochDay(); showPicker = false },
            onDismiss = { showPicker = false },
        )
    }
}
