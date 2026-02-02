// Simplified BRISQUE JNI for static linking
// All OpenCV code is embedded - no dlopen needed!

#include <jni.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/imgcodecs.hpp>
#include <opencv2/quality.hpp>
#include <android/log.h>

#define TAG "BrisqueJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

extern "C" JNIEXPORT jfloat JNICALL
Java_com_je_dejpeg_compose_utils_brisque_BRISQUEAssessor_computeBRISQUEFromFile(
        JNIEnv *env,
        jobject /* obj */,
        jstring imagePath,
        jstring modelPath,
        jstring rangePath) {

    const char *imagePathStr = env->GetStringUTFChars(imagePath, nullptr);
    const char *modelPathStr = env->GetStringUTFChars(modelPath, nullptr);
    const char *rangePathStr = env->GetStringUTFChars(rangePath, nullptr);

    float result = -1.0f;

    cv::Mat image = cv::imread(imagePathStr);
    if (image.empty()) {
        LOGE("Could not load image: %s", imagePathStr);
        env->ReleaseStringUTFChars(imagePath, imagePathStr);
        env->ReleaseStringUTFChars(modelPath, modelPathStr);
        env->ReleaseStringUTFChars(rangePath, rangePathStr);
        return -1.0f;
    }

    cv::Mat grayImage;
    if (image.channels() == 3) {
        cv::cvtColor(image, grayImage, cv::COLOR_BGR2GRAY);
    } else if (image.channels() == 4) {
        cv::cvtColor(image, grayImage, cv::COLOR_BGRA2GRAY);
    } else {
        grayImage = image;
    }

    if (grayImage.type() != CV_8U) {
        grayImage.convertTo(grayImage, CV_8U);
    }

    cv::Scalar score = cv::quality::QualityBRISQUE::compute(
        grayImage,
        cv::String(modelPathStr),
        cv::String(rangePathStr)
    );

    result = static_cast<float>(score[0]);
    LOGD("BRISQUE score: %f", result);

    env->ReleaseStringUTFChars(imagePath, imagePathStr);
    env->ReleaseStringUTFChars(modelPath, modelPathStr);
    env->ReleaseStringUTFChars(rangePath, rangePathStr);
    
    return result;
}
