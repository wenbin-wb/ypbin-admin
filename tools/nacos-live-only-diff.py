#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Nacos 导入前的「live-only 键」差异检查（issue #304）。

## 要解决的问题

`deploy/install.sh` 把 `deploy/nacos/*.yaml` **整份** POST 到 Nacos（覆盖语义）。任何
**只存在于线上 live 配置、没有进模板**的键，都会被这次覆盖静默抹掉。同类事故已发生两次：

| 被抹掉的键 | 后果 |
|---|---|
| `ypbin.security.identity.enabled`（auth 侧应为 false） | 平台**无法登录**（正确口令也 401） |

两次都是「CI 不红、启动无告警，直到用户操作才暴露」。本工具把这件事变成**导入前可见**：

- 拉取线上当前配置，与**渲染后**的待导入内容逐键比对；
- 列出「live 有、模板无」的键路径；
- 白名单（`deploy/nacos-live-only-allowlist.txt`）里的键不告警（用于确属有意的 live-only 配置）；
- `--strict` 时只要有白名单外的 live-only 键就**非零退出**（部署脚本据此中止）。

## 用法

```bash
# 独立检查（本机离线，给定两份内容）
python3 tools/nacos-live-only-diff.py --live live.yaml --template rendered.yaml \
    --allowlist deploy/nacos-live-only-allowlist.txt [--strict]

# 自测（CI 会跑）
python3 tools/nacos-live-only-diff.py --self-test
```

退出码：`0` = 无白名单外差异（或仅告警）；`1` = strict 下有白名单外差异；`2` = 用法/解析错误。

## 已知局限（如实声明）

只做**键路径**级别的扁平比对，不做值比对（值差异是模板的职责，不属「丢键」风险）。

键解析用缩进栈实现，**不使用 PyYAML**：部署机上 PyYAML 不保证存在（`tools/check-identity-config.sh`
明确要求 CI 额外 `pip install pyyaml`，说明它不是默认依赖），而「部署前检查」必须能在裸机上跑。

不解析块标量（`|` / `>`）内部的文本——本仓 `deploy/nacos/*.yaml` 不使用块标量（见自测用例
`test_templates_have_no_block_scalar`，它会在有人引入块标量时提醒本工具需要升级）。
"""

from __future__ import annotations

import argparse
import re
import sys
import tempfile
from pathlib import Path

# YAML 键行：缩进 + 键名 + 冒号。键名限定字符集，避免把值里的冒号（如
# `server-addr: ${NACOS_ADDR:localhost:8848}`）误当键。
KEY_RE = re.compile(r"^(?P<indent>[ ]*)(?P<key>[A-Za-z0-9_.\-]+):(?P<rest>\s.*|)$")
# 块标量起始（`key: |` / `key: >-` 等）
BLOCK_SCALAR_RE = re.compile(r":\s*[|>][+-]?\s*$")


def warn(message: str) -> None:
    """输出 GitHub Actions 可识别的告警行（本地运行也能看到）。"""
    print(f"::warning::{message}")


def error(message: str) -> None:
    """输出 GitHub Actions 可识别的错误行。"""
    print(f"::error::{message}")


def flatten_keys(text: str) -> tuple[set[str], list[int]]:
    """把 YAML 文本压成「点分键路径」集合。

    用**缩进栈**还原层级：遇到缩进不大于栈顶的行即出栈，因此不依赖「缩进是 2 还是 4 空格」。

    跳过：注释行、空行、序列项（`- ...`）本身。序列项下面的映射键路径会以序列项为分界被切断
    （本仓模板的序列只用于标量列表，如 `excludes`、`routes`，不需要其内部键）。

    :param text: YAML 文本
    :return: (键路径集合, 含块标量的行号列表)
    """
    keys: set[str] = set()
    stack: list[tuple[int, str]] = []
    block_scalar_lines: list[int] = []

    for lineno, raw in enumerate(text.splitlines(), 1):
        stripped = raw.strip()
        if not stripped or stripped.startswith("#"):
            continue
        indent = len(raw) - len(raw.lstrip(" "))
        if stripped.startswith("-"):
            # 序列项：既不是键，也意味着后续同级内容仍在列表上下文；把栈收敛到当前缩进
            while stack and stack[-1][0] >= indent:
                stack.pop()
            continue
        match = KEY_RE.match(raw)
        if not match:
            # 块标量内容行（无「键:」形态）等：跳过，但不破坏栈
            continue
        if BLOCK_SCALAR_RE.search(raw):
            block_scalar_lines.append(lineno)
        while stack and stack[-1][0] >= indent:
            stack.pop()
        key = match.group("key")
        path = ".".join([parent for _, parent in stack] + [key])
        keys.add(path)
        stack.append((indent, key))

    return keys, block_scalar_lines


def load_allowlist(path: Path | None) -> set[str]:
    """读取白名单：每行一个键路径，`#` 起为注释，允许行尾注释。"""
    if path is None or not path.exists():
        return set()
    allowed: set[str] = set()
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.split("#", 1)[0].strip()
        if line:
            allowed.add(line)
    return allowed


def leaf_keys(keys: set[str]) -> set[str]:
    """筛出**叶子键**：去掉「只是映射节点」的路径（存在其它键以它为前缀时）。

    为什么只报叶子：`ypbin.security.identity` 与 `ypbin.security.identity.enabled` 会同时出现在键集合里，
    全报既啰嗦、也让白名单失去精度（登记父节点会连带放行将来新增的子键）；
    而叶子才是用户真正配置的那一行。

    :param keys: 全部键路径
    :return: 叶子键集合
    """
    prefixes = set()
    for key in keys:
        parts = key.split(".")
        for index in range(1, len(parts)):
            prefixes.add(".".join(parts[:index]))
    return {key for key in keys if key not in prefixes}


def diff(live_text: str, template_text: str, allowlist: set[str]) -> tuple[list[str], list[int]]:
    """计算 live-only 键（已剔除白名单）。

    :return: (白名单外的 live-only 叶子键（排序后）, 含块标量的行号列表)
    """
    live_keys, live_block = flatten_keys(live_text)
    template_keys, template_block = flatten_keys(template_text)
    # 只比较叶子：模板里某个映射节点存在、但它的子键缺失，同样属于「会被删掉的配置」
    live_only = sorted(leaf_keys(live_keys) - template_keys - allowlist)
    return live_only, sorted(set(live_block) | set(template_block))


def redact_value_if_needed(key: str) -> str:
    """敏感键在提示里只给键路径，不给值（本工具本来就只输出键路径，此函数用于统一口径说明）。"""
    return key


# --------------------------------------------------------------------------- 自测

SELF_TEST_LIVE = """\
# 线上配置（含一个模板里没有的键）
ypbin:
  security:
    identity:
      enabled: false
    interceptor: true
  tenant:
    fail-on-missing-tenant: true
