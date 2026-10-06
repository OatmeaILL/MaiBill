package com.maibill.app.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.maibill.app.data.CategoryEntity
import com.maibill.app.data.Repository
import com.maibill.app.data.SettingKeys
import com.maibill.app.data.TransactionEntity
import com.maibill.app.data.TxSource
import com.maibill.app.data.TxType
import com.maibill.app.ui.components.CategoryIcon
import com.maibill.app.ui.components.ConfirmDialog
import com.maibill.app.ui.components.DailyGreeting
import com.maibill.app.ui.components.EmptyHint
import com.maibill.app.ui.components.MoneyText
import com.maibill.app.ui.components.MonthHeader
import com.maibill.app.ui.components.MutsumiAvatar
import com.maibill.app.ui.components.MutsumiEmpty
import com.maibill.app.ui.components.SectionRow
import com.maibill.app.ui.components.StatBar
import com.maibill.app.ui.components.rememberMutsumiQuote
import com.maibill.app.ui.theme.appIsDark
import com.maibill.app.ui.theme.heroGradient
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
import java.time.YearMonth

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModel : ViewModel() {
    data class Ui(
        val month: YearMonth,
        val txs: List<TransactionEntity>,
        val cats: Map<Long, CategoryEntity>,
        val budgetFen: Long,
    )

    val month = MutableStateFlow(YearMonth.now())
    private val repo = Repository()

    val data: StateFlow<Ui?> = month.flatMapLatest { m ->
        val range = TimeUtil.monthRange(m)
        combine(
            repo.monthTx(range.first, range.last),
            repo.categories(),
            repo.stringFlow(SettingKeys.MONTHLY_BUDGET),
        ) { txs, cats, budget ->
            Ui(m, txs, cats.associateBy { it.id }, budget?.toLongOrNull() ?: 0L)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun prev() { month.value = month.value.minusMonths(1) }
    fun next() { if (month.value < YearMonth.now()) month.value = month.value.plusMonths(1) }
    fun deleteTx(t: TransactionEntity) { viewModelScope.launch { repo.deleteTx(t) } }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel() }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
    onGotoStats: () -> Unit,
    onEditTransaction: (Long) -> Unit = {},
) {
    val ui by vm.data.collectAsState()
    var menuTarget by remember { mutableStateOf<TransactionEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<TransactionEntity?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var daily by remember { mutableStateOf(DailyGreeting.shouldShow(ctx)) }
    LaunchedEffect(daily) {
        if (daily) {
            kotlinx.coroutines.delay(5000)
            daily = false
            DailyGreeting.mark(ctx)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        MonthHeader(
            month = ui?.month ?: YearMonth.now(),
            onChange = { m -> if (m < (ui?.month ?: YearMonth.now())) vm.prev() else vm.next() },
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        ui?.let { u ->
            HeroCard(u, onSetBudget = onGotoStats)
            if (daily) {
                DailyBanner(onDismiss = {
                    daily = false
                    DailyGreeting.mark(ctx)
                })
            }
            SectionRow("账单明细", "共 ${u.txs.size} 笔", Modifier.padding(horizontal = 10.dp))
            if (u.txs.isEmpty()) {
                val quote = rememberMutsumiQuote()
                MutsumiEmpty(
                    artRes = com.maibill.app.R.drawable.mutsumi_chibi_art,
                    title = "嗯。还没有账单。",
                    sub = "点底部 ＋，记一笔就好。$quote",
                )
            } else {
                u.txs.groupBy { it.date }.forEach { (day, list) ->
                    DayCard(
                        day, list, u.cats,
                        onLongPress = { menuTarget = it },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(120.dp))
    }

    // 长按账单：编辑 / 删除
    menuTarget?.let { t ->
        val canEdit = t.type == TxType.EXPENSE || t.type == TxType.INCOME
        AlertDialog(
            onDismissRequest = { menuTarget = null },
            title = { Text("这笔账单", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column {
                    if (canEdit) {
                        MenuOption("✏️", "修改金额 / 分类 / 备注", onClick = {
                            menuTarget = null
                            onEditTransaction(t.id)
                        })
                    }
                    MenuOption("🗑️", "删除这条账单", danger = true, onClick = {
                        menuTarget = null
                        deleteTarget = t
                    })
                }
            },
            confirmButton = {},
            dismissButton = {},
        )
    }

    deleteTarget?.let { t ->
        ConfirmDialog(
            title = "删除账单",
            message = "要删除「${t.note.ifBlank { "这笔账单" }}」吗？删除后无法恢复。",
            onConfirm = { vm.deleteTx(t) },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun MenuOption(emoji: String, label: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 15.sp)
        Spacer(Modifier.width(10.dp))
        Text(
            label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun HeroCard(u: HomeViewModel.Ui, onSetBudget: () -> Unit) {
    val expense = u.txs.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
    val income = u.txs.filter { it.type == TxType.INCOME }.sumOf { it.amount }
    val saved = u.txs.filter { it.type == TxType.SAVE }.sumOf { it.amount } -
        u.txs.filter { it.type == TxType.UNSAVE }.sumOf { it.amount }
    val hasBudget = u.budgetFen > 0
    val usedPct = if (hasBudget) ((expense * 100 / u.budgetFen).toInt()).coerceAtMost(999) else 0
    val remain = u.budgetFen - expense
    val isCurrent = u.month == YearMonth.now()
    val daysLeft = if (isCurrent) u.month.lengthOfMonth() - LocalDate.now().dayOfMonth + 1 else 0
    val mLabel = "${u.month.monthValue}月"

    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(heroGradient())
    ) {
        // 右上角的 Q 版小睦：贴在卡片角落，不压任何数字
        Image(
            painter = painterResource(com.maibill.app.R.drawable.mutsumi_lay_art),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 2.dp)
                .size(88.dp),
        )
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${mLabel}支出", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.92f))
                if (hasBudget) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.17f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Text("已用 $usedPct%", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            MoneyText(expense, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(vertical = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroPill("${mLabel}收入", income, Modifier.weight(1f))
                HeroPill("${mLabel}结余", income - expense, Modifier.weight(1f))
            }
            if (saved != 0L) {
                Text(
                    if (saved > 0) "${mLabel}净存入 ¥${Money.txt(saved)} · 不计入支出"
                    else "${mLabel}净取出 ¥${Money.txt(-saved)} · 不计入支出",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.88f),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (hasBudget) {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (remain >= 0) "预算剩余 ¥${Money.txt(remain)}" else "已超支 ¥${Money.txt(-remain)}",
                        fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    if (isCurrent && remain > 0 && daysLeft > 0) {
                        Text("日均可用 ¥${Money.txt(remain / daysLeft)}", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                StatBar(
                    progress = expense.toFloat() / u.budgetFen,
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.26f),
                    height = 8.dp,
                )
            } else {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.16f))
                        .clickable(onClick = onSetBudget)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("＋ 设置每月预算", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DailyBanner(onDismiss: () -> Unit) {
    val today = LocalDate.now()
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onDismiss)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MutsumiAvatar(com.maibill.app.R.drawable.mutsumi_chibi_art, 40.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            "嗯。${today.monthValue}月${today.dayOfMonth}日，今天也请多指教。",
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
        Text("✕", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun HeroPill(label: String, fen: Long, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(13.dp))
            .background(Color.White.copy(alpha = 0.17f))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(label, fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.SemiBold)
        MoneyText(fen, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun DayCard(
    day: Long,
    list: List<TransactionEntity>,
    cats: Map<Long, CategoryEntity>,
    onLongPress: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayExpense = list.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
    val daySaved = list.filter { it.type == TxType.SAVE }.sumOf { it.amount } -
        list.filter { it.type == TxType.UNSAVE }.sumOf { it.amount }
    val label = TimeUtil.dayLabel(day)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                TimeUtil.dateText(day) + if (label.isNotBlank()) " · $label" else "",
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                buildString {
                    append("支出 ¥${Money.txt(dayExpense)}")
                    if (daySaved > 0L) append(" · 存入 ¥${Money.txt(daySaved)}")
                    if (daySaved < 0L) append(" · 取出 ¥${Money.txt(-daySaved)}")
                },
                fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        list.forEachIndexed { i, t ->
            if (i > 0) HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            BillRow(t, cats[t.categoryId], onLongPress)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BillRow(t: TransactionEntity, cat: CategoryEntity?, onLongPress: (TransactionEntity) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = { onLongPress(t) })
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(cat?.emoji ?: "📄", cat?.colorIndex ?: 10)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cat?.name ?: "已删分类", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                if (t.type == TxType.SAVE || t.type == TxType.UNSAVE) SaveTag()
            }
            val timeStr = TimeUtil.timeText(t.createdAt)
            val sub = when {
                t.note.isNotBlank() && t.source != TxSource.MANUAL -> "${t.note} · ${t.source}"
                t.note.isNotBlank() -> t.note
                t.source != TxSource.MANUAL -> t.source
                else -> ""
            }
            val full = when {
                timeStr.isNotEmpty() && sub.isNotEmpty() -> "$timeStr · $sub"
                timeStr.isNotEmpty() -> timeStr
                else -> sub
            }
            if (full.isNotBlank()) {
                Text(
                    full, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val isPlus = t.type == TxType.INCOME || t.type == TxType.UNSAVE
        Text(
            (if (isPlus) "+" else "-") + "¥" + Money.txt(t.amount),
            fontSize = 15.sp, fontWeight = FontWeight.Bold,
            color = if (isPlus) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun SaveTag() {
    val dark = appIsDark()
    Box(
        Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(paletteContainer(9, dark))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        // 浅色用深绿、深色用亮薄荷，保证小字号可读性
        Text(
            "存钱", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
            color = if (dark) Color(0xFF3BD08F) else Color(0xFF0C6B45),
        )
    }
}
