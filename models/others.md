A list of all other models currently converted to `onnx` from their `pytorch` variants.

> [!CAUTION]
> Some of these models stress your device/CPU and can make it run hot. User discretion is advised to monitor device temperature.

support status
- ✧ = tested, but can be unstable
- ✚ = didn't crash, was only checked for runability

speed:
- ➤ = fast, good on low-end devices
- ➠ = medium, depends on device
- ➽ = slow, resource intensive
- ➽➽ = VERY slow

# [Small models](https://huggingface.co/colpona/dejpeg-models/tree/main/other-models/nanomodels)
(for low-end devices)

- ✧ ➤ [1x-AnimeUndeint-Compact-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/nanomodels/1x-AnimeUndeint-Compact-fp16.onnx) ([source](https://openmodeldb.info/models/1x-AnimeUndeint-Compact)) - usage: compression, jagged lines
- ✧ ➤ [1x-BroadcastToStudio_Compact-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/nanomodels/1x-BroadcastToStudio_Compact-fp16.onnx) ([source](https://openmodeldb.info/models/1x-BroadcastToStudio-Compact)) - usage: cartoons, broadcast compression
- ✧ ➤ [1x-RGB-max-Denoise-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/nanomodels/1x-RGB-max-Denoise-fp16.onnx) ([source](https://openmodeldb.info/models/1x-RGB-max-Denoise)) - usage: minor compression, general noise
- ✧ ➤ [1x-WB-Denoise-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/nanomodels/1x-WB-Denoise-fp16.onnx) ([source](https://openmodeldb.info/models/1x-BW-Denoise)) - usage: colorless cartoon noise
- ✧ ➤ [1x-span-anime-pretrain-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/nanomodels/1x-span-anime-pretrain-fp16.onnx) ([source](https://openmodeldb.info/models/1x-span-anime-pretrain)) - usage: general compression, general noise, anime
- ✧ ➤ [1xBook-Compact-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/nanomodels/1xBook-Compact-fp16.onnx) ([source](https://openmodeldb.info/models/1x-Book-Compact)) - usage: book scanning
- ✧ ➤ [1xOverExposureCorrection_compact-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/nanomodels/1xOverExposureCorrection_compact-fp16.onnx) ([source](https://openmodeldb.info/models/1x-ExposureCorrection-compact)) - usage: exposure correction

# [Other models](https://huggingface.co/colpona/dejpeg-models/tree/main/other-models):

### General compression:
- ✚ ➠ [1x_JPEGDestroyerV2_96000G-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_JPEGDestroyerV2_96000G-fp16.onnx) ([source](https://openmodeldb.info/models/1x-JPEGDestroyer)) - usage: general compression
- ✚ ➠ [1x-NMKD-Jaywreck3-Lite-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x-NMKD-Jaywreck3-Lite-fp16.onnx) ([source](https://openmodeldb.info/models/1x-NMKD-Jaywreck3-Lite)) - usage: general compression
- ✚ ➠ [1x_NMKD-h264Texturize-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_NMKD-h264Texturize-fp16.onnx) ([source](https://openmodeldb.info/models/1x-NMKD-h264Texturize)) - usage: texturization, h264 compression
- ✚ ➽ [VHS-Sharpen-1x_46000_G-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/VHS-Sharpen-1x_46000_G-fp16.onnx) ([source](https://openmodeldb.info/models/1x-VHS-Sharpen)) - usage: VHS compression
- ✚ ➠ [1x_BCGone_Smooth_110000_G-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_BCGone_Smooth_110000_G-fp16.onnx) ([source](https://openmodeldb.info/models/1x-BCGone-Smooth)) - usage: non-standard compression (BC1 compression)
- ✚ ➠ [1x-cinepak-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x-cinepak-fp16.onnx) ([source](https://openmodeldb.info/models/1x-cinepak)) - usage: non-standard compression (cinepak, msvideo1 and roq)
- ✚ ➠ [1x_BCGone-DetailedV2_40-60_115000_G-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_BCGone-DetailedV2_40-60_115000_G-fp16.onnx) ([source](https://openmodeldb.info/models/1x-BCGone-DetailedV2-40-60)) - usage: non-standard compression (BC1 compression)
- ✚ ➠ [1x-DeBink-v4.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x-DeBink-v4.onnx) ([source](https://openmodeldb.info/models/1x-DeBink-v4)) - usage: bink compression, better on geometry
- ✚ ➠ [1x-DeBink-v5.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x-DeBink-v5.onnx) ([source](https://openmodeldb.info/models/1x-DeBink-v5)) - usage: bink compression, stronger
- ✚ ➠ [1x-DeBink-v6.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x-DeBink-v6.onnx) ([source](https://openmodeldb.info/models/1x-DeBink-v6)) - usage: bink compression, soft, retains detail

#### JPEG quality range models: ([sources](https://huggingface.co/utnah/esrgan))
- ✚ ➽➽ [1x_JPEG_00-20-fp16.ort](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_JPEG_00-20-fp16.ort)
- ✚ ➽➽ [1x_JPEG_20-40-fp16.ort](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_JPEG_20-40-fp16.ort)
- ✚ ➽➽ [1x_JPEG_40-60-fp16.ort](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_JPEG_40-60-fp16.ort)
- ✚ ➽➽ [1x_JPEG_60-80-fp16.ort](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_JPEG_60-80-fp16.ort)
- ✚ ➽➽ [1x_JPEG_80-100-fp16.ort](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_JPEG_80-100-fp16.ort)
- ✚ ➽ [1x_artifacts_jpg_00_20_alsa-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_artifacts_jpg_00_20_alsa-fp16.onnx)
- ✚ ➽ [1x_artifacts_jpg_20_40_alsa-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_artifacts_jpg_20_40_alsa-fp16.onnx)
- ✚ ➽ [1x_artifacts_jpg_40_60_alsa-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_artifacts_jpg_40_60_alsa-fp16.onnx)
- ✚ ➽ [1x_artifacts_jpg_60_80_alsa-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_artifacts_jpg_60_80_alsa-fp16.onnx)
- ✚ ➽ [1x_artifacts_jpg_80_100_alsa-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_artifacts_jpg_80_100_alsa-fp16.onnx)

### Miscellaneous
- ✚ ➠ [1x-Anti-Aliasing-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x-Anti-Aliasing-fp16.onnx) ([source](https://openmodeldb.info/models/1x-Anti-Aliasing)) - usage: anti-aliasing
- ✚ ➠ [1x-KDM003-scans-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x-KDM003-scans-fp16.onnx) ([source](https://openmodeldb.info/models/1x-KDM003-scans)) - usage: scanned art/drawings, mild general compression, moire
- ✚ ➠ [1x-SpongeColor-Lite-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x-SpongeColor-Lite-fp16.onnx) ([source](https://openmodeldb.info/models/1x-SpongeColor-Lite)) - usage: colorization, cartoons
- ✧ ➽ [1x_Bandage-Smooth-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_Bandage-Smooth-fp16.onnx) ([source](https://openmodeldb.info/models/1x-Bandage-Smooth)) - usage: color banding
- ✚ ➠ [1x_Bendel_Halftone-fp32.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_Bendel_Halftone-fp32.onnx) ([source](https://openmodeldb.info/models/1x-Bendel-Halftone)) - usage: halftones
- ✚ ➠ [1x_ColorizerV2_22000G-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_ColorizerV2_22000G-fp16.onnx) ([source](https://openmodeldb.info/models/1x-BS-Colorizer)) - usage: general colorizer
- ✚ ➽ [1x_DeEdge-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_DeEdge-fp16.onnx) ([source](https://openmodeldb.info/models/1x-DeEdge)) - usage: edge removal
- ✚ ➠ [1x_DeSharpen-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_DeSharpen-fp16.onnx) ([source](https://openmodeldb.info/models/1x-DeSharpen)) - usage: removes oversharpening
- ✧ ➽ [1x_DitherDeleterV3-Smooth-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_DitherDeleterV3-Smooth-fp16.onnx) ([source](https://openmodeldb.info/models/1x-DitherDeleterV3-Smooth)) - usage: dithering
- ✧ ➠ [1x_GainresV4-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_GainresV4-fp16.onnx) ([source](https://openmodeldb.info/models/1x-GainRESV4)) - usage: anti-aliasing, general artifacts, CGI

([sources](https://huggingface.co/utnah/esrgan))

- ✚ ➠ [1x-Debandurh-FS-Ultra-lite-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x-Debandurh-FS-Ultra-lite-fp16.onnx) ([source](https://openmodeldb.info/models/1x-Debandurh-FS-Ultra-lite)) - usage: debanding
- ✚ ➽ [1x_NMKD-BrightenRedux_200k-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_NMKD-BrightenRedux_200k-fp16.onnx) - usage: brightness reduction
- ✚ ➽ [1x_NMKDDetoon_97500_G-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_NMKDDetoon_97500_G-fp16.onnx) - usage: detoning
- ✚ ➽ [1x_NoiseToner-Poisson-Detailed_108000_G-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_NoiseToner-Poisson-Detailed_108000_G-fp16.onnx) - usage: poisson noise toning (detailed)
- ✚ ➽ [1x_NoiseToner-Poisson-Soft_101000_G-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_NoiseToner-Poisson-Soft_101000_G-fp16.onnx) - usage: poisson noise toning (soft)
- ✚ ➽ [1x_NoiseToner-Uniform-Detailed_100000_G-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_NoiseToner-Uniform-Detailed_100000_G-fp16.onnx) - usage: uniform noise toning (detailed)
- ✚ ➽ [1x_NoiseToner-Uniform-Soft_100000_G-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_NoiseToner-Uniform-Soft_100000_G-fp16.onnx) - usage: uniform noise toning (soft)
- ✚ ➽ [1x_ReDetail_v2_126000_G-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_ReDetail_v2_126000_G-fp16.onnx) - usage: detail "enhancement"
- ✚ ➽ [1x_Repainter_20000_G-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_Repainter_20000_G-fp16.onnx) - usage: repainting(?)
- ✚ ➽ [1x_artifacts_dithering_alsa-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_artifacts_dithering_alsa-fp16.onnx) - usage: dithering
- ✚ ➽ [1x_nmkdbrighten_10000_G-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_nmkdbrighten_10000_G-fp16.onnx) - usage: brightening

<!-- this readme was typed out by a real human -->
