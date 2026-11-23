import 'personal_word.dart';
import 'reconcile.dart';

/// [reconcile] for the personal dictionary.
Changes<PersonalWord> reconcileWords({
  required List<PersonalWord> phone,
  required List<Synced<PersonalWord>> account,
  required Set<String> known,
}) => reconcile(
  phone: phone,
  account: account,
  known: known,
  keyOf: (w) => w.key,
  valid: (w) => w.word.isNotEmpty,
);
