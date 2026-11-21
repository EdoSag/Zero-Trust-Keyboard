import 'dart:convert';

import 'package:zero_trust_core/zero_trust_core.dart';

/// The keyboard's own settings, as the native side gives them and as the
/// account keeps them.
class KeyboardSettings {
  const KeyboardSettings({
    required this.version,
    required this.values,
    this.held = const {},
  });

  /// The one item that holds the settings. A fixed id says nothing about
  /// what is inside.
  static const itemId = 'keyboard-settings';

  String get id => itemId;

  /// The version of the keyboard's settings format on the phone that wrote
  /// them. A newer app can move settings to new names, so settings are only
  /// taken from the same version.
  final int version;

  /// Setting name to value. The name starts with the value's type
  /// ("b:auto_cap"), which this side never needs to understand: values are
  /// compared as JSON and handed back as they came.
  final Map<String, Object?> values;

  /// Settings this phone keeps as they are for now, and that are not in
  /// [values]: a language list that names a layout the user made, whose
  /// file is only on this phone. They are neither sent nor replaced, and
  /// the account keeps what it has. Never part of the account's item.
  final Set<String> held;

  factory KeyboardSettings.fromMap(Map<Object?, Object?> map) =>
      KeyboardSettings(
        version: (map['version'] as num?)?.toInt() ?? 0,
        held: {
          for (final name in map['held'] as List<Object?>? ?? const [])
            if (name is String) name,
        },
        values: {
          for (final MapEntry(:key, :value)
              in (map['prefs'] as Map<Object?, Object?>? ?? const {}).entries)
            if (key is String && value != null) key: value,
        },
      );

  Map<String, Object?> toMap() => {'version': version, 'prefs': values};
}

final syncedSettingsCollection = ZtCollection<KeyboardSettings>(
  kind: 'settings',
  idOf: (s) => s.id,
  fromJson: (id, json, _) => KeyboardSettings.fromMap(json),
  toJson: (s) => s.toMap(),
);

/// What to change so that the phone and the account hold the same settings.
class SettingsMerge {
  const SettingsMerge({
    required this.merged,
    required this.setOnPhone,
    required this.removeFromPhone,
    required this.upload,
  });

  /// The settings both sides hold once the changes are made: the `base` of
  /// the next [mergeSettings].
  final Map<String, Object?> merged;

  /// Settings to write on the phone.
  final Map<String, Object?> setOnPhone;

  /// Settings to put back to their default on the phone.
  final List<String> removeFromPhone;

  /// Whether the account's item must be written.
  final bool upload;
}

/// Merges the phone's settings with the account's, setting by setting.
///
/// [base] is what both sides held after the last sync on this phone. A
/// setting the account changed since then is taken from the account; any
/// other is taken from the phone. So a change made here and a different
/// change made on another phone both survive, and when both changed the
/// same setting the account's value wins. A setting missing from a side
/// that had it in [base] was put back to its default there.
///
/// With no [base] (first sync on this phone) the account's settings are
/// taken, and settings only this phone has are added to them.
///
/// A setting the phone holds ([KeyboardSettings.held]) is left alone on
/// both sides.
///
/// Null when the account's settings were written by a newer app: then
/// nothing may be changed. Settings written by an older app are replaced by
/// this phone's.
SettingsMerge? mergeSettings({
  required KeyboardSettings phone,
  required KeyboardSettings? account,
  required Map<String, Object?>? base,
}) {
  if (account != null && account.version > phone.version) return null;
  if (account == null || account.version < phone.version) {
    return SettingsMerge(
      merged: phone.values,
      setOnPhone: const {},
      removeFromPhone: const [],
      upload: true,
    );
  }
  final merged = <String, Object?>{};
  final keys = {...phone.values.keys, ...account.values.keys, ...?base?.keys};
  for (final key in keys) {
    final onPhone = phone.values[key];
    final inAccount = account.values[key];
    final Object? value;
    if (phone.held.contains(key)) {
      value = inAccount;
    } else if (base == null) {
      value = inAccount ?? onPhone;
    } else {
      value = _same(inAccount, base[key]) ? onPhone : inAccount;
    }
    if (value != null) merged[key] = value;
  }
  return SettingsMerge(
    merged: merged,
    setOnPhone: {
      for (final MapEntry(:key, :value) in merged.entries)
        if (!phone.held.contains(key) && !_same(value, phone.values[key]))
          key: value,
    },
    removeFromPhone: [
      for (final key in phone.values.keys)
        if (!merged.containsKey(key)) key,
    ],
    upload: !_sameMap(merged, account.values),
  );
}

/// The saved `base` of [mergeSettings]; null when there is none or it
/// cannot be read, which merges as on a first sync and loses nothing the
/// account holds.
Map<String, Object?>? decodeSettingsBase(String? saved) {
  if (saved == null || saved.isEmpty) return null;
  try {
    return (jsonDecode(saved) as Map).cast<String, Object?>();
  } on Object {
    return null;
  }
}

bool _same(Object? a, Object? b) => jsonEncode(a) == jsonEncode(b);

bool _sameMap(Map<String, Object?> a, Map<String, Object?> b) =>
    a.length == b.length && a.keys.every((k) => _same(a[k], b[k]));
