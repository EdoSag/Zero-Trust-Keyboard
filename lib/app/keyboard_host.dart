import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../sync/keyboard_settings.dart';
import '../sync/learned_words.dart';
import '../sync/personal_word.dart';

/// Where the keyboard stands in the phone's settings.
class ImeStatus {
  const ImeStatus({required this.enabled, required this.selected});

  /// Turned on in the phone's list of keyboards.
  final bool enabled;

  /// The keyboard that opens when a text field is tapped.
  final bool selected;

  bool get ready => enabled && selected;

  @override
  bool operator ==(Object other) =>
      other is ImeStatus &&
      other.enabled == enabled &&
      other.selected == selected;

  @override
  int get hashCode => Object.hash(enabled, selected);
}

/// The native side of the app: the keyboard service and its own settings
/// screens live in Kotlin (`MainActivity`, channel `zt_keyboard/host`).
class KeyboardHost {
  const KeyboardHost();

  static const MethodChannel _channel = MethodChannel('zt_keyboard/host');

  Future<ImeStatus> status() async {
    final map = await _channel.invokeMapMethod<String, bool>('imeStatus');
    return ImeStatus(
      enabled: map?['enabled'] ?? false,
      selected: map?['selected'] ?? false,
    );
  }

  /// The phone's "On-screen keyboards" list, to turn ZT Keyboard on.
  Future<void> openImeSettings() => _channel.invokeMethod('openImeSettings');

  /// The phone's "Choose input method" dialog.
  Future<void> showImePicker() => _channel.invokeMethod('showImePicker');

  /// The keyboard's own settings: languages, layout, themes, dictionaries.
  Future<void> openKeyboardSettings() =>
      _channel.invokeMethod('openKeyboardSettings');

  /// The personal dictionary, or null while ZT Keyboard is not turned on in
  /// the phone's keyboard list: Android then shows it as empty, which must
  /// not be read as "every word was deleted".
  Future<List<PersonalWord>?> personalWords() async {
    final list = await _channel.invokeListMethod<Map<Object?, Object?>>(
      'personalWords',
    );
    return list?.map(PersonalWord.fromMap).toList();
  }

  /// Adds and removes personal dictionary entries. False when the keyboard
  /// is not turned on, and nothing was changed.
  Future<bool> applyPersonalWords({
    required List<PersonalWord> add,
    required List<PersonalWord> remove,
  }) async =>
      await _channel.invokeMethod<bool>('applyPersonalWords', {
        'add': [for (final w in add) w.toMap()],
        'remove': [for (final w in remove) w.toMap()],
      }) ??
      false;

  /// The text clips pinned in the keyboard's clipboard, or null when the
  /// clipboard cannot be opened now. Pictures and very long texts are left
  /// out: they stay on this phone.
  Future<List<String>?> pinnedClips() =>
      _channel.invokeListMethod<String>('pinnedClips');

  /// Pins [add] (adding the ones the clipboard does not hold) and unpins
  /// [remove]. False when the clipboard cannot be opened.
  Future<bool> applyPinnedClips({
    required List<String> add,
    required List<String> remove,
  }) async =>
      await _channel.invokeMethod<bool>('applyPinnedClips', {
        'add': add,
        'remove': remove,
      }) ??
      false;

  /// The keyboard's own settings that can sync, or null when they cannot
  /// be read now. Settings that belong to this phone (screen sizes, the
  /// voice model, what was used last) are left out.
  Future<KeyboardSettings?> keyboardSettings() async {
    final map = await _channel.invokeMapMethod<Object?, Object?>(
      'keyboardSettings',
    );
    return map == null ? null : KeyboardSettings.fromMap(map);
  }

  /// Writes [set] and puts [remove] back to their defaults. False when the
  /// settings could not be changed.
  Future<bool> applyKeyboardSettings({
    required Map<String, Object?> set,
    required List<String> remove,
  }) async =>
      await _channel.invokeMethod<bool>('applyKeyboardSettings', {
        'set': set,
        'remove': remove,
      }) ??
      false;

  /// The words the keyboard learned from typing, by language, for the
  /// languages that are turned on. Null when learning is switched off in
  /// the keyboard's settings or a history cannot be read now.
  Future<WordsByLanguage?> learnedWords() async =>
      decodeWordsByLanguage(await _channel.invokeMethod<Object?>('learnedWords'));

  /// Adds words to the typing history. False when learning is switched
  /// off, and nothing was added.
  Future<bool> addLearnedWords(Map<String, List<String>> words) async =>
      await _channel.invokeMethod<bool>('addLearnedWords', {'words': words}) ??
      false;
}

final keyboardHostProvider = Provider<KeyboardHost>(
  (ref) => const KeyboardHost(),
);
