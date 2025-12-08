package com.example.killquestion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.killquestion.data.local.MistakeManager
import com.example.killquestion.data.local.ProgressManager
import com.example.killquestion.data.model.QuizContext
import com.example.killquestion.data.remote.AiConfigManager
import com.example.killquestion.data.remote.SimpleAiClient
import com.example.killquestion.data.repository.QuestionRepository
import com.example.killquestion.ui.components.AnimatedBackground
import com.example.killquestion.ui.screens.QuizScreen
import com.example.killquestion.ui.screens.SelectionScreen
import com.example.killquestion.ui.screens.StartScreen
import com.example.killquestion.ui.screens.WeaknessAnalysisScreen // [新增引用]
import com.example.killquestion.ui.theme.*
import kotlinx.coroutines.launch
import com.example.killquestion.data.local.AnalysisStorage
import com.example.killquestion.data.local.CustomQuestionStorage
import com.example.killquestion.ui.screens.OnlineSearchScreen
import com.example.killquestion.data.local.FontManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ProgressManager.init(this)
        MistakeManager.init(this)
        AiConfigManager.init(this)
        AnalysisStorage.init(this)
        CustomQuestionStorage.init(this)
        FontManager.init(this)
        // [新增]：每次启动App，自动在后台检查并下载缺少的字体
        FontManager.startBackgroundDownload()

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = ZenGreenPrimary,
                    background = ZenBackgroundStart,
                    surface = ZenSurface,
                    onSurface = TextPrimary
                )
            ) {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf("START") }
    var quizContext by remember { mutableStateOf<QuizContext?>(null) }

    // [新增] 提升弱点分析的状态到这里，确保切换页面不丢失
    var weaknessCards by remember { mutableStateOf(AnalysisStorage.getCards()) }
    var isWeaknessAnalyzing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        QuestionRepository.load(context)
    }

    // 处理返回键
    BackHandler(enabled = currentScreen != "START") {
        when (currentScreen) {
            "QUIZ" -> currentScreen = if (quizContext?.isMistakeMode == true) "START" else quizContext?.sourceScreen ?: "START"
            "SELECTION_CHAPTER", "SELECTION_TYPE", "WEAKNESS_ANALYSIS", "ONLINE_SEARCH" -> currentScreen = "START" // [修改]
            else -> { }
        }
    }

    fun triggerWeaknessAnalysis(forceRefresh: Boolean = false) {
        // 如果不强制刷新，且内存有数据，直接跳
        if (!forceRefresh && weaknessCards.isNotEmpty()) {
            currentScreen = "WEAKNESS_ANALYSIS"
            return
        }

        // 如果强制刷新，或者内存没数据但本地有数据(且没选强制刷新)，这里其实已经被上面的初始化覆盖了
        // 所以这里主要处理“强制刷新”或“完全无数据”的情况

        currentScreen = "WEAKNESS_ANALYSIS"
        isWeaknessAnalyzing = true

        scope.launch {
            val mistakes = QuestionRepository.getMistakeQuestions()
            val result = SimpleAiClient.analyzeWeakness(mistakes)

            // [新增]：AI 返回结果后，保存到本地
            AnalysisStorage.saveCards(result)

            weaknessCards = result
            isWeaknessAnalyzing = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedBackground()

        // 注意：WeaknessAnalysisScreen 也是全屏的，不需要 systemBarsPadding (它内部处理了)
        // 但其他页面需要。
        Box(modifier = Modifier.fillMaxSize()) {
            when (currentScreen) {
                "START" -> {
                    // 给 StartScreen 加 padding
                    Box(modifier = Modifier.systemBarsPadding()) {
                        StartScreen(
                            onModeSelect = { mode ->
                                when (mode) {
                                    "CHAPTER" -> currentScreen = "SELECTION_CHAPTER"
                                    "TYPE" -> currentScreen = "SELECTION_TYPE"
                                    "ONLINE" -> currentScreen = "ONLINE_SEARCH"
                                    "MISTAKE" -> {
                                        val mistakes = QuestionRepository.getMistakeQuestions()
                                        if (mistakes.isNotEmpty()) {
                                            quizContext = QuizContext("错题本", mistakes, isMistakeMode = true, sourceScreen = "START")
                                            currentScreen = "QUIZ"
                                        }
                                    }
                                }
                            },
                            // [新增] 传入跳转回调
                            onWeaknessAnalysisClick = {
                                triggerWeaknessAnalysis(forceRefresh = false)
                            }
                        )
                    }
                }
                "SELECTION_CHAPTER" -> Box(modifier = Modifier.systemBarsPadding()) {
                    SelectionScreen(
                        title = "章节选择",
                        subtitle = "系统复习 · 循序渐进",
                        items = QuestionRepository.chapterList,
                        onBack = { currentScreen = "START" },
                        onSelect = { chapter ->
                            val qs = QuestionRepository.getQuestionsByChapter(chapter)
                            quizContext = QuizContext(chapter, qs, sourceScreen = "SELECTION_CHAPTER")
                            currentScreen = "QUIZ"
                        }
                    )
                }
                "SELECTION_TYPE" -> Box(modifier = Modifier.systemBarsPadding()) {
                    SelectionScreen(
                        title = "题型专练",
                        subtitle = "专项突破 · 查漏补缺",
                        // [关键修复]：这里传入 items 时，手动合并 题型列表 + 联网章节列表
                        // 这样 SelectionScreen 才能从 list 里分离出 onlineItems 并显示
                        items = QuestionRepository.categoryList +
                                QuestionRepository.chapterList.filter { it.startsWith("联网搜索") },

                        onBack = { currentScreen = "START" },
                        onSelect = { selectedItem ->
                            // ... 之前的逻辑 (判断 startsWith 联网搜索) 保持不变 ...
                            if (selectedItem.startsWith("联网搜索")) {
                                val qs = QuestionRepository.getQuestionsByChapter(selectedItem)
                                quizContext = QuizContext(selectedItem.removePrefix("联网搜索:"), qs, sourceScreen = "SELECTION_TYPE")
                            } else {
                                val qs = QuestionRepository.getQuestionsByCategory(selectedItem)
                                quizContext = QuizContext(selectedItem, qs, sourceScreen = "SELECTION_TYPE")
                            }
                            currentScreen = "QUIZ"
                        }
                    )
                }
                "ONLINE_SEARCH" -> Box(modifier = Modifier.systemBarsPadding()) {
                    OnlineSearchScreen(
                        onBack = { currentScreen = "START" },
                        onQuizStart = { title, questions ->
                            quizContext = QuizContext(title, questions, sourceScreen = "ONLINE_SEARCH")
                            currentScreen = "QUIZ"
                        }
                    )
                }
                "QUIZ" -> Box(modifier = Modifier.systemBarsPadding()) {
                    quizContext?.let { ctx ->
                        QuizScreen(
                            context = ctx,
                            onBack = {
                                currentScreen = if (ctx.isMistakeMode) "START" else ctx.sourceScreen
                            }
                        )
                    }
                }
                // [新增] 弱点分析页面 (新页面自己处理了padding，所以外面不包)
                "WEAKNESS_ANALYSIS" -> {
                    WeaknessAnalysisScreen(
                        cards = weaknessCards,
                        isLoading = isWeaknessAnalyzing,
                        onBack = { currentScreen = "START" },
                        onRefresh = { triggerWeaknessAnalysis(forceRefresh = true) }
                    )
                }
            }
        }
    }
}