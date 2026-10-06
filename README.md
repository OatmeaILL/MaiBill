# 小睦记账 MaiBill

一款**纯本地、无网络权限**的安卓记账 App，主题为 BanG Dream! It's MyGo!!!!! 角色**若叶睦（小睦）**——薄荷绿主色取自她的应援色。

包名 `com.maibill.app` · minSdk 26 (Android 8.0) · targetSdk 35

**📦 下载：[Releases v2.4](https://github.com/OatmeaILL/MaiBill/releases/tag/v2.4)**（含 `mai-bill-v2.4.apk`，允许「安装未知来源」后直接装）

> ⚠️ 素材版权声明：本项目代码以 GPLv3 开源，但仓库内的若叶睦角色立绘 / Q 版图（`app/src/main/res/drawable-nodpi/mutsumi_*.png`、`mipmap-*/ic_launcher_*`）源自 BanG Dream! 官方素材，**版权归 Bushiroad / 绘森等权利方所有，仅供个人学习交流，切勿用于商业用途**。这部分内容不适用 GPLv3 授权。移除/替换这些素材不影响程序功能——`res/drawable/mutsumi_chibi.xml` 是自绘矢量兜底版。

## 功能

**记账**
- 快速记一笔：自定义数字键盘（＋－连算、退格还原累计值）、备注、日期选择、"保存并再记"
- 长按账单 → 修改金额 / 分类 / 备注 / 日期，或删除
- 删除进**回收站**（30 天自动清除，可恢复 / 彻底删除）
- 账单来源标记：手动 / 微信支付 / 支付宝；流水显示时刻（HH:mm）

**统计**
- 明细页：月度 Hero 卡（支出 / 收入 / 结余 / 净存入 / 预算条）、按日分组流水
- 图表页：收支总览、预算卡（含"按当前节奏月底预计"）、**对比上月**卡、分类占比环形图、每日趋势柱状图

**存钱**
- 多目标进度卡（名称 / 金额 / 期限 / 颜色自定义）
- 存入 / 取出自动生成"储蓄"分类账单，不计入支出统计与预算
- 目标达成文案："🎉 嗯，做到了。"

**自动记账（三通道）**
1. 通知监听 `NotificationListenerService` —— 读微信 / 支付宝支付通知
2. 无障碍识别 `AccessibilityService` —— 识别支付成功页面（App 内付款无通知时兜底）
3. 悬浮气泡 `TYPE_APPLICATION_OVERLAY` —— 右上角 "✓ 记一笔 ¥x · 来源"，点击带预填跳记账页

**数据**
- CSV 导出（BOM 头，Excel 友好）
- 本地自动备份：每 24h 备份到系统 `下载/小睦记账备份/`；支持手动备份 / 恢复（SAF 选文件 → 校验 → 覆盖）

**个性化**
- 应援色主题切换：小睦薄荷绿（默认）/ 灯晴空蓝 / 爱音樱粉 / 乐奈蜜柑 / 立希蔷薇红 / 祥子雾紫
- 每日开屏问候、外观模式三档（自动 / 白天 / 黑夜）

## 技术栈

| 项 | 选型 |
|---|---|
| 语言 / 构建 | Kotlin 2.0.21 · Gradle 8.9 · AGP 8.7.3 · JDK 17 |
| UI | Jetpack Compose（BOM 2024.09.03）· Material Design 3 |
| 存储 | Room 2.6.1（KSP）· 数据库 `maibill.db`，schema **version 3** |
| 导航 | Navigation Compose 2.8.2（单 Activity） |
| 测试 | JUnit 4.13.2 · 24 个单元测试（金额解析 / 键盘连算等） |

## 构建

仓库不包含构建环境（Android SDK / JDK / Gradle 发行版体积约 2GB），请先自备环境：

```bash
# 需要：JDK 17、Android SDK Platform 35 + Build-Tools
gradle :app:assembleRelease      # 产物：app/build/outputs/apk/release/app-release.apk
gradle :app:testReleaseUnitTest  # 24 个单元测试
```

### 签名配置

`app/build.gradle.kts` 里的 `signingConfigs.release` 引用了 `../build-env/maibill.jks`。该密钥文件**不在仓库中**（见 `.gitignore`），克隆后需要自备签名，或改用 Android Studio 默认的 debug 签名做本地安装调试。

## 数据模型（Room）

- **transactions**：id · type(`EXPENSE`/`INCOME`/`SAVE`/`UNSAVE`) · amount(分, Long) · categoryId · note · date(epochDay) · goalId(可空) · source(手动/微信/支付宝) · deletedAt(0=正常) · createdAt(毫秒)
- **categories**：id · name · emoji · colorIndex(0-10) · kind(`EXPENSE`/`INCOME`/`SAVING`) · sortOrder
- **savings_goals**：id · name · emoji · colorIndex · targetFen · deadline(可空) · createdAt
- **settings**：key · value（`monthly_budget` / `auto_record` 开关 / 主题 / 备份时间）

迁移历史：v1→v2 加 `source` 列；v2→v3 加 `deletedAt` 列（回收站）。**加列必须写迁移，不能 destructive reset** —— 用户数据会丢。

## 已知限制

**自动记账**
- 好友转账 / 红包没有系统通知 → 只能手动记
- 无障碍授权后部分系统（HyperOS 等）需重启手机才真正开始派发通知，且需加自启动白名单
- 白名单外的 App 不识别

**其他**
- 记账页键盘遮挡：内容区底部留白 310dp
- 悬浮层下方文字必须留白（避让键盘 / 底栏）
- 任何无效输入都禁用保存键或 Toast，**永不静默失败**

## 目录结构

```
MaiBill/
├── app/src/main/java/com/maibill/app/
│   ├── MainActivity.kt            # 单 Activity，处理气泡 / 快捷方式带入的预填
│   ├── MaiBillApplication.kt      # Graph 初始化、种子数据、自动备份、回收站清理
│   ├── data/                      # Entity / Daos / AppDatabase / Repository / Backup
│   ├── auto/                      # AutoRecord.kt（通知监听+气泡）、PayWatchService.kt（无障碍）
│   ├── ui/
│   │   ├── AppRoot.kt             # NavHost
│   │   ├── home/ stats/ savings/ mine/ record/ categories/ trash/ auto/
│   │   ├── components/            # Common.kt、AmountKeypad.kt、BottomBar.kt
│   │   └── theme/                 # Color.kt、Theme.kt
│   └── util/                      # Money.kt（分↔元）、TimeUtil.kt
├── app/src/test/.../MoneyAndInputTest.kt   # 24 个单元测试
├── design/                        # 高保真设计稿（HTML，可交互）与预览图
├── 交接文档.md                     # 完整功能清单 / 设计系统 / 踩坑记录
└── LICENSE                        # GPLv3
```

## 开发注意

- 改深浅色配色一律调 `appIsDark()`（`Theme.kt`，已含外观模式），**不要直接用 `isSystemInDarkTheme()`**，否则强制白天 / 黑夜时配色对不上
- 深色底色由 `MaiBillTheme` 的根 `Surface(color = background, contentColor = onBackground)` 统一提供，新增界面别在外层覆盖不透明浅色底

## License

代码部分以 **[GPL v3.0](LICENSE)** 开源。角色素材版权归原作者所有，见上文声明。