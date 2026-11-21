#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-only
"""Builds the ZT Hebrew word list (he_zt_wordlist.combined.gz).

    python3 build_zt_hebrew.py iw_wordlist.combined.gz he_IL.dic he_IL.aff he_full.txt he_zt_wordlist.combined.gz

The list is every word of the Android list, plus the words people actually use in
subtitles that the Hspell speller accepts. Hebrew glues "and", "the", "that", "in", "to"
and friends onto the front of a word, and the Android list holds few of those forms; the
subtitle counts say which of them are worth the room.

Inputs (see README.md for where they come from and their licences):
  iw_wordlist.combined.gz  the Android list, kept whole with its own frequencies
  he_IL.dic, he_IL.aff     Hspell as packaged for LibreOffice: base words, and the rules
                           for which prefixes each word may take
  he_full.txt              word counts from OpenSubtitles 2018 (FrequencyWords)

Only this script reads the inputs; nothing from them is run.
"""
import collections
import gzip
import math
import re
import sys

MIN_COUNT = 10          # a new word must be seen this often in the subtitles
MIN_LENGTH = 2
MAX_FREQUENCY = 150     # new words never outrank the Android list's most common words
VERSION = 45
DATE = 1795219200       # 2026-11-21, fixed so the same inputs give the same file

# Subtitle credits ("and synced by"), not words people type that often.
SKIP = {"וסונכרן", "וסנכרון", "סונכרן"}

HEBREW = re.compile(r"^[א-ת]+$")


def read_android(path):
    words = collections.OrderedDict()
    with gzip.open(path, "rt", encoding="utf-8") as lines:
        header = next(lines)
        for line in lines:
            m = re.match(r"\s*word=([^,]+),f=(\d+)", line)
            if m:
                words[m.group(1)] = int(m.group(2))
    return header, words


def read_hspell(dic, aff):
    base = {}
    with open(dic, encoding="utf-8") as lines:
        next(lines)  # the count
        for line in lines:
            word, _, flags = line.rstrip("\n").partition("/")
            if word:
                base.setdefault(word, set()).update(flags)
    rules = collections.defaultdict(list)  # prefix -> (flag, strip, condition)
    with open(aff, encoding="utf-8") as lines:
        for line in lines:
            p = line.split("#")[0].split()
            if len(p) >= 5 and p[0] == "PFX":
                strip = "" if p[2] == "0" else p[2]
                rules[p[3]].append((p[1], strip, None if p[4] == "." else re.compile(p[4])))
    longest = max(map(len, rules))

    def accepts(word):
        if word in base:
            return True
        for n in range(1, min(longest, len(word) - 1) + 1):
            for flag, strip, condition in rules.get(word[:n], ()):
                stem = strip + word[n:]
                flags = base.get(stem)
                if flags and flag in flags and (condition is None or condition.match(stem)):
                    return True
        return False

    return accepts


def read_counts(path):
    with open(path, encoding="utf-8") as lines:
        for line in lines:
            word, _, count = line.rstrip("\n").rpartition(" ")
            if count.isdigit():
                yield word, int(count)


def frequency(count):
    # Fitted on the 67,189 words both lists share: Android's f against ln(count).
    return max(1, min(MAX_FREQUENCY, round(47.33 + 8.42 * math.log(count))))


def main(android, dic, aff, counts, out):
    header, words = read_android(android)
    accepts = read_hspell(dic, aff)
    added = {}
    for word, count in read_counts(counts):
        if count < MIN_COUNT or len(word) < MIN_LENGTH or word in words or word in added or word in SKIP:
            continue
        if HEBREW.match(word) and accepts(word):
            added[word] = frequency(count)
    merged = sorted(list(words.items()) + list(added.items()), key=lambda e: (-e[1], e[0]))
    header = re.sub(r"date=\d+", "date=%d" % DATE, header.strip())
    header = re.sub(r"version=\d+", "version=%d" % VERSION, header)
    with open(out, "wb") as raw, gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as gz:
        gz.write((header + "\n").encode("utf-8"))
        for word, f in merged:
            gz.write((" word=%s,f=%d,flags=,originalFreq=%d\n" % (word, f, f)).encode("utf-8"))
    print("android %d, added %d, total %d" % (len(words), len(added), len(merged)))


if __name__ == "__main__":
    main(*sys.argv[1:6])
