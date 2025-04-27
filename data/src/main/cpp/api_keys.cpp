#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_swarn_chatapp_data_remote_ApiKeysImpl_getPieSocketApiKey(
        JNIEnv *env,
        jobject) {
    std::string apiKey = "YOUR_API_KEY";
    return env->NewStringUTF(apiKey.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_swarn_chatapp_data_remote_ApiKeysImpl_getPieSocketClusterId(
        JNIEnv *env,
        jobject ) {
    std::string clusterId = "YOUR_CLUSTER_ID";
    return env->NewStringUTF(clusterId.c_str());
} 