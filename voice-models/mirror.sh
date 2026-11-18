#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# Fetches the speech models named in a sources file, checks each against its pinned
# sha256, quantizes where asked, and writes them with their list (voice-models.tsv)
# into a folder. Used by the "Voice models" workflow.
#   mirror.sh <sources.tsv> <output folder> <path to whisper-quantize>
set -euo pipefail

sources="$1"
out="$2"
quantize="$3"
mkdir -p "$out"
list="$out/voice-models.tsv"
printf '# file\tbytes\tsha256\tlanguages\tEnglish name\tHebrew name\n' > "$list"

fail() { echo "::error::$1"; exit 1; }

while IFS=$'\t' read -r file languages url wanted quant label_en label_he; do
  case "$file" in ''|'#'*) continue ;; esac
  [ -n "${label_he:-}" ] || fail "$file: the line needs seven fields"
  source="$out/source.tmp"
  echo "== $file"
  curl --fail --location --silent --show-error --retry 3 -o "$source" "$url" < /dev/null
  got=$(sha256sum "$source" | cut -d' ' -f1)
  [ "$got" = "$wanted" ] || fail "$file: the source has sha256 $got, expected $wanted"
  if [ "$quant" = "-" ]; then
    mv "$source" "$out/$file"
  else
    "$quantize" "$source" "$out/$file" "$quant" < /dev/null > "$out/quantize.log" 2>&1 \
      || { tail -20 "$out/quantize.log"; fail "$file: quantizing failed"; }
    rm -f "$source" "$out/quantize.log"
  fi
  # Every ggml file starts with the number 0x67676d6c, lowest byte first.
  [ "$(head -c 4 "$out/$file" | od -An -tx1 | tr -d ' \n')" = "6c6d6767" ] || fail "$file is not a ggml model"
  bytes=$(stat -c %s "$out/$file")
  [ "$bytes" -lt 2000000000 ] || fail "$file is too large for a release file"
  sha256=$(sha256sum "$out/$file" | cut -d' ' -f1)
  printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$file" "$bytes" "$sha256" "$languages" "$label_en" "$label_he" >> "$list"
done < "$sources"

echo "== voice-models.tsv"
cat "$list"
