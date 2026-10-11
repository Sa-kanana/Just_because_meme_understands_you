"""脚本目录共用路径，集中管理运行时结果文件的位置。"""

from pathlib import Path


# 中文说明：SCRIPT_DIR 指向当前 scripts 目录，避免依赖启动命令的工作目录。
SCRIPT_DIR = Path(__file__).resolve().parent
# 中文说明：OUTPUT_DIR 存放脚本运行生成的临时结果，不与源码混放。
OUTPUT_DIR = SCRIPT_DIR / "output"


def get_output_path(filename: str) -> Path:
    """创建运行结果目录并返回目标文件路径。

    filename：调用方提供的结果文件名，不应包含目录部分。
    """
    output_path = Path(filename)
    if output_path.name != filename:
        raise ValueError("结果文件名不能包含目录")

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    return OUTPUT_DIR / output_path
