#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-only
"""Adds word pairs and "possibly offensive" marks to the ZT Hebrew word list, in place.

    python3 add_hebrew_pairs.py he_zt_wordlist.combined.gz he_wiki_pairs.tsv.gz he_offensive.txt

A word pair tells the keyboard which words tend to come next, so it can offer "הביתה"
after "הולך" before a letter is typed. The pairs come from Hebrew Wikipedia, counted by
count_hebrew_pairs.py (see README.md). A pair goes in only when the next word is clearly
more likely after the first word than on its own; the keyboard already knows the rest.

The words in he_offensive.txt are marked possibly offensive, and no pair leads to one.

Running it again on its own output gives the same file: old pairs and marks are dropped
first. Only this script reads the inputs; nothing from them is run.
"""
import collections
import gzip
import math
import sys

PER_WORD = 12           # at most this many next words for each word
MIN_COUNT = 5           # a pair must be seen this often
SCALE = 8.42            # the list's frequency steps per e-fold of probability, as in build_zt_hebrew.py
MIN_GAIN = 8            # a pair must beat the next word's own frequency by this much


def read_list(path):
    header, words = None, collections.OrderedDict()
    with gzip.open(path, "rt", encoding="utf-8") as lines:
        header = next(lines).rstrip("\n")
        for line in lines:
            if line.startswith(" word="):
                fields = dict(p.split("=", 1) for p in line.strip().split(","))
                words[fields["word"]] = int(fields["f"])
    return header, words


def read_offensive(path):
    with open(path, encoding="utf-8") as lines:
        return {w for w in (l.split("#")[0].strip() for l in lines) if w}


def read_pairs(path):
    totals, pairs = {}, collections.defaultdict(list)
    with gzip.open(path, "rt", encoding="utf-8") as lines:
        for line in lines:
            first, after, count = line.rstrip("\n").split("\t")
            if after:
                pairs[first].append((after, int(count)))
            else:
                totals[first] = int(count)
    return totals, pairs


def pair_frequency(count, total):
    return max(0, min(255, round(255 + SCALE * math.log(count / total))))


def main(word_list, pairs_file, offensive_file):
    header, words = read_list(word_list)
    offensive = read_offensive(offensive_file)
    totals, pairs = read_pairs(pairs_file)
    kept = {}
    for first, nexts in pairs.items():
        if first not in words or first in offensive:
            continue
        chosen = []
        for after, count in sorted(nexts, key=lambda e: (-e[1], e[0])):
            if count < MIN_COUNT or after not in words or after in offensive:
                continue
            f = pair_frequency(count, totals[first])
            if f >= words[after] + MIN_GAIN:
                chosen.append((after, f))
            if len(chosen) == PER_WORD:
                break
        if chosen:
            kept[first] = chosen
    marked = 0
    with open(word_list, "wb") as raw, gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as gz:
        gz.write((header + "\n").encode("utf-8"))
        for word, f in words.items():
            mark = ",possibly_offensive=true" if word in offensive else ""
            marked += bool(mark)
            gz.write((" word=%s,f=%d,flags=,originalFreq=%d%s\n" % (word, f, f, mark)).encode("utf-8"))
            for after, pf in kept.get(word, ()):
                gz.write(("  bigram=%s,f=%d\n" % (after, pf)).encode("utf-8"))
    print("words %d, with pairs %d, pairs %d, marked offensive %d"
          % (len(words), len(kept), sum(map(len, kept.values())), marked))


if __name__ == "__main__":
    main(*sys.argv[1:4])
