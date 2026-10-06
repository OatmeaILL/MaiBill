package com.maibill.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maibill.app.R
import com.maibill.app.ui.theme.heroGradient

private const val TAB_LIST = 0
private const val TAB_CHART = 1
private const val TAB_TARGET = 2
private const val TAB_AVATAR = 3

private data class BottomItem(val page: Int, val kind: Int, val label: String)

/**
 * 自绘底部导航栏：4 个标签 + 中央记账大按钮（悬浮在栏上）
 * selectedPage 为当前 Pager 页（0=明细 1=图表 2=存钱 3=我的）
 * 前三格是同一套线条图标（emoji 风格不统一、太热闹）；「我的」放小睦的头像——一眼就知道这是她的 App。
 */
@Composable
fun BottomBar(
    selectedPage: Int,
    onSelect: (Int) -> Unit,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        BottomItem(0, TAB_LIST, "明细"),
        BottomItem(1, TAB_CHART, "图表"),
        null,
        BottomItem(2, TAB_TARGET, "存钱"),
        BottomItem(3, TAB_AVATAR, "我的"),
    )
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
                    .navigationBarsPadding()
                    .height(62.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    if (item == null) {
                        Spacer(Modifier.width(62.dp))
                    } else {
                        val on = item.page == selectedPage
                        // 选中图标弹性放大，制造"蹦一下"的反馈
                        val scale by animateFloatAsState(
                            targetValue = if (on) 1.16f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                            label = "navScale",
                        )
                        val tint = if (on) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                        Column(
                            Modifier
                                .clickable { onSelect(item.page) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
                                if (item.kind == TAB_AVATAR) {
                                    Image(
                                        painter = painterResource(R.drawable.mutsumi_art),
                                        contentDescription = "若叶睦",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .alpha(if (on) 1f else 0.75f)
                                            .clip(CircleShape)
                                            .border(
                                                1.5.dp,
                                                if (on) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outlineVariant,
                                                CircleShape,
                                            ),
                                    )
                                } else {
                                    Canvas(Modifier.size(22.dp)) { drawTabIcon(item.kind, tint) }
                                }
                            }
                            Text(
                                item.label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = tint,
                            )
                        }
                    }
                }
            }
            // 中央记账大按钮
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-24).dp)
                    .size(56.dp)
                    .shadow(14.dp, CircleShape)
                    .clip(CircleShape)
                    .background(heroGradient())
                    .border(4.dp, MaterialTheme.colorScheme.background, CircleShape)
                    .clickable(onClick = onRecord),
                contentAlignment = Alignment.Center,
            ) {
                Text("＋", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.W300)
            }
        }
    }
}

/** 统一的线条图标：1.7dp 描边、圆头，三个标签风格一致 */
private fun DrawScope.drawTabIcon(kind: Int, tint: Color) {
    val s = size.minDimension
    val sw = s * 0.078f
    val stroke = Stroke(width = sw, cap = StrokeCap.Round)
    when (kind) {
        // 明细：一张小票
        TAB_LIST -> {
            drawRoundRect(
                color = tint,
                topLeft = Offset(s * 0.22f, s * 0.11f),
                size = Size(s * 0.56f, s * 0.78f),
                cornerRadius = CornerRadius(s * 0.16f),
                style = stroke,
            )
            drawLine(tint, Offset(s * 0.37f, s * 0.38f), Offset(s * 0.63f, s * 0.38f), sw, StrokeCap.Round)
            drawLine(tint, Offset(s * 0.37f, s * 0.58f), Offset(s * 0.55f, s * 0.58f), sw, StrokeCap.Round)
        }
        // 图表：三根柱
        TAB_CHART -> {
            val bars = listOf(0.27f to 0.48f, 0.50f to 0.22f, 0.73f to 0.40f)
            bars.forEach { (x, top) ->
                drawLine(tint, Offset(s * x, s * top), Offset(s * x, s * 0.82f), sw * 1.15f, StrokeCap.Round)
            }
        }
        // 存钱：靶心
        TAB_TARGET -> {
            drawCircle(tint, radius = s * 0.31f, style = stroke)
            drawCircle(tint, radius = s * 0.10f, style = stroke)
        }
    }
}
