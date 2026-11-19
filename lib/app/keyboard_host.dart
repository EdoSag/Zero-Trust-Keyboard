import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

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
}

final keyboardHostProvider = Provider<KeyboardHost>(
  (ref) => const KeyboardHost(),
);
