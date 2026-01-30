<div align="center">
<img src="fastlane/appassets/dejpeg.png" height="140" alt="A grey mountain rotated 45 degrees clockwise with a lowercase letter j rotated 90 degrees clockwise">
<br>An app for removing compression and noise from photos<h2></h2>
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

This is not another "AI upscaler" but a compression artifact remover and denoiser using public models such as [FBCNN](https://github.com/jiaxi-jiang/FBCNN) and [SCUNet](https://github.com/cszn/SCUNet)

## features:
- Remove compression artifacts
- Denoise
- Before/after view
- Fully offline

## models:
You can download models [here](models/)

## examples:
Check out [examples](examples/) to get an idea of what DeJPEG can be used for

## limitations:
- Processed locally, a fast device is recommended
- Only standard image formats supported

## versions:
There exists 2 "build types" of dejpeg, `lite` and `full` (noted by the presense of `-opencv` in full builds' version name). `lite` does not mean it has any limitations, it only lacks OpenCV binaries thus image descaling is not available. This is the version distributed in app stores like IzzyOnDroid.

This change was done on my part to make building more transparent, previously to be under the 30mb app limit and still have both onnx and OpenCV, I used to manually build, compress and upload compiled binaries directly, which created ambiguity on how they were made. In order to not further complicate the build process, the app was split. `full` builds are available in compressed zip/7z form under releases alongside the `lite` builds, and the need for compression is not longer a strict requirement.

## desktop support:
[chaiNNer](https://github.com/chaiNNer-org/chaiNNer) is a cross-platform image/model utility, which should work well with these models

For FBCNN, which chaiNNer does support but in a limited fashion, install [this custom node](chainner/) and use the [original PyTorch models](https://github.com/jiaxi-jiang/FBCNN/releases/latest), not the mobile onnx.

<details>
<summary><h3>building</h3></summary>

### requirements
- Android SDK
- For full builds: Android NDK (tested on version 27.3.x)
- Optional: UPX for library compression (must be in path if enabled)

### steps
1. Clone repo

2. (optional) run `./generatelibs.sh` to build native libraries (not needed for lite builds)

   options:
   - `--abi <abi>`: Target ABI (arm64-v8a, armeabi-v7a, x86_64, x86, or all). Default: arm64-v8a
   - `--debug`: Build debug variant (disables compression).
   - `--no-upx`: Disable UPX compression.
   - `--full`: Build with OpenCV for BRISQUE descaling (requires NDK).
   - `--no-cleanup`: Reuse existing libraries without rebuilding.
   - `--help`: Show usage.

   Note: UPX will only work if found in path.

3. `./gradlew clean assembleLiteDebug` (or `assembleFullDebug` for BRISQUE)

   Or open in Android Studio and build from there.
</details>

<details>
<summary><h3>credits and license</h3></summary>

### disclaimer:

I am by no means a professional developer and only do this in my spare time, the code is not perfect and quite janky.

This is a GUI for a select amount of `1x` ONNX processing models, used under their respective licenses (Apache 2.0)

You are welcome to embed parts of this app in your own project as long as it remains free as in beer and abides to the GPLv3 license.

  Credits to [@adrianerrea](https://github.com/adrianerrea/fromPytorchtoMobile) for a starting point, [FBCNN](https://github.com/jiaxi-jiang/FBCNN) and [SCUNet](https://github.com/cszn/SCUNet) creators plus all other model owners.

  DeJPEG is not affiliated or related with Topaz `DEJPEG` or any other similarly named software/project. Although I've wondered if the term 'JPEG' is copyrighted/trademarked due to it literally being the acronym for Joint Photographic Experts Group, for this reason I might need to change the app's name if legal issues start to occur.
</details>
