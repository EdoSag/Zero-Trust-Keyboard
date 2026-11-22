// SPDX-License-Identifier: AGPL-3.0-only
// JNI layer between dev.sagron.zerotrustkeyboard.lm.LlamaNative and llama.cpp.
// One model with one context per handle; the Kotlin side uses it from one thread at a time.
#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <string>
#include <vector>

#include "llama.h"

#define TAG "ZtLlm"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

namespace {

struct Engine {
    llama_model *model = nullptr;
    llama_context *context = nullptr;
    const llama_vocab *vocab = nullptr;
    std::atomic<bool> cancelled{false};
};

// llama.cpp logs every model detail at load; keep warnings and errors only. Never the text.
void logCallback(enum ggml_log_level level, const char *text, void *) {
    if (level == GGML_LOG_LEVEL_ERROR) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", text);
    } else if (level == GGML_LOG_LEVEL_WARN) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "%s", text);
    }
}

bool abortCallback(void *data) {
    return static_cast<Engine *>(data)->cancelled.load();
}

void freeEngine(Engine *engine) {
    if (engine->context != nullptr) llama_free(engine->context);
    if (engine->model != nullptr) llama_model_free(engine->model);
    delete engine;
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_dev_sagron_zerotrustkeyboard_lm_LlamaNative_open(JNIEnv *env, jclass, jstring modelPath, jint threads, jint contextSize) {
    llama_log_set(logCallback, nullptr);
    llama_backend_init();
    auto *engine = new Engine();

    llama_model_params modelParams = llama_model_default_params();
    modelParams.n_gpu_layers = 0;
    modelParams.use_mmap = true;
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    engine->model = llama_model_load_from_file(path, modelParams);
    env->ReleaseStringUTFChars(modelPath, path);
    if (engine->model == nullptr) {
        LOGW("could not load the model");
        freeEngine(engine);
        return 0;
    }
    engine->vocab = llama_model_get_vocab(engine->model);

    llama_context_params contextParams = llama_context_default_params();
    contextParams.n_ctx = contextSize;
    contextParams.n_batch = contextSize;
    contextParams.n_ubatch = 256;
    contextParams.n_threads = threads;
    contextParams.n_threads_batch = threads;
    contextParams.no_perf = true;
    contextParams.abort_callback = abortCallback;
    contextParams.abort_callback_data = engine;
    engine->context = llama_init_from_model(engine->model, contextParams);
    if (engine->context == nullptr) {
        LOGW("could not make a context");
        freeEngine(engine);
        return 0;
    }
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT void JNICALL
Java_dev_sagron_zerotrustkeyboard_lm_LlamaNative_close(JNIEnv *, jclass, jlong handle) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    if (engine != nullptr) freeEngine(engine);
}

// Stops the generate() that is running on another thread, and any later one until allow().
JNIEXPORT void JNICALL
Java_dev_sagron_zerotrustkeyboard_lm_LlamaNative_cancel(JNIEnv *, jclass, jlong handle) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    if (engine != nullptr) engine->cancelled.store(true);
}

JNIEXPORT void JNICALL
Java_dev_sagron_zerotrustkeyboard_lm_LlamaNative_allow(JNIEnv *, jclass, jlong handle) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    if (engine != nullptr) engine->cancelled.store(false);
}

// Continues the prompt (UTF-8, may hold the model's special tokens) with up to maxTokens
// tokens, always taking the most likely one. Stops at the end of the answer, or at a line
// break when stopAtNewline. Returns the new text as UTF-8, or null when it failed or was
// cancelled.
JNIEXPORT jbyteArray JNICALL
Java_dev_sagron_zerotrustkeyboard_lm_LlamaNative_generate(JNIEnv *env, jclass, jlong handle, jbyteArray promptUtf8, jint maxTokens, jboolean stopAtNewline) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    if (engine == nullptr || promptUtf8 == nullptr || maxTokens <= 0) return nullptr;

    std::string prompt(static_cast<size_t>(env->GetArrayLength(promptUtf8)), '\0');
    env->GetByteArrayRegion(promptUtf8, 0, static_cast<jsize>(prompt.size()), reinterpret_cast<jbyte *>(&prompt[0]));

    const int contextSize = static_cast<int>(llama_n_ctx(engine->context));
    int count = -llama_tokenize(engine->vocab, prompt.data(), static_cast<int32_t>(prompt.size()), nullptr, 0, true, true);
    if (count <= 0 || count + maxTokens > contextSize) return nullptr;
    std::vector<llama_token> tokens(static_cast<size_t>(count));
    if (llama_tokenize(engine->vocab, prompt.data(), static_cast<int32_t>(prompt.size()), tokens.data(), count, true, true) != count) return nullptr;

    // Every request starts from an empty memory: nothing of an earlier text stays.
    llama_memory_clear(llama_get_memory(engine->context), true);

    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    std::string out;
    bool ok = true;
    llama_batch batch = llama_batch_get_one(tokens.data(), count);
    llama_token next = 0;
    for (int produced = 0; produced < maxTokens; produced++) {
        if (engine->cancelled.load() || llama_decode(engine->context, batch) != 0) {
            ok = false;
            break;
        }
        next = llama_sampler_sample(sampler, engine->context, -1);
        if (llama_vocab_is_eog(engine->vocab, next)) break;
        char piece[256];
        int n = llama_token_to_piece(engine->vocab, next, piece, sizeof(piece), 0, false);
        if (n < 0) {
            ok = false;
            break;
        }
        std::string text(piece, static_cast<size_t>(n));
        if (stopAtNewline) {
            size_t newline = text.find('\n');
            if (newline != std::string::npos) {
                out.append(text, 0, newline);
                break;
            }
        }
        out += text;
        batch = llama_batch_get_one(&next, 1);
    }
    llama_sampler_free(sampler);
    if (!ok || engine->cancelled.load()) return nullptr;

    jbyteArray result = env->NewByteArray(static_cast<jsize>(out.size()));
    if (result != nullptr)
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(out.size()), reinterpret_cast<const jbyte *>(out.data()));
    return result;
}

} // extern "C"
