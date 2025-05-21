![New Project(2)](https://github.com/user-attachments/assets/254d0b97-dec6-4885-a25d-c04587a4bba0)

# DeJPEG

this is an Android app for removing jpeg compression artifacts using [FBCNN](https://github.com/jiaxi-jiang/FBCNN) and (the deprecated) PyTorch Mobile library

## features:
1. batch processing
2. before/after viewing
3. exporting as BMP or PNG
4. model loading (color/grey/doublegrey)
5. no internet (even at the permission level; fully offline, fully private)

## download:
apk from releases

## models:
download the mobile models from [here](https://github.com/jeeneo/FBCNN-mobile/releases/tag/v1.0)

![Screenshot_20250520_221854_DeJPEG](https://github.com/user-attachments/assets/61b35fbe-5ff4-4943-b980-d7ef78a7beb7)

## limitations:

1. it's all locally processed on your phone (no gpu), I'd recommend a device with 4gb of ram or more (the models themselves are ~250mb)
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
