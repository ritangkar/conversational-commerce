#!/usr/bin/env python3
"""Builds ONE self-contained JSON with the handoff context, the session history and every tracked file (design inputs, owner
examples, master prompt, all code, generated files, docs). Output: docs/handoff/UC1_FULL_HANDOFF.json"""
import hashlib, json, pathlib, subprocess

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "docs/handoff/UC1_FULL_HANDOFF.json"
SKIP_PREFIX = ("docs/handoff/",)
SKIP_EXACT = {"storefront/chat-feature/package-lock.json"}

HISTORY = [
    "User supplied uc1_repo_seed.zip (design parts 1-6, UC1_Stage3_Handoff.json, owner code samples transcribed from screenshots, master prompt) and asked Claude Code to execute the master prompt: build the UC1 accelerator in stages, conventions binding, protected decisions fixed, ask rather than invent.",
    "User instruction: create branch main and push everything there (done; the session default branch ccr-ded3ed0f-wotwwa has no commits).",
    "I completed Stage 0 (convention digest, assumptions, approvals, verification log) and Stage 1 (layout, secret scanning, CI skeletons, SECURITY.md, property keys) and stopped to report that a licensed SAP Commerce platform was missing.",
    "User then said: write all the code; cannot really test right now; test with dummy data. I built the whole stack: platform-independent engine verified by 49 JUnit tests on dummy data and a fake provider, SAP-bound layer written but never compiled, pack generator, Angular feature library with tested core, CI workflows, docs.",
    "User will continue on a work laptop that has SAP Commerce, in a different Claude chat, and asked for a handoff JSON. Final request: ONE downloadable JSON in the chat containing the whole code, the whole architecture plan and all knowledge passed to or created by Claude. This file is that JSON.",
    "Honest status at handoff: engine tested; SAP-bound modules, ImpEx, Spartacus wiring, provider request shapes and model IDs are written from memory and UNVERIFIED; spikes S1-S6 and S8 not run; S7 partly run.",
    "Environment facts: session had Java 21, Node 22, Python 3.13, Maven Central and npm reachable; no SAP Commerce, no provider keys, no browser run; Google Drive/Sheets/NexLev MCP servers were disconnected and irrelevant.",
    "Git commits on main (oldest first): Seed; Stages 0-1; Engine; Engine tests; SAP-bound layer; Facades/OCC/packgen; Frontend; Docs/spikes; Verification log; Handoff bundles."
]


def main():
    ctx = json.loads((ROOT / "docs/handoff/handoff-context.json").read_text(encoding="utf-8"))
    names = subprocess.run(["git", "ls-files"], cwd=ROOT, capture_output=True, text=True, check=True).stdout.split("\n")
    files = {}
    for n in sorted(x for x in names if x and not x.startswith(SKIP_PREFIX) and x not in SKIP_EXACT):
        files[n] = (ROOT / n).read_text(encoding="utf-8")
    ctx["code_access"] = {
        "this_file": "UC1_FULL_HANDOFF.json is self-contained: key 'files' holds every repository file as path -> exact content, 'sha256' holds hashes.",
        "restore": "python3 -c \"import json,pathlib,hashlib;d=json.load(open('UC1_FULL_HANDOFF.json',encoding='utf-8'));[(pathlib.Path(p).parent.mkdir(parents=True,exist_ok=True),pathlib.Path(p).write_text(c,encoding='utf-8')) for p,c in d['files'].items()]\"  (run inside an empty target folder; then git init)",
        "also_available": "https://github.com/ritangkar/conversational-commerce (branch main)",
        "regenerate_generated_files": "python3 tools/packgen/generate.py ; python3 tools/beangen/generate.py ; python3 tools/ci/tokens.py"}
    doc = {"document": "UC1 FULL HANDOFF: everything passed to and created by Claude Code in the build session",
           "generated_on": "2026-11-13",
           "reading_order": ["new_chat_prompt", "session_history", "protected_decisions_summary", "conventions", "architecture", "status", "next_steps_on_sap_machine", "owner_needs", "files (start with README.md, docs/capability-status.md, docs/design/*, docs/examples/*)"],
           "session_history": HISTORY, **ctx,
           "files": files, "sha256": {p: hashlib.sha256(c.encode()).hexdigest() for p, c in files.items()}}
    OUT.write_text(json.dumps(doc, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(len(files), "files", OUT.stat().st_size, "bytes")


main()
