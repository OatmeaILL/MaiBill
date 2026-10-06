package com.maibill.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.maibill.app.data.CatTotal
import com.maibill.app.data.CategoryEntity
import com.maibill.app.data.Repository
import com.maibill.app.data.SettingKeys
import com.maibill.app.data.TxType
import com.maibill.app.ui.components.BudgetDialog
import com.maibill.app.ui.components.MoneyText
import com.maibill.app.ui.components.MutsumiEmpty
import com.maibill.app.ui.components.StatBar
import com.maibill.app.ui.components.TitleMonthBar
import com.maibill.app.ui.components.secondaryCard
import com.maibill.app.ui.theme.MaiColors
import com.maibill.app.ui.theme.appIsDark
import com.maibill.app.ui.theme.paletteBase
import com.maibill.app.ui.theme.rankColor
import com.maibill.app.util.Money
import com.maibill.app.util.TimeUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StatsViewModel : ViewModel() {
    data class Seg(val name: String, val emoji: String, val colorIndex: Int, val total: Long, val frac: Float)
    data class Ui(
        val month: YearMonth,
        val expense: Long,
        val income: Long,
        val saved: Long,
        val segs: List<Seg>,
        val daily: List<Long>,
        val budgetFen: Long,
        val prevExpense: Long,
    )

    private data class P1(
        val expense: Long,
        val catTotals: List<CatTotal>,
        val daily: Map<Long, Long>,
        val cats: List<CategoryEntity>,
        val budgetFen: Long,
    )

    val month = MutableStateFlow(YearMonth.now())
    private val repo = Repository()

    val data: StateFlow<Ui?> = month.flatMapLatest { m ->
        val range = TimeUtil.monthRange(m)
        val inner = combine(
            repo.monthSum(TxType.EXPENSE, range.first, range.last),
            repo.categoryTotals(range.first, range.last),
            repo.dailyTotals(range.first, range.last),
            repo.categories(),
            repo.stringFlow(SettingKeys.MONTHLY_BUDGET),
        ) { exp, ct, daily, cats, budget ->
            P1(exp, ct, daily.associate { it.day to it.total }, cats, budget?.toLongOrNull() ?: 0L)
        }
        val prev = m.minusMonths(1)
        val prevDays = if (m == YearMonth.now()) minOf(LocalDate.now().dayOfMonth, prev.lengthOfMonth()) else prev.lengthOfMonth()
        val prevRange = prev.atDay(1).toEpochDay()..prev.atDay(prevDays).toEpochDay()
        combine(
            inner,
            repo.monthSum(TxType.INCOME, range.first, range.last),
            repo.netSavedInMonth(range.first, range.last),
            repo.monthSum(TxType.EXPENSE, prevRange.first, prevRange.last),
        ) { p, income, saved, prevExpense ->
            val sorted = p.catTotals.sortedByDescending { it.total }
            val catMap = p.cats.associateBy { it.id }
            val segs = sorted.take(5).map { ct ->
                val c = catMap[ct.categoryId]
                Seg(c?.name ?: "已删分类", c?.emoji ?: "📦", c?.colorIndex ?: 10, ct.total, segFrac(ct.total, p.expense))
            }
            val rest = sorted.drop(5).sumOf { it.total }
            val segsFull = if (rest > 0) segs + Seg("其他", "📦", 10, rest, segFrac(rest, p.expense)) else segs
            val dailyList = (1..m.lengthOfMonth()).map { d -> p.daily[m.atDay(d).toEpochDay()] ?: 0L }
            Ui(m, p.expense, income, saved, segsFull, dailyList, p.budgetFen, prevExpense)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun segFrac(total: Long, expense: Long): Float =
        if (expense > 0) total.toFloat() / expense else 0f

    fun prev() { month.value = month.value.minusMonths(1) }
    fun next() { if (month.value < YearMonth.now()) month.value = month.value.plusMonths(1) }
    fun setBudget(fen: Long) { viewModelScope.launch { repo.setSetting(SettingKeys.MONTHLY_BUDGET, fen.toString()) } }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory { initializer { StatsViewModel() } }
    }
}

@Composable
fun StatsScreen(vm: StatsViewModel = viewModel(factory = StatsViewModel.Factory)) {
    val ui by vm.data.collectAsState()
    var showBudget by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        TitleMonthBar("图表", ui?.month, onChange = { m -> if (m < (ui?.month ?: YearMonth.now())) vm.prev() else vm.next() })

        ui?.let { u ->
            OverviewCard(u, Modifier.padding(horizontal = 16.dp))
            BudgetCard(u, onClick = { showBudget = true }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            CompareCard(u, Modifier.padding(horizontal = 16.dp))
            DonutCard(u, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            TrendCard(u, modifier = Modifier.padding(horizontal = 16.dp))
        }
        Spacer(Modifier.height(120.dp))
    }

    val cur = ui
    if (showBudget && cur != null) {
        BudgetDialog(currentFen = cur.budgetFen, onSave = { vm.setBudget(it) }, onDismiss = { showBudget = false })
    }
}

@Composable
private fun CompareCard(u: StatsViewModel.Ui, modifier: Modifier = Modifier) {
    val diff = u.expense - u.prevExpense
    Column(
        modifier
            .fillMaxWidth()
            .secondaryCard()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("对比上月", fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            Text(
                "上月同期 ¥${Money.txt(u.prevExpense)}",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(10.dp))
        if (u.prevExpense == 0L) {
            Text(
                "「……上次的事，不记得了。」",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold,
            )
        } else {
            val pct = (diff * 100 / u.prevExpense).toInt()
            Text(
                if (diff >= 0) "多花 ¥${Money.txt(diff)}（+$pct%）" else "省下 ¥${Money.txt(-diff)}（$pct%）",
                fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                color = if (diff >= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun OverviewCard(u: StatsViewModel.Ui, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .secondaryCard()
            .padding(vertical = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            OverviewCell("支出", u.expense, Color(0xFFFF6B5E), Modifier.weight(1f))
            OverviewCell("收入", u.income, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            OverviewCell("结余", u.income - u.expense, Color(0xFF98A29B), Modifier.weight(1f))
        }
        if (u.saved != 0L) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (u.saved > 0) "本月净存入 " else "本月净取出 ",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold,
                )
                MoneyText(if (u.saved > 0) u.saved else -u.saved, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Text("  · 不计入支出", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun OverviewCell(label: String, fen: Long, dotColor: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(3.dp)).background(dotColor))
            Spacer(Modifier.width(5.dp))
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        }
        MoneyText(fen, fontSize = 16.5.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun BudgetCard(u: StatsViewModel.Ui, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val isCurrent = u.month == YearMonth.now()
    val daysGone = when {
        isCurrent -> LocalDate.now().dayOfMonth
        u.month < YearMonth.now() -> u.month.lengthOfMonth()
        else -> 0
    }
    val usedPct = if (u.budgetFen > 0) ((u.expense * 100 / u.budgetFen).toInt()).coerceAtMost(999) else 0
    val remain = u.budgetFen - u.expense

    Column(
        modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${u.month.monthValue}月预算", fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            Text(
                if (u.budgetFen > 0) "总预算 ¥${Money.txt(u.budgetFen)}" else "点击设置预算",
                fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(12.dp))
        StatBar(progress = if (u.budgetFen > 0) u.expense.toFloat() / u.budgetFen else 0f)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (u.budgetFen > 0) "已用 $usedPct%" else "记录你的每月预算，超支前提醒你",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            if (u.budgetFen > 0) {
                Text(
                    if (remain >= 0) "剩余 ¥${Money.txt(remain)}" else "已超支 ¥${Money.txt(-remain)}",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = if (remain >= 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                )
            }
        }
        if (u.budgetFen > 0 && isCurrent && daysGone > 0) {
            val forecast = if (daysGone > 0) u.expense / daysGone * u.month.lengthOfMonth() else 0L
            val ok = forecast <= u.budgetFen
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    // 超支用琥珀（她眼睛的颜色）而不是刺目的红：仍然是"注意"的语气，不和满屏的绿打架
                    .background(if (ok) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaiColors.amberContainer)
                    .padding(horizontal = 11.dp, vertical = 8.dp)
            ) {
                Text(
                    if (ok) "✓ 按当前节奏，月底约支出 ¥${Money.txt0(forecast)}，在预算内"
                    else "⚠ 按当前节奏，月底约支出 ¥${Money.txt0(forecast)}，将超支 ¥${Money.txt0(forecast - u.budgetFen)}",
                    fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                    color = if (ok) MaterialTheme.colorScheme.primary else MaiColors.amber,
                )
            }
        }
    }
}

@Composable
private fun DonutCard(u: StatsViewModel.Ui, modifier: Modifier = Modifier) {
    val dark = appIsDark()
    Column(
        modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("分类占比", fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            Text("不含储蓄", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (u.expense == 0L) {
            MutsumiEmpty(
                artRes = com.maibill.app.R.drawable.mutsumi_art,
                title = "嗯。还没有支出。",
                sub = "记几笔支出，就会长出图表。",
                compact = true,
                mascotSize = 64.dp,
            )
        } else {
            var played by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(150); played = true }
            val anim by animateFloatAsState(if (played) 1f else 0f, tween(700, easing = FastOutSlowInEasing), label = "donut")

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(148.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(148.dp)) {
                        val stroke = 26.dp.toPx()
                        val inset = stroke / 2 + 1.dp.toPx()
                        var start = -90f
                        // 同色系阶梯：按占比排名取色，深浅交替，第 5 档起用琥珀
                        u.segs.forEachIndexed { i, s ->
                            val sweep = s.frac * 360f * anim
                            if (sweep > 0f) {
                                drawArc(
                                    color = rankColor(i, dark),
                                    startAngle = start,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    topLeft = Offset(inset, inset),
                                    size = Size(size.width - inset * 2, size.height - inset * 2),
                                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                                )
                            }
                            start += s.frac * 360f
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("本月支出", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                        Text("¥${Money.txt0(u.expense)}", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            u.segs.forEachIndexed { i, s ->
                if (i > 0) HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(rankColor(i, dark)))
                    Spacer(Modifier.width(9.dp))
                    Text(s.name, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    MoneyText(s.total, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "${(s.frac * 100).roundToInt()}%",
                        fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(42.dp), textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendCard(u: StatsViewModel.Ui, modifier: Modifier = Modifier) {
    val isCurrent = u.month == YearMonth.now()
    val highlight = if (isCurrent) LocalDate.now().dayOfMonth - 1 else -1
    Column(
        modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("每日趋势", fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            Text("单位 ¥", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        if (u.expense == 0L) {
            EmptyCompact("📈", "嗯。趋势也还没有。", "记几笔支出后再看", compact = true)
        } else {
            TrendBars(u.daily, highlight)
            val n = u.daily.size
            Row(
                Modifier.fillMaxWidth().padding(top = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf(1, n / 4 + 1, n / 2 + 1, n * 3 / 4, n).forEach { d ->
                    Text("$d", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun TrendBars(values: List<Long>, highlight: Int) {
    var played by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(150); played = true }
    val anim by animateFloatAsState(if (played) 1f else 0f, tween(700, easing = FastOutSlowInEasing), label = "trend")
    val maxV = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    // 深浅模式下都用主题主色，未高亮柱降低不透明度
    val primaryColor = MaterialTheme.colorScheme.primary
    val dimColor = primaryColor.copy(alpha = 0.4f)
    Canvas(Modifier.fillMaxWidth().height(104.dp)) {
        val n = values.size
        if (n == 0) return@Canvas
        val gap = 2.5.dp.toPx()
        val bw = (size.width - gap * (n - 1)) / n
        values.forEachIndexed { i, v ->
            if (v <= 0L) return@forEachIndexed
            val h = (v.toFloat() / maxV) * size.height * anim
            drawRoundRect(
                color = if (i == highlight) primaryColor else dimColor,
                topLeft = Offset(i * (bw + gap), size.height - h),
                size = Size(bw, h.coerceAtLeast(4.dp.toPx())),
                cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
            )
        }
    }
}

@Composable
private fun EmptyCompact(emoji: String, title: String, sub: String, compact: Boolean = true) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 26.sp)
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        Text(sub, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
    }
}
