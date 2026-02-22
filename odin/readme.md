an experimental version of Intel® Open Image Denoise for Android

intended to process raster images

this guide assumes your build system is linux-based and you have the Android NDK installed

clone odin:
```shell
git clone --recurse-submodules https://github.com/RenderKit/oidn.git odinroot/odin && cd odinroot
```

create a subfolder called `ispc`, inside that download and extract ispc version `1.30.0` from [here](https://github.com/ispc/ispc/releases/download/v1.30.0/ispc-v1.30.0-linux.tar.gz) into `ispc`:
```shell
mkdir ispc && wget https://github.com/ispc/ispc/releases/download/v1.30.0/ispc-v1.30.0-linux.tar.gz && tar -xzf ispc-v1.30.0-linux.tar.gz -C ispc && rm ispc-v1.30.0-linux.tar.gz
```

then clone oneTBB
```shell
git clone https://github.com/uxlfoundation/oneTBB.git && cd ..
```

run these scripts as follows under dejpeg's root:

choose and run a variant which patch the odin repo:

`bash odin/patch_all.sh` : all models embedded
`bash odin/patch_min.sh`: patches odin with only small models
`bash odin/patch_none.sh`: no models embedded (requires importing `.tza` models and will return null if no models are imported)

then run the build script `build.sh`

and finally run gradlew `./gradlew clean assembleDebug -PbuildOdin=true`
