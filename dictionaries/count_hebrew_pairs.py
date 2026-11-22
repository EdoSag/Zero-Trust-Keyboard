#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-only
"""Counts which Hebrew word follows which in Hebrew Wikipedia (he_wiki_pairs.tsv.gz).

    python3 count_hebrew_pairs.py he_zt_wordlist.combined.gz WIKI_DIR he_wiki_pairs.tsv.gz

WIKI_DIR holds the Parquet files of the Hebrew part of the Wikimedia "wikipedia" dataset
(one article per row, column "text"). Only pairs of two words that are both in the ZT
Hebrew list count, and only when nothing but spaces stands between them: a full stop, a
comma, a bracket or a line break ends the pair. The output keeps, for every first word,
its most common next words (at most KEEP, each seen at least MIN_COUNT times), plus a line
with how often the first word was seen before any listed word at all, as

    first<TAB>next<TAB>count      for a pair
    first<TAB><TAB>count          for the first word's total

sorted, so the same input always gives the same file. The dictionary builder turns the
counts into next-word chances. This script reads the inputs; nothing from them is run.

Needs numpy and pyarrow.
"""
import gzip
import os
import re
import sys

import numpy as np
import pyarrow.parquet as pq

MIN_COUNT = 5
KEEP = 24
SHIFT = 18  # word ids fit in 18 bits (the list has under 262,144 words)

TOKEN = re.compile(r"[א-ת]+|\S")
HEBREW = re.compile(r"^[א-ת]+$")


def read_words(path):
    words = []
    with gzip.open(path, "rt", encoding="utf-8") as lines:
        for line in lines:
            if line.startswith(" word="):
                words.append(line[6:].split(",", 1)[0])
    assert len(words) < (1 << SHIFT)
    return words


def pair_keys(text, ids):
    """The pair keys (first id << SHIFT | next id) of one article."""
    keys = []
    for line in text.split("\n"):
        previous = -1
        for token in TOKEN.findall(line):
            current = ids.get(token, -1) if HEBREW.match(token) else -1
            if previous >= 0 and current >= 0:
                keys.append((previous << SHIFT) | current)
            previous = current
    return keys


def add(total, keys):
    if not keys:
        return total
    unique, counts = np.unique(np.asarray(keys, dtype=np.int64), return_counts=True)
    if total is None:
        return unique, counts.astype(np.int64)
    both = np.concatenate([total[0], unique])
    weights = np.concatenate([total[1], counts])
    merged, where = np.unique(both, return_inverse=True)
    return merged, np.bincount(where, weights=weights).astype(np.int64)


def main(wordlist, wiki_dir, out):
    words = read_words(wordlist)
    ids = {word: i for i, word in enumerate(words)}
    total = None
    files = sorted(f for f in os.listdir(wiki_dir) if f.endswith(".parquet"))
    for name in files:
        table = pq.read_table(os.path.join(wiki_dir, name), columns=["text"])
        keys = []
        for text in table.column("text").to_pylist():
            keys.extend(pair_keys(text or "", ids))
            if len(keys) > 20_000_000:
                total = add(total, keys)
                keys = []
        total = add(total, keys)
        print("%s: %d pairs so far" % (name, 0 if total is None else len(total[0])), flush=True)
    keys, counts = total
    firsts = keys >> SHIFT
    nexts = keys & ((1 << SHIFT) - 1)
    first_totals = np.bincount(firsts, weights=counts, minlength=len(words)).astype(np.int64)
    rows = []
    order = np.lexsort((-counts, firsts))  # by first word, then most common next word
    kept = 0
    current = -1
    for i in order:
        if counts[i] < MIN_COUNT:
            continue
        if firsts[i] != current:
            current = firsts[i]
            kept = 0
        if kept < KEEP:
            rows.append((words[firsts[i]], words[nexts[i]], int(counts[i])))
            kept += 1
    listed = {first for first, _, _ in rows}
    for first in listed:
        rows.append((first, "", int(first_totals[ids[first]])))
    rows.sort(key=lambda r: (r[0], r[1] != "", -r[2], r[1]))
    with open(out, "wb") as raw, gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as gz:
        for first, second, count in rows:
            gz.write(("%s\t%s\t%d\n" % (first, second, count)).encode("utf-8"))
    print("first words %d, pairs %d" % (len(listed), len(rows) - len(listed)))


if __name__ == "__main__":
    main(*sys.argv[1:4])
