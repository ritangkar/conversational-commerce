#!/usr/bin/env python3
"""Usage: restore.py <target-dir> <bundle.json>...  Writes every file of the bundles into target-dir and verifies sha256."""
import hashlib, json, pathlib, sys

target = pathlib.Path(sys.argv[1])
for bundle in sys.argv[2:]:
    data = json.loads(pathlib.Path(bundle).read_text(encoding="utf-8"))
    for path, content in data["files"].items():
        if hashlib.sha256(content.encode()).hexdigest() != data["sha256"][path]:
            sys.exit(f"hash mismatch (damaged paste?): {path}")
        dest = target / path
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(content, encoding="utf-8")
    print(f"{bundle}: {len(data['files'])} files restored")
