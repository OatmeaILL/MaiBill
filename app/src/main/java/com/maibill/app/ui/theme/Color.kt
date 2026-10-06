package com.maibill.app.ui.theme

import androidx.compose.ui.graphics.Color

// 品牌色（v2.4「褪色薄荷」：降饱和、偏冷，取自小睦的发色）
val MintPrimary = Color(0xFF2E9C74)      // 主色
val MintDeep = Color(0xFF195E49)         // Hero 渐变深端
val MintSoft = Color(0xFF6FC9A2)         // 浅端 / 图表浅档
val MintBright = Color(0xFF3BD08F)       // 深色模式提亮端

// 琥珀：取自她眼睛的暖金色（超支提示、图表第 5 档）
val Amber = Color(0xFFC08A2E)
val AmberContainer = Color(0xFFFAF0DA)
val AmberDark = Color(0xFFE0B45E)
val AmberContainerDark = Color(0xFF332C1C)

// 进度条轨道：比 surfaceVariant 深一档，0% 时也看得见空槽
val TrackBarLight = Color(0xFFE2E8E0)
val TrackBarDark = Color(0xFF2F3B31)

// 分类调色板：index → 强调色（浅深模式通用）
val Palette: List<Color> = listOf(
    Color(0xFFF5842C), // 0 餐饮·橙
    Color(0xFFF55E8C), // 1 购物·粉
    Color(0xFF3D9BFF), // 2 交通·蓝
    Color(0xFF8E6BFF), // 3 娱乐·紫
    Color(0xFF16BFA6), // 4 居家·青
    Color(0xFF38BDF8), // 5 医疗·天蓝
    Color(0xFFF5A623), // 6 学习·琥珀
    Color(0xFFF26D85), // 7 人情·玫红
    Color(0xFF5B8DEF), // 8 通讯·靛蓝
    Color(0xFF2E9C74), // 9 储蓄·薄荷（跟随主色）
    Color(0xFF98A29B), // 10 其他·灰
)

// 浅色模式下的圆底柔色（与 Palette 一一对应）
val PaletteContainer: List<Color> = listOf(
    Color(0xFFFFEDE0), Color(0xFFFFE9F0), Color(0xFFE4F1FF), Color(0xFFEFEAFF),
    Color(0xFFDFF8F2), Color(0xFFE3F6FD), Color(0xFFFFF3DC), Color(0xFFFFE7EB),
    Color(0xFFE9F0FE), Color(0xFFDFF2E8), Color(0xFFEEF1ED),
)

fun paletteBase(i: Int): Color = Palette.getOrElse(i) { Palette[10] }
fun paletteContainer(i: Int, dark: Boolean): Color =
    if (dark) paletteBase(i).copy(alpha = 0.22f)
    else PaletteContainer.getOrElse(i) { PaletteContainer[10] }

/**
 * 分类占比环形图用的薄荷同色系阶梯：按占比从大到小取色，深浅交替，
 * 保证相邻两档一眼能分开；第 5 档起用琥珀，避免整图全是绿。
 */
private val LadderLight = listOf(
    Color(0xFF1E6B52), Color(0xFF7AC9A6), Color(0xFF2E9C74), Color(0xFFA9D9C0), Amber,
)
private val LadderDark = listOf(
    Color(0xFF5EC49B), Color(0xFF276E55), Color(0xFF8FD8B6), Color(0xFF3E8E6C), AmberDark,
)

fun rankColor(rank: Int, dark: Boolean): Color {
    val l = if (dark) LadderDark else LadderLight
    return l.getOrElse(rank) { l.last() }
}
