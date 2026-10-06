package com.maibill.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** 统一的数据入口，UI 层只跟它打交道 */
class Repository {
    // ---- 账单 ----
    fun monthTx(from: Long, to: Long): Flow<List<TransactionEntity>> = Graph.txDao.monthTx(from, to)
    fun recentSavings(n: Int = 30): Flow<List<TransactionEntity>> = Graph.txDao.recentSavings(n)
    fun categoryTotals(from: Long, to: Long): Flow<List<CatTotal>> = Graph.txDao.categoryTotals(from, to)
    fun dailyTotals(from: Long, to: Long): Flow<List<DayTotal>> = Graph.txDao.dailyTotals(from, to)
    fun goalSums(): Flow<List<GoalSum>> = Graph.txDao.goalSums()
    fun savedInMonth(from: Long, to: Long): Flow<Long> = Graph.txDao.savedInMonth(from, to)
    fun netSavedInMonth(from: Long, to: Long): Flow<Long> = Graph.txDao.netSavedInMonth(from, to)
    fun monthSum(type: String, from: Long, to: Long): Flow<Long> = Graph.txDao.monthSum(type, from, to)

    suspend fun allTxOnce(): List<TransactionEntity> = Graph.txDao.allOnce()
    suspend fun txById(id: Long): TransactionEntity? = Graph.txDao.byId(id)
    suspend fun updateTx(t: TransactionEntity) = Graph.txDao.update(t)
    suspend fun countByCategory(id: Long) = Graph.txDao.countByCategory(id)
    suspend fun countByGoal(id: Long) = Graph.txDao.countByGoal(id)
    suspend fun savingCategory(): CategoryEntity? = Graph.categoryDao.savingCategory()
    fun categoryCounts(): Flow<List<CatCount>> = Graph.txDao.categoryCounts()

    suspend fun addTx(t: TransactionEntity) = Graph.txDao.insert(t)

    /** 删除进回收站（30 天后自动清除） */
    suspend fun deleteTx(t: TransactionEntity) = Graph.txDao.softDelete(t.id, System.currentTimeMillis())
    suspend fun restoreTx(t: TransactionEntity) = Graph.txDao.restore(t.id)
    suspend fun purgeTx(t: TransactionEntity) = Graph.txDao.purge(t.id)
    suspend fun purgeOldTx() = Graph.txDao.purgeOld(System.currentTimeMillis() - 30L * 24 * 3600 * 1000)
    fun deletedTx(): Flow<List<TransactionEntity>> = Graph.txDao.deletedTx()

    // ---- 分类 ----
    fun categories(): Flow<List<CategoryEntity>> = Graph.categoryDao.all()
    suspend fun categoryCount() = Graph.categoryDao.count()
    suspend fun addCategory(c: CategoryEntity) = Graph.categoryDao.insert(c)
    suspend fun updateCategory(c: CategoryEntity) = Graph.categoryDao.update(c)
    suspend fun deleteCategory(c: CategoryEntity) = Graph.categoryDao.delete(c)

    // ---- 存钱目标 ----
    fun goals(): Flow<List<SavingsGoalEntity>> = Graph.goalDao.all()
    suspend fun goalById(id: Long) = Graph.goalDao.byId(id)
    suspend fun addGoal(g: SavingsGoalEntity) = Graph.goalDao.insert(g)
    suspend fun updateGoal(g: SavingsGoalEntity) = Graph.goalDao.update(g)
    suspend fun deleteGoal(g: SavingsGoalEntity) = Graph.goalDao.delete(g)

    /** 存入/取出：同时生成一笔「储蓄」账单；储蓄分类丢失时自动补建，避免静默失效 */
    suspend fun deposit(goal: SavingsGoalEntity, save: Boolean, fen: Long) {
        val cat = savingCategory() ?: run {
            val entity = CategoryEntity(name = "储蓄", emoji = "💰", colorIndex = 9, kind = CatKind.SAVING, sortOrder = 0)
            val id = Graph.categoryDao.insert(entity)
            entity.copy(id = id)
        }
        val type = if (save) TxType.SAVE else TxType.UNSAVE
        val verb = if (save) "存入" else "取出"
        addTx(
            TransactionEntity(
                type = type, amount = fen, categoryId = cat.id,
                note = "$verb「${goal.name}」", date = java.time.LocalDate.now().toEpochDay(), goalId = goal.id,
            )
        )
    }

    // ---- 设置 ----
    fun stringFlow(key: String): Flow<String?> = Graph.settingsDao.stringFlow(key)
    suspend fun setSetting(key: String, value: String) = Graph.settingsDao.put(SettingsEntity(key, value))

    /** 导出全部账单为 CSV 文本（含 BOM 由调用方处理） */
    suspend fun exportCsv(): String {
        val txs = allTxOnce()
        val catMap = HashMap<Long, CategoryEntity>()
        Graph.categoryDao.all().first().forEach { catMap[it.id] = it }
        val goalMap = HashMap<Long, SavingsGoalEntity>()
        Graph.goalDao.all().first().forEach { goalMap[it.id] = it }
        val typeLabel = mapOf(
            TxType.EXPENSE to "支出", TxType.INCOME to "收入", TxType.SAVE to "存入", TxType.UNSAVE to "取出",
        )
        val sb = StringBuilder("日期,时间,类型,分类,来源,金额(元),备注,存钱目标\n")
        val df = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val tf = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
        for (t in txs) {
            val d = java.time.LocalDate.ofEpochDay(t.date).format(df)
            val time = if (t.createdAt > 0)
                java.time.Instant.ofEpochMilli(t.createdAt).atZone(java.time.ZoneId.systemDefault()).format(tf)
            else ""
            val esc = { s: String -> "\"" + s.replace("\"", "\"\"") + "\"" }
            val cat = esc(catMap[t.categoryId]?.name ?: "")
            val goal = esc(t.goalId?.let { goalMap[it]?.name } ?: "")
            val amount = java.math.BigDecimal(t.amount).movePointLeft(2).toPlainString()
            val note = esc(t.note)
            val src = esc(t.source)
            sb.append("$d,$time,${typeLabel[t.type] ?: t.type},$cat,$src,$amount,$note,$goal\n")
        }
        return sb.toString()
    }
}
