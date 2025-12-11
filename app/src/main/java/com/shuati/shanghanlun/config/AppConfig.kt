package com.shuati.shanghanlun.config

/**
 * 【母版配置 - 伤寒论默认版】
 * 这个文件定义了 APP 的静态配置和 AI 提示词模板。
 *
 * 注意：
 * 1. 在母版直接运行时，这里的值生效。
 * 2. 使用 Python 脚本生成新 APP 时，脚本会完全重写这个文件，注入新的配置。
 */
object AppConfig {
    // ================= 1. 基础资源配置 =================
    // 题库文件名 (必须与 assets 里的文件名一致)
    const val ASSET_QUESTION_FILE = "questions_full.json"

    // ================= 2. UI 文案配置 =================
    const val UI_TITLE_MAIN = "伤寒论"
    const val UI_SUBTITLE_MAIN = "中医经典 · 刷题宝典"
    const val UI_AUTHOR_CREDIT = "Designed by 邝梓濠"

    // ================= 3. 联网功能配置 =================
    const val VERSION_CHECK_URL = "https://xn--r7qu00bb2c.top/version.json"
    const val AI_HEADER_TITLE = "ShangHanLun Quiz" // 请求头标识

    // ================= 4. AI 提示词模板 (核心) =================
    object AiPrompts {
        // [场景 A] 基础人设
        const val ROLE_ANALYSIS = "你是一位中医专家。"

        // [场景 B] 单题解析 (问答)
        // AiClient 会将 $question, $answer 拼接到这段文字前面
        val PROMPT_ANALYSIS_TEMPLATE = """
            请解析这道题：
            1. 核心考点。
            2. 为什么选该答案。
            3. 排除干扰项（如果是选择题）。
            
            要求：Markdown格式，精练，200字以内。
        """.trimIndent()

        // [场景 C] 弱点分析 (错题本)
        // AiClient 会把错题列表拼接到这段文字后面
        val PROMPT_WEAKNESS_SYSTEM = """
            你是一位中医考研辅导专家。以下是学生最近做错的题目：
            
            ${'$'}questionsText
            
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

        // [场景 D] 联网搜题 (关键词生成)
        // 注意：%s 是关键词占位符，%d 是数量占位符 (由 Kotlin String.format 填充)
        val PROMPT_SEARCH_GENERATION = """
            请根据关键词 %s，生成 %d 道中医（伤寒论课程）题目。
            
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
            3. 题目数量尽量接近 %d 道。
        """.trimIndent()

        // [场景 E] AI 绘图角色
        const val IMAGE_GEN_ROLE = "现在你的角色是AI图片生成机器人。"
    }
}