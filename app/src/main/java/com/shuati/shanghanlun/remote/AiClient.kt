package com.shuati.shanghanlun.data.remote

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.shuati.shanghanlun.config.AppConfig
import com.shuati.shanghanlun.data.model.Option
import com.shuati.shanghanlun.data.model.Question
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
        val index = keyIndex.getAndIncrement() % keys.size
        return keys[index]
    }

    // [核心] 通用请求方法
    private fun sendRequestStream(prompt: String, isStreaming: Boolean): Flow<String> = flow {
        val currentKey = getNextKey()
        if (currentKey.isBlank()) {
            emit("❌ 错误：未配置 API Key，请在设置中添加。")
            return@flow
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL(AiConfigManager.baseUrl)

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
            // [修改] HTTP Referer 和 Title 也使用配置 (虽然不关键，但保持一致更好)
            connection.setRequestProperty("HTTP-Referer", "https://github.com/YourApp")
            connection.setRequestProperty("X-Title", AppConfig.AI_HEADER_TITLE)
            connection.connectTimeout = 15000
            connection.readTimeout = 60000
            connection.doOutput = true

            val payloadMap = mapOf(
                "model" to AiConfigManager.model,
                "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
                "stream" to isStreaming
            )
            val jsonBody = gson.toJson(payloadMap)

            connection.outputStream.use { os -> os.write(jsonBody.toByteArray(Charsets.UTF_8)) }

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val inputStream = connection.inputStream
                val reader = BufferedReader(inputStream.reader())

                if (isStreaming) {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val dataLine = line?.trim() ?: continue
                        if (dataLine.startsWith("data:")) {
                            val jsonStr = dataLine.removePrefix("data:").trim()
                            if (jsonStr == "[DONE]") break
                            try {
                                val chunkObj = gson.fromJson(jsonStr, Map::class.java)
                                val choices = chunkObj["choices"] as? List<*>
                                val delta = (choices?.get(0) as? Map<*, *>)?.get("delta") as? Map<*, *>
                                val content = delta?.get("content") as? String
                                if (!content.isNullOrEmpty()) {
                                    emit(content)
                                }
                            } catch (e: Exception) { }
                        }
                    }
                } else {
                    val responseText = reader.readText()
                    val responseObj = gson.fromJson(responseText, Map::class.java)
                    val choices = responseObj["choices"] as? List<*>
                    val message = (choices?.get(0) as? Map<*, *>)?.get("message") as? Map<*, *>
                    val content = message?.get("content") as? String ?: ""
                    emit(content)
                }
            } else {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "无详细信息"
                val errorMsg = when (responseCode) {
                    401 -> "❌ 认证失败 (401)：API Key 无效或过期。"
                    403 -> "❌ 拒绝访问 (403)：该地区被禁止或账号异常。"
                    429 -> "❌ 请求过多 (429)：达到速率限制，正在切换..."
                    500, 502, 503 -> "❌ 服务器错误 ($responseCode)。"
                    else -> "❌ 请求失败 ($responseCode)：$errorText"
                }
                emit(errorMsg)
            }
        } catch (e: Exception) {
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

    // =========================================================
    //  以下是适配 Factory 模式的业务方法
    // =========================================================

    // 1. 问答/解析 (使用流式)
    fun askAiStream(question: String, answer: String, fullAnalysis: String): Flow<String> {
        // [修改] 直接拼接，不包含任何硬编码指令
        val prompt = """
            ${AppConfig.AiPrompts.ROLE_ANALYSIS}
            
            题目：$question
            参考答案：$answer
            
            ${AppConfig.AiPrompts.PROMPT_ANALYSIS_TEMPLATE}
        """.trimIndent()
        return sendRequestStream(prompt, true)
    }

    // 2. 弱点分析 (使用流式)
    suspend fun analyzeWeakness(mistakeQuestions: List<Question>): List<Pair<String, String>> {
        if (mistakeQuestions.isEmpty()) return listOf("暂无错题" to "快去刷题吧！")

        val sampleQuestions = mistakeQuestions.takeLast(15)
        // 简单的题目列表拼接
        val questionsText = sampleQuestions.mapIndexed { i, q ->
            "${i+1}. [${q.category}] ${q.content}"
        }.joinToString("\n")

        // [核心修改] 这里不再包含“数量与策略”、“格式要求”等硬编码
        // 所有的指令都已包含在 AppConfig.AiPrompts.PROMPT_WEAKNESS_SYSTEM 中
        val prompt = """
            ${AppConfig.AiPrompts.PROMPT_WEAKNESS_SYSTEM}
            
            以下是学生最近做错的题目：
            $questionsText
        """.trimIndent()

        val sb = StringBuilder()
        sendRequestStream(prompt, false).collect { sb.append(it) }
        val content = sb.toString()

        if (content.startsWith("❌")) {
            return listOf("分析失败" to content)
        }

        // 解析 AI 返回的格式 (标题#内容|||...)
        return content.split("|||").mapNotNull {
            val parts = it.trim().split("#")
            if (parts.size >= 2) parts[0].trim() to parts[1].trim() else null
        }.ifEmpty { listOf("分析完成" to content) }
    }

    // 3. 联网搜题 (必须非流式)
    suspend fun generateOnlineQuestions(keyword: String, count: Int): List<Question> {
        // [核心修改] 所有的 JSON 格式要求、题型映射、分类要求，
        // 现在都由 Python 脚本预先注入到了 PROMPT_SEARCH_GENERATION 里。
        // 我们只需要用 format 把关键词和数量填进去即可。
        val promptTemplate = AppConfig.AiPrompts.PROMPT_SEARCH_GENERATION

        // 简单的容错处理
        val prompt = try {
            promptTemplate.format(keyword, count)
        } catch (e: Exception) {
            // 万一格式化失败，回退到简单拼接
            "$promptTemplate\n关键词：$keyword, 数量：$count"
        }

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

    // 4. AI 绘图
    fun generateImageCreation(userDescription: String): Flow<String> {
        // [修改] 角色也从 Config 读取，保持一致性
        val role = AppConfig.AiPrompts.IMAGE_GEN_ROLE

        val prompt = """
            你好，$role
            
            用户给出的中文关键词描述是：【$userDescription】
            
            请你执行以下步骤：
            1. 理解用户的描述，进行艺术加工、润色，丰富光影、细节、风格等描述。
            2. 将润色后的描述翻译成英文 Prompt。
            3. 将英文 Prompt 经过 URL 编码 (例如空格转为 %20) 后，填充到下方链接的 {prompt} 处。
            
            请直接输出一张 Markdown 图片，格式如下（不要输出其他废话）：
            ![AI Image](https://image.pollinations.ai/prompt/{prompt}?width=1024&height=1024&enhance=true&private=true&nologo=true&safe=true&model=flux)
        """.trimIndent()

        return sendRequestStream(prompt, true)
    }
}