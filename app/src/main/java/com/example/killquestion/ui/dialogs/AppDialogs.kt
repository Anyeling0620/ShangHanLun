package com.example.killquestion.ui.dialogs

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.killquestion.data.local.FontConfig
import com.example.killquestion.data.local.FontManager
import com.example.killquestion.data.local.ProgressManager
import com.example.killquestion.data.remote.AiConfigManager
import com.example.killquestion.data.remote.AppVersion
import com.example.killquestion.data.remote.SimpleAiClient
import com.example.killquestion.data.remote.UpdateManager
import com.example.killquestion.ui.components.BouncyButton
import com.example.killquestion.ui.components.MarkdownText
import com.example.killquestion.ui.theme.*
import com.example.killquestion.utils.ImageSaver
import kotlinx.coroutines.launch
import java.util.regex.Pattern

// [通用组件] 流光边框弹窗容器
@Composable
fun RainbowBorderDialogSurface(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val scaleAnim = remember { Animatable(0.9f) }
    val infiniteTransition = rememberInfiniteTransition(label = "flow")
    val offsetAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "flow_offset"
    )

    LaunchedEffect(Unit) {
        scaleAnim.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
    }

    val flowBrush = Brush.linearGradient(
        colors = RainbowColors,
        start = Offset(offsetAnim, offsetAnim),
        end = Offset(offsetAnim + 1000f, offsetAnim + 1000f),
        tileMode = TileMode.Mirror
    )

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .graphicsLayer { scaleX = scaleAnim.value; scaleY = scaleAnim.value }
        ) {
            // 背景层（流光）
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .scale(1.03f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(flowBrush)
                    .blur(16.dp)
            )

            // 内容层
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                shadowElevation = 0.dp
            ) {
                Box {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .zIndex(1f)
                    ) {
                        Icon(Icons.Default.Close, null, tint = TextSecondary)
                    }
                    content()
                }
            }
        }
    }
}

