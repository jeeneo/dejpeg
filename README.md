<div align="center">
  <img src="https://github.com/user-attachments/assets/6d1e6fde-58b6-4991-9bb3-57b64627fbcf" height="140" alt="">
  <br>
  an app for removing noise and compression from photos
  <h2></h2>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" 
       style="width: 300px; max-width: 100%; height: auto; margin: 10px;" alt="an image of two rectangular areas of the same picture of yellow tulips, the one at the top is 50 percent compression, the one below has 0 percent compression but still has noticeable PNG compression artifacts">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/02.png" 
       style="width: 300px; max-width: 100%; height: auto; margin: 10px;" alt="an image fruit, two purple figs sitting directly on a table. one fig is behind the other fig. the fig in the front is cut in half but still remaining upright. text on the left near the first fig reads fig 1. text near the fig on the right reads fig 2. both figs are sitting on top of a table that has the text table 1. there's a slider down the middle of the image showing the differences between a processed image and the original one">
  <p>
<p align="center">
  <a href="https://apt.izzysoft.de/fdroid/index/apk/com.je.dejpeg"><img src="https://raw.githubusercontent.com/jeeneo/dejpeg/refs/heads/main/assets/IzzyOnDroid.png" width="220" alt="IzzyOnDroid"></a>
  <a href="http://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/jeeneo/dejpeg"><img src="https://raw.githubusercontent.com/jeeneo/dejpeg/refs/heads/main/assets/obtanium.png" width="220" alt="Obtanium"></a>
  <a href="https://github.com/jeeneo/dejpeg/releases/latest"><img src="https://raw.githubusercontent.com/jeeneo/dejpeg/refs/heads/main/assets/badge_github.png" width="220" alt="Get it on GitHub"></a>
</p>
  </p>
</div>


## features:
- batch processing
- supports most image formats
- before/after view
- [fully offline](https://github.com/jeeneo/dejpeg/blob/main/app/src/main/AndroidManifest.xml)

(this is not a "super resolution AI upscaler", but a simple non-destructive way to clean up compressed/noisy images)

## models (required):
[FBCNN](https://github.com/jeeneo/FBCNN-mobile/releases/latest) (JPEG compression)

[SCUNet](https://github.com/jeeneo/SCUNet-mobile/releases/latest) (Grain/Noise)

info [here](https://github.com/jeeneo/dejpeg/wiki/model-types) (also in the apps FAQ)

you can also run other experimental models, more info [here](https://github.com/jeeneo/dejpeg-experimental)

## limitations:
- processed locally, minimum 4gb ram and 4 threads recommended

see the [wiki](https://github.com/jeeneo/dejpeg/wiki) for more information

## desktop

there's a beta version of DeJPEG for desktops (Linux and Windows) [here](https://github.com/jeeneo/dejpeg-desktop) (made WITHOUT electron, size is 50mb)

## note:
De*JPEG* is not affiliated or related with Topaz `DEJPEG` or any other similarly named software/project

## credits:
[@adrianerrea](https://github.com/adrianerrea/fromPytorchtoMobile) for base application, [FBCNN](https://github.com/jiaxi-jiang/FBCNN) and [SCUNet](https://github.com/cszn/SCUNet) creators and all other model creators, this is only a wrapper for `1x` ONNX models

## license:
all models used are under their respective licenses

you are free to embed parts of this app in your own project as long as it remains free/non-paywalled and must abide to the GPL v3 license
