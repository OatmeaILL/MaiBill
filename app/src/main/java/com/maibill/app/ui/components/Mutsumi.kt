package com.maibill.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maibill.app.R

/** 小睦语录（她的台词都很短） */
object MutsumiQuotes {
    val idle = listOf("嗯。", "哦。", "……", "这样啊。")
}

/** 小睦头像（淡薄荷圆底）：resId 指定不同位置的图 */
@Composable
fun MutsumiAvatar(resId: Int, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(resId),
            contentDescription = "若叶睦",
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 空状态：小睦 + 她的语气 */
@Composable
fun MutsumiEmpty(
    artRes: Int,
    title: String,
    sub: String,
    modifier: Modifier = Modifier,
    mascotSize: Dp = 92.dp,
    compact: Boolean = false,
    /** false = 直接放插画，不套薄荷圆底（横构图/大图用，避免被圆形裁掉手脚） */
    circle: Boolean = true,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 18.dp else 38.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (circle) {
            MutsumiAvatar(artRes, mascotSize)
        } else {
            Image(
                painter = painterResource(artRes),
                contentDescription = "若叶睦",
                modifier = Modifier.size(mascotSize),
            )
        }
        Text(
            title, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            sub, fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** 随机一句小睦 */
@Composable
fun rememberMutsumiQuote(): String = remember { MutsumiQuotes.idle.random() }

/** 每日开屏问候：每天第一次打开首页时显示一次 */
object DailyGreeting {
    private const val FILE = "greeting"
    fun shouldShow(ctx: android.content.Context): Boolean =
        ctx.getSharedPreferences(FILE, android.content.Context.MODE_PRIVATE)
            .getString("last", "") != java.time.LocalDate.now().toString()

    fun mark(ctx: android.content.Context) {
        ctx.getSharedPreferences(FILE, android.content.Context.MODE_PRIVATE)
            .edit().putString("last", java.time.LocalDate.now().toString()).apply()
    }
}
