import 'dart:convert';

import 'package:zero_trust_core/zero_trust_core.dart';

/// Words by language tag ("en-US", "he").
typedef WordsByLanguage = Map<String, Set<String>>;

/// The words the keyboard learned from typing, as the account keeps them:
/// one item.
class LearnedWords {
  const LearnedWords(this.words);

  /// The one item that holds them. A fixed id says nothing about what is
  /// inside.
  static const itemId = 'learned-words';

  String get id => itemId;

  final WordsByLanguage words;

  factory LearnedWords.fromJson(Map<String, dynamic> json) =>
      LearnedWords(decodeWordsByLanguage(json['words']) ?? const {});

  Map<String, dynamic> toJson() => {'words': encodeWordsByLanguage(words)};
}

final learnedWordsCollection = ZtCollection<LearnedWords>(
  kind: 'learned',
  idOf: (w) => w.id,
  fromJson: (id, json, _) => LearnedWords.fromJson(json),
  toJson: (w) => w.toJson(),
);

/// Sorted lists, so the same words always make the same JSON.
Map<String, List<String>> encodeWordsByLanguage(WordsByLanguage words) => {
  for (final language in words.keys.toList()..sort())
    language: words[language]!.toList()..sort(),
};

/// Null when [value] is not a map of lists.
WordsByLanguage? decodeWordsByLanguage(Object? value) {
  if (value is! Map) return null;
  return {
    for (final MapEntry(:key, value: list) in value.entries)
      if (key is String && list is List)
        key: {
          for (final word in list)
            if (word is String) word,
        },
  };
}

/// The saved `base` of [mergeLearned]; null when there is none or it cannot
/// be read, which merges as on a first sync.
WordsByLanguage? decodeLearnedBase(String? saved) {
  if (saved == null || saved.isEmpty) return null;
  try {
    return decodeWordsByLanguage(jsonDecode(saved));
  } on Object {
    return null;
  }
}

/// What to change so that the phone and the account hold the same learned
/// words.
class LearnedMerge {
  const LearnedMerge({
    required this.merged,
    required this.addToPhone,
    required this.upload,
  });

  /// What the account holds once the changes are made: the `base` of the
  /// next [mergeLearned].
  final WordsByLanguage merged;

  /// Words to add to the phone's typing history. Never empty lists.
  final Map<String, List<String>> addToPhone;

  /// Whether the account's item must be written.
  final bool upload;
}

/// Merges the words this phone learned with the account's, language by
/// language.
///
/// [base] is what the account held after the last sync on this phone. A
/// word the account gained or lost since then follows the account; any
/// other follows the phone, so a word learned here goes up and a word this
/// phone forgot leaves the account.
///
/// Words are only ever added to a phone. A word that left the account but
/// is still in this phone's history stays here, and goes up again on the
/// next round: a phone that still uses a word keeps it alive for the
/// others.
///
/// A language the phone does not report (not turned on here) is left as
/// the account has it.
LearnedMerge mergeLearned({
  required WordsByLanguage phone,
  required WordsByLanguage? account,
  required WordsByLanguage? base,
}) {
  final merged = <String, Set<String>>{};
  final addToPhone = <String, List<String>>{};
  for (final language in {...phone.keys, ...?account?.keys}) {
    final inAccount = account?[language] ?? const <String>{};
    final onPhone = phone[language];
    if (onPhone == null) {
      merged[language] = inAccount;
      continue;
    }
    final before = base?[language];
    final Set<String> both;
    if (account == null || before == null) {
      both = {...onPhone, ...inAccount};
    } else {
      both = {
        for (final word in {...onPhone, ...inAccount, ...before})
          if (inAccount.contains(word) != before.contains(word)
              ? inAccount.contains(word)
              : onPhone.contains(word))
            word,
      };
    }
    merged[language] = both;
    final add = both.difference(onPhone).toList()..sort();
    if (add.isNotEmpty) addToPhone[language] = add;
  }
  merged.removeWhere((_, words) => words.isEmpty);
  return LearnedMerge(
    merged: merged,
    addToPhone: addToPhone,
    upload:
        account == null ||
        jsonEncode(encodeWordsByLanguage(merged)) !=
            jsonEncode(
              encodeWordsByLanguage({
                for (final MapEntry(:key, :value) in account.entries)
                  if (value.isNotEmpty) key: value,
              }),
            ),
  );
}
