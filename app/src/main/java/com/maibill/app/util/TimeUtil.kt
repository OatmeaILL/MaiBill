package com.maibill.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

object TimeUtil {
    fun monthRange(m: YearMonth): LongRange =
        m.atDay(1).toEpochDay()..m.atEndOfMonth().toEpochDay()

    fun dayLabel(epochDay: Long, today: LocalDate = LocalDate.now()): String {
        val d = LocalDate.ofEpochDay(epochDay)
        return when (d) {
            today -> "今天"
            today.minusDays(1) -> "昨天"
            else -> ""
        }
    }

    /** "9月30日" */
    fun dateText(d: LocalDate): String = "${d.monthValue}月${d.dayOfMonth}日"

    fun dateText(epochDay: Long): String = dateText(LocalDate.ofEpochDay(epochDay))

    fun fullText(d: LocalDate): String = "${d.year}年${d.monthValue}月${d.dayOfMonth}日"

    /** 账单行的时刻 "19:43"（createdAt 为毫秒时间戳） */
    fun timeText(createdAt: Long): String =
        if (createdAt <= 0) ""
        else Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

    /** CSV 用的完整时刻 "19:43:25" */
    fun timeTextFull(createdAt: Long): String =
        if (createdAt <= 0) ""
        else Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))

    fun fromPickerMillis(millis: Long?): LocalDate? =
        millis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }

    fun toPickerMillis(d: LocalDate): Long = d.toEpochDay() * 86_400_000L
}
