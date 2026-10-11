"""测量 AI SSE 接口在指定并发下的首 token 延迟、总耗时和错误数。"""

from __future__ import annotations

import argparse
import asyncio
import json
import os
import statistics
import time
import uuid
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


@dataclass(frozen=True)
class BenchmarkResult:
    request_number: int  # 本轮请求序号，用于定位单条失败结果。
    status_code: int  # 服务返回的 HTTP 状态码；连接失败时为 0。
    elapsed_ms: float  # 从发起 HTTP 请求到 SSE 响应结束的耗时。
    ttft_ms: float | None  # 首个 token 事件到达时间；没有 token 时为 null。
    token_events: int  # 收到的 token 事件数量。
    error_events: int  # 收到的 SSE error 事件数量。
    error: str | None  # 网络或 HTTP 错误摘要，不包含请求密钥。


def positive_int(value: str) -> int:
    """校验并发数和请求数必须大于零。"""
    parsed = int(value)
    if parsed <= 0:
        raise argparse.ArgumentTypeError("数值必须大于 0")
    return parsed


def percentile(values: list[float], percent: float) -> float | None:
    """以 nearest-rank 方式计算百分位，空样本返回 null。"""
    if not values:
        return None
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, int((percent / 100) * len(ordered) + 0.999999) - 1))
    return round(ordered[index], 2)


def _build_payload(index: int, args: argparse.Namespace, template: dict | None) -> dict:
    """构造请求体；可从 JSON 文件读取适配 Java 网关的完整业务请求。"""
    if template is None:
        payload = {
            "request_id": uuid.uuid4().hex,  # 本次 SSE 请求唯一标识。
            "session_id": args.session_id,  # MemeAgent 会话标识；直连 Java 时请使用 --body-file。
            "query": args.query,  # 本轮压测发送的梗搜索问题。
            "messages": [],  # 本次请求携带的历史消息列表。
            "context": {  # 业务候选和地区等检索上下文。
                "locale": "zh-CN",  # AI 回答语言和地区。
                "hint_meme_ids": [],  # 可选的业务梗 ID 候选。
                "extra": {},  # 可选的业务侧扩展数据。
            },
            "max_tokens": args.max_tokens,  # 本次回答允许生成的最大 token 数。
        }
        return payload

    payload = json.loads(json.dumps(template))
    request_id_field = (
        "request_id"
        if "request_id" in payload or "requestId" not in payload
        else "requestId"
    )
    payload[request_id_field] = f"benchmark-{index}-{uuid.uuid4().hex}"
    return payload


def _consume_sse(lines: Iterable[bytes], started_at: float) -> tuple[float | None, int, int]:
    """读取 SSE 行并统计 token 首达时间、token 数与 error 数。"""
    current_event = "message"
    first_token_ms: float | None = None
    token_events = 0
    error_events = 0
    for raw_line in lines:
        line = raw_line.decode("utf-8", errors="replace").rstrip("\r\n")
        if line.startswith("event:"):
            current_event = line.partition(":")[2].strip()
        elif line.startswith("data:"):
            if current_event == "token":
                token_events += 1
                if first_token_ms is None:
                    first_token_ms = round((time.perf_counter() - started_at) * 1000, 2)
            elif current_event == "error":
                error_events += 1
        elif not line:
            current_event = "message"
    return first_token_ms, token_events, error_events


def _send_request(
    index: int,
    args: argparse.Namespace,
    template: dict | None,
) -> BenchmarkResult:
    """在线程池中发起一个同步 HTTP 请求并读取完整 SSE 响应。"""
    started_at = time.perf_counter()
    payload = _build_payload(index, args, template)
    headers = {"Accept": "text/event-stream", "Content-Type": "application/json"}
    if args.api_key:
        headers["X-Internal-Api-Key"] = args.api_key
    if args.bearer_token:
        headers["Authorization"] = f"Bearer {args.bearer_token}"
    request = Request(
        args.url,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers=headers,
        method="POST",
    )
    try:
        with urlopen(request, timeout=args.timeout) as response:
            status_code = response.status
            ttft_ms, token_events, error_events = _consume_sse(response, started_at)
            return BenchmarkResult(
                request_number=index,
                status_code=status_code,
                elapsed_ms=round((time.perf_counter() - started_at) * 1000, 2),
                ttft_ms=ttft_ms,
                token_events=token_events,
                error_events=error_events,
                error=None,
            )
    except HTTPError as error:
        return BenchmarkResult(
            request_number=index,
            status_code=error.code,
            elapsed_ms=round((time.perf_counter() - started_at) * 1000, 2),
            ttft_ms=None,
            token_events=0,
            error_events=0,
            error=f"HTTP {error.code}",
        )
    except (TimeoutError, URLError, OSError) as error:
        return BenchmarkResult(
            request_number=index,
            status_code=0,
            elapsed_ms=round((time.perf_counter() - started_at) * 1000, 2),
            ttft_ms=None,
            token_events=0,
            error_events=0,
            error=type(error).__name__,
        )


