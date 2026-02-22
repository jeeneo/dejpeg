an experimental version of Intel® Open Image Denoise for Android

intended to process raster images

this guide assumes your build system is linux-based and you have the Android NDK installed

clone oidn:
```shell
git clone --recurse-submodules https://github.com/RenderKit/oidn.git oidnroot/oidn && cd oidnroot
```

create a subfolder called `ispc`, inside that download and extract ispc version `1.30.0` from [here](https://github.com/ispc/ispc/releases/download/v1.30.0/ispc-v1.30.0-linux.tar.gz) into `ispc`:
```shell
mkdir ispc && wget https://github.com/ispc/ispc/releases/download/v1.30.0/ispc-v1.30.0-linux.tar.gz && tar -xzf ispc-v1.30.0-linux.tar.gz -C ispc && rm ispc-v1.30.0-linux.tar.gz
```

then clone oneTBB
```shell
git clone https://github.com/uxlfoundation/oneTBB.git && cd ..
```

choose a patch config and run in the repos root:

(patches the repo with android fixes)

`bash oidn/patch_all.sh`: all models embedded

`bash oidn/patch_min.sh`: only small models embedded

`bash oidn/patch_none.sh`: no models embedded (requires importing `.tza` models available [here](https://github.com/RenderKit/oidn-weights))

then run the build script `bash oidn/build.sh`

and finally run gradlew `./gradlew clean assembleDebug -PbuildOidn=true`
