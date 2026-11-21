import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:zerotrust_keyboard/sync/learned_words.dart';

void main() {
  test('nothing in the account yet: this phone\'s words go up', () {
    final merge = mergeLearned(
      phone: {
        'en-US': {'zerotrust'},
      },
      account: null,
      base: null,
    );
    expect(merge.upload, isTrue);
    expect(merge.addToPhone, isEmpty);
    expect(merge.merged, {
      'en-US': {'zerotrust'},
    });
  });

  test('first sync on a phone joins both sides', () {
    final merge = mergeLearned(
      phone: {
        'en-US': {'here'},
      },
      account: {
        'en-US': {'there'},
      },
      base: null,
    );
    expect(merge.addToPhone, {
      'en-US': ['there'],
    });
    expect(merge.merged['en-US'], {'here', 'there'});
    expect(merge.upload, isTrue);
  });

  test('a new word on each side travels to the other', () {
    final merge = mergeLearned(
      phone: {
        'he': {'שלום', 'כאן'},
      },
      account: {
        'he': {'שלום', 'שם'},
      },
      base: {
        'he': {'שלום'},
      },
    );
    expect(merge.addToPhone, {
      'he': ['שם'],
    });
    expect(merge.merged['he'], {'שלום', 'כאן', 'שם'});
    expect(merge.upload, isTrue);
  });

  test('a word this phone forgot leaves the account', () {
    final merge = mergeLearned(
      phone: {
        'en-US': {'kept'},
      },
      account: {
        'en-US': {'kept', 'faded'},
      },
      base: {
        'en-US': {'kept', 'faded'},
      },
    );
    expect(merge.merged['en-US'], {'kept'});
    expect(merge.addToPhone, isEmpty);
    expect(merge.upload, isTrue);
  });

  test('a word another phone forgot stays here, then goes up again', () {
    final first = mergeLearned(
      phone: {
        'en-US': {'kept', 'used'},
      },
      account: {
        'en-US': {'kept'},
      },
      base: {
        'en-US': {'kept', 'used'},
      },
    );
    // Nothing is ever removed from a phone.
    expect(first.addToPhone, isEmpty);
    expect(first.merged['en-US'], {'kept'});
    expect(first.upload, isFalse);

    final next = mergeLearned(
      phone: {
        'en-US': {'kept', 'used'},
      },
      account: {
        'en-US': {'kept'},
      },
      base: first.merged,
    );
    expect(next.merged['en-US'], {'kept', 'used'});
    expect(next.upload, isTrue);
  });

  test('a language that is not turned on here is left as the account has it', () {
    final merge = mergeLearned(
      phone: {
        'en-US': {'word'},
      },
      account: {
        'en-US': {'word'},
        'he': {'שלום'},
      },
      base: {
        'en-US': {'word'},
        'he': {'שלום'},
      },
    );
    expect(merge.merged['he'], {'שלום'});
    expect(merge.addToPhone, isEmpty);
    expect(merge.upload, isFalse);
  });

  test('in step: nothing to do', () {
    final words = {
      'en-US': {'a', 'b'},
      'he': <String>{},
    };
    final merge = mergeLearned(
      phone: words,
      account: {
        'en-US': {'b', 'a'},
      },
      base: {
        'en-US': {'a', 'b'},
      },
    );
    expect(merge.addToPhone, isEmpty);
    expect(merge.upload, isFalse);
  });

  test('words survive the trip through the account and the saved base', () {
    const item = LearnedWords({
      'he': {'שלום', 'מה'},
      'en-US': {'zerotrust'},
    });
    final json = learnedWordsCollection.encode(item);
    expect(json['kind'], 'learned');
    final back = learnedWordsCollection.fromJson(
      LearnedWords.itemId,
      (jsonDecode(jsonEncode(json)) as Map).cast<String, dynamic>(),
      DateTime(2026),
    );
    expect(back.words, item.words);
    expect(
      decodeLearnedBase(jsonEncode(encodeWordsByLanguage(item.words))),
      item.words,
    );
    expect(decodeLearnedBase('not json'), isNull);
    expect(decodeLearnedBase('[1]'), isNull);
    expect(decodeLearnedBase(null), isNull);
  });
}
