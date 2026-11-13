#!/usr/bin/env bash
# secret-scan:allow-test-fixture (this file plants a fake key on purpose)
# Plants a fake key in a temp dir and requires the scanner to catch it; also requires the repo to be clean.
set -eu
here=$(cd "$(dirname "$0")" && pwd); root=$(cd "$here/../.." && pwd)
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
printf 'cc.provider.key.demo=%s\n' "FAKE$(printf 'x%.0s' {1..20})" > "$tmp/local.txt"
if "$here/secret-scan.sh" "$tmp" >/dev/null; then echo "FAIL: planted key not caught"; exit 1; fi
echo "planted key caught"
"$here/secret-scan.sh" "$root"
