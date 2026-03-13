<div align="center">
<img src="fastlane/appassets/logo/dejpeg_logo_rounded.svg" height="140" alt="A gray mountain rotated 45 degrees clockwise with a lowercase letter j rotated 90 degrees clockwise">
<table>
<tr>
<td><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" width="240"></td>
<td><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/02.png" width="240"></td>
</tr>
</table>
<p>
<p align="center">
<a href="https://apt.izzysoft.de/fdroid/index/apk/com.je.dejpeg"><img src="fastlane/appassets/IzzyOnDroid.png" width="220" alt="IzzyOnDroid"></a>
<a href="https://codeberg.org/dryerlint/dejpeg/releases/download/latest/dejpeg-arm64-v8a.apk"><img src="fastlane/appassets/codeberg-badge.png" width="220" alt="Codeberg direct apk"></a>
<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.je.dejpeg%22%2C%22url%22%3A%22https%3A%2F%2Fcodeberg.org%2Fdryerlint%2Fdejpeg%22%2C%22author%22%3A%22dryerlint%22%2C%22name%22%3A%22DeJPEG%22%2C%22preferredApkIndex%22%3A0%2C%22overrideSource%22%3A%22Codeberg%22%7D"><img src="fastlane/appassets/obtanium.png" width="220" alt="Obtainium config"></a>
</div>

Removes JPEG artifacts and noise, not an upscaler.

uses:

* [FBCNN](https://github.com/jiaxi-jiang/FBCNN)
* [SCUNet](https://github.com/cszn/SCUNet)

(and others)

---

### Features

* Artifact removal
* Denoise
* Offline

---

### Models

[models](models/)

---

### Examples

[examples](examples/)

---

### Limits

* Local processing
* Standard image formats only and not huge

---

### Desktop

Can use [chaiNNer](https://chainner.app/)

FBCNN:

* install custom node (`chainner/`)
* use PyTorch models from the [FBCNN repo](https://github.com/jiaxi-jiang/FBCNN) (not ONNX)

---

### Contributing

Translate:
[https://translate.codeberg.org/projects/dejpeg](https://translate.codeberg.org/projects/dejpeg)


### Additional details

I'm sure you've heard about Google's restriction of 3rd party apks. I won't bore you with repetition and and just state the affect this has on this app, it will not be on the Play Store.

<details>
<summary><h3>building</h3></summary>

Standard gradle build proceses

For building with [Intel® Oidn Denoising](https://www.openimagedenoise.org/), see [oidn/readme.md](oidn/readme.md)

</details>

<details>
<summary><h3>credits and license</h3></summary>

### disclaimer:

This is a GUI for a select amount of `1x` ONNX processing models, used under their respective licenses (Apache 2.0)

You are welcome to embed parts of this app in your own project as long as it remains free as in beer, abides to the GPLv3 license, and credit has been given

  Credits to [@adrianerrea](https://github.com/adrianerrea/fromPytorchtoMobile) for a starting point, [FBCNN](https://github.com/jiaxi-jiang/FBCNN) and [SCUNet](https://github.com/cszn/SCUNet) creators plus all other model owners.

  DeJPEG is not affiliated or related with Topaz `DEJPEG` or any other similarly named software/project. Although I've wondered if the term 'JPEG' is copyrighted/trademarked due to it literally being the acronym for Joint Photographic Experts Group, for this reason I might need to change the app's name if legal issues start to occur
</details>
