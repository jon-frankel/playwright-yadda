#!/usr/bin/env bash
# Builds and packs the library, then installs that tarball into the examples under examples/.
#
# Usage: scripts/examples-install.sh [example ...]    (all examples when none are named)
#
# The examples depend on file:../../.pack/playwright-yadda.tgz, so they run what a published install
# would get, without a registry. They have no lockfile (see .gitignore): the tarball's hash changes with
# every build, and a leftover lockfile with an old hash makes pnpm fail.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

pnpm run build
rm -rf .pack
mkdir .pack
pnpm pack --pack-destination .pack
mv .pack/playwright-yadda-*.tgz .pack/playwright-yadda.tgz

if [ $# -gt 0 ]; then
  examples=("$@")
else
  examples=()
  for dir in examples/*/; do examples+=("$(basename "$dir")"); done
fi

for example in "${examples[@]}"; do
  (cd "examples/$example" && pnpm install --no-lockfile)
  echo "✅ examples/$example has playwright-yadda@$(node -p "require('./examples/$example/node_modules/playwright-yadda/package.json').version")"
done
