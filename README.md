<div align='center'>
 <img src='https://github.com/user-attachments/assets/6d1e6fde-58b6-4991-9bb3-57b64627fbcf' height='140' alt="">
  <br>
   an Android image compression and denoising app
	<h2></h2>
    <table align='center'>
      <br>
        <tr>
            <td><img src='fastlane/metadata/android/en-US/images/phoneScreenshots/01.png' height='400' alt=""></td>
            <td><img src='fastlane/metadata/android/en-US/images/phoneScreenshots/02.png' height='400' alt=""></td>
        </tr>
    </table>
    <p>
        <a href='https://apt.izzysoft.de/fdroid/index/apk/com.je.dejpeg'><img src='https://raw.githubusercontent.com/jeeneo/dejpeg/refs/heads/main/assets/IzzyOnDroid.png' width="220" alt="IzzyOnDroid"></a>
        <a href='http://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/jeeneo/dejpeg'><img src='https://raw.githubusercontent.com/jeeneo/dejpeg/refs/heads/main/assets/obtanium.png' width="220" alt="Obtanium"></a>
        <a href='https://github.com/jeeneo/dejpeg/releases/latest'><img src='https://raw.githubusercontent.com/jeeneo/dejpeg/refs/heads/main/assets/badge_github.png' width="220" alt="Get it on GitHub"></a>
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

## limitations:
- processed locally, a device with 4gb of ram and a CPU with at least 4 threads is recommended.

## note:
De*JPEG* is not affiliated or related with Topaz `DEJPEG` or any other similarly named software/project

## credits:
[@adrianerrea](https://github.com/adrianerrea/fromPytorchtoMobile) for base application, [FBCNN](https://github.com/jiaxi-jiang/FBCNN) and [SCUNet](https://github.com/cszn/SCUNet) creators for the majority of the work; this app is just a wrapper for their models

## license:
all models used are under their respective licenses

you are free to embed parts of this app in your own project as long as it remains free/non-paywalled and must abide to the GPL v3 license
