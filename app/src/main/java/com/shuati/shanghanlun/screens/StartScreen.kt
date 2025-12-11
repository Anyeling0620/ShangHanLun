package com.shuati.shanghanlun.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shuati.shanghanlun.data.local.MistakeManager
import com.shuati.shanghanlun.data.repository.QuestionRepository
import com.shuati.shanghanlun.ui.components.MenuCard
import com.shuati.shanghanlun.ui.components.SmallIconButton
import com.shuati.shanghanlun.ui.components.StatCard
import com.shuati.shanghanlun.ui.dialogs.AiImageDialog
import com.shuati.shanghanlun.ui.dialogs.EasterEggDialog
import com.shuati.shanghanlun.ui.dialogs.GuideDialog
import com.shuati.shanghanlun.ui.dialogs.SettingsDialog
import com.shuati.shanghanlun.ui.theme.ColorMistake
import com.shuati.shanghanlun.ui.theme.ColorfulGradient
import com.shuati.shanghanlun.ui.theme.MainGradient
import com.shuati.shanghanlun.ui.theme.MistakeGradient
import com.shuati.shanghanlun.ui.theme.TextSecondary
import com.shuati.shanghanlun.ui.theme.ZenDark
import com.shuati.shanghanlun.ui.theme.ZenGreenPrimary
import com.shuati.shanghanlun.config.AppConfig

@Composable
fun StartScreen(
    onModeSelect: (String) -> Unit,
    onWeaknessAnalysisClick: () -> Unit // [新增回调]
) {
    val isDataLoaded = QuestionRepository.isLoaded
    val trigger = MistakeManager.mistakeChangeTrigger
    val mistakeCount = remember(trigger) { MistakeManager.getAllMistakeIds().size }

    // --- 状态管理 ---
    var easterEggCount by remember { mutableIntStateOf(0) }
    var showEasterEgg by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }

    // [删除] 之前内部的 weaknessCards, showWeaknessDialog 等状态
    // [删除] 之前内部的 startWeaknessAnalysis 函数

    // --- 弹窗渲染区域 ---
    if (showEasterEgg) {
        EasterEggDialog(onDismiss = { showEasterEgg = false; easterEggCount = 0 })
    }
    if (showSettings) {
        SettingsDialog(onDismiss = { showSettings = false })
    }
    if (showGuide) {
        GuideDialog(onDismiss = { showGuide = false })
    }
    // [新增] 状态控制
    var showAiImageDialog by remember { mutableStateOf(false) }

    if (showAiImageDialog) {
        AiImageDialog(onDismiss = { showAiImageDialog = false })
    }
    // [删除] WeaknessResultDialog 的调用

    // --- 界面布局 ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // --- 顶部栏 ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = AppConfig.UI_TITLE_MAIN,
                    style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily.Serif),
                    fontWeight = FontWeight.Bold,
                    color = ZenDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = AppConfig.UI_SUBTITLE_MAIN,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                    letterSpacing = 2.sp,
                    fontSize = 14.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallIconButton(
                    icon = Icons.Default.Info,
                    onClick = { showGuide = true }
                )
                SmallIconButton(
                    icon = Icons.Default.Image, // 记得导入 Icons.Default.Image
                    onClick = { showAiImageDialog = true }
                )
                SmallIconButton(
                    icon = Icons.Default.Settings,
                    onClick = { showSettings = true }
                )
                SmallIconButton(
                    icon = Icons.Default.Psychology,
                    // [修改] 点击直接调用外部回调
                    onClick = onWeaknessAnalysisClick
                )
            }
        }

        // [修改核心]：使用一个 Column 占据中间剩余所有空间，并均匀分布内部 5 个元素
        Column(
            modifier = Modifier
                .weight(1f) // 占据中间全部空间
                .fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceEvenly // 均匀分布
        ) {
            // 模块 1: 统计卡片
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "总题量",
                    // 这里虽然看起来没用到 isDataLoaded，但因为上面读取了它，
                    // 所以当加载完成时，界面会自动刷新，这里就会再次调用 getTotalCount() 拿到新值
                    value = if (isDataLoaded) "${QuestionRepository.getTotalCount()}" else "...",
                    icon = Icons.Default.LibraryBooks,
                    accentColor = ZenGreenPrimary
                )
                StatCard(
                    Modifier.weight(1f),
                    "错题数",
                    "$mistakeCount",
                    Icons.Default.Warning,
                    ColorMistake
                )
            }

            // 模块 2: 章节刷题
            MenuCard(
                title = "章节刷题",
                subtitle = "扎实基础",
                icon = Icons.Default.MenuBook,
                gradient = MainGradient,
                onClick = { onModeSelect("CHAPTER") }
            )

            // 模块 3: 题型刷题
            MenuCard(
                title = "题型刷题",
                subtitle = "针对训练",
                icon = Icons.AutoMirrored.Filled.List,
                gradient = Brush.linearGradient(listOf(Color(0xFF457B9D), Color(0xFF1D3557))),
                onClick = { onModeSelect("TYPE") }
            )

            // 模块 4: 联网刷题
            MenuCard(
                title = "联网刷题",
                subtitle = "AI 实时出题 (无限)",
                icon = Icons.Default.CloudDownload,
                gradient = Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))),
                onClick = { onModeSelect("ONLINE") }
            )

            // 模块 5: 错题回顾 (重命名)
            MenuCard(
                title = "错题回顾", // [修改名字]
                subtitle = "温故知新",
                icon = Icons.Default.HistoryEdu,
                gradient = MistakeGradient,
                onClick = { if (mistakeCount > 0) onModeSelect("MISTAKE") }
            )
        }

        Text(
            text = AppConfig.UI_AUTHOR_CREDIT,
            style = TextStyle(
                brush = ColorfulGradient,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Cursive
            ),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 16.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    easterEggCount++
                    if (easterEggCount >= 5) {
                        showEasterEgg = true
                    }
                }
        )
    }


}