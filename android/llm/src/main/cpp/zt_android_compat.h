// SPDX-License-Identifier: AGPL-3.0-only
// Included before every llama.cpp source file. The Offline edition runs on Android 5
// (API 21), where bionic has no posix_madvise (added in API 23). madvise does the same
// thing for llama.cpp's purpose (read-ahead hints on the mapped model) and exists on
// every Android version.
#pragma once
#if defined(__ANDROID__) && __ANDROID_API__ < 23
#include <sys/mman.h>
#define posix_madvise(addr, len, advice) madvise((addr), (len), (advice))
#ifndef POSIX_MADV_NORMAL
#define POSIX_MADV_NORMAL MADV_NORMAL
#endif
#ifndef POSIX_MADV_RANDOM
#define POSIX_MADV_RANDOM MADV_RANDOM
#endif
#ifndef POSIX_MADV_SEQUENTIAL
#define POSIX_MADV_SEQUENTIAL MADV_SEQUENTIAL
#endif
#ifndef POSIX_MADV_WILLNEED
#define POSIX_MADV_WILLNEED MADV_WILLNEED
#endif
#ifndef POSIX_MADV_DONTNEED
#define POSIX_MADV_DONTNEED MADV_DONTNEED
#endif
#endif
