package com.example.killquestion.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.killquestion.data.local.MistakeManager
import com.example.killquestion.data.local.ProgressManager
import com.example.killquestion.data.model.Option
import com.example.killquestion.data.model.Question
import com.example.killquestion.data.model.QuestionType
import com.example.killquestion.data.model.QuizContext
import com.example.killquestion.data.remote.SimpleAiClient
import com.example.killquestion.ui.components.BouncyButton
import com.example.killquestion.ui.components.GlassyCard
import com.example.killquestion.ui.components.TypeCapsule
import com.example.killquestion.ui.dialogs.AiResponseDialog
import com.example.killquestion.ui.dialogs.JumpDialog
import com.example.killquestion.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun QuizScreen(context: QuizContext, onBack: () -> Unit) {
    val questions = context.questions
    val initialIndex = remember {
        if (context.isMistakeMode) 0
        else {
            val idx = questions.indexOfFirst { !ProgressManager.isCompleted(it.id) }
            if (idx == -1) 0 else idx
        }
    }

    var currentIndex by remember { mutableStateOf(initialIndex) }
    var showAnswer by remember { mutableStateOf(false) }
    var selectedLabels by remember { mutableStateOf(setOf<String>()) }
    var showJumpDialog by remember { mutableStateOf(false) }

    val currentQ = if (questions.isNotEmpty()) questions[currentIndex] else return
    val uiType = currentQ.getUiType()

    val mistakeTrigger = MistakeManager.mistakeChangeTrigger
    val isMistake = remember(currentQ.id, mistakeTrigger) { MistakeManager.isMistake(currentQ.id) }
    val isLast = currentIndex == questions.size - 1

    // AI 相关状态
    var showAiDialog by remember { mutableStateOf(false) }
    var aiResponseText by remember { mutableStateOf("") } // [修改] 用于显示累积的文本
    var aiLoading by remember { mutableStateOf(false) } // 这里的 loading 含义变为“正在生成中”

    // 缓存每题的完整回复
    val aiCache = remember { mutableStateMapOf<String, String>() }

    val scope = rememberCoroutineScope()

    LaunchedEffect(currentIndex) {
        showAnswer = false
        selectedLabels = emptySet()
    }

    fun handleSubmit() {
        showAnswer = true
        if (!context.isMistakeMode) {
            ProgressManager.markCompleted(currentQ.id)
        }
        val userAns = selectedLabels.sorted().joinToString("")
        val realAns = currentQ.getRealAnswer()
        val isCorrect = userAns == realAns

        if (uiType in listOf(QuestionType.SINGLE_CHOICE, QuestionType.MULTI_CHOICE, QuestionType.TRUE_FALSE)) {
            if (!isCorrect) {
                MistakeManager.addMistake(currentQ.id)
            } else if (context.isMistakeMode) {
                MistakeManager.removeMistake(currentQ.id)
            }
        }
    }

    fun handleNavigation(isNext: Boolean) {
        showAnswer = false
        selectedLabels = emptySet()
        if (isNext && !isLast) currentIndex++
        else if (!isNext && currentIndex > 0) currentIndex--
    }

    if (showJumpDialog) {
        JumpDialog(
            totalCount = questions.size,
            currentIndex = currentIndex,
            onDismiss = { showJumpDialog = false },
            onJump = { index ->
                showAnswer = false
                selectedLabels = emptySet()
                currentIndex = index
                showJumpDialog = false
            }
        )
    }

    // [修改] 流式请求处理
    fun handleAskAi(forceRefresh: Boolean = false) {
        showAiDialog = true

        // 1. 如果有缓存且不强制刷新，直接显示缓存
        if (!forceRefresh && aiCache.containsKey(currentQ.id)) {
            aiResponseText = aiCache[currentQ.id] ?: ""
            aiLoading = false
            return
        }

        // 2. 开始新请求
        aiLoading = true
        aiResponseText = "" // 清空当前显示
        if (forceRefresh) {
            aiCache.remove(currentQ.id)
        }

        scope.launch {
            // 调用流式接口
            SimpleAiClient.askAiStream(
                question = currentQ.content,
                answer = currentQ.getRealAnswer(),
                fullAnalysis = currentQ.analysis
            ).collect { chunk ->
                // 每收到一个片段，就追加到文本中
                aiResponseText += chunk
            }

            // 收集完毕
            aiCache[currentQ.id] = aiResponseText
            aiLoading = false
        }
    }

    if (showAiDialog) {
        AiResponseDialog(
            content = aiResponseText, // [关键] 这里传入的是实时变化的文本
            isLoading = aiLoading,    // 只要流还在继续，loading 就是 true
            onDismiss = { showAiDialog = false },
            onRefresh = { handleAskAi(forceRefresh = true) }
        )
    }

    val accuracyStr = remember(questions, MistakeManager.mistakeChangeTrigger, showAnswer, currentIndex) {
        val answeredList = questions.filter { ProgressManager.isCompleted(it.id) }
        val totalAnswered = answeredList.size
        if (totalAnswered == 0) "0%" else {
            val errorCount = answeredList.count { MistakeManager.isMistake(it.id) }
            val correctCount = totalAnswered - errorCount
            val percent = (correctCount.toFloat() / totalAnswered * 100).toInt()
            "$percent%"
        }
    }

    Scaffold(
        topBar = {
            QuizTopBar(
                title = context.title,
                current = currentIndex + 1,
                total = questions.size,
                accuracy = accuracyStr,
                onBack = onBack,
                onJumpRequest = { showJumpDialog = true },
                onAiRequest = { handleAskAi(forceRefresh = false) }
            )
        },
        bottomBar = {
            QuizBottomBar(
                showAnswer = showAnswer,
                canSubmit = selectedLabels.isNotEmpty() || uiType == QuestionType.FILL_BLANK || uiType == QuestionType.ESSAY,
                isLast = isLast,
                isMistake = isMistake,
                onToggleMistake = { MistakeManager.toggleMistake(currentQ.id) },
                onSubmit = { handleSubmit() },
                onNext = { handleNavigation(true) },
                onPrev = { handleNavigation(false) }
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 20.dp, bottom = 16.dp)
            ) {
                TypeCapsule(text = currentQ.category, color = ZenGreenPrimary)
                if (context.isMistakeMode) {
                    Spacer(modifier = Modifier.width(8.dp))
                    TypeCapsule(text = "错题复习", color = ColorMistake)
                }
            }

            Text(
                text = currentQ.content,
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(32.dp))

            when (uiType) {
                QuestionType.SINGLE_CHOICE, QuestionType.TRUE_FALSE, QuestionType.MULTI_CHOICE -> {
                    OptionList(
                        options = currentQ.options,
                        selectedLabels = selectedLabels,
                        showAnswer = showAnswer,
                        correctAnswer = currentQ.getRealAnswer(),
                        onSelect = { label ->
                            if (!showAnswer) {
                                selectedLabels = if (uiType == QuestionType.MULTI_CHOICE) {
                                    if (selectedLabels.contains(label)) selectedLabels - label else selectedLabels + label
                                } else {
                                    setOf(label)
                                }
                            }
                        }
                    )
                }
                else -> {
                    SubjectiveAnswerCard(showAnswer) { handleSubmit() }
                }
            }

            if (showAnswer) {
                Spacer(modifier = Modifier.height(32.dp))
                AnalysisCard(currentQ)
                Spacer(modifier = Modifier.height(100.dp))
            } else if (!context.isMistakeMode && ProgressManager.isCompleted(questions.last().id) && isLast && showAnswer) {
                CompletionMessage()
            }
        }
    }
}

