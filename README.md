<div align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" height="140" alt="">
  <br>
  An open source app for removing noise and compression from photos
  <h2></h2>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" 
       style="width: 240px; max-width: 100%; height: auto; margin: 10px;" alt="">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/02.png" 
       style="width: 240px; max-width: 100%; height: auto; margin: 10px;" alt="">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/03.png" 
       style="width: 240px; max-width: 100%; height: auto; margin: 10px;" alt="">
  <p>
<p align="center">
  <a href="https://apt.izzysoft.de/fdroid/index/apk/com.je.dejpeg"><img src="fastlane/githubassets/IzzyOnDroid.png" width="220" alt="IzzyOnDroid"></a>
  <a href="http://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/jeeneo/dejpeg"><img src="fastlane/githubassets/obtanium.png" width="220" alt="Obtanium"></a>
  <a href="https://github.com/jeeneo/dejpeg/releases/latest/download/dejpeg-arm64-v8a.apk"><img src="fastlane/githubassets/badge_github.png" width="220" alt="Get it on GitHub"></a>
</p>
  </p>
</div>

## features:
- batch processing
- supports most image formats
- before/after view
- custom models (beta)
- image descaling (beta)
- [fully offline](https://github.com/jeeneo/dejpeg/blob/main/app/src/main/AndroidManifest.xml)

This is not a "super resolution AI upscaler", but simple non-destructive method for cleaning up/restoring images

## models (required):
[FBCNN](https://github.com/jeeneo/FBCNN-mobile/releases/latest) (JPEG compression)

[SCUNet](https://github.com/jeeneo/SCUNet-mobile/releases/latest) (Grain/Noise)

Info [here](https://github.com/jeeneo/dejpeg/wiki/model-types) (also in the apps FAQ)

you can also run other experimental models, more info [here](https://github.com/jeeneo/dejpeg-experimental)

## limitations:
- processed locally, minimum 4gb ram and 4 threads recommended
- very large images might cause crashes

See the [wiki](https://github.com/jeeneo/dejpeg/wiki) for more information

## desktop
The desktop version was deprecated and lacking many features, please use [chaiNNer](https://github.com/chaiNNer-org/chaiNNer)

## note/disclaimer:
De*JPEG* is not affiliated or related with Topaz `DEJPEG` or any other similarly named software/project.

This app stems from personal uses and was modified for public release

## credits:
[@adrianerrea](https://github.com/adrianerrea/fromPytorchtoMobile) for the base application, [FBCNN](https://github.com/jiaxi-jiang/FBCNN) and [SCUNet](https://github.com/cszn/SCUNet) creators plus all other model creators.

This is a GUI wrapper for a select amount of `1x` ONNX image processing models

## license:
All models used are under their respective licenses

You are free to embed parts of this app in your own project as long as it remains free/non-paywalled and must abide to the GPL v3 license

## building

this app includes OpenCV with [BRISQUE analysis for descaling an image](https://github.com/jeeneo/dejpeg/issues/24), which is experimental but ive occasionally found it useful.

you need the NDK installed to fully build with [opencv](https://github.com/opencv/opencv) + [opencv contrib](https://github.com/opencv/opencv_contrib) (for BRISQUE), else prebuilt binaries are included and you can just run `./gradlew clean assembleDebug`

use this to build OpenCV w/ BRISQUE (edit it respective to your location of the NDK):

```bash
cmake \
  -DCMAKE_TOOLCHAIN_FILE=/home/<user>/Android/Sdk/ndk/27.3.13750724/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_NATIVE_API_LEVEL=21 \
  -DANDROID_STL=c++_shared \
  -DCMAKE_BUILD_TYPE=MinSizeRel \
  -DCMAKE_CXX_FLAGS_MINSIZEREL="-Os -DNDEBUG -fvisibility=hidden -fvisibility-inlines-hidden -ffunction-sections -fdata-sections" \
  -DCMAKE_C_FLAGS_MINSIZEREL="-Os -DNDEBUG -fvisibility=hidden -fvisibility-inlines-hidden -ffunction-sections -fdata-sections" \
  -DCMAKE_SHARED_LINKER_FLAGS="-Wl,--gc-sections" \
  -DOPENCV_EXTRA_MODULES_PATH=/home/<user>/opencv/opencv_contrib/modules \
  -DBUILD_SHARED_LIBS=ON \
  -DBUILD_TESTS=OFF \
  -DBUILD_PERF_TESTS=OFF \
  -DBUILD_ANDROID_EXAMPLES=OFF \
  -DBUILD_DOCS=OFF \
  -DBUILD_opencv_java=OFF \
  -DWITH_GSTREAMER=OFF \
  -DWITH_V4L=OFF \
  -DWITH_GTK=OFF \
  -DWITH_QT=OFF \
  -DWITH_IPP=OFF \
  -DWITH_CUDA=OFF \
  -DWITH_OPENCL=OFF \
  -DWITH_VTK=OFF \
  -DWITH_JASPER=OFF \
  -DWITH_OPENEXR=OFF \
  -DBUILD_EXAMPLES=OFF \
  -DBUILD_PACKAGE=OFF \
  -DBUILD_opencv_core=ON \
  -DBUILD_opencv_imgproc=ON \
  -DBUILD_opencv_ml=ON \
  -DBUILD_opencv_imgcodecs=ON \
  -DBUILD_opencv_quality=ON \
  -DBUILD_opencv_dnn=OFF \
  -DBUILD_opencv_video=OFF \
  -DBUILD_opencv_features2d=OFF \
  -DBUILD_opencv_calib3d=OFF \
  /home/<user>/source/opencv/opencv
```

```bash
ANDROID_HOME=/home/<user>/Android/Sdk ANDROID_SDK_ROOT=/home/<user>/Android/Sdk make
```

then strip debug symbols:
```bash
llvm-strip lib/arm64-v8a/libopencv_{core,imgproc,ml,imgcodecs,quality}.so
```

then copy out `core, imgproc, ml, imgcodecs and quality`.so files into `src/main/jniLibs`

you can skip stripping and just copy the libs from `lib/arm64-v8a` to there and the next operation will strip them but you'll need to build a `Release` instead of `Debug` (and sign)

delete `libbrisque_jni.so` from `jniLibs`else the build will fail then run

```bash
BUILD_BRISQUE_JNI=ON ./gradlew clean assembleDebug
```

note: the binaries in the official release are compressed using `upx --best --lzma` after being stripped of debug symbols, excluding `libbrisque_jni.so` and `libc++_shared.so` for IzzyOnDroid's 30mb limit, else you can skip with it being ~34mb if building fully from source