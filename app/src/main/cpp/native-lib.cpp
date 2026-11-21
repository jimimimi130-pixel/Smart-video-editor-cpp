#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "VideoEditorCore"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_videoeditor_MainActivity_prepareTimelineSummary(
        JNIEnv* env,
        jobject /* this */,
        jstring projectName,
        jint clipCount,
        jint durationSec) {
    const char* namePtr = env->GetStringUTFChars(projectName, nullptr);
    std::string title = namePtr ? namePtr : "Untitled";
    if (namePtr) {
        env->ReleaseStringUTFChars(projectName, namePtr);
    }

    LOGI("Preparing timeline for %s with %d clips", title.c_str(), clipCount);

    std::string summary = "Project: " + title +
                          " | Clips: " + std::to_string(clipCount) +
                          " | Duration: " + std::to_string(durationSec) + "s";
    return env->NewStringUTF(summary.c_str());
}