@Composable
fun QuizTopBar(
    title: String,
    current: Int,
    total: Int,
    accuracy: String,
    onBack: () -> Unit,
    onJumpRequest: () -> Unit,
    onAiRequest: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.background(Color.White.copy(alpha = 0.8f), CircleShape)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Black.copy(0.05f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(current.toFloat() / total)
                        .fillMaxHeight()
                        .background(ZenGreenPrimary)
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFE8F5E9),
                modifier = Modifier.height(32.dp),
                border = BorderStroke(1.dp, ZenGreenPrimary.copy(alpha = 0.1f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircleOutline,
                        contentDescription = null,
                        tint = ZenGreenPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = accuracy,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = ZenGreenPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = CircleShape,
                color = ZenGreenPrimary.copy(alpha = 0.1f),
                modifier = Modifier
                    .size(36.dp)
                    .clickable(onClick = onAiRequest)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = "Ask AI",
                        tint = ZenGreenPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.8f),
                shadowElevation = 1.dp,
                modifier = Modifier.clickable(onClick = onJumpRequest)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        "$current/$total",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun QuizBottomBar(
    showAnswer: Boolean,
    canSubmit: Boolean,
    isLast: Boolean,
    isMistake: Boolean,
    onToggleMistake: () -> Unit,
    onSubmit: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    Surface(
        shadowElevation = 24.dp,
        color = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPrev,
                modifier = Modifier
                    .size(50.dp)
                    .background(Color(0xFFF1F5F9), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextSecondary)
            }

            Spacer(modifier = Modifier.width(12.dp))

            IconButton(
                onClick = onToggleMistake,
                modifier = Modifier
                    .size(50.dp)
                    .background(if (isMistake) ColorStar.copy(alpha = 0.1f) else Color(0xFFF1F5F9), CircleShape)
            ) {
                Icon(
                    if (isMistake) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    null,
                    tint = if (isMistake) ColorStar else TextSecondary,
                    modifier = Modifier.scale(if (isMistake) 1.1f else 1f)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            BouncyButton(
                onClick = { if (showAnswer) onNext() else onSubmit() },
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (canSubmit || showAnswer) ZenGreenPrimary else Color(0xFFE2E8F0)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (showAnswer) (if (isLast) "完成本章" else "下一题") else "提交答案",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (canSubmit || showAnswer) Color.White else TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun OptionList(
    options: List<Option>,
    selectedLabels: Set<String>,
    showAnswer: Boolean,
    correctAnswer: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        options.forEach { option ->
            key(option.label) {
                val isSelected = selectedLabels.contains(option.label)
                val isCorrectOption = correctAnswer.contains(option.label)
                val LightGreenBg = Color(0xFFE8F5E9)

                val containerColor = when {
                    showAnswer && isCorrectOption -> ColorCorrect.copy(alpha = 0.1f)
                    showAnswer && isSelected && !isCorrectOption -> ColorWrong.copy(alpha = 0.1f)
                    isSelected -> LightGreenBg
                    else -> ZenSurface
                }

                val contentColor = when {
                    showAnswer && isCorrectOption -> ColorCorrect
                    showAnswer && isSelected && !isCorrectOption -> ColorWrong
                    isSelected -> ZenGreenPrimary
                    else -> TextSecondary
                }

                val textColor = when {
                    isSelected -> ZenDark
                    else -> TextPrimary
                }

                val animatedFill by animateColorAsState(containerColor, label = "fill")

                BouncyButton(onClick = { if(!showAnswer) onSelect(option.label) }) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = animatedFill,
                        border = null,
                        shadowElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected && !showAnswer) ZenGreenPrimary.copy(alpha = 0.2f)
                                        else if (containerColor != ZenSurface) contentColor.copy(alpha = 0.1f)
                                        else Color(0xFFF3F4F6)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = option.label,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected && !showAnswer) ZenGreenPrimary else if (containerColor != ZenSurface) contentColor else TextSecondary
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Text(
                                text = option.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected && !showAnswer) textColor else TextPrimary,
                                modifier = Modifier.weight(1f),
                                lineHeight = 24.sp
                            )

                            if (showAnswer) {
                                if (isCorrectOption) Icon(Icons.Default.CheckCircle, null, tint = ColorCorrect, modifier = Modifier.size(24.dp))
                                else if (isSelected) Icon(Icons.Default.Cancel, null, tint = ColorWrong, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SubjectiveAnswerCard(showAnswer: Boolean, onClick: () -> Unit) {
    if (!showAnswer) {
        BouncyButton(onClick = onClick) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(100.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 1.dp,
                border = BorderStroke(1.dp, ZenGreenPrimary.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.TouchApp, null, tint = ZenGreenPrimary, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("点击查看参考答案", color = ZenGreenPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AnalysisCard(question: Question) {
    val uiType = question.getUiType()
    val isTrueFalse = uiType == QuestionType.TRUE_FALSE
    val realAns = if (isTrueFalse) {
        if (question.getRealAnswer() == "A") "正确" else "错误"
    } else {
        question.getRealAnswer()
    }
    val fullExplanation = question.getFullExplanation()
    val isObjective = uiType in listOf(QuestionType.SINGLE_CHOICE, QuestionType.MULTI_CHOICE, QuestionType.TRUE_FALSE, QuestionType.FILL_BLANK)
    val ansFontSize = if (isObjective) MaterialTheme.typography.headlineMedium.fontSize else MaterialTheme.typography.bodyLarge.fontSize

    GlassyCard(
        backgroundColor = Color(0xFFF0FDF4),
        elevation = 0.dp,
        border = null
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = ZenGreenPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("解析", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ZenGreenPrimary)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text("正确答案", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = realAns,
                    fontSize = ansFontSize,
                    fontWeight = FontWeight.Bold,
                    color = ColorCorrect
                )
            }

            if (fullExplanation.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = ZenGreenPrimary.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = fullExplanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    lineHeight = 34.sp
                )
            }
        }
    }
}

@Composable
fun CompletionMessage() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.EmojiEvents, null, tint = ColorStar, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "恭喜！本章节已全部完成。",
            color = ZenGreenPrimary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(100.dp))
    }
}