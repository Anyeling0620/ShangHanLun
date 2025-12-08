package com.example.killquestion.data.remote

import com.example.killquestion.data.model.Option
import com.example.killquestion.data.model.Question
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger

object SimpleAiClient {
    private val gson = Gson()
    // 用于轮询 Key 的索引
    private val keyIndex = AtomicInteger(0)

    // 获取下一个 Key (轮询策略)
    private fun getNextKey(): String {
        val keys = AiConfigManager.getKeyList()
        if (keys.isEmpty()) return ""
        // 简单的轮询：每次调用索引+1，取模
        val index = keyIndex.getAndIncrement() % keys.size
        return keys[index]
    }

    // [核心] 通用请求方法：返回 Flow<String> 实现流式
    // isStreaming: 是否开启流式模式 (true: 逐字返回, false: 一次性返回)
    private fun sendRequestStream(prompt: String, isStreaming: Boolean): Flow<String> = flow {
        val currentKey = getNextKey()
        if (currentKey.isBlank()) {
            emit("❌ 错误：未配置 API Key，请在设置中添加。")
            return@flow
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL(AiConfigManager.baseUrl)

            // [特性3] 配置代理
            connection = if (AiConfigManager.enableProxy) {
                val port = AiConfigManager.proxyPort.toIntOrNull() ?: 7890
                val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(AiConfigManager.proxyHost, port))
                url.openConnection(proxy) as HttpURLConnection
            } else {
                url.openConnection() as HttpURLConnection
            }

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Authorization", "Bearer $currentKey")
            connection.setRequestProperty("HTTP-Referer", "https://github.com/YourApp")
            connection.setRequestProperty("X-Title", "ShangHanLun Quiz")
            connection.connectTimeout = 15000 // 15秒连接超时
            connection.readTimeout = 60000    // 60秒读取超时
            connection.doOutput = true

            // [特性4] 开启 stream: true
            val payloadMap = mapOf(
                "model" to AiConfigManager.model,
                "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
                "stream" to isStreaming // 动态决定是否流式
            )
            val jsonBody = gson.toJson(payloadMap)

            connection.outputStream.use { os -> os.write(jsonBody.toByteArray(Charsets.UTF_8)) }

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val inputStream = connection.inputStream
                val reader = BufferedReader(inputStream.reader())

                if (isStreaming) {
                    // --- 流式处理 ---
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val dataLine = line?.trim() ?: continue
                        if (dataLine.startsWith("data:")) {
                            val jsonStr = dataLine.removePrefix("data:").trim()
                            if (jsonStr == "[DONE]") break

                            try {
                                // 解析流式 JSON: {"choices":[{"delta":{"content":"..."}}]}
                                val chunkObj = gson.fromJson(jsonStr, Map::class.java)
                                val choices = chunkObj["choices"] as? List<*>
                                val delta = (choices?.get(0) as? Map<*, *>)?.get("delta") as? Map<*, *>
                                val content = delta?.get("content") as? String

                                if (!content.isNullOrEmpty()) {
                                    emit(content) // 发射每一个字
                                }
                            } catch (e: Exception) {
                                // 忽略解析错误的行
                            }
                        }
                    }
                } else {
                    // --- 非流式处理 (用于搜题生成JSON) ---
                    val responseText = reader.readText()
                    val responseObj = gson.fromJson(responseText, Map::class.java)
                    val choices = responseObj["choices"] as? List<*>
                    val message = (choices?.get(0) as? Map<*, *>)?.get("message") as? Map<*, *>
                    val content = message?.get("content") as? String ?: ""
                    emit(content)
                }
            } else {
                // [特性1] 详细错误处理
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "无详细信息"
                val errorMsg = when (responseCode) {
                    401 -> "❌ 认证失败 (401)：API Key 无效或过期。"
                    403 -> "❌ 拒绝访问 (403)：该地区被禁止或账号异常。"
                    429 -> "❌ 请求过多 (429)：达到速率限制，正在尝试切换 Key..." // 这里未来可以做自动重试
                    500, 502, 503 -> "❌ 服务器错误 ($responseCode)：AI 服务商暂时不可用。"
                    else -> "❌ 请求失败 ($responseCode)：$errorText"
                }
                emit(errorMsg)
            }

        } catch (e: Exception) {
            // [特性1] 网络异常详细分类
            val errorMsg = when (e) {
                is UnknownHostException -> "❌ 网络错误：找不到服务器，请检查网络或代理设置。"
                is SocketTimeoutException -> "❌ 连接超时：AI 响应太慢，请检查网络。"
                else -> "❌ 未知错误：${e.localizedMessage}"
            }
            emit(errorMsg)
        } finally {
            connection?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    // 1. 问答/解析 (使用流式)
    fun askAiStream(question: String, answer: String, fullAnalysis: String): Flow<String> {
        val prompt = """
            你是一位中医专家。
            题目：$question
            参考答案：$answer
            
            请解析这道题：
            1. 核心考点。
            2. 为什么选该答案。
            3. 排除干扰项（如果是选择题）。
            
            要求：Markdown格式，精练，200字以内。
        """.trimIndent()
        return sendRequestStream(prompt, true)
    }

    // 2. 弱点分析 (使用流式)
    suspend fun analyzeWeakness(mistakeQuestions: List<Question>): List<Pair<String, String>> {
        if (mistakeQuestions.isEmpty()) return listOf("暂无错题" to "快去刷题吧！")
        val sampleQuestions = mistakeQuestions.takeLast(15)
        val questionsText = sampleQuestions.mapIndexed { i, q -> "${i+1}. [${q.category}] ${q.content}" }.joinToString("\n")

        val prompt = """
            你是一位中医考研辅导专家。以下是学生最近做错的题目：
            
            $questionsText
            
            请为这些错题制作【复习知识卡片】。
            
            【数量与策略】：
            1. **不要**进行笼统的概括。
            2. 请尽量为**每一个**具体的知识点/汤证/病机生成一张独立的卡片。
            3. 如果多道题考的是同一个汤证（如都是桂枝汤），请合并为一张深度解析卡片。
            4. 目标是生成尽可能详细的复习资料，卡片数量根据题目实际考点数量决定（不设上限）。

            【卡片内容要求】：
            必须包含以下分点（使用 Markdown 列表）：
            - **易错点**：指出为什么容易做错。
            - **核心考点详解**：深度剖析该方剂或条文。
            - **辨证眼目/口诀**：辅助记忆的关键词。

            【格式严格要求】：
            1. 格式：知识点标题#知识点内容
            2. 每张卡片之间用 "|||" 分隔。
            3. 标题纯文本，内容使用 Markdown。
        """.trimIndent()

        // 收集 Flow 结果拼接成字符串
        val sb = StringBuilder()
        sendRequestStream(prompt, false).collect { sb.append(it) }
        val content = sb.toString()

        if (content.startsWith("❌")) {
            return listOf("分析失败" to content)
        }

        return content.split("|||").mapNotNull {
            val parts = it.trim().split("#")
            if (parts.size >= 2) parts[0].trim() to parts[1].trim() else null
        }.ifEmpty { listOf("分析完成" to content) }
    }

    // 3. 联网搜题 (必须非流式)
    suspend fun generateOnlineQuestions(keyword: String, count: Int): List<Question> {
        val prompt = """
            请根据关键词【$keyword】，生成 $count 道中医（伤寒论课程）题目。
            
            【题型混合要求】：
            请按适合该知识点的形式，混合以下题型（不要局限于选择题）：
            1. **A1/A2型题** (单选) -> type: "SINGLE_CHOICE"
            2. **X型题** (多选) -> type: "MULTI_CHOICE"
            3. **判断说明题** -> type: "TRUE_FALSE" (选项放 A:正确, B:错误)
            4. **填空题** -> type: "FILL_BLANK" (options留空)
            5. **名词解释题/简答题/论述题/病例分析题** -> type: "ESSAY" (options留空)
            
            【JSON 格式要求】：
            必须返回严格的 JSON 数组，JSON 结构如下：
            [
              {
                "type": "SINGLE_CHOICE",
                "category": "A1型题",
                "content": "题目内容",
                "options": [
                  {"label": "A", "text": "选项内容"}
                ],
                "answer": "A",
                "analysis": "解析内容"
              }
            ]
            
            【内容要求】：
            1. 难度适中，符合中医执业医师/考研标准。
            2. 确保 JSON 格式合法，不要包含 ```json 等标记。
            3. 题目数量尽量接近 $count 道。
        """.trimIndent()

        val sb = StringBuilder()
        sendRequestStream(prompt, false).collect { sb.append(it) }
        val jsonStr = sb.toString()

        if (jsonStr.startsWith("❌")) return emptyList()

        val cleanJson = jsonStr.replace("```json", "").replace("```", "").trim()

        return try {
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val rawList: List<Map<String, Any>> = gson.fromJson(cleanJson, type)
            rawList.mapIndexed { index, map ->
                val rawOptions = map["options"] as? List<Map<String, String>> ?: emptyList()
                val options = rawOptions.map { Option(it["label"] ?: "", it["text"] ?: "") }
                Question(
                    id = "online_${System.currentTimeMillis()}_$index",
                    number = 0,
                    chapter = "联网搜索: $keyword",
                    category = map["category"] as? String ?: "综合题",
                    type = map["type"] as? String ?: "ESSAY",
                    content = map["content"] as? String ?: "加载失败",
                    options = options,
                    answer = map["answer"] as? String ?: "略",
                    analysis = map["analysis"] as? String ?: "暂无"
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    // [新增] 4. AI 绘图 (生成 Pollinations 图片链接)
    fun generateImageCreation(userDescription: String): Flow<String> {
        val modelName = AiConfigManager.model
        // 构建你要求的 Prompt
        val prompt = """
            你好 $modelName，现在你的角色是AI图片生成机器人。
            
            用户给出的中文关键词描述是：【$userDescription】
            
            请你执行以下步骤：
            1. 理解用户的描述，进行艺术加工、润色，丰富光影、细节、风格等描述（不要偏离原意）。
            2. 将润色后的描述翻译成英文 Prompt。
            3. 将英文 Prompt 经过 URL 编码 (例如空格转为 %20) 后，填充到下方链接的 {prompt} 处。
            
            请直接输出一张 Markdown 图片，格式如下（不要输出其他废话）：
            ![AI Image](https://image.pollinations.ai/prompt/{prompt}?width=1024&height=1024&enhance=true&private=true&nologo=true&safe=true&model=flux)
        """.trimIndent()

        // 使用流式请求，虽然只要一个链接，但保持统一
        return sendRequestStream(prompt, true)
    }
}