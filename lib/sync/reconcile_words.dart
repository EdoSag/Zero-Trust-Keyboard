import 'personal_word.dart';

/// What to change so that the personal dictionary on this phone and the one
/// in the account hold the same entries.
class WordChanges {
  const WordChanges({
    required this.addToPhone,
    required this.removeFromPhone,
    required this.addToAccount,
    required this.removeFromAccount,
    required this.known,
  });

  final List<PersonalWord> addToPhone;
  final List<PersonalWord> removeFromPhone;
  final List<PersonalWord> addToAccount;

  /// Ids of account items to delete.
  final List<String> removeFromAccount;

  /// The entries both sides hold once the changes are made: the [known]
  /// argument of the next [reconcileWords].
  final Set<String> known;

  bool get isEmpty =>
      addToPhone.isEmpty &&
      removeFromPhone.isEmpty &&
      addToAccount.isEmpty &&
      removeFromAccount.isEmpty;
}

/// Compares the phone's entries with the account's.
///
/// [known] holds the keys both sides had after the last sync on this phone.
/// It tells a new entry from a deleted one: an entry only one side has is
/// new there when it is not in [known], and was deleted on the other side
/// when it is. With an empty [known] (first sync) nothing is ever deleted
/// and the two sides are joined.
WordChanges reconcileWords({
  required List<PersonalWord> phone,
  required List<SyncedWord> account,
  required Set<String> known,
}) {
  final onPhone = <String, PersonalWord>{for (final w in phone) w.key: w};

  // Two phones can add the same word before they sync: keep one item.
  final inAccount = <String, SyncedWord>{};
  final removeFromAccount = <String>[];
  for (final item in [...account]..sort((a, b) => a.id.compareTo(b.id))) {
    if (item.word.word.isEmpty || inAccount.containsKey(item.word.key)) {
      removeFromAccount.add(item.id);
    } else {
      inAccount[item.word.key] = item;
    }
  }

  final addToPhone = <PersonalWord>[];
  final removeFromPhone = <PersonalWord>[];
  final addToAccount = <PersonalWord>[];
  final after = <String>{};

  for (final MapEntry(:key, value: word) in onPhone.entries) {
    if (inAccount.containsKey(key)) {
      after.add(key);
    } else if (known.contains(key)) {
      // Was in the account, is no longer: deleted on another phone.
      removeFromPhone.add(word);
    } else {
      addToAccount.add(word);
      after.add(key);
    }
  }
  for (final MapEntry(:key, value: item) in inAccount.entries) {
    if (onPhone.containsKey(key)) continue;
    if (known.contains(key)) {
      // Was on this phone, is no longer: deleted here.
      removeFromAccount.add(item.id);
    } else {
      addToPhone.add(item.word);
      after.add(key);
    }
  }

  return WordChanges(
    addToPhone: addToPhone,
    removeFromPhone: removeFromPhone,
    addToAccount: addToAccount,
    removeFromAccount: removeFromAccount,
    known: after,
  );
}
