#include <jni.h>
#include <OpenImageDenoise/oidn.h>
#include <android/log.h>
#include <string>
#include <cstring>
#include <vector>
#include <cstdio>

#define LOG_TAG "OIDN_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::vector<uint8_t> readFile(const char *path) {
    FILE *f = fopen(path, "rb");
    if (!f) {
        LOGE("readFile: cannot open '%s'", path);
        return {};
    }
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    rewind(f);
    if (size <= 0) {
        fclose(f);
        LOGE("readFile: empty or unreadable file '%s'", path);
        return {};
    }
    std::vector<uint8_t> buf(static_cast<size_t>(size));
    if (fread(buf.data(), 1, buf.size(), f) != buf.size()) {
        fclose(f);
        LOGE("readFile: short read on '%s'", path);
        return {};
    }
    fclose(f);
    LOGI("readFile: loaded %ld bytes from '%s'", size, path);
    return buf;
}

extern "C" {

JNIEXPORT jint JNICALL
Java_com_je_dejpeg_compose_OidnProcessor_nativeGetDeviceCount(JNIEnv *, jclass) {
    return (jint) oidnGetNumPhysicalDevices();
}

JNIEXPORT jstring JNICALL
Java_com_je_dejpeg_compose_OidnProcessor_nativeGetDeviceName(JNIEnv *env, jclass) {
    int count = oidnGetNumPhysicalDevices();
    if (count <= 0) return env->NewStringUTF("none");
    const char *name = oidnGetPhysicalDeviceString(0, "name");
    return env->NewStringUTF(name ? name : "CPU");
}

JNIEXPORT jfloatArray JNICALL
Java_com_je_dejpeg_compose_OidnProcessor_nativeDenoise(
        JNIEnv *env,
        jobject      /* this */,
        jfloatArray jColor,
        jint width,
        jint height,
        jstring jWeightsPath,
        jint numThreads,
        jint quality,
        jint maxMemoryMB,
        jboolean hdr,
        jboolean srgb,
        jfloat inputScale) {

    jsize len = env->GetArrayLength(jColor);
    if (len != (jsize) (width * height * 3)) {
        LOGE("nativeDenoise: buffer length mismatch: got %d expected %d",
             len, width * height * 3);
        return nullptr;
    }

    std::vector<uint8_t> weightsBuf;
    if (jWeightsPath != nullptr) {
        const char *path = env->GetStringUTFChars(jWeightsPath, nullptr);
        if (path) {
            weightsBuf = readFile(path);
            env->ReleaseStringUTFChars(jWeightsPath, path);
        }
        if (weightsBuf.empty()) {
            LOGE("nativeDenoise: failed to load weights");
            return nullptr;
        }
    }

    jfloat *colorPtr = env->GetFloatArrayElements(jColor, nullptr);
    if (!colorPtr) {
        LOGE("nativeDenoise: failed to pin float array");
        return nullptr;
    }

    OIDNDevice device = oidnNewDevice(OIDN_DEVICE_TYPE_CPU);
    if (!device) {
        LOGE("nativeDenoise: oidnNewDevice returned null");
        env->ReleaseFloatArrayElements(jColor, colorPtr, JNI_ABORT);
        return nullptr;
    }
    if (numThreads > 0) {
        oidnSetDeviceInt(device, "numThreads", numThreads);
    }
    oidnCommitDevice(device);

    const char *errMsg = nullptr;
    if (oidnGetDeviceError(device, &errMsg) != OIDN_ERROR_NONE) {
        LOGE("nativeDenoise: device commit error: %s", errMsg ? errMsg : "unknown");
        oidnReleaseDevice(device);
        env->ReleaseFloatArrayElements(jColor, colorPtr, JNI_ABORT);
        return nullptr;
    }

    OIDNFilter filter = oidnNewFilter(device, "RT");
    if (!filter) {
        LOGE("nativeDenoise: oidnNewFilter returned null");
        oidnReleaseDevice(device);
        env->ReleaseFloatArrayElements(jColor, colorPtr, JNI_ABORT);
        return nullptr;
    }

    if (!weightsBuf.empty()) {
        oidnSetSharedFilterData(filter, "weights",
                                weightsBuf.data(),
                                weightsBuf.size());
    }

    oidnSetSharedFilterImage(filter, "color", colorPtr, OIDN_FORMAT_FLOAT3, width, height, 0, 0, 0);
    oidnSetSharedFilterImage(filter, "output", colorPtr, OIDN_FORMAT_FLOAT3, width, height, 0, 0,
                             0);
    oidnSetFilterBool(filter, "hdr", (bool) hdr);
    oidnSetFilterBool(filter, "srgb", (bool) srgb);
    if (quality > 0) oidnSetFilterInt(filter, "quality", quality);
    if (maxMemoryMB > 0) oidnSetFilterInt(filter, "maxMemoryMB", maxMemoryMB);
    if (inputScale > 0.0f) oidnSetFilterFloat(filter, "inputScale", inputScale);

    oidnCommitFilter(filter);
    weightsBuf.clear();
    weightsBuf.shrink_to_fit();

    if (oidnGetDeviceError(device, &errMsg) != OIDN_ERROR_NONE) {
        LOGE("nativeDenoise: filter commit error: %s", errMsg ? errMsg : "unknown");
        oidnReleaseFilter(filter);
        oidnReleaseDevice(device);
        env->ReleaseFloatArrayElements(jColor, colorPtr, JNI_ABORT);
        return nullptr;
    }

    oidnExecuteFilter(filter);

    if (oidnGetDeviceError(device, &errMsg) != OIDN_ERROR_NONE) {
        LOGE("nativeDenoise: execute error: %s", errMsg ? errMsg : "unknown");
        oidnReleaseFilter(filter);
        oidnReleaseDevice(device);
        env->ReleaseFloatArrayElements(jColor, colorPtr, JNI_ABORT);
        return nullptr;
    }

    LOGI("nativeDenoise: success %dx%d", width, height);

    oidnReleaseFilter(filter);
    oidnReleaseDevice(device);
    env->ReleaseFloatArrayElements(jColor, colorPtr, 0);
    return jColor;
}

}
