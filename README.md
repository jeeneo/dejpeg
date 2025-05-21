![New Project(2)](https://github.com/user-attachments/assets/254d0b97-dec6-4885-a25d-c04587a4bba0)

# De*JPEG*

this is an Android app for removing jpeg compression artifacts using [FBCNN](https://github.com/jiaxi-jiang/FBCNN) and [SCUNet](https://github.com/cszn/SCUNet) using (the deprecated) PyTorch Mobile library

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

1. proessed on your phone (no gpu), I'd recommend a device with 4gb of ram or more (the models themselves are ~250mb)
2. size of the input image has been tested up to `1920` (any), but see reason 1 again.
3. transparency isnt supported and will be flattened (fix planned)

## note:

DeJPEG is not affiliated or related to Topaz's `DEJPEG` or any other program/project

## credits:

[@adrianerrea](https://github.com/adrianerrea/fromPytorchtoMobile) for information on how to create and convert a mobile PyTorch app

[FBCNN creators](https://github.com/jiaxi-jiang/FBCNN)

## license:
FBCNN is released under the Apache 2.0 license, however this app and its code is under the GPLv3 license
(honestly it's just PyTorch mobile which is deprecated anyway)
