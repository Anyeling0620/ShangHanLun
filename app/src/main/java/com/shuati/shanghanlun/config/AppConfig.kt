package com.shuati.shanghanlun.config

// 这个文件的内容将在生成新APP时被 Python 脚本整段替换
object AppConfig {
    // ================= 1. 基础配置 =================
    // 题库文件名 (生成新APP时，脚本会把新题库重命名为这个名字放进 assets)
    const val ASSET_QUESTION_FILE = "questions_full.json"

    // ================= 2. UI 文案 =================
    const val UI_TITLE_MAIN = "伤寒论"           // 首页大标题
    const val UI_SUBTITLE_MAIN = "中医经典 · 刷题宝典" // 首页副标题
    const val UI_AUTHOR_CREDIT = "Designed by 邝梓濠" // 底部署名

    // ================= 3. 联网配置 =================
    const val VERSION_CHECK_URL = "https://xn--r7qu00bb2c.top/version.json" // 版本更新地址

    // ================= 4. AI 核心人设 (核心) =================
    object AiPrompts {
        // [场景1] 单题解析
        const val ROLE_ANALYSIS = "你是一位中医专家。"
        val PROMPT_ANALYSIS_TEMPLATE = """
            请解析这道题：
            1. 核心考点。
            2. 为什么选该答案。
            3. 排除干扰项。
            要求：Markdown格式，精练，200字以内。
        """.trimIndent()

        // [场景2] 弱点分析 (错题本)
        val PROMPT_WEAKNESS_SYSTEM = """
            你是一位中医考研辅导专家。
            请为错题制作复习卡片。
            要求：包含易错点、核心考点详解、辨证眼目/口诀。
        """.trimIndent()

        // [场景3] 联网出题
        // 注意：%s 是关键词占位符，%d 是数量占位符
        val PROMPT_SEARCH_GENERATION = "请根据关键词【%s】，生成 %d 道中医（伤寒论课程）题目..."

        // [场景4] AI绘图
        const val IMAGE_GEN_ROLE = "现在你的角色是AI图片生成机器人。"
    }
}