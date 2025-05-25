![djpeg_logo](https://github.com/user-attachments/assets/4f8f432b-f30e-4bcc-aae3-02cb076e7cec)


# De*JPEG*

this is an Android app for removing jpeg compression artifacts and denoising images with [FBCNN](https://github.com/jiaxi-jiang/FBCNN) and [SCUNet](https://github.com/cszn/SCUNet) using (the deprecated) PyTorch Mobile library

## features:
1. batch processing
2. before/after viewing
3. exporting as BMP or PNG
4. different models (FBCNN and SCUNET + grey variants)
5. no internet (even at the permission level; fully offline, fully private)

## download:
apk from [releases](https://github.com/jeeneo/dejpeg/releases/latest)

## models:
[FBCNN](https://github.com/jeeneo/FBCNN-mobile/releases/tag/v1.0)

[SCUNet](https://github.com/jeeneo/SCUNet-mobile/releases/tag/v1.0)

## screenshot:
![Screenshot_20250521_182015_DeJPEG](https://github.com/user-attachments/assets/124f0580-3353-465e-b6bd-b847173ec660)


## limitations:
1. proessed on your phone (no gpu), I'd recommend a device with 4gb of ram or more (the models themselves are ~250mb)
2. input images above 1000px will be processed in chunks
3. transparency isnt supported and will be flattened (fix planned)

## note:
De*JPEG* is not affiliated or related to Topaz's `DEJPEG` or any other program/project

## todo:

1. ✅ ~~use custom before/after view (replaces ComposeBeforeAfter and fixes jitter)~~ done
2. ⭕ gif support
3. ⭕ preserve alpha (transparency)
4. ⭕ more models?

## credits:
[@adrianerrea](https://github.com/adrianerrea/fromPytorchtoMobile) for information on how to create and convert a mobile PyTorch app

[FBCNN creators](https://github.com/jiaxi-jiang/FBCNN)

## license:
all models used are under their respective licenses, however this app and its code is under the GPLv3 license, however I ask you not to charge money for this

you are free to embed parts of this app in your own project as long as it remains free/non-paywalled

if you do, credit is appreciated but not required.
