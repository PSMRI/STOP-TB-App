#include <jni.h>
#include <string>

const char* M_ENCRYPTED_PASS_KEY = ENCRYPTED_PASS_KEY;
const char* M_ABHA_CLIENT_SECRET = ABHA_CLIENT_SECRET;
const char* M_ABHA_CLIENT_ID = ABHA_CLIENT_ID;
const char* M_BASE_TMC_URL = BASE_TMC_URL;
const char* M_BASE_ABHA_URL = BASE_ABHA_URL;
const char* M_ABHA_TOKEN_URL = ABHA_TOKEN_URL;
const char* M_ABHA_AUTH_URL = ABHA_AUTH_URL;
const char* M_CHAT_URL = CHAT_URL;

extern "C" JNIEXPORT jstring JNICALL
Java_org_piramalswasthya_stoptb_utils_KeyUtils_encryptedPassKey(JNIEnv* env, jobject thiz) {
    return env->NewStringUTF(M_ENCRYPTED_PASS_KEY);
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_piramalswasthya_stoptb_utils_KeyUtils_abhaClientSecret(JNIEnv* env, jobject thiz) {
    return env->NewStringUTF(M_ABHA_CLIENT_SECRET);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_org_piramalswasthya_stoptb_utils_KeyUtils_abhaClientID(JNIEnv* env, jobject thiz) {
    return env->NewStringUTF(M_ABHA_CLIENT_ID);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_org_piramalswasthya_stoptb_utils_KeyUtils_baseTMCUrl(JNIEnv* env, jobject thiz) {
    return env->NewStringUTF(M_BASE_TMC_URL);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_org_piramalswasthya_stoptb_utils_KeyUtils_baseAbhaUrl(JNIEnv* env, jobject thiz) {
    return env->NewStringUTF(M_BASE_ABHA_URL);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_org_piramalswasthya_stoptb_utils_KeyUtils_abhaTokenUrl(JNIEnv* env, jobject thiz) {
    return env->NewStringUTF(M_ABHA_TOKEN_URL);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_org_piramalswasthya_stoptb_utils_KeyUtils_abhaAuthUrl(JNIEnv* env, jobject thiz) {
    return env->NewStringUTF(M_ABHA_AUTH_URL);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_org_piramalswasthya_stoptb_utils_KeyUtils_chatUrl(JNIEnv* env, jobject thiz) {
    return env->NewStringUTF(M_CHAT_URL);
}