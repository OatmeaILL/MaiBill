package com.maibill.app.ui.categories

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.maibill.app.data.CatKind
import com.maibill.app.data.CategoryEntity
import com.maibill.app.data.Repository
import com.maibill.app.ui.components.CategoryIcon
import com.maibill.app.ui.components.ColorSwatches
import com.maibill.app.ui.components.ConfirmDialog
import com.maibill.app.ui.components.SectionRow
import com.maibill.app.ui.components.SimpleField
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriesViewModel : ViewModel() {
    data class Ui(val cats: List<CategoryEntity>, val usage: Map<Long, Int>)

    private val repo = Repository()

    val data: StateFlow<Ui> = combine(repo.categories(), repo.categoryCounts()) { cats, counts ->
        Ui(cats, counts.associate { it.categoryId to it.count })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Ui(emptyList(), emptyMap()))

    fun save(c: CategoryEntity) {
        viewModelScope.launch { if (c.id == 0L) repo.addCategory(c) else repo.updateCategory(c) }
    }

    fun delete(c: CategoryEntity, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val n = repo.countByCategory(c.id)
            if (n > 0) onResult(false, "该分类下有 $n 笔账单，无法删除")
            else { repo.deleteCategory(c); onResult(true, "已删除「${c.name}」") }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory { initializer { CategoriesViewModel() } }
    }
}

@Composable
fun CategoriesScreen(
    vm: CategoriesViewModel = viewModel(factory = CategoriesViewModel.Factory),
    onBack: () -> Unit,
) {
    val ui by vm.data.collectAsState()
    val ctx = LocalContext.current
    // null = 关闭；id==0 = 新增（kind 决定归属）
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<CategoryEntity?>(null) }

    fun toast(msg: String) = android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
    ) {
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
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Text("‹", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(
                "分类管理", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 10.dp),
            )
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { editing = CategoryEntity(name = "", emoji = "🏷️", colorIndex = 0, kind = CatKind.EXPENSE) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("＋ 支出", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { editing = CategoryEntity(name = "", emoji = "🏷️", colorIndex = 0, kind = CatKind.INCOME) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("＋ 收入", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        ui?.let { u ->
            LazyColumn(Modifier.fillMaxSize()) {
                item { SectionRow("支出分类", tail = "点击编辑", modifier = Modifier.padding(horizontal = 10.dp)) }
                val exp = u.cats.filter { it.kind == CatKind.EXPENSE }
                exp.forEach { c ->
                    item(key = "e${c.id}") { CatRow(c, u.usage[c.id] ?: 0) { editing = c } }
                }
                item {
                    SectionRow("收入分类", tail = null, modifier = Modifier.padding(horizontal = 10.dp).padding(top = 6.dp))
                }
                val inc = u.cats.filter { it.kind == CatKind.INCOME }
                inc.forEach { c ->
                    item(key = "i${c.id}") { CatRow(c, u.usage[c.id] ?: 0) { editing = c } }
                }
                item {
                    SectionRow("储蓄（系统内置）", tail = null, modifier = Modifier.padding(horizontal = 10.dp).padding(top = 6.dp))
                }
                u.cats.filter { it.kind == CatKind.SAVING }.forEach { c ->
                    item(key = "s${c.id}") { CatRow(c, u.usage[c.id] ?: 0, onClick = null) }
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }
    }

    editing?.let { c ->
        CategoryDialog(
            initial = c,
            onSave = { vm.save(it); editing = null },
            onDelete = if (c.id != 0L && c.kind != CatKind.SAVING) {
                {
                    editing = null
                    deleteTarget = c
                }
            } else null,
            onDismiss = { editing = null },
        )
    }

    deleteTarget?.let { c ->
        ConfirmDialog(
            title = "删除分类",
            message = "要删除「${c.name}」吗？",
            onConfirm = {
                vm.delete(c) { ok, msg ->
                    toast(msg)
                }
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun CatRow(c: CategoryEntity, usage: Int, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(c.emoji, c.colorIndex)
        Spacer(Modifier.width(12.dp))
        Text(c.name, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text("$usage 笔", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onClick != null) {
            Text("›", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun CategoryDialog(
    initial: CategoryEntity,
    onSave: (CategoryEntity) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var emoji by remember { mutableStateOf(initial.emoji) }
    var colorIdx by remember { mutableStateOf(initial.colorIndex) }
    val ctx = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial.id == 0L) "新增${if (initial.kind == CatKind.INCOME) "收入" else "支出"}分类"
                else "编辑「${initial.name}」",
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    SimpleField(
                        value = emoji, onValueChange = { if (it.length <= 4) emoji = it },
                        label = "图标", modifier = Modifier.width(92.dp), maxLength = 4,
                    )
                    SimpleField(value = name, onValueChange = { if (it.length <= 8) name = it }, label = "名称", modifier = Modifier.weight(1f), maxLength = 8)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("预览", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    CategoryIcon(emoji.ifBlank { "🏷️" }, colorIdx, size = 44.dp, font = 22.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(name.ifBlank { "新分类" }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                ColorSwatches(selected = colorIdx, onSelect = { colorIdx = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    onSave(initial.copy(name = name.trim(), emoji = emoji.ifBlank { "🏷️" }, colorIndex = colorIdx))
                } else {
                    android.widget.Toast.makeText(ctx, "请填写分类名称", android.widget.Toast.LENGTH_SHORT).show()
                }
            }) { Text("保存", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
