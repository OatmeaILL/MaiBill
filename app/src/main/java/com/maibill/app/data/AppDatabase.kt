package com.maibill.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction

@Database(
    entities = [CategoryEntity::class, TransactionEntity::class, SavingsGoalEntity::class, SettingsEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun settingsDao(): SettingsDao
}

/** v2：账单新增"来源"列，旧数据默认"手动"，不丢任何记录 */
val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN source TEXT NOT NULL DEFAULT '手动'")
    }
}

/** v3：回收站（软删除标记） */
val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN deletedAt INTEGER NOT NULL DEFAULT 0")
    }
}

/** 进程级单例，Application.onCreate 里初始化 */
object Graph {
    lateinit var db: AppDatabase
        private set
    val categoryDao get() = db.categoryDao()
    val txDao get() = db.transactionDao()
    val goalDao get() = db.savingsGoalDao()
    val settingsDao get() = db.settingsDao()

    fun init(context: Context) {
        db = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "maibill.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }

    suspend fun seedIfEmpty() {
        // 事务保证原子性：进程中途被杀也不会留下"只有一半分类"的残缺种子
        db.withTransaction {
            if (categoryDao.count() > 0) return@withTransaction
            val defaults = listOf(
                Triple("餐饮", "🍜", 0), Triple("购物", "🛍️", 1), Triple("交通", "🚌", 2),
                Triple("娱乐", "🎮", 3), Triple("居家", "🏠", 4), Triple("医疗", "💊", 5),
                Triple("学习", "📚", 6), Triple("人情", "🎁", 7), Triple("通讯", "📱", 8),
                Triple("其他", "📎", 10),
            ).mapIndexed { i, (name, emoji, color) ->
                CategoryEntity(name = name, emoji = emoji, colorIndex = color, kind = CatKind.EXPENSE, sortOrder = i)
            } + listOf(
                CategoryEntity(name = "工资", emoji = "💼", colorIndex = 9, kind = CatKind.INCOME, sortOrder = 0),
                CategoryEntity(name = "理财", emoji = "📈", colorIndex = 2, kind = CatKind.INCOME, sortOrder = 1),
                CategoryEntity(name = "红包", emoji = "🧧", colorIndex = 1, kind = CatKind.INCOME, sortOrder = 2),
                CategoryEntity(name = "其他收入", emoji = "➕", colorIndex = 10, kind = CatKind.INCOME, sortOrder = 3),
                CategoryEntity(name = "储蓄", emoji = "💰", colorIndex = 9, kind = CatKind.SAVING, sortOrder = 0),
            )
            defaults.forEach { categoryDao.insert(it) }
        }
    }
}
