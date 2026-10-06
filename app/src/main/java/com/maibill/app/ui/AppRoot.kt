package com.maibill.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.maibill.app.auto.PendingAutoRecord
import com.maibill.app.ui.auto.AutoRecordScreen
import com.maibill.app.ui.categories.CategoriesScreen
import com.maibill.app.ui.components.BottomBar
import com.maibill.app.ui.home.HomeScreen
import com.maibill.app.ui.mine.MineScreen
import com.maibill.app.ui.record.RecordScreen
import com.maibill.app.ui.savings.SavingsScreen
import com.maibill.app.ui.stats.StatsScreen
import com.maibill.app.ui.trash.TrashScreen
import kotlinx.coroutines.launch

private val TAB_SPEC = tween<Float>(380, easing = FastOutSlowInEasing)

@Composable
fun AppRoot() {
    val nav = rememberNavController()

    // 自动记账气泡点击 → 跳转记账页；预填数据由 RecordViewModel 初始化时消费并清除
    val pendingAuto = PendingAutoRecord.request
    LaunchedEffect(pendingAuto) {
        if (pendingAuto > 0L) {
            if (nav.currentDestination?.route == "record") {
                PendingAutoRecord.clear()
            } else {
                nav.navigate("record") { launchSingleTop = true }
            }
        }
    }

    NavHost(
        navController = nav,
        startDestination = "main",
        modifier = Modifier.fillMaxSize(),
        enterTransition = { fadeIn(tween(200)) },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { fadeOut(tween(160)) },
    ) {
        // 主界面：去往"记一笔/分类管理"时不淡出，保持面板下方的层次感
        composable(
            "main",
            exitTransition = {
                when (targetState.destination.route) {
                    "record", "categories" -> ExitTransition.None
                    else -> fadeOut(tween(160))
                }
            },
            popEnterTransition = {
                when (initialState.destination.route) {
                    "record", "categories" -> EnterTransition.None
                    else -> fadeIn(tween(200))
                }
            },
        ) {
            MainTabsScreen(
                onGotoRecord = { nav.navigate("record") { launchSingleTop = true } },
                onGotoCategories = { nav.navigate("categories") { launchSingleTop = true } },
                onGotoAuto = { nav.navigate("auto") { launchSingleTop = true } },
                onGotoTrash = { nav.navigate("trash") { launchSingleTop = true } },
                onEditTransaction = { id -> nav.navigate("record?editId=$id") { launchSingleTop = true } },
            )
        }
        // 记一笔 / 修改账单：从底部滑入，关闭时滑回，像一张弹出的面板（带 editId 即编辑模式）
        composable(
            "record?editId={editId}",
            arguments = listOf(navArgument("editId") {
                type = NavType.LongType
                defaultValue = -1L
            }),
            enterTransition = {
                slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200))
            },
            popExitTransition = {
                slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(180))
            },
        ) { entry ->
            RecordScreen(
                editId = entry.arguments?.getLong("editId")?.takeIf { it > 0L },
                onClose = { nav.popBackStack() },
            )
        }
        // 分类管理：从右侧轻推入
        composable(
            "categories",
            enterTransition = {
                slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(200))
            },
            popExitTransition = {
                slideOutHorizontally(tween(250, easing = FastOutSlowInEasing)) { it / 4 } + fadeOut(tween(160))
            },
        ) { CategoriesScreen(onBack = { nav.popBackStack() }) }
        // 回收站
        composable(
            "trash",
            enterTransition = {
                slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(200))
            },
            popExitTransition = {
                slideOutHorizontally(tween(250, easing = FastOutSlowInEasing)) { it / 4 } + fadeOut(tween(160))
            },
        ) { TrashScreen(onBack = { nav.popBackStack() }) }
        // 自动记账：权限引导 + 开关
        composable(
            "auto",
            enterTransition = {
                slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(200))
            },
            popExitTransition = {
                slideOutHorizontally(tween(250, easing = FastOutSlowInEasing)) { it / 4 } + fadeOut(tween(160))
            },
        ) { AutoRecordScreen(onBack = { nav.popBackStack() }) }
    }
}

/** 四个主标签页：左右滑动 + 底栏点击均可切换；页滑出视口即释放（入场动画每次到达都能重播，状态由 ViewModel 与 rememberSaveable 保留） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainTabsScreen(
    onGotoRecord: () -> Unit,
    onGotoCategories: () -> Unit,
    onGotoAuto: () -> Unit,
    onGotoTrash: () -> Unit,
    onEditTransaction: (Long) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
    val scope = rememberCoroutineScope()

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapAnimationSpec = tween(320, easing = FastOutSlowInEasing),
            ),
        ) { page ->
            Box(Modifier.fillMaxSize()) {
                when (page) {
                    0 -> HomeScreen(
                        onGotoStats = {
                            scope.launch { pagerState.animateScrollToPage(1, animationSpec = TAB_SPEC) }
                        },
                        onEditTransaction = onEditTransaction,
                    )
                    1 -> StatsScreen()
                    2 -> SavingsScreen()
                    else -> MineScreen(
                        onGotoCategories = onGotoCategories,
                        onGotoAuto = onGotoAuto,
                        onGotoTrash = onGotoTrash,
                    )
                }
            }
        }
        BottomBar(
            selectedPage = pagerState.targetPage,
            onSelect = { p -> scope.launch { pagerState.animateScrollToPage(p, animationSpec = TAB_SPEC) } },
            onRecord = onGotoRecord,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
