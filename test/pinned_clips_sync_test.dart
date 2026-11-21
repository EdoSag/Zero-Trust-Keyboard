import 'package:flutter_test/flutter_test.dart';
import 'package:zerotrust_keyboard/sync/pinned_clip.dart';
import 'package:zerotrust_keyboard/sync/reconcile.dart';

Changes<String> compare({
  required List<String> phone,
  required List<Synced<String>> account,
  required Set<String> known,
}) => reconcile(
  phone: phone,
  account: account,
  known: known,
  keyOf: clipKey,
  valid: (text) => text.isNotEmpty,
);

void main() {
  test('the key is a hash: it does not hold the clip', () {
    final key = clipKey('my address is 5 Herzl St');
    expect(key.length, 64);
    expect(key.contains('Herzl'), isFalse);
    expect(clipKey('my address is 5 Herzl St'), key);
    expect(clipKey('my address is 6 Herzl St'), isNot(key));
  });

  test('first sync joins the pinned clips of both sides', () {
    final changes = compare(
      phone: ['here'],
      account: [const Synced(id: 'a', value: 'there')],
      known: {},
    );
    expect(changes.addToAccount, ['here']);
    expect(changes.addToPhone, ['there']);
    expect(changes.removeFromPhone, isEmpty);
    expect(changes.removeFromAccount, isEmpty);
  });

  test('a clip unpinned here leaves the account', () {
    final changes = compare(
      phone: [],
      account: [const Synced(id: 'a', value: 'old')],
      known: {clipKey('old')},
    );
    expect(changes.removeFromAccount, ['a']);
    expect(changes.addToPhone, isEmpty);
  });

  test('a clip unpinned on another phone is unpinned here', () {
    final changes = compare(
      phone: ['old', 'kept'],
      account: [const Synced(id: 'a', value: 'kept')],
      known: {clipKey('old'), clipKey('kept')},
    );
    expect(changes.removeFromPhone, ['old']);
    expect(changes.known, {clipKey('kept')});
  });

  test('an empty clip in the account is removed, not pinned', () {
    final changes = compare(
      phone: [],
      account: [const Synced(id: 'a', value: '')],
      known: {},
    );
    expect(changes.removeFromAccount, ['a']);
    expect(changes.addToPhone, isEmpty);
  });

  test('clips survive the trip through the account', () {
    const item = Synced(id: 'x', value: 'שלום\nsecond line');
    final json = syncedClipsCollection.encode(item);
    expect(json['kind'], 'clip');
    final back = syncedClipsCollection.fromJson('x', json, DateTime(2026));
    expect(back.value, 'שלום\nsecond line');
  });
}
