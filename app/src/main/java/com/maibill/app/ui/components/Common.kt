package com.maibill.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.maibill.app.ui.theme.MaiColors
import com.maibill.app.ui.theme.appIsDark
import com.maibill.app.ui.theme.heroGradient
import com.maibill.app.ui.theme.paletteBase
import com.maibill.app.ui.theme.paletteContainer
import com.maibill.app.util.Money
import com.maibill.app.util.TimeUtil
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** 空状态提示 */
@Composable
fun EmptyHint(emoji: String, title: String, sub: String, compact: Boolean = false) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 22.dp else 46.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = if (compact) 28.sp else 34.sp)
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
        Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

/** 页标题 + 右侧月份切换（图表页/存钱页用） */
@Composable
fun TitleMonthBar(title: String, month: YearMonth?, onChange: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.weight(1f))
        if (month != null) MonthPill(month, onChange, compact = true)
    }
}

/**
 * 月份切换器：一个胶囊里放 ‹ 年月 ›，两头都能点——比"一个圆形箭头 + 一段文字"更像选择器，
 * 也不会被误认成返回键。到头（最早月 / 已到本月）时对应箭头变灰且不可点。
 */
@Composable
fun MonthPill(month: YearMonth, onChange: (YearMonth) -> Unit, compact: Boolean = false) {
    val earliest = YearMonth.of(2000, 1)
    val canPrev = month > earliest
    val canNext = month < YearMonth.now()
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonthChevron("‹", canPrev) { onChange(month.minusMonths(1)) }
        Text(
            if (compact && month.year == YearMonth.now().year) "${month.monthValue}月"
            else "${month.year}年${month.monthValue}月",
            fontSize = if (compact) 13.sp else 14.5.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 5.dp),
        )
        MonthChevron("›", canNext) { onChange(month.plusMonths(1)) }
    }
}

@Composable
private fun MonthChevron(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        )
    }
}

/** 金额文本：等宽数字 + 滚动动画 */
@Composable
fun MoneyText(
    fen: Long,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = 15.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
) {
    val target = fen.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    val v: Int by animateIntAsState(
        targetValue = target,
        animationSpec = if (animated) tween(450) else tween(0),
        label = "money",
    )
    Text(
        String.format(Locale.CHINA, "%,.2f", v / 100.0),
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        modifier = modifier,
        style = LocalTextStyle.current.merge(TextStyle(fontFeatureSettings = "tnum")),
    )
}

/** 圆角进度条 */
@Composable
fun StatBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaiColors.trackBar,
    height: Dp = 9.dp,
) {
    val p by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(650, easing = FastOutSlowInEasing),
        label = "bar",
    )
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(p)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
    }
}

/**
 * 次卡：不投影、只描边。同一屏里主卡留影、次卡描边，一眼能看出先看哪张；
 * 主卡继续用 `.shadow(6.dp, shape) + .clip(shape) + .background(surface)` 那套。
 */
@Composable
fun Modifier.secondaryCard(shape: Shape = RoundedCornerShape(20.dp)): Modifier =
    this.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surface)

/** 彩色圆底分类图标 */
@Composable
fun CategoryIcon(
    emoji: String,
    colorIndex: Int,
    size: Dp = 40.dp,
    font: TextUnit = 19.sp,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val dark = appIsDark()
    val base = paletteBase(colorIndex)
    val m = Modifier
        .size(size)
        .clip(CircleShape)
        .background(paletteContainer(colorIndex, dark))
        .then(
            if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                .border(2.5.dp, base, CircleShape) else Modifier
        )
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    Box(m, contentAlignment = Alignment.Center) {
        Text(emoji, fontSize = font)
    }
}

/** 分区标题行 */
@Composable
fun SectionRow(title: String, tail: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.weight(1f))
        if (tail != null) Text(tail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 月份切换头（明细页顶部） */
@Composable
fun MonthHeader(month: YearMonth, onChange: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonthPill(month, onChange)
    }
}

/** 轻量「输入框样式」行内输入（用于弹窗） */
@Composable
fun SimpleField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    number: Boolean = false,
    maxLength: Int = 20,
) {
    TextField(
        value = value,
        onValueChange = { s ->
            if (s.length <= maxLength && (s.isEmpty() || s.last() != ' ')) onValueChange(s)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        shape = RoundedCornerShape(12.dp),
        textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** 双段选择（支出/收入、存入/取出） */
@Composable
fun TwoSeg(options: List<String>, current: Int, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == current
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onChange(i) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 13.5.sp,
                    fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 通用确认弹窗 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.ExtraBold) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text("确定", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 预算设置弹窗（单位：元，支持两位小数回显） */
@Composable
fun BudgetDialog(currentFen: Long, onSave: (Long) -> Unit, onDismiss: () -> Unit) {
    var text by remember {
        mutableStateOf(if (currentFen > 0) Money.plain(currentFen) else "")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("每月总预算", fontWeight = FontWeight.ExtraBold) },
        text = {
            SimpleField(
                value = text,
                onValueChange = { s ->
                    if (s.length <= 12 && s.count { it == '.' } <= 1 && s.all { it.isDigit() || it == '.' }) text = s
                },
                label = "每月预算（元）",
                number = true,
                maxLength = 12,
            )
        },
        confirmButton = {
            val parsed = Money.parseFen(text)
            TextButton(
                onClick = {
                    // 清空输入 = 清除预算(0)；无效输入按钮已禁用
                    onSave(if (text.isBlank()) 0L else (parsed ?: 0L))
                    onDismiss()
                },
                enabled = text.isBlank() || parsed != null,
            ) { Text("保存", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 日期选择弹窗 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = TimeUtil.toPickerMillis(initial),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPick(TimeUtil.fromPickerMillis(state.selectedDateMillis) ?: initial)
            }) { Text("确定", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        DatePicker(state = state)
    }
}

/** 渐变主按钮 */
@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(heroGradient())
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
    }
}

/** 表单网格里的颜色选择 */
@Composable
fun ColorSwatches(selected: Int, onSelect: (Int) -> Unit, count: Int = 11) {
    val dark = appIsDark()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (0 until count).toList().chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { i ->
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(paletteContainer(i, dark))
                            .then(
                                if (selected == i)
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                                        .border(2.5.dp, paletteBase(i), CircleShape)
                                else Modifier
                            )
                            .clickable { onSelect(i) },
                        contentAlignment = Alignment.Center,
                    ) {}
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}
