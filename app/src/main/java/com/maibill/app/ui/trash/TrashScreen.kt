package com.maibill.app.ui.trash

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
import com.maibill.app.data.Repository
import com.maibill.app.data.TransactionEntity
import com.maibill.app.data.TxType
import com.maibill.app.ui.components.CategoryIcon
import com.maibill.app.ui.components.ConfirmDialog
import com.maibill.app.ui.components.MutsumiEmpty
import com.maibill.app.util.Money
import com.maibill.app.util.TimeUtil
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrashViewModel : ViewModel() {
    data class Row(val tx: TransactionEntity, val cat: CategoryEntity?)

    private val repo = Repository()

    val data: StateFlow<List<Row>> = combine(repo.deletedTx(), repo.categories()) { txs, cats ->
        val map = cats.associateBy { it.id }
        txs.map { Row(it, map[it.categoryId]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun restore(t: TransactionEntity) { viewModelScope.launch { repo.restoreTx(t) } }
    fun purge(t: TransactionEntity) { viewModelScope.launch { repo.purgeTx(t) } }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory { initializer { TrashViewModel() } }
    }
}

@Composable
fun TrashScreen(onBack: () -> Unit) {
    val vm: TrashViewModel = viewModel(factory = TrashViewModel.Factory)
    val rows by vm.data.collectAsState()
    var purgeTarget by remember { mutableStateOf<TransactionEntity?>(null) }

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
                "回收站", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 10.dp),
            )
        }

        Text(
            "删除的账单在这里保留 30 天，之后自动清除。恢复后会回到原来的位置。",
            fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
        )

        if (rows.isEmpty()) {
            MutsumiEmpty(
                artRes = com.maibill.app.R.drawable.mutsumi_chibi_art,
                title = "嗯。回收站是空的。",
                sub = "删错的账单可以在这里找回来。",
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item { Spacer(Modifier.height(6.dp)) }
                rows.forEach { r ->
                    item(key = r.tx.id) { TrashRow(r, onRestore = { vm.restore(r.tx) }, onPurge = { purgeTarget = r.tx }) }
                }
                item { Spacer(Modifier.height(60.dp)) }
            }
        }
    }

    purgeTarget?.let { t ->
        ConfirmDialog(
            title = "彻底删除",
            message = "要彻底删除「${t.note.ifBlank { "这笔账单" }}」吗？删除后无法找回。",
            onConfirm = { vm.purge(t) },
            onDismiss = { purgeTarget = null },
        )
    }
}

@Composable
private fun TrashRow(r: TrashViewModel.Row, onRestore: () -> Unit, onPurge: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(6.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(r.cat?.emoji ?: "📄", r.cat?.colorIndex ?: 10, size = 36.dp, font = 17.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                (r.cat?.name ?: "已删分类") + " · " + Money.txt(r.tx.amount) + " 元",
                fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${TimeUtil.dateText(r.tx.date)} 删除",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onRestore)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text("恢复", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onPurge)
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            Text("彻底删除", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        }
    }
}
