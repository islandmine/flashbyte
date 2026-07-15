#!/bin/sh
# Place this file at ./run/entrypoint.sh on the host (mounted to /server/entrypoint.sh).
# Downloads the latest flashbyte-proxy snapshot from the private Maven repo, then runs it.
set -eu

JAR=/server/flashbyte.jar
URL="${FLASHBYTE_REPO_URL:?FLASHBYTE_REPO_URL not set}"
USER="${FLASHBYTE_REPO_USER:?FLASHBYTE_REPO_USER not set}"
PASS="${FLASHBYTE_REPO_PASS:?FLASHBYTE_REPO_PASS not set}"

echo "[flashbyte] fetching latest proxy build..."
# Download to a temp file first so a failed fetch doesn't clobber the existing jar.
if curl -fSL --user "$USER:$PASS" -o "$JAR.new" "$URL"; then
  mv "$JAR.new" "$JAR"
  echo "[flashbyte] updated $JAR"
elif [ -f "$JAR" ]; then
  rm -f "$JAR.new"
  echo "[flashbyte] download failed, falling back to existing $JAR" >&2
else
  echo "[flashbyte] download failed and no existing jar — cannot start" >&2
  exit 1
fi

echo "[flashbyte] starting proxy..."
exec java ${JAVA_OPTS:-} -jar "$JAR"
