#!/usr/bin/env python3
"""Builds docs/handoff/code-bundle-*.json: every hand-written file as {path: content}, split per module, with sha256 and sizes.
Generated files (ImpEx, order plans, bean XML, _tokens.scss) are excluded: regenerate them with the commands in handoff-context.json.
Restore on another machine with tools/handoff/restore.py."""
import hashlib, json, pathlib, subprocess

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "docs/handoff"
SKIP_PREFIX = ("docs/design/", "docs/handoff/", "backend/ccsampledata/resources/ccsampledata/import/", "storefront/chat-feature/node_modules/")
SKIP_EXACT = {"backend/ccfacades/resources/ccfacades-beans.xml", "backend/ccocc/resources/ccocc-beans.xml",
              "storefront/chat-feature/src/styles/_tokens.scss", "storefront/chat-feature/package-lock.json"}
GROUPS = [("backend-cccore-engine", "backend/cccore/src/com/conversational/commerce/core/engine/"),
          ("backend-cccore-engine-tests", "backend/cccore/testsrc/"),
          ("backend-cccore-sap-bound", "backend/cccore/"), ("backend-other-modules", "backend/"),
          ("storefront", "storefront/"), ("tools-and-ci", "tools/"), ("github-workflows", ".github/"),
          ("docs", "docs/"), ("repo-root", "")]


def files():
    names = subprocess.run(["git", "ls-files"], cwd=ROOT, capture_output=True, text=True, check=True).stdout.split("\n")
    return sorted(n for n in names if n and not n.startswith(SKIP_PREFIX) and n not in SKIP_EXACT)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    buckets = {g: {} for g, _ in GROUPS}
    for n in files():
        for g, prefix in GROUPS:
            if n.startswith(prefix):
                buckets[g][n] = (ROOT / n).read_text(encoding="utf-8")
                break
    index = {}
    for g, items in buckets.items():
        if not items:
            continue
        name = f"code-bundle-{g}.json"
        body = {"bundle": g, "note": "path -> exact file content. Restore with tools/handoff/restore.py or write each file by hand.",
                "files": items, "sha256": {p: hashlib.sha256(c.encode()).hexdigest() for p, c in items.items()}}
        text = json.dumps(body, ensure_ascii=False, indent=1)
        (OUT / name).write_text(text + "\n", encoding="utf-8")
        index[name] = {"files": len(items), "bytes": len(text.encode())}
    (OUT / "bundle-index.json").write_text(json.dumps(index, indent=1) + "\n")
    print(json.dumps(index, indent=1))


main()
