![dejpeg](https://github.com/user-attachments/assets/6d1e6fde-58b6-4991-9bb3-57b64627fbcf)

# De*JPEG*

... is a free Android app for removing jpeg compression artifacts and denoising images using [FBCNN](https://github.com/jiaxi-jiang/FBCNN) and [SCUNet](https://github.com/cszn/SCUNet).

## features:
1. batch processing
2. transparency re-mapping (experimental)
3. fully offline

note: this is not a "super resolution AI upscaler", but a simple non-destructive way to clean up compressed/noisy images

## download:
[here](https://github.com/jeeneo/dejpeg/releases/latest)

## mobile models (required):
[FBCNN](https://github.com/jeeneo/FBCNN-mobile/releases/latest) (JPEG artifacts)

[SCUNet](https://github.com/jeeneo/SCUNet-mobile/releases/latest) (Grain/Noise)

info about models [here](https://github.com/jeeneo/dejpeg/wiki/model-types) (also in the apps FAQ)

## screenshots:

<img src="https://raw.githubusercontent.com/jeeneo/dejpeg/refs/heads/main/fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" width="300" > <img src="https://raw.githubusercontent.com/jeeneo/dejpeg/refs/heads/main/fastlane/metadata/android/en-US/images/phoneScreenshots/02.png" width="300" >

## limitations:
- processed locally, a device with 4gb of ram and a CPU with at least 4 threads is recommended.

## note:
De*JPEG* is not affiliated or related to Topaz `DEJPEG` or any other software/project

### extra details

<details>
<summary>for Qualcomm devs</summary>
<br>

tl;dr: Snapdragon devices support a special type of hardware acceleration, but these models wouldn't benefit from it.

I've looked into Hexagon/HTP support for accelerating the speed of the processing but the models from my internal testing perform better with CPU.

which means if I were to use QCOMMs special HWA, the model would still need to use the CPU for some parts of processing.

best I can understand it as HTP doesn't support some of FBCNNs operations and would shift back and forth from HTP to CPU during any image, which isn't really beneficial.

I'd also need a Qualcomm developer license requiring me to hand over a lot of personal information for simply what I consider a hobby.

and retraining/recreating the models aside from simple conversion is outside my expertise.

</details>

## credits:
[@adrianerrea](https://github.com/adrianerrea/fromPytorchtoMobile) for information on how to create a mobile PyTorch app and various information which made it easier to run the ONNX model

[FBCNN](https://github.com/jiaxi-jiang/FBCNN) and [SCUNet](https://github.com/cszn/SCUNet) creators

## transparency
<details>
<summary>do you use AI?</summary>
<br>

I partially use Generative AI, (ChatGPT, Github Copilot, et. al.) for some complicated tasks and problems.

I only use it in my IDE, all my comments, changelogs, and interactions with me are typed with my hands, from my brain.

and no, this text wasn't written by AI.

</details>


## license:
all models used are under their respective licenses

you are free to embed parts of this app in your own project as long as it remains free/non-paywalled and must abide to the GPL v3 license