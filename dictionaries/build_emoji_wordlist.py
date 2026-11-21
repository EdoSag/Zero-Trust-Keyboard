#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-only
"""Builds an emoji word list (emoji_<language>_wordlist.combined.gz) from Unicode CLDR.

    python3 build_emoji_wordlist.py he annotations/he.xml annotationsDerived/he.xml \
        ../android/app/src/main/assets/emoji emoji_he_wordlist.combined.gz

The list is what emoji search, emoji suggestions while typing and the emoji names shown
on a long press read. It holds two kinds of entries:

  a search word, with the emoji it finds as shortcuts (best match first), and
  an emoji, with its name as its one shortcut.

Only emoji the keyboard itself can show (the files in assets/emoji) are included, each
in its neutral form; skin tones are picked on the key. The CLDR files are read as data.
"""
import collections
import gzip
import os
import re
import sys
import xml.etree.ElementTree as ET

CATEGORIES = [
    "SMILEYS_AND_EMOTION", "PEOPLE_AND_BODY", "ANIMALS_AND_NATURE", "FOOD_AND_DRINK",
    "TRAVEL_AND_PLACES", "ACTIVITIES", "OBJECTS", "SYMBOLS", "FLAGS",
]
MAX_EMOJI_PER_WORD = 18     # the suggestion strip and the search page show no more
WORD_FREQUENCY = 60
VERSION = 1
DATE = 1795219200           # 2026-11-21, fixed so the same inputs give the same file
# Words that say nothing about a picture.
SKIP = {
    "he": {"של", "עם", "על", "את", "או", "אל", "גם", "לא", "זה"},
    "en": {"of", "with", "and", "the", "in", "on", "for", "or", "to", "at"},
}
# Everyday favourites, best first, win a tie, so "heart" finds the red heart first.
POPULAR = ("❤️ 😂 🤣 🔥 👍 🙏 😍 😭 😊 🥰 🎉 💔 ✨ 😘 👏 💪 🌹 😁 😎 💕 😉 😢 😡 🤔 👌 🎂 ☀️ 🌙 ⭐".split())
NAMES = {"he": "אימוג'י בעברית", "en": "Emoji in English"}
SPLIT = re.compile(r"[\s,.:;!?()\[\]{}\"“”„«»/\\|–—-]+")
FE0F = "️"


def keyboard_emoji(folder):
    """The keyboard's emoji in the order it shows them, neutral form only."""
    emoji = []
    for category in CATEGORIES:
        with open(os.path.join(folder, category + ".txt"), encoding="utf-8") as lines:
            for line in lines:
                parts = line.split()
                if parts:
                    emoji.append(parts[0])
    return emoji


def annotations(paths):
    names, keywords = {}, {}
    for path in paths:
        for node in ET.parse(path).getroot().iter("annotation"):
            cp, text = node.get("cp"), (node.text or "").strip()
            if not text:
                continue
            if node.get("type") == "tts":
                names.setdefault(cp, text)
            else:
                keywords.setdefault(cp, [k.strip() for k in text.split("|") if k.strip()])
    return names, keywords


def words_of(text, language):
    # "סטודנט/ית" is one word with a feminine ending, not two words.
    text = re.sub(r"/[\u05d0-\u05ea]{1,2}(?![\u05d0-\u05ea])", "", text)
    for word in SPLIT.split(text.lower()):
        word = word.strip("'’׳״")
        if len(word) >= 2 and word not in SKIP[language] and "=" not in word:
            yield word


def main(language, base, derived, folder, out):
    names, keywords = annotations([base, derived])
    found = collections.defaultdict(dict)   # word -> {emoji: rank}, lower is better
    described = []
    for position, emoji in enumerate(keyboard_emoji(folder)):
        cp = emoji.replace(FE0F, "")
        name = names.get(cp)
        if name is None:
            continue
        described.append((emoji, " ".join(name.replace(",", " ").replace("=", " ").split())))
        # A word of the name beats a keyword, then a favourite beats the rest, a short
        # name beats a long one, an early keyword beats a late one, and between equals
        # the emoji the keyboard shows first wins.
        other = POPULAR.index(emoji) if emoji in POPULAR else len(POPULAR)
        name_words = list(words_of(name, language))
        for word in name_words:
            found[word].setdefault(emoji, (0, other, len(name_words), position))
        for index, keyword in enumerate(keywords.get(cp, ())):
            for word in words_of(keyword, language):
                found[word].setdefault(emoji, (1, other, index, position))
    lines = ["dictionary=emoji:%s,locale=%s,description=%s,date=%d,version=%d"
             % (language, language, NAMES[language], DATE, VERSION)]
    for word in sorted(found):
        best = sorted(found[word], key=found[word].get)[:MAX_EMOJI_PER_WORD]
        lines.append(" word=%s,f=%d,flags=,originalFreq=%d" % (word, WORD_FREQUENCY, WORD_FREQUENCY))
        for rank, emoji in enumerate(best):
            lines.append("  shortcut=%s,f=%d" % (emoji, max(1, 14 - rank)))
    for emoji, name in described:
        lines.append(" word=%s,f=1,flags=,originalFreq=1,not_a_word=true" % emoji)
        lines.append("  shortcut=%s,f=14" % name)
    with open(out, "wb") as raw, gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as gz:
        gz.write(("\n".join(lines) + "\n").encode("utf-8"))
    print("%s: %d emoji named, %d search words" % (language, len(described), len(found)))


if __name__ == "__main__":
    main(*sys.argv[1:6])
