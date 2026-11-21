import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:zerotrust_keyboard/sync/keyboard_settings.dart';

KeyboardSettings settings(Map<String, Object?> values, {int version = 4201}) =>
    KeyboardSettings(version: version, values: values);

void main() {
  test('nothing in the account yet: this phone\'s settings go up', () {
    final merge = mergeSettings(
      phone: settings({'b:auto_cap': false}),
      account: null,
      base: null,
    )!;
    expect(merge.upload, isTrue);
    expect(merge.setOnPhone, isEmpty);
    expect(merge.removeFromPhone, isEmpty);
    expect(merge.merged, {'b:auto_cap': false});
  });

  test('first sync on a new phone takes the account and keeps its extras', () {
    final merge = mergeSettings(
      phone: settings({'b:auto_cap': true, 's:theme_colors': 'light'}),
      account: settings({'b:auto_cap': false, 'i:timeout': 300}),
      base: null,
    )!;
    expect(merge.setOnPhone, {'b:auto_cap': false, 'i:timeout': 300});
    expect(merge.removeFromPhone, isEmpty);
    expect(merge.merged['s:theme_colors'], 'light');
    expect(merge.upload, isTrue);
  });

  test('a change here goes up, a change there comes down', () {
    final base = {'b:auto_cap': true, 's:theme_colors': 'light'};
    final merge = mergeSettings(
      phone: settings({'b:auto_cap': false, 's:theme_colors': 'light'}),
      account: settings({'b:auto_cap': true, 's:theme_colors': 'ocean'}),
      base: base,
    )!;
    expect(merge.merged, {'b:auto_cap': false, 's:theme_colors': 'ocean'});
    expect(merge.setOnPhone, {'s:theme_colors': 'ocean'});
    expect(merge.upload, isTrue);
  });

  test('both changed the same setting: the account wins', () {
    final merge = mergeSettings(
      phone: settings({'s:theme_colors': 'dark'}),
      account: settings({'s:theme_colors': 'ocean'}),
      base: {'s:theme_colors': 'light'},
    )!;
    expect(merge.setOnPhone, {'s:theme_colors': 'ocean'});
    expect(merge.upload, isFalse);
  });

  test('a setting put back to default travels both ways', () {
    final there = mergeSettings(
      phone: settings({'b:auto_cap': false, 'b:sound_on': true}),
      account: settings({'b:sound_on': true}),
      base: {'b:auto_cap': false, 'b:sound_on': true},
    )!;
    expect(there.removeFromPhone, ['b:auto_cap']);
    expect(there.upload, isFalse);

    final here = mergeSettings(
      phone: settings({'b:sound_on': true}),
      account: settings({'b:auto_cap': false, 'b:sound_on': true}),
      base: {'b:auto_cap': false, 'b:sound_on': true},
    )!;
    expect(here.removeFromPhone, isEmpty);
    expect(here.setOnPhone, isEmpty);
    expect(here.merged, {'b:sound_on': true});
    expect(here.upload, isTrue);
  });

  test('a held setting is left alone on both sides', () {
    const key = 's:enabled_subtypes';
    final merge = mergeSettings(
      phone: KeyboardSettings(
        version: 4201,
        values: {'b:auto_cap': true},
        held: {key},
      ),
      account: settings({'b:auto_cap': true, key: 'en;he'}),
      base: {'b:auto_cap': true, key: 'en'},
    )!;
    expect(merge.setOnPhone, isEmpty);
    expect(merge.removeFromPhone, isEmpty);
    expect(merge.merged[key], 'en;he');
    expect(merge.upload, isFalse);
  });

  test('held names come from the phone and never reach the account', () {
    final phone = KeyboardSettings.fromMap({
      'version': 4201,
      'prefs': {'b:auto_cap': true},
      'held': ['s:enabled_subtypes'],
    });
    expect(phone.held, {'s:enabled_subtypes'});
    expect(phone.toMap().containsKey('held'), isFalse);
  });

  test('in step: nothing to do', () {
    final values = {
      'b:auto_cap': false,
      'S:pinned_keys': ['a', 'b'],
      'f:font_scale': '0.35',
    };
    final merge = mergeSettings(
      phone: settings(values),
      // As it comes back from the account: through JSON.
      account: settings(
        (jsonDecode(jsonEncode(values)) as Map).cast<String, Object?>(),
      ),
      base: decodeSettingsBase(jsonEncode(values)),
    )!;
    expect(merge.setOnPhone, isEmpty);
    expect(merge.removeFromPhone, isEmpty);
    expect(merge.upload, isFalse);
  });

  test('settings from a newer app are left alone', () {
    expect(
      mergeSettings(
        phone: settings({'b:auto_cap': false}),
        account: settings({'b:auto_cap': true}, version: 4300),
        base: null,
      ),
      isNull,
    );
  });

  test('settings from an older app are replaced, not applied', () {
    final merge = mergeSettings(
      phone: settings({'b:auto_cap': false}),
      account: settings({'b:old_name': true}, version: 4100),
      base: {'b:old_name': true},
    )!;
    expect(merge.setOnPhone, isEmpty);
    expect(merge.removeFromPhone, isEmpty);
    expect(merge.upload, isTrue);
    expect(merge.merged, {'b:auto_cap': false});
  });

  test('an unreadable base merges like a first sync', () {
    expect(decodeSettingsBase(null), isNull);
    expect(decodeSettingsBase(''), isNull);
    expect(decodeSettingsBase('not json'), isNull);
    expect(decodeSettingsBase('[1]'), isNull);
  });

  test('settings survive the trip through the account', () {
    final item = settings({
      'b:auto_cap': false,
      'S:pinned_keys': ['a', 'b'],
    });
    final json = syncedSettingsCollection.encode(item);
    expect(json['kind'], 'settings');
    final back = syncedSettingsCollection.fromJson(
      KeyboardSettings.itemId,
      (jsonDecode(jsonEncode(json)) as Map).cast<String, Object?>(),
      DateTime(2026),
    );
    expect(back.version, 4201);
    expect(back.values, item.values);
  });
}
