// Pont entre Kotlin (voice/WhisperLib.kt) et whisper.cpp.
// Trois fonctions : charger un modèle, transcrire un morceau d'audio, libérer le modèle.
#include <jni.h>
#include <string>
#include <android/log.h>
#include "whisper.h"

#define LOG(...) __android_log_print(ANDROID_LOG_INFO, "FernWhisper", __VA_ARGS__)

extern "C" JNIEXPORT jlong JNICALL
Java_com_atelierjlg_fern_keyboard_voice_WhisperLib_initContext(JNIEnv *env, jobject /* this */, jstring path) {
    const char *p = env->GetStringUTFChars(path, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(p, cparams);
    env->ReleaseStringUTFChars(path, p);
    if (ctx == nullptr) LOG("modèle illisible");
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_com_atelierjlg_fern_keyboard_voice_WhisperLib_freeContext(JNIEnv *, jobject, jlong ptr) {
    if (ptr != 0) whisper_free(reinterpret_cast<whisper_context *>(ptr));
}

// Le texte revient en octets UTF-8 (décodés côté Kotlin) : Whisper peut couper un caractère en deux,
// ce que NewStringUTF ne supporte pas.
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_atelierjlg_fern_keyboard_voice_WhisperLib_transcribe(
        JNIEnv *env, jobject, jlong ptr, jfloatArray audio, jint threads, jstring language, jstring prompt) {
    auto *ctx = reinterpret_cast<whisper_context *>(ptr);
    if (ctx == nullptr) return env->NewByteArray(0);

    const char *lang = env->GetStringUTFChars(language, nullptr);
    const char *init = env->GetStringUTFChars(prompt, nullptr);
    jsize n = env->GetArrayLength(audio);
    jfloat *samples = env->GetFloatArrayElements(audio, nullptr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.no_context = true;
    params.no_timestamps = true;
    params.suppress_blank = true;
    params.n_threads = threads;
    params.language = lang;
    // Un petit texte d'exemple pousse Whisper vers la ponctuation et le français du quotidien.
    params.initial_prompt = init;

    int rc = whisper_full(ctx, params, samples, n);
    env->ReleaseFloatArrayElements(audio, samples, JNI_ABORT);

    std::string out;
    if (rc == 0) {
        const int segments = whisper_full_n_segments(ctx);
        for (int i = 0; i < segments; ++i) out += whisper_full_get_segment_text(ctx, i);
    } else {
        LOG("échec de la transcription : %d", rc);
    }
    env->ReleaseStringUTFChars(language, lang);
    env->ReleaseStringUTFChars(prompt, init);
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(out.size()));
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(out.size()), reinterpret_cast<const jbyte *>(out.data()));
    return bytes;
}
