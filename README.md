![dejpeg](https://github.com/user-attachments/assets/55f35274-1492-4bb1-ab00-f816db612f90)

# De*JPEG*

... is a free Android app for removing jpeg compression artifacts and denoising images with [FBCNN](https://github.com/jiaxi-jiang/FBCNN) and [SCUNet](https://github.com/cszn/SCUNet) with mobile-compatible models

## features:
1. batch processing
2. transparancy re-mapping (experimental)
3. free and no internet needed

## download:
[here](https://github.com/jeeneo/dejpeg/releases/latest)

## mobile models (required):
[FBCNN](https://github.com/jeeneo/FBCNN-mobile/releases/latest)

[SCUNet](https://github.com/jeeneo/SCUNet-mobile/releases/latest)

## screenshots:

![tulips](https://raw.githubusercontent.com/jeeneo/dejpeg/refs/heads/main/fastlane/metadata/android/en-US/images/phoneScreenshots/01.jpg)
![figs](https://raw.githubusercontent.com/jeeneo/dejpeg/refs/heads/main/fastlane/metadata/android/en-US/images/phoneScreenshots/02.jpg)


## limitations:
1. processed on your phone (no gpu), I'd recommend a device with 4gb of ram or more
2. input images above 1200px will be processed in chunks

## note:
De*JPEG* is not affiliated or related to Topaz's `DEJPEG` or any other software/project

## credits:
[@adrianerrea](https://github.com/adrianerrea/fromPytorchtoMobile) for information on how to create a mobile PyTorch app and various information which made it easier to run the ONNX model

[FBCNN](https://github.com/jiaxi-jiang/FBCNN) and [SCUNet](https://github.com/cszn/SCUNet) creators

## transparency
<details>
<summary>do you use AI?</summary>
<br>

I partially use Generative AI, (ChatGPT, Github Copilot, et. al.) for some of the complicated tasks and problems, but not for everything.

I believe AI has its place, as a tool, not as a replacement.

I only use it in my IDE, all my comments, changelogs (except for one, which it messed up), and interactions with me are typed out by me, from my brain.

I do not pay for access to generative AI as well, i use whatever free tier is available

if you check, you can see Google's "Jules AI" as a contributor (if its still there) and it made a PR based on things i described which completely broke the entire app.

and no, this text wasn't written by AI.

</details>


## license:
all models used are under their respective licenses

you are free to embed parts of this app in your own project as long as it remains free/non-paywalled and must abide to the GPL v3 license
