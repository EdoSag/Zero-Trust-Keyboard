/// Something the account keeps, with the id of its item. The id is random:
/// ids are not encrypted, so they must say nothing about the value.
class Synced<T> {
  const Synced({required this.id, required this.value});

  final String id;
  final T value;
}

/// What to change so that this phone and the account hold the same entries.
class Changes<T> {
  const Changes({
    required this.addToPhone,
    required this.removeFromPhone,
    required this.addToAccount,
    required this.removeFromAccount,
    required this.known,
  });

  final List<T> addToPhone;
  final List<T> removeFromPhone;
  final List<T> addToAccount;

  /// Ids of account items to delete.
  final List<String> removeFromAccount;

  /// The keys both sides hold once the changes are made: the `known`
  /// argument of the next [reconcile].
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
///
/// [keyOf] says what makes two entries the same entry. An account item that
/// is not [valid] is removed from the account.
Changes<T> reconcile<T>({
  required List<T> phone,
  required List<Synced<T>> account,
  required Set<String> known,
  required String Function(T) keyOf,
  bool Function(T)? valid,
}) {
  final onPhone = <String, T>{for (final v in phone) keyOf(v): v};

  // Two phones can add the same entry before they sync: keep one item.
  final inAccount = <String, Synced<T>>{};
  final removeFromAccount = <String>[];
  for (final item in [...account]..sort((a, b) => a.id.compareTo(b.id))) {
    final key = keyOf(item.value);
    if ((valid != null && !valid(item.value)) || inAccount.containsKey(key)) {
      removeFromAccount.add(item.id);
    } else {
      inAccount[key] = item;
    }
  }

  final addToPhone = <T>[];
  final removeFromPhone = <T>[];
  final addToAccount = <T>[];
  final after = <String>{};

  for (final MapEntry(:key, :value) in onPhone.entries) {
    if (inAccount.containsKey(key)) {
      after.add(key);
    } else if (known.contains(key)) {
      // Was in the account, is no longer: deleted on another phone.
      removeFromPhone.add(value);
    } else {
      addToAccount.add(value);
      after.add(key);
    }
  }
  for (final MapEntry(:key, value: item) in inAccount.entries) {
    if (onPhone.containsKey(key)) continue;
    if (known.contains(key)) {
      // Was on this phone, is no longer: deleted here.
      removeFromAccount.add(item.id);
    } else {
      addToPhone.add(item.value);
      after.add(key);
    }
  }

  return Changes(
    addToPhone: addToPhone,
    removeFromPhone: removeFromPhone,
    addToAccount: addToAccount,
    removeFromAccount: removeFromAccount,
    known: after,
  );
}
