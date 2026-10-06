package com.maibill.app.util

import java.math.BigDecimal
import java.text.DecimalFormat
import java.util.Locale

object Money {
    private val fmt2 = DecimalFormat("#,##0.00")

    /** 分 → "12,847.64" */
    fun txt(fen: Long): String = fmt2.format(fen / 100.0)

    /** 分 → "12,847"（不带小数） */
    fun txt0(fen: Long): String = String.format(Locale.CHINA, "%,.0f", fen / 100.0)

    /** 分 → "12847.64"（CSV 用，不带千分位） */
    fun plain(fen: Long): String = BigDecimal(fen).movePointLeft(2).toPlainString()

    /** 键盘输入串 → 分；非法或 ≤0 返回 null */
    fun parseFen(input: String): Long? {
        if (input.isEmpty() || input == "." || input == "-") return null
        return try {
            val fen = BigDecimal(input).movePointRight(2).longValueExact()
            if (fen > 0) fen else null
        } catch (e: Exception) {
            null
        }
    }
}
