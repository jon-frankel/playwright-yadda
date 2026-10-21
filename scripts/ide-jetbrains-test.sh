#!/usr/bin/env bash
# Runs the JetBrains plugin tests (ide/jetbrains). The Gradle build is pinned to JDK 25, which not
# every machine has, so look for one: JAVA_HOME, then any installed JDK 25, then the runtime that
# ships inside WebStorm 2026.2. An installed WebStorm is also used as the test platform, which
# skips downloading one (about 1 GB).
set -euo pipefail

cd "$(dirname "$0")/../ide/jetbrains"

is_jdk_25() {
  [ -x "$1/bin/java" ] && "$1/bin/java" -version 2>&1 | head -1 | grep -q '"25[."]'
}

webstorm=""
for candidate in "$HOME/Applications/WebStorm.app" "/Applications/WebStorm.app"; do
  if [ -d "$candidate" ]; then
    webstorm="$candidate"
    break
  fi
done

jdk_candidates=("${JAVA_HOME:-}")
# `java_home -v 25` also returns newer JDKs, so every candidate is checked below.
if [ -x /usr/libexec/java_home ]; then
  jdk_candidates+=("$(/usr/libexec/java_home -v 25 2>/dev/null || true)")
fi
if [ -n "$webstorm" ]; then
  jdk_candidates+=("$webstorm/Contents/jbr/Contents/Home")
fi

jdk=""
for candidate in "${jdk_candidates[@]}"; do
  if [ -n "$candidate" ] && is_jdk_25 "$candidate"; then
    jdk="$candidate"
    break
  fi
done

if [ -z "$jdk" ]; then
  echo "ide-jetbrains-test: no JDK 25 found. Set JAVA_HOME to one, or install WebStorm 2026.2+ (its bundled runtime works)." >&2
  exit 1
fi
export JAVA_HOME="$jdk"

args=(test --console=plain)
if [ -n "$webstorm" ]; then
  args+=("-PlocalIdePath=$webstorm")
fi

exec ./gradlew "${args[@]}"
