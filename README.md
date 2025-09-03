<div align="center">
  <img src="https://github.com/user-attachments/assets/6d1e6fde-58b6-4991-9bb3-57b64627fbcf" height="140" alt="">
  <br>
  an app for removing noise and compression from photos
  <h2></h2>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" 
       style="width: 300px; max-width: 100%; height: auto; margin: 10px;" alt="">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/02.png" 
       style="width: 300px; max-width: 100%; height: auto; margin: 10px;" alt="">
  <p>
<p align="center">
  <a href="https://apt.izzysoft.de/fdroid/index/apk/com.je.dejpeg"><img src="https://raw.githubusercontent.com/jeeneo/dejpeg/refs/heads/main/assets/IzzyOnDroid.png" width="220" alt="IzzyOnDroid"></a>
  <a href="http://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/jeeneo/dejpeg"><img src="https://raw.githubusercontent.com/jeeneo/dejpeg/refs/heads/main/assets/obtanium.png" width="220" alt="Obtanium"></a>
  <a href="https://github.com/jeeneo/dejpeg/releases/latest"><img src="https://raw.githubusercontent.com/jeeneo/dejpeg/refs/heads/main/assets/badge_github.png" width="220" alt="Get it on GitHub"></a>
</p>
  </p>
</div>

### note: see [this issue](https://github.com/jeeneo/dejpeg/issues/26) before downloading

## features:
- batch processing
- supports most image formats
- before/after view
- [fully offline](https://github.com/jeeneo/dejpeg/blob/main/app/src/main/AndroidManifest.xml)

this is not a "super resolution AI upscaler", but simple non-destructive method for cleaning up/restoring images

## models (required):
[FBCNN](https://github.com/jeeneo/FBCNN-mobile/releases/latest) (JPEG compression)

[SCUNet](https://github.com/jeeneo/SCUNet-mobile/releases/latest) (Grain/Noise)

info [here](https://github.com/jeeneo/dejpeg/wiki/model-types) (also in the apps FAQ)

you can also run other experimental models, more info [here](https://github.com/jeeneo/dejpeg-experimental)

## limitations:
- processed locally, minimum 4gb ram and 4 threads recommended
- limited to images below 8000 pixels, and images above 4000px will affect performance

see the [wiki](https://github.com/jeeneo/dejpeg/wiki) for more information

## desktop
for desktops, you can try my [desktop version](https://github.com/jeeneo/dejpeg-desktop), or more advanced, [chaiNNer](https://github.com/chaiNNer-org/chaiNNer) (it has a learning curve and takes a bit to set up, but works pretty well.)

## note/disclaimer:
De*JPEG* is not affiliated or related with Topaz `DEJPEG` or any other similarly named software/project.

this app stems from personal uses and was modified for public release

## credits:
[@adrianerrea](https://github.com/adrianerrea/fromPytorchtoMobile) for the base application, [FBCNN](https://github.com/jiaxi-jiang/FBCNN) and [SCUNet](https://github.com/cszn/SCUNet) creators plus all other model creators.

this is a GUI wrapper for a select amount of `1x` ONNX image processing models

## license:
all models used are under their respective licenses

you are free to embed parts of this app in your own project as long as it remains free/non-paywalled and must abide to the GPL v3 license