// [弹窗] AI 图片生成 (支持 Coil 显示和 ImageSaver 保存)
@Composable
fun AiImageDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var prompt by remember { mutableStateOf("") }
    var resultMarkdown by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 正则提取 URL: ![Image](https://...) -> 提取括号内的链接
    val imageUrl = remember(resultMarkdown) {
        val pattern = Pattern.compile("\\((https?://.*?)\\)")
        val matcher = pattern.matcher(resultMarkdown)
        if (matcher.find()) matcher.group(1) else null
    }

    RainbowBorderDialogSurface(onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Image, null, tint = ZenGreenPrimary, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("AI 灵感绘图", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("输入中文描述，AI 自动润色并生成大片", style = MaterialTheme.typography.bodySmall, color = TextSecondary)

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("画面描述") },
                placeholder = { Text("例如：一只在太空中喝咖啡的赛博朋克猫咪") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ZenGreenPrimary,
                    unfocusedBorderColor = Color(0xFFE2E8F0)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            BouncyButton(
                onClick = {
                    if (prompt.isNotBlank()) {
                        isGenerating = true
                        resultMarkdown = ""
                        scope.launch {
                            SimpleAiClient.generateImageCreation(prompt).collect {
                                resultMarkdown += it
                            }
                            isGenerating = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isGenerating) Color.Gray else ZenGreenPrimary, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isGenerating) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("正在构思画面...", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("立即生成", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 图片展示区
            if (resultMarkdown.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = Color(0xFFF1F5F9))
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF8FAFC)),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUrl != null) {
                        // 1. 显示网络图片
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "AI Generated Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        // 2. 右上角下载按钮 (非生成状态下显示)
                        if (!isGenerating) {
                            IconButton(
                                onClick = {
                                    if (!isSaving) {
                                        isSaving = true
                                        scope.launch {
                                            ImageSaver.saveImageToGallery(context, imageUrl)
                                            isSaving = false
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(36.dp)
                                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "保存",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // 如果还在生成或解析失败，显示原始文本
                        Box(modifier = Modifier.padding(16.dp)) {
                            MarkdownText(
                                markdown = resultMarkdown,
                                color = TextPrimary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                if (!isGenerating && imageUrl == null) {
                    Text(
                        "生成失败，未能获取图片链接",
                        color = ColorMistake,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

// [弹窗] 设置 (包含字体下载进度、多行Key、代理、更新)
@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    var apiKeys by remember { mutableStateOf(AiConfigManager.apiKeys) }
    var model by remember { mutableStateOf(AiConfigManager.model) }
    var baseUrl by remember { mutableStateOf(AiConfigManager.baseUrl) }
    var enableProxy by remember { mutableStateOf(AiConfigManager.enableProxy) }
    var proxyHost by remember { mutableStateOf(AiConfigManager.proxyHost) }
    var proxyPort by remember { mutableStateOf(AiConfigManager.proxyPort) }

    var refreshTrigger by remember { mutableStateOf(0) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var newVersion by remember { mutableStateOf<AppVersion?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateCheckMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    if (showUpdateDialog && newVersion != null) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("发现新版本 ${newVersion!!.versionName}", fontWeight = FontWeight.Bold) },
            text = { Text(newVersion!!.note) },
            confirmButton = {
                TextButton(onClick = { UpdateManager.openBrowserDownload(context, newVersion!!.downloadUrl); showUpdateDialog = false }) {
                    Text("去下载", color = ZenGreenPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showUpdateDialog = false }) { Text("暂不更新", color = TextSecondary) } }
        )
    }

    RainbowBorderDialogSurface(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .heightIn(max = 700.dp)
        ) {
            Text("全局设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ZenDark)
            Spacer(modifier = Modifier.height(16.dp))

            // --- 字体列表 ---
            Text("字体风格 (点击下载)", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(modifier = Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(110.dp)
            ) {
                items(FontManager.fontList) { fontConfig: FontConfig ->
                    val isSelected = FontManager.currentFontName == fontConfig.name
                    val isDownloaded = FontManager.isFontDownloaded(fontConfig.code)
                    var downloadProgress by remember { mutableFloatStateOf(-1f) }
                    val isDownloading = downloadProgress >= 0f
                    val bgColor = if (isSelected) ZenGreenPrimary.copy(alpha = 0.1f) else Color(0xFFF1F5F9)
                    val borderColor = if (isSelected) ZenGreenPrimary else Color.Transparent

                    Surface(
                        shape = RoundedCornerShape(12.dp), color = bgColor, border = BorderStroke(2.dp, borderColor),
                        modifier = Modifier.height(50.dp).clickable(enabled = !isDownloading) {
                            if (isDownloaded) FontManager.switchFont(fontConfig.code)
                            else {
                                downloadProgress = 0f
                                scope.launch {
                                    val success = FontManager.downloadFont(fontConfig.code) { p -> downloadProgress = p }
                                    downloadProgress = -1f
                                    if (success) refreshTrigger++
                                }
                            }
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                            Text(fontConfig.name, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) ZenGreenPrimary else TextPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            if (isDownloading) CircularProgressIndicator(progress = { downloadProgress }, modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = ZenGreenPrimary, trackColor = ZenGreenPrimary.copy(alpha = 0.2f))
                            else if (isSelected) Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp), tint = ZenGreenPrimary)
                            else if (!isDownloaded) Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(14.dp), tint = TextSecondary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = Color(0xFFF1F5F9))
            Spacer(modifier = Modifier.height(16.dp))

            // --- AI & 代理设置 (可滚动) ---
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("AI 配置 (OpenRouter)", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                OutlinedTextField(value = apiKeys, onValueChange = { apiKeys = it }, label = { Text("Keys") }, placeholder = { Text("API Keys (换行分隔)") }, modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 3, textStyle = TextStyle(fontSize = 12.sp))
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Models") }, placeholder = { Text("x-ai/grok-4.1-fast") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = TextStyle(fontSize = 12.sp))
                OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("API") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = TextStyle(fontSize = 12.sp))

                Spacer(modifier = Modifier.height(8.dp))

                // 代理与更新
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(x = (-12).dp)) {
                        Checkbox(checked = enableProxy, onCheckedChange = { enableProxy = it })
                        Text("全局代理", fontSize = 14.sp, color = TextPrimary)
                    }
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF8FAFC), border = BorderStroke(1.dp, Color(0xFFE2E8F0)), modifier = Modifier.height(32.dp).clickable(enabled = !isCheckingUpdate) {
                        isCheckingUpdate = true; updateCheckMessage = null
                        scope.launch {
                            val version = UpdateManager.checkUpdate(context); isCheckingUpdate = false
                            if (version != null) { newVersion = version; showUpdateDialog = true } else { updateCheckMessage = "已是最新" }
                        }
                    }) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                            if (isCheckingUpdate) CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = ZenGreenPrimary)
                            else Text(text = updateCheckMessage ?: "检查更新", fontSize = 11.sp, color = if (updateCheckMessage != null) ZenGreenPrimary else TextSecondary, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                if (enableProxy) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = proxyHost, onValueChange = { proxyHost = it }, label = { Text("主机") }, placeholder = { Text("如 127.0.0.1") }, modifier = Modifier.weight(2f), singleLine = true, minLines = 1, textStyle = TextStyle(fontSize = 12.sp))
                        OutlinedTextField(value = proxyPort, onValueChange = { if (it.all { c -> c.isDigit() }) proxyPort = it }, label = { Text("端口") }, placeholder = { Text("如 7890") }, modifier = Modifier.weight(1f), singleLine = true, minLines = 1, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = TextStyle(fontSize = 12.sp))
                    }
                    Text("注：请在VPN软件中开启'允许局域网/HTTP代理'，通常主机为 127.0.0.1", fontSize = 10.sp, color = TextSecondary, lineHeight = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    AiConfigManager.apiKeys = apiKeys.trim(); AiConfigManager.model = model.trim(); AiConfigManager.baseUrl = baseUrl.trim()
                    AiConfigManager.enableProxy = enableProxy; AiConfigManager.proxyHost = proxyHost.trim(); AiConfigManager.proxyPort = proxyPort.trim()
                    onDismiss()
                }, colors = ButtonDefaults.buttonColors(containerColor = ZenGreenPrimary)) { Text("保存") }
            }
        }
    }
}

// [弹窗] 使用说明
@Composable
fun GuideDialog(onDismiss: () -> Unit) {
    RainbowBorderDialogSurface(onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, tint = ZenGreenPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("使用说明", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))

            val steps = listOf(
                "1. 首页模式" to "支持按章节顺序刷题，或按题型专项训练。",
                "2. 联网刷题" to "输入关键词（如“太阳病”），AI 会实时生成 10 道新题。支持自动存入本地题库。",
                "3. AI 绘图" to "输入中文描述，AI 自动生成精美插图。",
                "4. 错题复习" to "做错的题会自动加入错题本，答对一次即可移除。",
                "5. AI 助教" to "答题时点击 ✨，AI 会深度解析当前题目，点击刷新可重新提问。",
                "6. 更多惊喜" to "App 内隐藏了一些有趣的彩蛋，试着在首页多点点看？"
            )

            steps.forEach { (title, desc) ->
                Text(title, fontWeight = FontWeight.Bold, color = ZenGreenPrimary, fontSize = 15.sp)
                Text(desc, color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(modifier = Modifier.height(12.dp))
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ZenGreenPrimary)
            ) {
                Text("我知道了")
            }
        }
    }
}

// [弹窗] 快速跳转
@Composable
fun JumpDialog(totalCount: Int, currentIndex: Int, onDismiss: () -> Unit, onJump: (Int) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.6f).padding(16.dp),
            shape = RoundedCornerShape(24.dp), color = Color.White, shadowElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("快速跳转", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = TextSecondary) }
                }
                Spacer(modifier = Modifier.height(16.dp))
                LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 56.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    items(totalCount) { index ->
                        val isCurrent = index == currentIndex; val isCompleted = ProgressManager.isCompleted(index.toString())
                        Box(modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(if (isCurrent) ZenGreenPrimary else if (isCompleted) ZenGreenAccent.copy(alpha = 0.2f) else Color(0xFFF1F5F9)).clickable { onJump(index) }, contentAlignment = Alignment.Center) {
                            Text(text = "${index + 1}", color = if (isCurrent) Color.White else TextPrimary, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}

// [弹窗] 彩蛋
@Composable
fun EasterEggDialog(onDismiss: () -> Unit) {
    RainbowBorderDialogSurface(onDismiss = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 40.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "🎉", fontSize = 80.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(24.dp))
            val infiniteTransition = rememberInfiniteTransition(label = "text_flow")
            val offsetAnim by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 1000f, animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart), label = "")
            val textBrush = Brush.linearGradient(colors = RainbowColors, start = Offset(offsetAnim, 0f), end = Offset(offsetAnim + 500f, 0f), tileMode = TileMode.Mirror)

            Text(text = "彩蛋解锁", style = TextStyle(brush = textBrush, fontSize = 32.sp, fontWeight = FontWeight.Black), textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(28.dp))
            Text(text = "邱邱宝宝加油加油\n你的老公永远爱你", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151), lineHeight = 34.sp, textAlign = TextAlign.Center)
        }
    }
}

