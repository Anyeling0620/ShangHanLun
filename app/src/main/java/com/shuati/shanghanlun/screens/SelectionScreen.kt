package com.shuati.shanghanlun.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Abc
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shuati.shanghanlun.data.repository.QuestionRepository
import com.shuati.shanghanlun.ui.components.BouncyButton
import com.shuati.shanghanlun.ui.components.SmallIconButton
import com.shuati.shanghanlun.ui.theme.ColorMistake
import com.shuati.shanghanlun.ui.theme.TextSecondary
import com.shuati.shanghanlun.ui.theme.ZenDark

// 20种渐变色板 (保持不变)
val CardGradients = listOf(
    Brush.linearGradient(listOf(Color(0xFFE0F2F1), Color(0xFFB2DFDB))),
    Brush.linearGradient(listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB))),
    Brush.linearGradient(listOf(Color(0xFFF3E5F5), Color(0xFFE1BEE7))),
    Brush.linearGradient(listOf(Color(0xFFFFF3E0), Color(0xFFFFE0B2))),
    Brush.linearGradient(listOf(Color(0xFFFAFAFA), Color(0xFFEEEEEE))),
    Brush.linearGradient(listOf(Color(0xFFFCE4EC), Color(0xFFF8BBD0))),
    Brush.linearGradient(listOf(Color(0xFFF9FBE7), Color(0xFFDCEDC8))),
    Brush.linearGradient(listOf(Color(0xFFE1F5FE), Color(0xFF81D4FA))),
    Brush.linearGradient(listOf(Color(0xFFFFFDE7), Color(0xFFFFF9C4))),
    Brush.linearGradient(listOf(Color(0xFFE8EAF6), Color(0xFFC5CAE9))),
    Brush.linearGradient(listOf(Color(0xFFFFEBEE), Color(0xFFFFCDD2))),
    Brush.linearGradient(listOf(Color(0xFFE0F7FA), Color(0xFF80DEEA))),
    Brush.linearGradient(listOf(Color(0xFFEFEBE9), Color(0xFFD7CCC8))),
    Brush.linearGradient(listOf(Color(0xFFECEFF1), Color(0xFFCFD8DC))),
    Brush.linearGradient(listOf(Color(0xFFE8F5E9), Color(0xFFA5D6A7))),
    Brush.linearGradient(listOf(Color(0xFFEDE7F6), Color(0xFFB39DDB))),
    Brush.linearGradient(listOf(Color(0xFFFFF8E1), Color(0xFFFFECB3))),
    Brush.linearGradient(listOf(Color(0xFFE0F7FA), Color(0xFF4DD0E1))),
    Brush.linearGradient(listOf(Color(0xFFF8BBD0), Color(0xFFF48FB1))),
    Brush.linearGradient(listOf(Color(0xFFF1F8E9), Color(0xFFAED581)))
)