async def _run(args: argparse.Namespace) -> dict:
    """按指定并发运行请求，并返回聚合吞吐与延迟指标。"""
    template = (
        json.loads(Path(args.body_file).read_text(encoding="utf-8"))
        if args.body_file
        else None
    )
    semaphore = asyncio.Semaphore(args.concurrency)  # 限制同时打开的 SSE 请求数。

    async def run_one(index: int) -> BenchmarkResult:
        async with semaphore:
            return await asyncio.to_thread(_send_request, index, args, template)

    started_at = time.perf_counter()
    results = await asyncio.gather(*(run_one(index) for index in range(1, args.requests + 1)))
    total_seconds = max(0.001, time.perf_counter() - started_at)
    successful = [
        result
        for result in results
        if 200 <= result.status_code < 300 and result.error_events == 0
    ]
    durations = [result.elapsed_ms for result in successful]
    ttfts = [result.ttft_ms for result in successful if result.ttft_ms is not None]
    return {
        "url": args.url,
        "requests": args.requests,
        "concurrency": args.concurrency,
        "successful_requests": len(successful),
        "failed_requests": len(results) - len(successful),
        "throughput_requests_per_second": round(len(results) / total_seconds, 2),
        "latency_ms": {
            "mean": round(statistics.fmean(durations), 2) if durations else None,
            "p50": percentile(durations, 50),
            "p95": percentile(durations, 95),
        },
        "ttft_ms": {"p50": percentile(ttfts, 50), "p95": percentile(ttfts, 95)},
        "results": [asdict(result) for result in results],
    }


def _parse_args() -> argparse.Namespace:
    """读取压测目标和并发参数；默认仅向本机 MemeAgent 发一个请求。"""
    parser = argparse.ArgumentParser(description="测量本地或测试环境 AI SSE 接口的并发和延迟")
    parser.add_argument(
        "--url",
        default=os.getenv("MEME_BENCHMARK_URL", "http://127.0.0.1:8000/stream"),
        help="SSE POST 地址；默认是本机 MemeAgent /stream。",
    )
    parser.add_argument(
        "--api-key",
        default=os.getenv("MEME_AGENT_API_KEY", ""),
        help="MemeAgent 内部 API 密钥；也可通过 MEME_AGENT_API_KEY 环境变量提供。",
    )
    parser.add_argument(
        "--bearer-token",
        default="",
        help="可选 JWT；通过 Authorization: Bearer 头发送，用于测试 Java 用户端接口。",
    )
    parser.add_argument(
        "--body-file",
        help="可选 UTF-8 JSON 请求体文件，用于按 Java 网关实际 DTO 发送请求。",
    )
    parser.add_argument("--query", default="只因是什么梗", help="默认请求体中的搜索问题。")
    parser.add_argument(
        "--session-id",
        default="benchmark-session",
        help="直连 MemeAgent 时使用的会话标识。",
    )
    parser.add_argument("--max-tokens", type=positive_int, default=128, help="单条回答的最大 token 数。")
    parser.add_argument("--requests", type=positive_int, default=1, help="压测请求总数。")
    parser.add_argument("--concurrency", type=positive_int, default=1, help="同时发出的 SSE 请求数。")
    parser.add_argument("--timeout", type=float, default=180, help="每条请求的超时秒数。")
    return parser.parse_args()


def main() -> None:
    """执行压测并将机器可读 JSON 指标输出到标准输出。"""
    args = _parse_args()
    result = asyncio.run(_run(args))
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
