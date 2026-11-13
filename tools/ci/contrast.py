#!/usr/bin/env python3
"""WCAG 2.1 AA contrast check over the token file and every pack accent (part6). Exit 1 on any failure.
Pack accents must reach 4.5:1 with black or white text; the generator records which one is to be used."""
import json, pathlib, sys

ROOT = pathlib.Path(__file__).resolve().parents[2]


def lum(hex_):
    h = hex_.lstrip("#")
    r, g, b = (int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))
    f = lambda c: c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)


def ratio(a, b):
    la, lb = sorted((lum(a), lum(b)), reverse=True)
    return (la + 0.05) / (lb + 0.05)


def main():
    spec = json.loads((ROOT / "storefront/chat-feature/src/styles/tokens.json").read_text())
    t = spec["tokens"]
    failures = []
    for p in spec["pairs"]:
        r = ratio(t[p["fg"]], t[p["bg"]])
        ok = r >= p["min"]
        print(f"{'ok  ' if ok else 'FAIL'} {r:5.2f}:1 (min {p['min']}) {p['fg']} on {p['bg']}  [{p['use']}]")
        if not ok:
            failures.append(p)
    for f in spec["forbidden"]:
        r = ratio(t[f["fg"]], t[f["bg"]])
        if r >= 4.5:
            print(f"NOTE {f['fg']} on {f['bg']} now passes ({r:.2f}); rule in tokens.json is stale")
    for m in sorted((ROOT / "tools/packgen/manifests").glob("*.json")):
        accent = json.loads(m.read_text())["theme"]["--cc-pack-accent"]
        best = max(("#000000", "#FFFFFF"), key=lambda c: ratio(accent, c))
        r = ratio(accent, best)
        ok = r >= 4.5
        print(f"{'ok  ' if ok else 'FAIL'} {r:5.2f}:1 pack accent {accent} with {best} text ({m.stem})")
        if not ok:
            failures.append(m.stem)
    sys.exit(1 if failures else 0)


main()