"""

SELF_TEST_TEMPLATE = """\
# 模板（缺 identity.enabled）
ypbin:
  security:
    interceptor: true
  tenant:
    fail-on-missing-tenant: true
"""


def self_test() -> int:
    """不依赖外部文件的自测（CI 用）。返回 0 = 全通过。"""
    failures: list[str] = []

    def check(name: str, condition: bool, detail: str = "") -> None:
        if not condition:
            failures.append(f"{name}：{detail}")

    # 1) 基本差异：能抓出 identity.enabled
    live_only, _ = diff(SELF_TEST_LIVE, SELF_TEST_TEMPLATE, set())
    check("nesting", live_only == ["ypbin.security.identity.enabled"], f"实际 {live_only}")

    # 2) 白名单命中后不再告警
    live_only_allow = diff(SELF_TEST_LIVE, SELF_TEST_TEMPLATE,
                           {"ypbin.security.identity.enabled"})[0]
    check("allowlist", live_only_allow == [], f"实际 {live_only_allow}")

    # 3) 注释/空行不产生键
    keys, _ = flatten_keys("# x: 1\n\nypbin:\n  a: 1\n")
    check("comments", keys == {"ypbin", "ypbin.a"}, f"实际 {sorted(keys)}")

    # 4) 值里的冒号不被误当键（server-addr: ${NACOS_ADDR:localhost:8848}）
    keys2, _ = flatten_keys("ypbin:\n  cloud:\n    nacos:\n      server-addr: ${NACOS_ADDR:localhost:8848}\n")
    check("colon-in-value", keys2 == {"ypbin", "ypbin.cloud", "ypbin.cloud.nacos",
                                     "ypbin.cloud.nacos.server-addr"}, f"实际 {sorted(keys2)}")

    # 5) 序列项不产生键、也不污染后续同级键
    keys3, _ = flatten_keys("ypbin:\n  security:\n    excludes:\n      - /login\n      - /captcha\n    interceptor: true\n")
    check("sequence", keys3 == {"ypbin", "ypbin.security", "ypbin.security.excludes",
                                "ypbin.security.interceptor"}, f"实际 {sorted(keys3)}")

    # 6) 模板包含 live 全部键 ⇒ 无差异（导入不会丢键）
    live_only_same, _ = diff(SELF_TEST_LIVE, SELF_TEST_LIVE, set())
    check("no-diff", live_only_same == [], f"实际 {live_only_same}")

    # 7) unwrap：控制台 JSON 与纯文本两种形态都能取出正文
    check("unwrap-json", unwrap_console_json('{"code":0,"data":{"content":"x: 1"}}') == "x: 1",
          "控制台 JSON 未取出 content")
    check("unwrap-plain", unwrap_console_json("a: 1\n") == "a: 1\n", "纯文本被误处理")
    check("unwrap-empty", unwrap_console_json('{"code":0,"data":null}') == "", "空 data 应为空串")

    # 8) 本仓模板不得引入块标量（引入则本工具的扁平化需要升级）
    repo_root = Path(__file__).resolve().parent.parent
    nacos_dir = repo_root / "deploy" / "nacos"
    if nacos_dir.is_dir():
        for yaml_file in sorted(nacos_dir.glob("*.yaml")):
            _, block_lines = flatten_keys(yaml_file.read_text(encoding="utf-8"))
            check(f"block-scalar:{yaml_file.name}", not block_lines,
                  f"第 {block_lines} 行有块标量（| 或 >）；本工具的键扁平化不支持块标量，需升级")

    if failures:
        for item in failures:
            error(f"自测失败 — {item}")
        return 1
    print("[ok] nacos-live-only-diff 自测通过（8 组用例）")
    return 0


def unwrap_console_json(raw: str) -> str:
    """从 Nacos 控制台 GET 的响应里取出配置正文。

    控制台接口返回 ``{"code":0,"data":{"content":"..."}}``；旧版/客户端接口可能直接返回正文。
    两者都接受：先尝试按 JSON 解，取不到 ``data.content`` 就把原文当内容。

    :param raw: 接口原文
    :return: 配置正文（可能为空串）
    """
    import json

    try:
        payload = json.loads(raw)
    except ValueError:
        return raw
    if isinstance(payload, dict):
        data = payload.get("data")
        if isinstance(data, dict):
            content = data.get("content")
            if isinstance(content, str):
                return content
    return ""


# --------------------------------------------------------------------------- CLI

def main(argv: list[str]) -> int:
    """命令行入口。"""
    parser = argparse.ArgumentParser(description="Nacos 导入前的 live-only 键差异检查（issue #304）")
    parser.add_argument("--live", type=Path, help="线上当前配置内容（文件）")
    parser.add_argument("--template", type=Path, help="待导入的渲染后内容（文件）")
    parser.add_argument("--allowlist", type=Path, help="live-only 白名单（键路径，每行一个）")
    parser.add_argument("--strict", action="store_true", help="存在白名单外 live-only 键时非零退出")
    parser.add_argument("--unwrap", type=Path,
                        help="从 Nacos 控制台 GET 的响应文件中取出配置正文并写到 stdout（供脚本串联）")
    parser.add_argument("--self-test", action="store_true", help="运行自测（不需要外部文件）")
    args = parser.parse_args(argv)

    if args.unwrap:
        try:
            raw = args.unwrap.read_text(encoding="utf-8")
        except OSError as exc:
            error(f"读取 --unwrap 输入失败：{exc}")
            return 2
        content = unwrap_console_json(raw)
        if not content.strip():
            # 空内容 = 行为「线上无该 Data ID / 空配置」，由调用方决定跳过
            return 3
        sys.stdout.write(content)
        return 0

    if args.self_test:
        return self_test()

    if not args.live or not args.template:
        error("必须同时提供 --live 与 --template（或使用 --self-test）")
        return 2

    try:
        live_text = args.live.read_text(encoding="utf-8")
        template_text = args.template.read_text(encoding="utf-8")
    except OSError as exc:
        error(f"读取输入失败：{exc}")
        return 2

    live_only, block_lines = diff(live_text, template_text, load_allowlist(args.allowlist))
    if block_lines:
        warn(f"配置含块标量（第 {block_lines} 行）：本工具的键扁平化不解析块标量内容，"
             "差异结论可能不完整")

    if not live_only:
        print("[ok] 无 live-only 键（模板覆盖了线上全部键）")
        return 0

    for key in live_only:
        message = (f"线上有、模板无的键：`{key}` —— 本次导入会**删除**它"
                   f"（Nacos 导入是整份覆盖）。若确实应保留，请写进模板；"
                   f"若确属有意只在线上存在，请登记到 deploy/nacos-live-only-allowlist.txt 并写明理由")
        if args.strict:
            error(message)
        else:
            warn(message)

    if args.strict:
        error(f"strict 模式下发现 {len(live_only)} 个白名单外 live-only 键，已中止")
        return 1

    warn(f"发现 {len(live_only)} 个白名单外 live-only 键（非 strict 模式，仅告警继续）")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
