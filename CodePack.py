import os

# --- 配置区域 ---

# 1. 项目根目录 (脚本运行的地方，或者你指定的绝对路径)
# 如果脚本放在 D:\Android Programe\ 下，这里保持 "." 即可
PROJECT_ROOT = r"." 

# 2. 输出文件名
OUTPUT_FILE = "CodePackage.txt"

# 3. [核心修改] 指定要提取的路径列表 (相对路径 或 绝对路径)
# 这里填入你只关心的目录或文件
TARGET_PATHS = [
    r"app\src\main\java\com\example\killquestion",  # 核心业务代码目录
    r"app\src\main\AndroidManifest.xml"             # 清单文件
]

# 4. 依然保留一些过滤规则，防止在目标文件夹里读到垃圾文件
IGNORE_FILES = {
    ".DS_Store", "Thumbs.db"
}
IGNORE_EXTENSIONS = {
    ".class", ".jar", ".png", ".jpg", ".webp" # 防止源码目录下混入编译产物或图片
}


def should_ignore(filename):
    """检查文件是否应该被忽略"""
    if filename in IGNORE_FILES:
        return True
    if any(filename.lower().endswith(ext) for ext in IGNORE_EXTENSIONS):
        return True
    return False

def get_files_from_targets(root_dir, targets):
    """
    根据目标列表，收集所有需要处理的文件路径
    返回: [(display_path, full_path), ...]
    """
    collected_files = []

    for target in targets:
        # 处理路径：如果是相对路径，拼接上 root_dir
        if os.path.isabs(target):
            full_path = target
        else:
            full_path = os.path.join(root_dir, target)

        if not os.path.exists(full_path):
            print(f"[Warning] 路径不存在，已跳过: {full_path}")
            continue

        # 如果是文件，直接添加
        if os.path.isfile(full_path):
            # 计算显示用的相对路径
            try:
                rel_path = os.path.relpath(full_path, root_dir)
            except ValueError:
                rel_path = full_path # 无法计算相对路径时显示绝对路径
            collected_files.append((rel_path, full_path))
        
        # 如果是目录，遍历该目录
        elif os.path.isdir(full_path):
            for r, d, f in os.walk(full_path):
                for file in f:
                    if not should_ignore(file):
                        f_path = os.path.join(r, file)
                        try:
                            rel_path = os.path.relpath(f_path, root_dir)
                        except ValueError:
                            rel_path = f_path
                        collected_files.append((rel_path, f_path))
    
    # 按路径排序，保证输出顺序一致
    collected_files.sort(key=lambda x: x[0])
    return collected_files

def generate_project_context(root_dir, output_file, targets):
    files_to_process = get_files_from_targets(root_dir, targets)

    with open(output_file, "w", encoding="utf-8") as f:
        # 1. 写入精简的文件结构树
        f.write("### TARGET STRUCTURE ###\n")
        if not files_to_process:
            f.write("(No files found in specified targets)\n")
        
        for rel_path, _ in files_to_process:
            f.write(f"{rel_path}\n")

        f.write("\n\n" + "="*60 + "\n" + "="*60 + "\n\n")
        f.write("### FILE CONTENTS ###\n")

        # 2. 写入文件内容
        for rel_path, full_path in files_to_process:
            try:
                with open(full_path, "r", encoding="utf-8") as content_file:
                    content = content_file.read()
                    
                    f.write(f"\n--- FILE: {rel_path} ---\n")
                    
                    # 获取后缀用于markdown标记
                    ext = os.path.splitext(rel_path)[1].lstrip('.')
                    if not ext: ext = "txt"
                    
                    f.write(f"```{ext}\n")
                    f.write(content)
                    f.write("\n```\n")
            except Exception as e:
                f.write(f"\n--- FILE: {rel_path} (Read Error) ---\n")
                f.write(f"Error: {e}\n")

if __name__ == "__main__":
    # 确保脚本能找到文件，即使我们在不同目录下运行
    # 如果 PROJECT_ROOT 是 "."，则使用脚本所在目录或当前工作目录
    base_dir = os.path.abspath(PROJECT_ROOT)
    
    print(f"Scanning targets in: {base_dir}")
    generate_project_context(base_dir, OUTPUT_FILE, TARGET_PATHS)
    print(f"Done! Output saved to: {os.path.abspath(OUTPUT_FILE)}")