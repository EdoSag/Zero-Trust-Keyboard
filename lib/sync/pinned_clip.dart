import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:zero_trust_core/zero_trust_core.dart';

import 'reconcile.dart';

/// What makes two pinned clips the same clip: their text. A hash, so the
/// list of clips both sides hold, kept in this phone's preferences, is
/// short and holds no clip text.
String clipKey(String text) => sha256.convert(utf8.encode(text)).toString();

/// The pinned clips as the account keeps them: text only.
final syncedClipsCollection = ZtCollection<Synced<String>>(
  kind: 'clip',
  idOf: (c) => c.id,
  fromJson: (id, json, _) =>
      Synced(id: id, value: json['text'] as String? ?? ''),
  toJson: (c) => {'text': c.value},
);
