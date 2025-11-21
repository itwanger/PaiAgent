"""
AI Tools service main entry module
"""

import os
import sys
from pathlib import Path


def setup_python_path() -> None:
    """Set up Python path to include root, parent dir, and grandparent dir"""
    # 获取当前脚本的路径和项目根目录
    current_file_path = Path(__file__)
    project_root = current_file_path.parent  # aitools 目录
    parent_dir = project_root.parent  # plugin 目录
    grandparent_dir = parent_dir.parent  # core 目录

    # 将 core 目录添加到 sys.path，这样才能 import plugin.aitools
    paths_to_add = [str(grandparent_dir), str(parent_dir), str(project_root)]
    
    for path in paths_to_add:
        if path not in sys.path:
            sys.path.insert(0, path)
    
    print(f"🔧 Added to sys.path:")
    for path in paths_to_add:
        print(f"   - {path}")


def load_env_file(env_file: str) -> None:
    """Load environment variables from .env file"""
    if not os.path.exists(env_file):
        print(f"❌ Configuration file {env_file} does not exist")
        return

    print(f"📋 Loading configuration file: {env_file}")

    with open(env_file, "r", encoding="utf-8") as f:
        for line_num, line in enumerate(f, 1):
            line = line.strip()

            # Skip empty lines and comments
            if not line or line.startswith("#"):
                continue

            # Parse environment variables
            if "=" in line:
                key, value = line.split("=", 1)
                key = key.strip()
                value = value.strip()
                if key in os.environ:
                    print(f"  🔄 {key}={os.environ[key]} (using existing env var)")
                else:
                    os.environ[key] = value
                    print(f"  ✅ {key}={value} (loaded from config)")
            else:
                print(f"  ⚠️  Line {line_num} format error: {line}")


if __name__ == "__main__":
    print("🌟 AITools Development Environment Launcher")
    print("=" * 50)

    # Set up Python path FIRST (before any imports)
    setup_python_path()

    # Load environment configuration
    config_file = Path(__file__).parent / "config.env"
    load_env_file(str(config_file))

    # dev环境配置
    os.environ["PolarisUsername"] = ""
    os.environ["PolarisPassword"] = ""

    # Import and start server
    from plugin.aitools.app.start_server import AIToolsServer

    print("🚀 Starting AITools service...")
    AIToolsServer().start()