@Composable
fun SelectionScreen(title: String, subtitle: String, items: List<String>, onBack: () -> Unit, onSelect: (String) -> Unit) {
    // 强制刷新 UI 的状态 (当删除元素时触发)
    var refreshTrigger by remember { mutableStateOf(0) }

    val (onlineItems, offlineItems) = remember(items, refreshTrigger) {
        items.partition { it.startsWith("联网搜索") }
    }

    var isInOnlineFolder by remember { mutableStateOf(false) }

    // [新增] 删除确认弹窗状态
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = isInOnlineFolder) {
        isInOnlineFolder = false
    }

    // [新增] 弹窗 UI
    if (showDeleteDialog && deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除？", fontWeight = FontWeight.Bold) },
            text = { Text("删除后，该关键词下的所有题目将从本地清除，且无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 执行删除
                        QuestionRepository.deleteChapter(deleteTarget!!)
                        refreshTrigger++
                        showDeleteDialog = false
                    }
                ) { Text("确认删除", color = ColorMistake) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消", color = TextSecondary) }
            },
            containerColor = Color.White
        )
    }

    // [计算高度逻辑]
    // 目标：除以去顶部栏，剩下的空间 2列 x 5行 = 10个卡片
    // 获取屏幕高度
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    // 预估顶部栏+Padding的高度 (80dp 顶部 + 20dp 顶部padding + 10dp 底部padding) ≈ 110dp
    // 加上 Grid 的间距 (4 * 16dp)
    // 简单算法：可用高度 / 5
    val availableHeight = screenHeight - 120.dp
    val dynamicCardHeight = (availableHeight / 5) - 16.dp // 减去间距
    // 限制一个最小高度，防止屏幕太矮时卡片压扁
    val finalCardHeight = maxOf(100.dp, dynamicCardHeight)

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = {
                    if (isInOnlineFolder) isInOnlineFolder = false else onBack()
                }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = if (isInOnlineFolder) "联网刷题记录" else title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = ZenDark
                )
                Text(
                    text = if (isInOnlineFolder) "点击进入 / 点击右上角删除" else subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        // 列表区域
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (isInOnlineFolder) {
                // --- 二级页面 ---
                itemsIndexed(onlineItems) { index, item ->
                    val gradient = CardGradients[index % CardGradients.size]
                    val displayName = item.removePrefix("联网搜索:").trim()

                    ModernSelectionTile(
                        text = displayName,
                        index = index + 1,
                        gradient = gradient,
                        icon = Icons.Default.Cloud,
                        height = finalCardHeight,
                        onDelete = {
                            // [修改]：点击只弹出确认框，不直接删除
                            deleteTarget = item
                            showDeleteDialog = true
                        },
                        onClick = { onSelect(item) }
                    )
                }

                if (onlineItems.isEmpty()) {
                    item {
                        Text(
                            "暂无联网刷题记录\n请去首页进行联网刷题",
                            modifier = Modifier.padding(20.dp),
                            color = TextSecondary
                        )
                    }
                }

            } else {
                // --- 一级页面：普通章节 ---
                itemsIndexed(offlineItems) { index, item ->
                    val gradient = CardGradients[index % CardGradients.size]
                    val icon = when (index % 6) {
                        0 -> Icons.Default.AutoStories
                        1 -> Icons.Default.Category
                        2 -> Icons.Default.Bookmarks
                        3 -> Icons.Default.School
                        4 -> Icons.Default.Abc
                        else -> Icons.Default.AutoStories
                    }

                    ModernSelectionTile(
                        text = item,
                        index = index + 1,
                        gradient = gradient,
                        icon = icon,
                        height = finalCardHeight, // [应用动态高度]
                        onClick = { onSelect(item) }
                    )
                }

                // 文件夹入口 (放在最后)
                item {
                    ModernSelectionTile(
                        text = "联网刷题记录",
                        index = offlineItems.size + 1,
                        gradient = Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFDDD6FE))),
                        icon = Icons.Default.History,
                        height = finalCardHeight, // [应用动态高度]
                        onClick = { isInOnlineFolder = true }
                    )
                }
            }
        }
    }
}

@Composable
fun ModernSelectionTile(
    text: String,
    index: Int,
    gradient: Brush,
    icon: ImageVector,
    height: Dp,
    onDelete: (() -> Unit)? = null, // [新增] 删除回调，为空时不显示删除按钮
    onClick: () -> Unit
) {
    // 这是一个小技巧：如果处于 BouncyButton 内部，点击事件会被父级捕获。
    // 为了让删除按钮能独立点击，我们需要把它放在 Box 的最上层，并且处理点击事件传递。

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height) // 使用计算出的高度
    ) {
        // 底层卡片 (负责点击跳转)
        BouncyButton(onClick = onClick, modifier = Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(gradient)
            ) {
                // 背景图标
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(height * 0.6f) // 图标大小随卡片高度缩放
                        .align(Alignment.BottomEnd)
                        .offset(x = 10.dp, y = 10.dp)
                )

                // 文字内容
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.6f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = String.format("%02d", index),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary.copy(alpha = 0.8f)
                        )
                    }

                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = ZenDark,
                        maxLines = 3,
                        lineHeight = 22.sp
                    )
                }
            }
        }

        // [新增] 删除按钮 (右上角悬浮)
        if (onDelete != null) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .background(Color.White.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "删除",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}