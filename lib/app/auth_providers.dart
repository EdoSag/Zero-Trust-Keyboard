import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

/// Every Supabase auth event (sign-in, sign-out).
final authStateChangesProvider = StreamProvider<AuthState>(
  (ref) => Supabase.instance.client.auth.onAuthStateChange,
);

/// The signed-in account, or null. The keyboard works without one. A session
/// only proves identity to the backend; it never unlocks the vault.
final currentUserProvider = Provider<User?>((ref) {
  ref.watch(authStateChangesProvider);
  return Supabase.instance.client.auth.currentUser;
});
