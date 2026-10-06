package com.maibill.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maibill.app.ui.theme.heroGradient
import com.maibill.app.util.Money

/**
 * 金额输入状态：数字/小数点/＋－ 连算/退格
 * 金额以「分」计算，避免浮点误差
 */
class AmountInput {
    var text by mutableStateOf("")
        private set
    var accFen by mutableStateOf<Long?>(null)
        private set
    var op by mutableStateOf<Char?>(null)
        private set

    fun onKey(k: String) {
        when (k) {
            "del" -> {
                when {
                    text.isNotEmpty() -> text = text.dropLast(1)
                    // 回退运算符时把累计值还原为可编辑文本，保证"所见即所存"
                    op != null -> {
                        op = null
                        text = accFen?.let { Money.plain(it) } ?: ""
                        accFen = null
                    }
                    else -> accFen = null
                }
            }
            "+", "-" -> {
                val cur = Money.parseFen(text)
                if (cur != null) {
                    val acc = accFen
                    // 先折叠上一步运算，再挂起新运算符（否则 5＋3＋7 会丢中间项）
                    accFen = when {
                        acc == null -> cur
                        op == '-' -> acc - cur
                        else -> acc + cur
                    }
                    text = ""
                }
                if (accFen != null) op = k[0]
            }
            "." -> if (!text.contains('.')) text = if (text.isEmpty()) "0." else "$text."
            else -> {
                val intDigits = text.substringBefore('.').length
                val decDigits = text.substringAfter('.', "").length
                if (intDigits < 9 && decDigits < 2) {
                    text = if (text == "0") k else text + k
                }
            }
        }
    }

    /** 当前应保存的金额（分），null 表示无效（含结果 ≤0） */
    fun result(): Long? {
        val cur = Money.parseFen(text)
        val a = accFen
        val o = op
        return when {
            a != null && o != null && cur != null -> (if (o == '+') a + cur else a - cur).takeIf { it > 0 }
            a != null && o != null -> a.takeIf { it > 0 }
            else -> cur
        }
    }

    /** 连算提示，如 "5.00 ＋"（符号与键盘按键一致使用全角） */
    fun hint(): String? {
        val a = accFen ?: return null
        val o = op ?: return null
        val sym = if (o == '+') "＋" else "－"
        return "${Money.txt(a)} $sym"
    }

    /** 自动记账预填：直接以「分」设置金额 */
    fun prefill(fen: Long) {
        clear()
        text = Money.plain(fen)
    }

    fun clear() {
        text = ""
        accFen = null
        op = null
    }
}

/** 自定义数字键盘（navBarInset=false 用于弹窗内，避免多余空隙） */
@Composable
fun AmountKeypad(
    onKey: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    keyHeight: Dp = 52.dp,
    saveLabel: String = "保存",
    navBarInset: Boolean = true,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (navBarInset) Modifier.navigationBarsPadding() else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            KeyCap("7", 1f, keyHeight, onKey)
            KeyCap("8", 1f, keyHeight, onKey)
            KeyCap("9", 1f, keyHeight, onKey)
            KeyCap("del", 1f, keyHeight, onKey)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            KeyCap("4", 1f, keyHeight, onKey)
            KeyCap("5", 1f, keyHeight, onKey)
            KeyCap("6", 1f, keyHeight, onKey)
            KeyCap("+", 1f, keyHeight, onKey)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            KeyCap("1", 1f, keyHeight, onKey)
            KeyCap("2", 1f, keyHeight, onKey)
            KeyCap("3", 1f, keyHeight, onKey)
            KeyCap("-", 1f, keyHeight, onKey)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            KeyCap(".", 1f, keyHeight, onKey)
            KeyCap("0", 1f, keyHeight, onKey)
            SaveCap(2f, keyHeight, saveLabel, onSave)
        }
    }
}

@Composable
private fun RowScope.KeyCap(k: String, weight: Float, keyHeight: Dp, onKey: (String) -> Unit) {
    Box(
        Modifier
            .weight(weight)
            .height(keyHeight)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onKey(k) },
        contentAlignment = Alignment.Center,
    ) {
        val label = when (k) {
            "del" -> "⌫"
            "+" -> "＋"
            "-" -> "－"
            else -> k
        }
        Text(
            label,
            fontSize = 21.sp,
            fontWeight = if (k == "+" || k == "-") FontWeight.ExtraBold else FontWeight.SemiBold,
            color = when {
                k == "+" || k == "-" -> MaterialTheme.colorScheme.primary
                k == "del" -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun RowScope.SaveCap(weight: Float, keyHeight: Dp, label: String, onSave: () -> Unit) {
    Box(
        Modifier
            .weight(weight)
            .height(keyHeight)
            .shadow(6.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(heroGradient())
            .clickable(onClick = onSave),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
    }
}
