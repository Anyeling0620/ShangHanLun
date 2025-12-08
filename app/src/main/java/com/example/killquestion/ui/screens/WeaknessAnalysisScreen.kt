package com.example.killquestion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.killquestion.ui.components.SmallIconButton
import com.example.killquestion.ui.dialogs.WeaknessCardV2
import com.example.killquestion.ui.theme.*
import com.example.killquestion.util.PdfExportManager
import kotlinx.coroutines.launch

@Composable
fun WeaknessAnalysisScreen(
    cards: List<Pair<String, String>>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    // 搜索状态
    var searchQuery by remember { mutableStateOf("") }

    // 过滤后的卡片列表
    val filteredCards = remember(cards, searchQuery) {
        if (searchQuery.isBlank()) cards
        else cards.filter {
            it.first.contains(searchQuery, ignoreCase = true) ||
                    it.second.contains(searchQuery, ignoreCase = true)
        }
    }

    // Pager 状态
    val pagerState = rememberPagerState(pageCount = { filteredCards.size })
    val scope = rememberCoroutineScope()

    // 跳转弹窗状态
    var showJumpSelector by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // 快速跳转弹窗
    if (showJumpSelector) {
        Dialog(onDismissRequest = { showJumpSelector = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                modifier = Modifier.height(400.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("快速跳转", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(60.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredCards.size) { index ->
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (pagerState.currentPage == index) ZenGreenPrimary else ZenBackgroundStart)
                                    .clickable {
                                        scope.launch { pagerState.scrollToPage(index) }
                                        showJumpSelector = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    color = if (pagerState.currentPage == index) Color.White else TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(ZenBackgroundStart)) {
                // 顶部导航栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // [修改 1]：左侧返回按钮改用 SmallIconButton
                    SmallIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = onBack,
                        tint = TextPrimary
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("智能弱点分析", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ZenDark)
                        Text(
                            if (isLoading) "正在分析中..." else "生成 ${filteredCards.size} 张卡片",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }

                    // [修改 2]：右侧按钮组改用 SmallIconButton
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                        // 分享按钮
                        if (!isLoading && cards.isNotEmpty()) {
                            SmallIconButton(
                                icon = Icons.Default.Share,
                                onClick = { PdfExportManager.exportAndShare(context, cards) },
                                tint = ZenGreenPrimary
                            )
                        }

                        // 刷新按钮
                        SmallIconButton(
                            icon = Icons.Default.Refresh,
                            onClick = onRefresh,
                            tint = ZenGreenPrimary
                            // 注意：SmallIconButton 默认没有 enabled 属性，
                            // 如果需要禁用效果，可以在 onClick 里判断，或者给 SmallIconButton 加个 enabled 参数
                            // 这里简单处理：如果 isLoading 就不响应点击即可，视觉上不做灰色处理也行，或者保持原样
                        )
                    }
                }

                // 搜索栏
                if (!isLoading && cards.isNotEmpty()) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                // 搜索时重置到第一页
                                scope.launch { pagerState.scrollToPage(0) }
                            },
                            placeholder = { Text("搜索知识点...", fontSize = 14.sp, color = TextSecondary.copy(0.7f)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .background(Color.White, RoundedCornerShape(25.dp)), // 圆角更大
                            shape = RoundedCornerShape(25.dp),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = ZenGreenPrimary) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ZenGreenPrimary,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            )
                        )
                    }
                }
            }
        },
        containerColor = ZenBackgroundStart
    ) { padding ->

        // 内容区域
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        ) {
            if (isLoading) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = ZenGreenPrimary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("AI 正在梳理错题...", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text("可能需要十几秒，请稍候", color = TextSecondary)
                }
            } else if (filteredCards.isEmpty()) {
                Box(modifier = Modifier.align(Alignment.Center)) {
                    Text(
                        if(searchQuery.isNotEmpty()) "未找到相关知识点" else "暂无分析结果",
                        color = TextSecondary
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 卡片滑动区域
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f),
                        // [修改点 1]：减小左右边距，让卡片更宽
                        contentPadding = PaddingValues(horizontal = 12.dp), // 原来是 32.dp
                        // [修改点 2]：减小卡片间距
                        pageSpacing = 8.dp // 原来是 16.dp
                    ) { page ->
                        val item = filteredCards.getOrNull(page)
                        if (item != null) {
                            // [修改点 3]：稍微减小上下内边距，让卡片更高
                            Box(modifier = Modifier.padding(vertical = 8.dp)) { // 原来是 16.dp
                                WeaknessCardV2(
                                    title = item.first,
                                    content = item.second,
                                    index = page + 1,
                                    total = filteredCards.size
                                )
                            }
                        }
                    }

                    // 底部控制栏
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                            enabled = pagerState.currentPage > 0,
                            modifier = Modifier.background(Color.White, CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = if (pagerState.currentPage > 0) ZenGreenPrimary else Color.LightGray)
                        }

                        // 页码指示器 (点击弹出跳转)
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = ZenGreenPrimary,
                            modifier = Modifier
                                .height(40.dp)
                                .clickable { showJumpSelector = true },
                            shadowElevation = 4.dp
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            ) {
                                Text(
                                    text = "${pagerState.currentPage + 1} / ${filteredCards.size}",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        IconButton(
                            onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                            enabled = pagerState.currentPage < filteredCards.size - 1,
                            modifier = Modifier.background(Color.White, CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = if (pagerState.currentPage < filteredCards.size - 1) ZenGreenPrimary else Color.LightGray)
                        }
                    }
                }
            }
        }
    }
}