import 'package:flutter_test/flutter_test.dart';
import 'package:zerotrust_keyboard/app/email_links.dart';

void main() {
  test('the reset link of the email carries the one-time code after the #', () {
    expect(
      EmailLinks.recoveryTokenOf(
        Uri.parse(
          'https://zerotrust.bz/auth/keyboard/#token_hash=abc123&type=recovery',
        ),
      ),
      'abc123',
    );
  });

  test('the address the app asks Supabase to send people to is that page', () {
    expect(EmailLinks.passwordReset, 'https://zerotrust.bz/auth/keyboard/');
    expect(
      EmailLinks.recoveryTokenOf(
        Uri.parse('${EmailLinks.passwordReset}#token_hash=t&type=recovery'),
      ),
      't',
    );
  });

  test('other links are not reset links', () {
    for (final link in [
      // Another site, or not https.
      'https://example.com/auth/keyboard/#token_hash=abc&type=recovery',
      'http://zerotrust.bz/auth/keyboard/#token_hash=abc&type=recovery',
      // Another page of the site.
      'https://zerotrust.bz/join/tasks#token_hash=abc&type=recovery',
      // Not a recovery code, or none at all.
      'https://zerotrust.bz/auth/keyboard/#token_hash=abc&type=signup',
      'https://zerotrust.bz/auth/keyboard/#type=recovery',
      'https://zerotrust.bz/auth/keyboard/#token_hash=&type=recovery',
      'https://zerotrust.bz/auth/keyboard/',
      // The code must be after the #, where no server sees it.
      'https://zerotrust.bz/auth/keyboard/?token_hash=abc&type=recovery',
    ]) {
      expect(EmailLinks.recoveryTokenOf(Uri.parse(link)), isNull, reason: link);
    }
  });
}
