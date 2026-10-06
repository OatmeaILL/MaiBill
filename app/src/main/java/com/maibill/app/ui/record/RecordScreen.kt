package com.maibill.app.ui.record

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.maibill.app.data.CategoryEntity
import com.maibill.app.data.Graph
import com.maibill.app.data.Repository
import com.maibill.app.data.TransactionEntity
import com.maibill.app.data.TxSource
import com.maibill.app.data.TxType
import com.maibill.app.auto.PendingAutoRecord
import com.maibill.app.ui.components.AmountInput
import com.maibill.app.ui.components.AmountKeypad
import com.maibill.app.ui.components.CategoryIcon
import com.maibill.app.ui.components.DatePickDialog
import com.maibill.app.ui.components.TwoSeg
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.maibill.app.ui.theme.appIsDark
import com.maibill.app.ui.theme.paletteBase
import com.maibill.app.ui.theme.paletteContainer
import com.maibill.app.util.TimeUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RecordViewModel(private val editId: Long? = null) : ViewModel() {
    val type = MutableStateFlow(TxType.EXPENSE)

    val cats: StateFlow<List<CategoryEntity>> = type.flatMapLatest { kind ->
        Graph.categoryDao.byKind(kind)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedCatId = MutableStateFlow<Long?>(null)
    val note = MutableStateFlow("")
    val date = MutableStateFlow(LocalDate.now().toEpochDay())
    val saveAgain = MutableStateFlow(false)
    val savedEvent = MutableStateFlow(0)
    val saveFailedEvent = MutableStateFlow(0)
    val input = AmountInput()
    private var saving = false
    val isEditing = editId != null
    val source = MutableStateFlow(TxSource.MANUAL)
    private var editingTx: TransactionEntity? = null // 编辑时保留原始 createdAt/goalId

    init {
        if (editId != null) {
            // 编辑模式：载入已有账单，金额/分类/备注/日期/收支类型全部可改
            viewModelScope.launch {
                val t = Repository().txById(editId) ?: return@launch
                editingTx = t
                type.value = if (t.type == TxType.INCOME) TxType.INCOME else TxType.EXPENSE
                note.value = t.note
                date.value = t.date
                source.value = t.source
                input.prefill(t.amount)
                if (cats.value.any { it.id == t.categoryId }) {
                    selectedCatId.value = t.categoryId
                }
            }
        } else if (PendingAutoRecord.request > 0L) {
            // 自动记账气泡带入的预填（金额 + 来源），消费后清除
            PendingAutoRecord.amountFen?.let { input.prefill(it) }
            PendingAutoRecord.source?.let { source.value = it }
            PendingAutoRecord.clear()
        }
        viewModelScope.launch {
            cats.collect { list ->
                val cur = selectedCatId.value
                if (cur == null || list.none { it.id == cur }) {
                    selectedCatId.value = list.firstOrNull()?.id
                }
            }
        }
    }

    fun setType(t: String) { if (t != type.value) type.value = t }

    fun save() {
        if (saving) return
        val fen = input.result() ?: return
        if (fen <= 0) return
        val catId = selectedCatId.value ?: return
        saving = true
        viewModelScope.launch {
            val ok = try {
                val t = if (editingTx != null) {
                    // 编辑：copy 保留原始 id/createdAt/goalId
                    editingTx!!.copy(
                        type = type.value, amount = fen, categoryId = catId,
                        note = note.value.trim(), date = date.value, source = source.value,
                    )
                } else {
                    TransactionEntity(
                        type = type.value, amount = fen, categoryId = catId,
                        note = note.value.trim(), date = date.value, source = source.value,
                    )
                }
                if (editId != null) Repository().updateTx(t) else Repository().addTx(t)
                true
            } catch (e: Exception) {
                false // 磁盘满/数据库异常等，不让进程崩溃
            } finally {
                saving = false
            }
            if (!ok) {
                saveFailedEvent.value++
                return@launch
            }
            input.clear()
            note.value = ""
            if (!isEditing && saveAgain.value) {
                date.value = LocalDate.now().toEpochDay()
            } else {
                savedEvent.value++
            }
        }
    }

    companion object {
        fun factory(editId: Long?): ViewModelProvider.Factory = viewModelFactory {
            initializer { RecordViewModel(editId) }
        }
    }
}

@Composable
fun RecordScreen(
    editId: Long? = null,
    vm: RecordViewModel = viewModel(factory = remember(editId) { RecordViewModel.factory(editId) }),
    onClose: () -> Unit,
) {
    val type by vm.type.collectAsState()
    val cats by vm.cats.collectAsState()
    val selId by vm.selectedCatId.collectAsState()
    val note by vm.note.collectAsState()
    val dateE by vm.date.collectAsState()
    val again by vm.saveAgain.collectAsState()
    val savedEvent by vm.savedEvent.collectAsState()
    val saveFailedEvent by vm.saveFailedEvent.collectAsState()
    var showCheck by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    fun trySave() {
        val r = vm.input.result()
        if (r == null || r <= 0L) {
            android.widget.Toast.makeText(ctx, "请输入有效金额", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (vm.selectedCatId.value == null) {
            // 该类型下分类被删光时静默失败会让人以为已保存
            android.widget.Toast.makeText(ctx, "请先到「我的-分类管理」添加分类", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        vm.save()
    }

    LaunchedEffect(savedEvent) {
        if (savedEvent > 0) {
            showCheck = true
            delay(1150)
            showCheck = false
            onClose()
        }
    }
    LaunchedEffect(saveFailedEvent) {
        if (saveFailedEvent > 0) {
            android.widget.Toast.makeText(ctx, "保存失败，请重试", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // 顶栏
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Text("✕", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(
                    if (vm.isEditing) "修改账单" else "记一笔",
                    fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Text(
                    "保存", fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { trySave() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }

            // 支出 / 收入
            TwoSeg(
                options = listOf("支出", "收入"),
                current = if (type == TxType.INCOME) 1 else 0,
                onChange = { vm.setType(if (it == 1) TxType.INCOME else TxType.EXPENSE) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            // 金额显示
            Column(
                Modifier.fillMaxWidth().padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                vm.input.hint()?.let { hint ->
                    Text(hint, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("¥", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        vm.input.text.ifEmpty { "0" },
                        fontSize = 54.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                    Caret()
                }
            }

            // 分类选中 + 备注
            val selCat = cats.firstOrNull { it.id == selId }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                selCat?.let { c -> SelectedCatChip(c) }
                BasicTextField(
                    value = note,
                    onValueChange = { if (it.length <= 30) vm.note.value = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            if (note.isEmpty()) Text("📝 备注（选填）", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            inner()
                        }
                    },
                )
            }

            // 日期 + 再记开关
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    val label = when (dateE) {
                        LocalDate.now().toEpochDay() -> "📅 今天"
                        LocalDate.now().minusDays(1).toEpochDay() -> "📅 昨天"
                        else -> "📅 ${TimeUtil.dateText(dateE)}"
                    }
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!vm.isEditing) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (again) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                            .clickable { vm.saveAgain.value = !again }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Text(
                            "🔁 保存并再记",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (again) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 分类网格
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                cats.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { c ->
                            val on = c.id == selId
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { vm.selectedCatId.value = c.id }
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                CategoryIcon(c.emoji, c.colorIndex, size = 44.dp, font = 21.sp, selected = on)
                                Text(
                                    c.name, fontSize = 11.5.sp,
                                    fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = if (on) paletteBase(c.colorIndex) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                        repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            Spacer(Modifier.height(310.dp))
        }

        // 底部键盘（不随内容滚动）
        AmountKeypad(
            onKey = { vm.input.onKey(it) },
            onSave = { trySave() },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // 记账成功打勾反馈
        if (showCheck) CheckOverlay()
    }

    if (showDatePicker) {
        DatePickDialog(
            initial = LocalDate.ofEpochDay(dateE),
            onPick = { vm.date.value = it.toEpochDay(); showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }
}

@Composable
private fun SelectedCatChip(c: CategoryEntity) {
    val dark = appIsDark()
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(paletteContainer(c.colorIndex, dark))
            .padding(horizontal = 11.dp, vertical = 9.dp)
    ) {
        Text("${c.emoji} ${c.name}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = paletteBase(c.colorIndex))
    }
}

@Composable
private fun Caret() {
    val inf = rememberInfiniteTransition(label = "caret")
    val a by inf.animateFloat(
        initialValue = 1f, targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
        label = "caretAlpha",
    )
    Box(
        Modifier
            .padding(start = 5.dp)
            .size(3.dp, 46.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = a))
    )
}

@Composable
private fun CheckOverlay() {
    var played by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { played = true }
    val scale by animateFloatAsState(
        targetValue = if (played) 1f else 0.5f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "check",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(104.dp)
                    .scale(scale)
                    .shadow(18.dp, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(com.maibill.app.R.drawable.mutsumi_chibi_art),
                    contentDescription = "若叶睦",
                    modifier = Modifier.fillMaxSize(0.94f),
                )
            }
            Text(
                "嗯。", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
