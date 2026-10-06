package com.maibill.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 金额单位一律为「分」（Long），避免浮点误差 */

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val colorIndex: Int,
    val kind: String, // CatKind.EXPENSE / INCOME / SAVING
    val sortOrder: Int = 0,
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // TxType.EXPENSE / INCOME / SAVE / UNSAVE
    val amount: Long, // 恒为正数（分）
    val categoryId: Long,
    val note: String = "",
    val date: Long, // epochDay
    val goalId: Long? = null, // 储蓄记录关联的目标
    val source: String = TxSource.MANUAL, // 账单来源：手动 / 微信支付 / 支付宝
    val deletedAt: Long = 0, // 回收站：0=正常，>0=删除时间戳（30 天后自动清除）
    val createdAt: Long = System.currentTimeMillis(),
)

object TxSource {
    const val MANUAL = "手动"
    const val WECHAT = "微信支付"
    const val ALIPAY = "支付宝"
}

@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val colorIndex: Int,
    val targetFen: Long,
    val deadline: Long? = null, // epochDay，可空
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "settings")
data class SettingsEntity(@PrimaryKey val key: String, val value: String)

object TxType {
    const val EXPENSE = "EXPENSE"
    const val INCOME = "INCOME"
    const val SAVE = "SAVE"
    const val UNSAVE = "UNSAVE"
}

object CatKind {
    const val EXPENSE = "EXPENSE"
    const val INCOME = "INCOME"
    const val SAVING = "SAVING"
}

object SettingKeys {
    const val MONTHLY_BUDGET = "monthly_budget" // 分
}

/** DAO 投影 */
data class CatTotal(val categoryId: Long, val total: Long)
data class DayTotal(val day: Long, val total: Long)
data class GoalSum(val goalId: Long, val total: Long)
data class CatCount(val categoryId: Long, val count: Int)
