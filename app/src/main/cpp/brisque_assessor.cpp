#include <jni.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/imgcodecs.hpp>
#include <opencv2/quality.hpp>
#include <android/log.h>
#include <string>
#include <dlfcn.h>

#define TAG "BrisqueJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGI("JNI_OnLoad called for libbrisque_jni.so");
    const char* libs[] = {
        "libopencv_core.so",
        "libopencv_imgproc.so",
        "libopencv_imgcodecs.so",
        "libopencv_quality.so"
    };
    for (const char* lib : libs) {
        void* handle = dlopen(lib, RTLD_NOW | RTLD_GLOBAL);
        if (!handle) {
            LOGE("Failed to load %s with RTLD_GLOBAL: %s", lib, dlerror());
        } else {
            LOGD("Successfully loaded %s with RTLD_GLOBAL", lib);
        }
    }
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_je_dejpeg_compose_utils_brisque_BRISQUEAssessor_computeBRISQUEFromFile(
        JNIEnv *env,
        jobject obj,
        jstring imagePath,
        jstring modelPath,
        jstring rangePath) {

    LOGD("Native method called");
    const char *imagePathStr = env->GetStringUTFChars(imagePath, nullptr);
    const char *modelPathStr = env->GetStringUTFChars(modelPath, nullptr);
    const char *rangePathStr = env->GetStringUTFChars(rangePath, nullptr);

    try {
        LOGD("Loading image from: %s", imagePathStr);
        cv::Mat image = cv::imread(imagePathStr);

        if (image.empty()) {
            LOGE("Error: Could not load image from %s", imagePathStr);
            env->ReleaseStringUTFChars(imagePath, imagePathStr);
            env->ReleaseStringUTFChars(modelPath, modelPathStr);
            env->ReleaseStringUTFChars(rangePath, rangePathStr);
            return -1.0f;
        }
        LOGD("Image loaded successfully: %d x %d, channels: %d", image.cols, image.rows, image.channels());
        cv::Mat grayImage;
        if (image.channels() == 3) {
            LOGD("Converting BGR to GRAY");
            cv::cvtColor(image, grayImage, cv::COLOR_BGR2GRAY);
        } else if (image.channels() == 4) {
            LOGD("Converting BGRA to GRAY");
            cv::cvtColor(image, grayImage, cv::COLOR_BGRA2GRAY);
        } else {
            grayImage = image.clone();
        }
        if (grayImage.type() != CV_8U) {
            LOGD("Converting image to CV_8U");
            grayImage.convertTo(grayImage, CV_8U);
        }
        LOGD("Computing BRISQUE score using model: %s", modelPathStr);
        cv::Scalar scoreScalar = cv::quality::QualityBRISQUE::compute(
            grayImage,
            cv::String(modelPathStr),
            cv::String(rangePathStr)
        );
        float brisqueScore = static_cast<float>(scoreScalar[0]);
        LOGD("BRISQUE Score computed successfully: %f", brisqueScore);
        image.release();
        grayImage.release();
        env->ReleaseStringUTFChars(imagePath, imagePathStr);
        env->ReleaseStringUTFChars(modelPath, modelPathStr);
        env->ReleaseStringUTFChars(rangePath, rangePathStr);
        return brisqueScore;
    } catch (const std::exception& e) {
        LOGE("Error computing BRISQUE: %s", e.what());
        env->ReleaseStringUTFChars(imagePath, imagePathStr);
        env->ReleaseStringUTFChars(modelPath, modelPathStr);
        env->ReleaseStringUTFChars(rangePath, rangePathStr);
        return -1.0f;
    } catch (...) {
        LOGE("Unknown error computing BRISQUE");
        env->ReleaseStringUTFChars(imagePath, imagePathStr);
        env->ReleaseStringUTFChars(modelPath, modelPathStr);
        env->ReleaseStringUTFChars(rangePath, rangePathStr);
        return -1.0f;
    }
}
