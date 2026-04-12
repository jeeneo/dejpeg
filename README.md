<img src="fastlane/appassets/logo/dejpeg_logo_curved.svg" alt="A gray mountain rotated 45 degrees clockwise with a lowercase letter j rotated 90 degrees clockwise" height="72" >

<br>

<div>
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" width="200">
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/02.png" width="200">
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/03.png" width="200">
</div>

<br>

<a href="https://apt.izzysoft.de/fdroid/index/apk/com.je.dejpeg"><img src="fastlane/appassets/IzzyOnDroid.png" width="180" alt="IzzyOnDroid"></a>
<a href="https://codeberg.org/dryerlint/dejpeg/releases/download/latest/dejpeg-arm64-v8a.apk"><img src="fastlane/appassets/codeberg-badge.png" width="180" alt="Codeberg direct apk"></a>
<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.je.dejpeg%22%2C%22url%22%3A%22https%3A%2F%2Fcodeberg.org%2Fdryerlint%2Fdejpeg%22%2C%22author%22%3A%22dryerlint%22%2C%22name%22%3A%22DeJPEG%22%2C%22preferredApkIndex%22%3A0%2C%22overrideSource%22%3A%22Codeberg%22%7D"><img src="fastlane/appassets/obtanium.png" width="180" alt="Obtainium config"></a>

An offline compression artifact remover and denoise application for Android

[Models list available here](models/), see [examples](examples/) for how to use them.

### Contributing

Translate:
[https://translate.codeberg.org/projects/dejpeg](https://translate.codeberg.org/projects/dejpeg)

### Additional details

If you'd like to use models on PCs (Linux/Mac/Windows), look at [chaiNNer](https://chainner.app/)
(for FBCNN, install this [custom node](chainner/) and use PyTorch models from the [FBCNN repo](https://github.com/jiaxi-jiang/FBCNN))

Regarding the recent restriction of 3rd party apps, this won't be on the Play Store and I will not be involved with [Developer Verification](https://developer.android.com/developer-verification).

<details>
<summary><h4>AI transparency</h3></summary>

Previously, the kotlin implementation of brisque was created with the use of LLMs (Generative AI). Due to changes in my personal opinion of Generative AI, I have made the decision to remove this code in its entirety and replace it with the original brisque implementation from OpenCV. Both Oidn and Brisque will be merged into a `native` release with the store release only supporting ONNX.
All future usages of Generative AI will be ceased moving forward. Regarding language translation, I personally want to still use human translation, or classical translation, with LLMs being the last choice.

</details>

<details>
<summary><h3>building</h3></summary>

Standard gradle build proceses. For building with [Intel® Oidn Denoising](https://www.openimagedenoise.org/) and brisque, see [building.md](building/readme.md)

</details>

<details>
<summary><h3>credits and license</h3></summary>

This is a GUI for a select amount of `1x` ONNX processing models, used under their respective licenses (Apache 2.0)

DeJPEG v4 and onward is licensed under [AGPLv3-or-later](https://spdx.org/licenses/AGPL-3.0-or-later.html)
Older versions (v3 and under) are under GPLv3-only.

  Credits to [@adrianerrea](https://github.com/adrianerrea/fromPytorchtoMobile) for a starting point, [FBCNN](https://github.com/jiaxi-jiang/FBCNN) and [SCUNet](https://github.com/cszn/SCUNet) creators plus all other model owners.

  DeJPEG is not affiliated or related with Topaz `DEJPEG` or any other similarly named software/project. Although I've wondered if the term 'JPEG' is copyrighted/trademarked due to it literally being the acronym for Joint Photographic Experts Group, for this reason I might need to change the app's name if legal issues start to occur
</details>