// [弹窗] AI 响应结果
@Composable
fun AiResponseDialog(content: String, isLoading: Boolean, onDismiss: () -> Unit, onRefresh: () -> Unit) {
    val scaleAnim = remember { Animatable(0.8f) }
    val infiniteTransition = rememberInfiniteTransition(label = "ai_flow")
    val offsetAnim by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 2000f, animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart), label = "")
    val flowBrush = Brush.linearGradient(colors = RainbowColors, start = Offset(offsetAnim, offsetAnim), end = Offset(offsetAnim + 1000f, offsetAnim + 1000f), tileMode = TileMode.Mirror)
    LaunchedEffect(Unit) { scaleAnim.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }

    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).graphicsLayer { scaleX = scaleAnim.value; scaleY = scaleAnim.value }) {
            Box(modifier = Modifier.matchParentSize().scale(1.02f).clip(RoundedCornerShape(28.dp)).background(flowBrush).blur(16.dp))
            Surface(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(24.dp), color = Color.White) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AutoAwesome, null, tint = ZenGreenPrimary); Spacer(modifier = Modifier.width(8.dp)); Text("AI 智能解析", style = TextStyle(brush = flowBrush, fontWeight = FontWeight.Bold, fontSize = 18.sp)) } }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!isLoading && content.isNotBlank()) IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, null, tint = ZenGreenPrimary) }
                            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = TextSecondary) }
                        }
                    }
                    Divider(color = Color.Black.copy(0.05f))
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(20.dp), contentAlignment = Alignment.TopStart) {
                        if (content.isBlank() && isLoading) { Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = ZenGreenPrimary); Spacer(modifier = Modifier.height(16.dp)); Text("AI 正在思考中...", color = TextSecondary) } }
                        else { Column(modifier = Modifier.verticalScroll(rememberScrollState())) { MarkdownText(markdown = content, color = TextPrimary, fontSize = 16.sp, lineHeight = 28.sp); if (isLoading) Text("▌", color = ZenGreenPrimary, modifier = Modifier.padding(top = 4.dp)) } }
                    }
                }
            }
        }
    }
}

// [组件] 弱点卡片
@Composable
fun WeaknessCardV2(title: String, content: String, index: Int, total: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(4.dp), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxHeight(0.98f).fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(ZenGreenPrimary.copy(alpha = 0.15f), Color.White))).padding(20.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Surface(color = ZenGreenPrimary, shape = CircleShape, modifier = Modifier.size(28.dp)) { Box(contentAlignment = Alignment.Center) { Text(text = "$index", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) } }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = ZenDark, lineHeight = 32.sp)
                }
            }
            Divider(color = Color(0xFFF1F5F9))
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp)) { MarkdownText(markdown = content, color = TextPrimary, fontSize = 16.sp, lineHeight = 30.sp) }
            Box(modifier = Modifier.fillMaxWidth().height(8.dp).background(ZenGreenPrimary.copy(alpha = 0.5f)))
        }
    }
}