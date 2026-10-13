"""验证部署脚本的失败保护和启动顺序；不连接真实服务器或数据库。"""

import os
from pathlib import Path
import shlex
import shutil
import subprocess
import tempfile
import unittest


# 仓库根目录，用于读取实际交付的部署脚本。
ROOT = Path(__file__).resolve().parents[2]
# Linux 使用系统 Bash；Windows 优先使用 Git 自带 Bash，避免误调用未配置的 WSL。
BASH = (
    str(Path("C:/Program Files/Git/bin/bash.exe"))
    if os.name == "nt" and Path("C:/Program Files/Git/bin/bash.exe").exists()
    else shutil.which("bash")
)
# 全部为一次性测试凭据，不读取本地 deploy/.env。
ENVIRONMENT = {
    "MYSQL_ROOT_PASSWORD": "test-root-password",  # 测试 MySQL 管理密码。
    "MYSQL_APP_PASSWORD": "test-app-password",  # 测试 MySQL 应用密码。
    "REDIS_PASSWORD": "test-redis-password",  # 测试 Redis 密码。
    "POSTGRES_PASSWORD": "0123456789abcdef0123456789abcdef",  # URL 安全的测试密码。
    "MEME_AGENT_API_KEY": "test-internal-key",  # 测试内部鉴权密钥。
    "JWT_SECRET": "0123456789abcdef0123456789abcdef",  # 满足长度要求的测试密钥。
    "OSS_ACCESS_KEY_ID": "test-oss-id",  # 测试 OSS AccessKey ID。
    "OSS_ACCESS_KEY_SECRET": "test-oss-secret",  # 测试 OSS AccessKey Secret。
    "OSS_BUCKET_NAME": "test-bucket",  # 测试 OSS Bucket 名称。
    "OPENAI_API_KEY": "test-model-key",  # 测试模型 Key。
}


def bash_path(path):
    """将 Windows 盘符路径转换为 Git Bash 可识别的路径。"""
    value = path.resolve().as_posix()
    if os.name == "nt":
        return "/" + value[0].lower() + value[2:]
    return value


@unittest.skipUnless(BASH, "部署脚本测试需要 Bash")
class DeploymentScriptTest(unittest.TestCase):
    def setUp(self):
        self.workspace = tempfile.TemporaryDirectory(prefix="meme-deploy-test-")
        self.addCleanup(self.workspace.cleanup)
        self.directory = Path(self.workspace.name)
        self.bin_directory = self.directory / "bin"
        self.bin_directory.mkdir()
        self.script = self.directory / "deploy.sh"
        self.script.write_bytes((ROOT / "deploy/deploy.sh").read_bytes())
        (self.directory / "env.production.example").write_text("# 测试模板\n", encoding="utf-8")
        self.env_file = self.directory / ".env"
        self.log = self.directory / "docker-calls.log"
        self.write_environment()
        self.fake_docker = self.bin_directory / "docker"
        self.fake_docker.write_text(
            "#!/usr/bin/env bash\n"
            'printf "%s\\n" "$*" >> "$FAKE_DOCKER_LOG"\n'
            'if [[ "$*" == "info" && "${FAKE_DAEMON_FAILURE:-}" == 1 ]]; then exit 1; fi\n'
            'if [[ "$1" == "volume" && "$2" == "inspect" ]]; then\n'
            '  [[ -n "${FAKE_PG_VERSION:-}" ]] && exit 0 || exit 1\n'
            'fi\n'
            'if [[ "$1" == "run" ]]; then printf "%s\\n" "${FAKE_PG_VERSION:-}"; exit 0; fi\n'
            'if [[ "$*" == *"nginx -t" && "${FAKE_NGINX_CONFIG_FAILURE:-}" == 1 ]]; then exit 1; fi\n'
            'if [[ "$*" == *"build ${FAKE_BUILD_FAILURE:-__none__}"* ]]; then exit 42; fi\n'
            "exit 0\n",
            encoding="utf-8",
        )
        self.fake_docker.chmod(0o755)

    def write_environment(self, overrides=None):
        values = ENVIRONMENT | (overrides or {})
        self.env_file.write_text(
            "".join(key + "=" + value + "\n" for key, value in values.items()),
            encoding="utf-8",
        )

    def execute(self, overrides=None):
        environment = os.environ.copy()
        environment.update(overrides or {})
        environment["FAKE_DOCKER_LOG"] = bash_path(self.log)
        command = "export PATH=" + shlex.quote(bash_path(self.bin_directory)) + ':"$PATH"; bash ' + shlex.quote(bash_path(self.script))
        result = subprocess.run(
            [BASH, "--noprofile", "--norc", "-c", command],
            env=environment,
            capture_output=True,
            text=True,
            encoding="utf-8",
            timeout=20,
        )
        calls = self.log.read_text(encoding="utf-8").splitlines() if self.log.exists() else []
        return result, calls

    def test_quoted_empty_secret_is_rejected_without_starting_containers(self):
        self.write_environment({"OPENAI_API_KEY": '""'})
        result, calls = self.execute()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("OPENAI_API_KEY", result.stdout + result.stderr)
        self.assertFalse(any(" up " in call for call in calls))
        self.assertNotIn(ENVIRONMENT["OSS_ACCESS_KEY_SECRET"], result.stdout + result.stderr)

    def test_images_are_built_sequentially_before_services_start(self):
        result, calls = self.execute()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        builds = [call.rsplit("build ", 1)[1] for call in calls if " build " in call]
        self.assertEqual(["backend", "meme-agent", "nginx"], builds)
        start = next(index for index, call in enumerate(calls) if " up " in call)
        self.assertTrue(all(index < start for index, call in enumerate(calls) if " build " in call))
        self.assertNotIn("--build", calls[start])

    def test_failed_build_leaves_existing_services_and_volumes_untouched(self):
        result, calls = self.execute({"FAKE_BUILD_FAILURE": "backend"})
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(any(" up " in call or " down" in call or "volume rm" in call for call in calls))

    def test_postgres_16_data_requires_explicit_upgrade_before_start(self):
        result, calls = self.execute({"FAKE_PG_VERSION": "16"})
        self.assertNotEqual(0, result.returncode)
        self.assertIn("17", result.stdout + result.stderr)
        self.assertFalse(any(" up " in call for call in calls))

    def test_unreachable_docker_daemon_stops_before_build(self):
        result, calls = self.execute({"FAKE_DAEMON_FAILURE": "1"})
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(any(" build " in call or " up " in call for call in calls))

    def test_missing_env_generates_template_without_deployment(self):
        self.env_file.unlink()
        result, calls = self.execute()
        self.assertNotEqual(0, result.returncode)
        self.assertTrue(self.env_file.exists())
        self.assertEqual([], calls)

    def test_invalid_nginx_configuration_stops_before_service_update(self):
        result, calls = self.execute({"FAKE_NGINX_CONFIG_FAILURE": "1"})
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(any(" up " in call for call in calls))

    def test_quoted_empty_secret_with_inline_comment_is_rejected(self):
        self.write_environment({"OPENAI_API_KEY": '"" # 必须填写模型 Key'})
        result, calls = self.execute()
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(any(" up " in call for call in calls))

    def test_duplicate_keys_are_rejected_without_printing_their_values(self):
        with self.env_file.open("a", encoding="utf-8") as file:
            file.write("OPENAI_API_KEY=another-private-key\n")
        result, calls = self.execute()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("OPENAI_API_KEY", result.stdout + result.stderr)
        self.assertNotIn("another-private-key", result.stdout + result.stderr)
        self.assertFalse(any(" up " in call for call in calls))


if __name__ == "__main__":
    unittest.main()
