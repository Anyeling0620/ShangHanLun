import os
import shutil
import json

# ================= 配置区域 =================
# 母版路径
SOURCE_PROJECT_DIR = r"D:\Android Programe"
# 配置文件路径
CONFIG_FILE = r"D:\FactoryAssets\config.json"
# 输出路径 (生成的项目放这里)
OUTPUT_DIR = r"D:\FactoryOutput"

# 原始包结构
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
    # 忽略 build 文件夹以加快速度
    shutil.copytree(SOURCE_PROJECT_DIR, target_dir, ignore=shutil.ignore_patterns('build', '.gradle', '.idea', 'captures'))

    # 2. 重构目录 (com/shuati/shanghanlun -> com/shuati/math)
    java_root = os.path.join(target_dir, "app", "src", "main", "java")
    old_pkg_path = os.path.join(java_root, *ORIGINAL_PACKAGE_PARTS)

    # 新路径 com/shuati/{new_suffix}
    new_pkg_parts = ORIGINAL_PACKAGE_PARTS[:2] + [new_suffix]
    new_pkg_path = os.path.join(java_root, *new_pkg_parts)

    if os.path.exists(old_pkg_path):
        if not os.path.exists(new_pkg_path):
            os.renames(old_pkg_path, new_pkg_path)
        else:
            # 如果目录已存在，移动内容
            for item in os.listdir(old_pkg_path):
                shutil.move(os.path.join(old_pkg_path, item), new_pkg_path)
            shutil.rmtree(old_pkg_path)

    # 3. 全局替换代码中的包名引用
    replace_package_references(target_dir, ORIGINAL_PACKAGE_NAME, new_package_name)

    # 4. 注入 build.gradle 配置 (修改 applicationId 和 appName)
    inject_gradle_config(target_dir, config)

    # 5. 注入 Kotlin 代码配置 (AppConfig.kt)
    inject_app_config(target_dir, new_package_name, config)

    # 6. 替换资源 (题库和图标)
    inject_assets(target_dir, config)

    print(f"🎉 生成成功！请用 Android Studio 打开: {target_dir}\n")

def replace_package_references(root_dir, old_pkg, new_pkg):
    extensions = {'.kt', '.java', '.xml', '.gradle', '.pro', '.properties'}
    for subdir, _, files in os.walk(root_dir):
        for file in files:
            if os.path.splitext(file)[1] in extensions:
                file_path = os.path.join(subdir, file)
                try:
                    with open(file_path, 'r', encoding='utf-8') as f:
                        content = f.read()
                    if old_pkg in content:
                        new_content = content.replace(old_pkg, new_pkg)
                        with open(file_path, 'w', encoding='utf-8') as f:
                            f.write(new_content)
                except:
                    pass

def inject_gradle_config(root_dir, config):
    # 修改 app/build.gradle.kts (或者 build.gradle)
    # 简单粗暴的方法：直接替换 defaultConfig 里的 applicationId
    # 更稳妥的方法是配合 gradle.properties，这里演示直接替换字符串
    gradle_path = os.path.join(root_dir, "app", "build.gradle.kts")
    if not os.path.exists(gradle_path):
        gradle_path = os.path.join(root_dir, "app", "build.gradle")

    with open(gradle_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # 替换 applicationId "com.shuati.shanghanlun"
    content = content.replace(f'applicationId = "{ORIGINAL_PACKAGE_NAME}"', f'applicationId = "{config["app_id"]}"')
    content = content.replace(f'applicationId "{ORIGINAL_PACKAGE_NAME}"', f'applicationId "{config["app_id"]}"')

    # 替换 App Name (需配合 AndroidManifest manifestPlaceholders 使用，或者修改 strings.xml)
    # 这里我们直接去改 strings.xml 最稳妥
    strings_path = os.path.join(root_dir, "app", "src", "main", "res", "values", "strings.xml")
    if os.path.exists(strings_path):
        with open(strings_path, 'r', encoding='utf-8') as f:
            s_content = f.read()
        # 假设原名是 <string name="app_name">伤寒论刷题</string>
        # 这里用正则或简单替换可能不准，建议母版里把 app_name 写成特殊标记
        # 但为了简单，我们假设你母版 strings.xml 里 app_name 是 "伤寒论"
        s_content = s_content.replace(">伤寒论<", f">{config['app_name']}<")
        with open(strings_path, 'w', encoding='utf-8') as f:
            f.write(s_content)

    with open(gradle_path, 'w', encoding='utf-8') as f:
        f.write(content)


def inject_app_config(root_dir, package_name, config):
    # 路径：.../com/shuati/{suffix}/config/AppConfig.kt
    config_path = os.path.join(root_dir, "app", "src", "main", "java",
                               "com", "shuati", config["new_suffix"], "config", "AppConfig.kt")

    prompts = config["prompts"]

    # [修改点] 这里的 ASSET_QUESTION_FILE 必须是 .json
    kotlin_code = f"""package {package_name}.config

object AppConfig {{
    const val ASSET_QUESTION_FILE = "questions_full.json"

    const val UI_TITLE_MAIN = "{config['app_name']}"
    const val UI_SUBTITLE_MAIN = "智能刷题系统"
    const val UI_AUTHOR_CREDIT = "Designed by 邝梓濠"
    const val VERSION_CHECK_URL = "{config['version_url']}"

    object AiPrompts {{
        const val ROLE_ANALYSIS = "{prompts['role']}"
        val PROMPT_ANALYSIS_TEMPLATE = \"\"\"
{prompts['analysis']}
        \"\"\".trimIndent()

        val PROMPT_WEAKNESS_SYSTEM = \"\"\"
{prompts['weakness']}
        \"\"\".trimIndent()

        val PROMPT_SEARCH_GENERATION = "{prompts['search']}"
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

    # 1. 删除自适应图标目录
    anydpi_dir = os.path.join(res_dir, "mipmap-anydpi-v26")
    if os.path.exists(anydpi_dir):
        shutil.rmtree(anydpi_dir)

    # 2. 清理旧图标
    target_mipmap = os.path.join(res_dir, "mipmap-xxhdpi")
    if os.path.exists(target_mipmap):
        for file in os.listdir(target_mipmap):
            if file.startswith("ic_launcher"):
                os.remove(os.path.join(target_mipmap, file))

    # 3. 复制新图标
    icon_dest = os.path.join(target_mipmap, "ic_launcher.png")
    icon_round_dest = os.path.join(target_mipmap, "ic_launcher_round.png")

    if os.path.exists(config['icon_path']):
        shutil.copy(config['icon_path'], icon_dest)
        shutil.copy(config['icon_path'], icon_round_dest)

    # 4. [修改点] 处理题库文件
    # 目标文件名改为 questions_full.json
    data_dest = os.path.join(assets_dir, "questions_full.json")

    # 务必删除母版里可能残留的 .gz 文件，防止占用体积或引起混淆
    old_gz = os.path.join(assets_dir, "questions_full.gz")
    if os.path.exists(old_gz):
        os.remove(old_gz)

    # 复制原料库的文件到目标位置
    if os.path.exists(config['question_file']):
        shutil.copy(config['question_file'], data_dest)

if __name__ == "__main__":
    # 改为 'utf-8-sig' 可以兼容带有 BOM 的文件，也可以兼容普通文件
    with open(CONFIG_FILE, 'r', encoding='utf-8-sig') as f: 
        app_configs = json.load(f)
    for cfg in app_configs:
        generate_app(cfg)