# DeJPEG

this is an Android app for removing jpeg compression artifacts using [FBCNN](https://github.com/jiaxi-jiang/FBCNN) and (the deprecated) PyTorch Mobile library

## features:
1. batch support
2. before/after viewing
3. exporting as BMP or PNG
4. model loading (color/grey/doublegrey)
5. no internet (even at the permission level; fully offline, fully private)

## download:
apk from releases

## models:
download the mobile models from here

## limitations:

1. it's all locally processed on your phone (no GPU), so I'd recommend a device with 8gb of ram or more
2. size of the input image has been tested up to `1920` (either dimension), but theoretically can process a larger image and see reason 1 again.
3. transparency isnt supported and will be flattened (fix planned)

## note:

DeJPEG is not affiliated or related to Topaz's `DEJPEG` or any other program/project

## credits:

[@adrianerrea](https://github.com/adrianerrea/fromPytorchtoMobile) for information on how to create and convert a mobile PyTorch app

[FBCNN creators](https://github.com/jiaxi-jiang/FBCNN)

## license:

FBCNN is released under the Apache 2.0 license, however this app and its code is under the GPLv3 license
(honestly it's just PyTorch mobile which is deprecated anyway)
