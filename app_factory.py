import os
import shutil
import json
import gzip

# ================= 配置区域 =================
SOURCE_PROJECT_DIR = r"D:\Android Programe"
CONFIG_FILE = r"D:\FactoryAssets\config.json"
OUTPUT_DIR = r"D:\FactoryOutput"

ORIGINAL_PACKAGE_PARTS = ["com", "shuati", "shanghanlun"]
ORIGINAL_PACKAGE_NAME = ".".join(ORIGINAL_PACKAGE_PARTS)


# ===========================================

def generate_app(config):
    new_suffix = config["new_suffix"]
    new_package_name = f"com.shuati.{new_suffix}"
    target_dir = os.path.join(OUTPUT_DIR, config["project_name"])

    print(f"🚀 开始生成: {config['app_name']} ({new_package_name})...")

    # 1. 克隆项目
    if os.path.exists(target_dir):
        shutil.rmtree(target_dir)
    shutil.copytree(SOURCE_PROJECT_DIR, target_dir,
                    ignore=shutil.ignore_patterns('build', '.gradle', '.idea', 'captures'))

    # 2. 重构目录
    java_root = os.path.join(target_dir, "app", "src", "main", "java")
    old_pkg_path = os.path.join(java_root, *ORIGINAL_PACKAGE_PARTS)
    new_pkg_parts = ORIGINAL_PACKAGE_PARTS[:2] + [new_suffix]
    new_pkg_path = os.path.join(java_root, *new_pkg_parts)

    if os.path.exists(old_pkg_path):
        if not os.path.exists(new_pkg_path):
            os.renames(old_pkg_path, new_pkg_path)
        else:
            for item in os.listdir(old_pkg_path):
                shutil.move(os.path.join(old_pkg_path, item), new_pkg_path)
            shutil.rmtree(old_pkg_path)

    # 3. 全局替换包名引用
    replace_package_references(target_dir, ORIGINAL_PACKAGE_NAME, new_package_name)

    # 4. 注入 build.gradle 配置 (修改包名和APP名)
    inject_gradle_config(target_dir, config)

    # 5. 注入 Kotlin 代码配置 (AppConfig.kt - 核心逻辑)
    inject_app_config(target_dir, new_package_name, config)

    # 6. 替换资源 (题库和图标，强力清理)
    inject_assets(target_dir, config)

    print(f"🎉 生成成功！路径: {target_dir}\n")


# --- 辅助函数：从题库提取分类 ---
def extract_categories_from_source(file_path):
    categories = set()
    try:
        content = ""
        if file_path.endswith(".gz"):
            with gzip.open(file_path, 'rt', encoding='utf-8') as f:
                content = f.read()
        else:
            with open(file_path, 'r', encoding='utf-8') as f:
                content = f.read()

        data = json.loads(content)
        # 兼容 {data:[]} 和 [] 格式
        questions = data.get("data", []) if isinstance(data, dict) else data

        for q in questions:
            if "category" in q and q["category"]:
                categories.add(q["category"])
    except Exception as e:
        print(f"   ⚠️ 警告: 无法提取分类 ({e})，使用默认值")
        return ["综合题"]
    return sorted(list(categories))


# --- 核心函数：注入 AppConfig ---
def inject_app_config(root_dir, package_name, config):
    config_path = os.path.join(root_dir, "app", "src", "main", "java",
                               "com", "shuati", config["new_suffix"], "config", "AppConfig.kt")

    prompts = config["prompts"]
    terms = config["terms"]
    domain = config["subject_domain"]

    # 1. 提取分类字符串
    cats = extract_categories_from_source(config['question_file'])
    cat_str = ", ".join([f"【{c}】" for c in cats])
    cat_instruction = f"可用分类: {cat_str}。请根据题目形式选择最合适的填入 category 字段。"

    # 2. 【新增】提取 UI 文案 (使用 .get 提供默认值，防止 config 里没写报错)
    subtitle = config.get("ui_subtitle", "智能刷题系统")
    author = config.get("author_credit", "Designed by AI Factory")

    # 3. 动态生成 Kotlin 代码
    kotlin_code = f"""package {package_name}.config

object AppConfig {{
    const val ASSET_QUESTION_FILE = "questions_full.json"

    const val UI_TITLE_MAIN = "{config['app_name']}"
    const val UI_SUBTITLE_MAIN = "{subtitle}" 
    const val UI_AUTHOR_CREDIT = "{author}"

    const val VERSION_CHECK_URL = "{config.get('version_url', '')}"
    const val AI_HEADER_TITLE = "{config['project_name']}"

    object AiPrompts {{
        const val ROLE_ANALYSIS = "{prompts['role']}"

        val PROMPT_ANALYSIS_TEMPLATE = \"\"\"
            请解析这道题：
            1. 核心{terms['point_name']}。
            2. 为什么选该答案（解题思路）。
            3. 排除{terms['distractor']}。
            要求：Markdown格式，精练，200字以内。
        \"\"\".trimIndent()

        val PROMPT_WEAKNESS_SYSTEM = \"\"\"
            你是一位{domain}辅导专家。
            请为这些错题制作【复习知识卡片】。

            【数量与策略】：
            1. **不要**进行笼统的概括。
            2. 请尽量为**每一个**具体的{terms['point_name']}生成一张独立的卡片。
            3. 如果多道题考的是同一个{terms['point_name']}（如都是{terms['example_point']}），请合并。

            【卡片内容要求】：
            - **易错原因**：指出为什么容易做错。
            - **核心{terms['point_name']}详解**：深度剖析。
            - **{terms['memory_tip']}**：辅助记忆/解题的关键。

            【格式严格要求】：
            1. 格式：知识点标题#知识点内容
            2. 每张卡片之间用 "|||" 分隔。
        \"\"\".trimIndent()

        val PROMPT_SEARCH_GENERATION = \"\"\"
            请根据关键词【%s】，生成 %d 道{domain}题目。

            【题型与分类要求】：
            **必须优先使用以下 JSON category (分类) 标签**：
            {cat_instruction}
            如果是判断题则必须放入两个选项，一个选项为正确，一个选项为错误。

            **type 映射**：单选=SINGLE_CHOICE, 多选=MULTI_CHOICE, 判断=TRUE_FALSE, 填空=FILL_BLANK, 大题=ESSAY

            【JSON 格式要求】：
            必须返回严格的 JSON 数组，JSON 结构如下：
            [
              {{
                "type": "SINGLE_CHOICE",
                "category": "这里填上面要求的分类名",
                "content": "题目内容",
                "options": [ {{"label": "A", "text": "选项内容"}} ],
                "answer": "A",
                "analysis": "解析内容"
              }}
            ]

            【内容要求】：
            1. 难度适中，符合{domain}标准。
            2. 确保 JSON 格式合法，不要包含 ```json 等标记。
            3. 题目数量尽量接近 $count 道。
        \"\"\".trimIndent()

        const val IMAGE_GEN_ROLE = "AI绘图助手"
    }}
}}
"""
    os.makedirs(os.path.dirname(config_path), exist_ok=True)
    with open(config_path, 'w', encoding='utf-8') as f:
        f.write(kotlin_code)


