![djpeg_logo](https://github.com/user-attachments/assets/4f8f432b-f30e-4bcc-aae3-02cb076e7cec)


# De*JPEG*

this is an Android app for removing jpeg compression artifacts and denoising images with [FBCNN](https://github.com/jiaxi-jiang/FBCNN) and [SCUNet](https://github.com/cszn/SCUNet) with mobile-compatible models

## features:
1. batch processing
2. transparancy re-mapping (experimental)
4. exporting as BMP or PNG
5. no internet needed

## download:
[v2.0b (arm64 only)](https://github.com/jeeneo/dejpeg/releases/download/v2.0b/dejpeg-arm64-v8a-2.0b.apk)

## mobile models (required):
[FBCNN](https://github.com/jeeneo/FBCNN-mobile/releases/tag/v1.0)

[SCUNet](https://github.com/jeeneo/SCUNet-mobile/releases/tag/v1.0)

## screenshot:

![figs](https://github.com/user-attachments/assets/d20aa6b6-47d3-4036-87af-7ed9bf73a99e)
![tulips](https://github.com/user-attachments/assets/cc14390b-fbb8-43fc-8dc8-df2175f4b8f2)


## limitations:
1. processed on your phone (no gpu), I'd recommend a device with 4gb of ram or more (the models themselves are ~250mb)
2. input images above 1200px (x or y) will be processed in chunks

## note:
De*JPEG* is not affiliated or related to Topaz's `DEJPEG` or any other program/project

## credits:
[@adrianerrea](https://github.com/adrianerrea/fromPytorchtoMobile) for information on how to createa mobile PyTorch app and various information

[FBCNN](https://github.com/jiaxi-jiang/FBCNN) and [SCUNet](https://github.com/cszn/SCUNet) creators

## license:
all models used are under their respective licenses
you are free to embed parts of this app in your own project as long as it remains free/non-paywalled
