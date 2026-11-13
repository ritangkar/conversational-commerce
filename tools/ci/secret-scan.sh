#!/usr/bin/env bash
# Offline secret scan (fallback beside gitleaks). Usage: tools/ci/secret-scan.sh [path...]; exit 1 on a hit.
# Files containing the marker "secret-scan:allow-test-fixture" are skipped (only tools/ci/selftest.sh uses it).
set -u
paths=("${@:-.}")
pat='(AIza[0-9A-Za-z_-]{35}|sk-[A-Za-z0-9_-]{20,}|sk-ant-[A-Za-z0-9_-]{20,}|ghp_[A-Za-z0-9]{36}|github_pat_[A-Za-z0-9_]{50,}|AKIA[0-9A-Z]{16}|-----BEGIN [A-Z ]*PRIVATE KEY-----|cc\.provider\.key\.[A-Za-z0-9_.-]*[[:space:]]*=[[:space:]]*[^[:space:]$#][^[:space:]]*)'
hits=$(grep -rEIn --exclude-dir=.git --exclude-dir=node_modules --exclude-dir=docs "$pat" "${paths[@]}" | while IFS= read -r l; do f=${l%%:*}; grep -q 'secret-scan:allow-test-fixture' "$f" || echo "$l"; done)
if [ -n "$hits" ]; then echo "POSSIBLE SECRET(S) FOUND:"; echo "$hits" | sed -E 's/(:[0-9]+:).*/\1 <redacted>/'; exit 1; fi
echo "secret scan clean"