def inject_assets(root_dir, config):
    res_dir = os.path.join(root_dir, "app", "src", "main", "res")
    assets_dir = os.path.join(root_dir, "app", "src", "main", "assets")

    # 清理 anydpi 避免图标报错
    anydpi = os.path.join(res_dir, "mipmap-anydpi-v26")
    if os.path.exists(anydpi): shutil.rmtree(anydpi)

    # 清理旧图标
    target_mipmap = os.path.join(res_dir, "mipmap-xxhdpi")
    if os.path.exists(target_mipmap):
        for f in os.listdir(target_mipmap):
            if f.startswith("ic_launcher"): os.remove(os.path.join(target_mipmap, f))

    # 复制新图标
    if os.path.exists(config['icon_path']):
        shutil.copy(config['icon_path'], os.path.join(target_mipmap, "ic_launcher.png"))
        shutil.copy(config['icon_path'], os.path.join(target_mipmap, "ic_launcher_round.png"))

    # 替换题库 (重命名为 questions_full.json)
    if os.path.exists(config['question_file']):
        dest = os.path.join(assets_dir, "questions_full.json")
        # 删旧 .gz 和 .json
        for old in ["questions_full.gz", "questions_full.json"]:
            p = os.path.join(assets_dir, old)
            if os.path.exists(p): os.remove(p)
        shutil.copy(config['question_file'], dest)


def replace_package_references(root_dir, old_pkg, new_pkg):
    for subdir, _, files in os.walk(root_dir):
        for file in files:
            if os.path.splitext(file)[1] in {'.kt', '.java', '.xml', '.gradle', '.pro'}:
                path = os.path.join(subdir, file)
                try:
                    with open(path, 'r', encoding='utf-8') as f:
                        s = f.read()
                    if old_pkg in s:
                        with open(path, 'w', encoding='utf-8') as f: f.write(s.replace(old_pkg, new_pkg))
                except:
                    pass


def inject_gradle_config(root_dir, config):
    # 修改 strings.xml (App Name)
    strings_path = os.path.join(root_dir, "app", "src", "main", "res", "values", "strings.xml")
    if os.path.exists(strings_path):
        with open(strings_path, 'r', encoding='utf-8') as f: s = f.read()
        # 简单替换：假设母版是 "伤寒论"
        s = s.replace(">伤寒论<", f">{config['app_name']}<")
        # 也可以强制正则替换 app_name，这里简单处理
        with open(strings_path, 'w', encoding='utf-8') as f: f.write(s)

    # 修改 build.gradle.kts (Application ID)
    gradle_path = os.path.join(root_dir, "app", "build.gradle.kts")
    if not os.path.exists(gradle_path): gradle_path = os.path.join(root_dir, "app", "build.gradle")

    with open(gradle_path, 'r', encoding='utf-8') as f:
        s = f.read()
    s = s.replace(f'applicationId = "{ORIGINAL_PACKAGE_NAME}"', f'applicationId = "{config["app_id"]}"')
    s = s.replace(f'applicationId "{ORIGINAL_PACKAGE_NAME}"', f'applicationId "{config["app_id"]}"')
    with open(gradle_path, 'w', encoding='utf-8') as f:
        f.write(s)


if __name__ == "__main__":
    # 使用 utf-8-sig 兼容 BOM
    with open(CONFIG_FILE, 'r', encoding='utf-8-sig') as f:
        app_configs = json.load(f)
    for cfg in app_configs:
        generate_app(cfg)