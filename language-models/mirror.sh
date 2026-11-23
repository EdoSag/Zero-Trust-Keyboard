#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# Fetches the language models named in a sources file, checks each against its pinned
# sha256, and writes them with their list (language-models.tsv) into a folder. Used by the
# "Language models" workflow.
#   mirror.sh <sources.tsv> <output folder>
set -euo pipefail

sources="$1"
out="$2"
mkdir -p "$out"
list="$out/language-models.tsv"
# The same columns as the voice model list, so the app reads both the same way.
printf '# file\tbytes\tsha256\tlanguages\tEnglish name\tHebrew name\n' > "$list"

fail() { echo "::error::$1"; exit 1; }

while IFS=$'\t' read -r file url wanted label_en label_he; do
  case "$file" in ''|'#'*) continue ;; esac
  [ -n "${label_he:-}" ] || fail "$file: the line needs five fields"
  echo "== $file"
  curl --fail --location --silent --show-error --retry 3 -o "$out/$file" "$url" < /dev/null
  got=$(sha256sum "$out/$file" | cut -d' ' -f1)
  [ "$got" = "$wanted" ] || fail "$file: the source has sha256 $got, expected $wanted"
  # Every GGUF file starts with the letters GGUF.
  [ "$(head -c 4 "$out/$file")" = "GGUF" ] || fail "$file is not a GGUF model"
  bytes=$(stat -c %s "$out/$file")
  [ "$bytes" -lt 2000000000 ] || fail "$file is too large for a release file"
  printf '%s\t%s\t%s\t-\t%s\t%s\n' "$file" "$bytes" "$got" "$label_en" "$label_he" >> "$list"
done < "$sources"

echo "== language-models.tsv"
cat "$list"
