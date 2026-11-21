import 'package:zero_trust_core/zero_trust_core.dart';

/// One entry of the personal dictionary: a word the user added, with an
/// optional shortcut that types it and an optional language.
class PersonalWord {
  const PersonalWord({
    required this.word,
    this.shortcut = '',
    this.locale = '',
    this.weight = 250,
  });

  final String word;

  /// What to type to get [word] offered; empty for none.
  final String shortcut;

  /// The language the word is for, as Android writes it ("he", "en_US");
  /// empty for every language.
  final String locale;

  /// How readily the keyboard offers it, 0 to 255.
  final int weight;

  /// What makes two entries the same entry. The weight is not part of it.
  String get key => '$locale\u0000$word\u0000$shortcut';

  factory PersonalWord.fromMap(Map<Object?, Object?> map) => PersonalWord(
    word: map['word'] as String? ?? '',
    shortcut: map['shortcut'] as String? ?? '',
    locale: map['locale'] as String? ?? '',
    weight: (map['weight'] as num?)?.toInt() ?? 250,
  );

  Map<String, Object?> toMap() => {
    'word': word,
    'shortcut': shortcut,
    'locale': locale,
    'weight': weight,
  };
}

/// A [PersonalWord] as the account keeps it. The id is random: ids are not
/// encrypted, so they must say nothing about the word.
class SyncedWord {
  const SyncedWord({required this.id, required this.word});

  final String id;
  final PersonalWord word;
}

final syncedWordsCollection = ZtCollection<SyncedWord>(
  kind: 'word',
  idOf: (w) => w.id,
  fromJson: (id, json, _) =>
      SyncedWord(id: id, word: PersonalWord.fromMap(json)),
  toJson: (w) => w.word.toMap(),
);
