> [!CAUTION]
> Running for an extended amount of time can make your device run hot, please monitor device temperature

Each model has an <img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABgAAAAYCAYAAADgdz34AAABcElEQVRIS2NkoDFgpLH5DAQtePDggRELC8va////KyA7Bsi/z8TEFCgjI3MRnyNxWvD48WN7oMYDxPjw79+/1goKCsewqcVqwZMnT5YDXRhBjOEwNUD1c+Xk5FLQ9WBY8OjRo+2MjIwepBgOUwvUtwIYZJHIelEsAAaLOVDyBDmGw/QA40VDWlr6JtxSZMOAFvzHZ7isrCzYQcSqA6mF+wAYNG5AL+6khgWsrKy6EhISV1AsALrqJVBAjJLgQdJ7HuhbI3QL8AYPqRbDghMeRITCFWQBsXGArHZ4WXAL6DVVaqQioBnXgMGpjRLJwOLBEpjdsZYnMEuJjQNg2WQMLJvOoVhASgYilCBgDsGwAOgLA6AvzpOaJJHV4y0qoL7YAqS9ybEE6LgVwBIVd2EHM5TM4no60PAsdIfhq3AcgIr3E+MToMvNgIafxqaWYJUJLARNgYXgGqBmOTQDbgGr0kBJSclr+BxB0AJifDCgFgAA79ikGbsxV9sAAAAASUVORK5CYII=" width="24" alt="Info icon"> icon which contains information regarding it's purpose that you can view after importing.

Speed:
- ➤ = fast, good on low-end devices
- ➠ = medium, depends on device
- ➽ = slow, resource intensive

# Officially supported models:

- FBCNN ([source](https://github.com/jiaxi-jiang/FBCNN)) - usage: compression
  - [Color](https://huggingface.co/colpona/dejpeg-models/resolve/main/fbcnn/fbcnn_color_fp16.onnx)
  - [grayscale](https://huggingface.co/colpona/dejpeg-models/resolve/main/fbcnn/fbcnn_gray_fp16.onnx)
  - [Double grayscale](https://huggingface.co/colpona/dejpeg-models/resolve/main/fbcnn/fbcnn_gray_double_fp16.onnx)

- SCUNet ([source](https://github.com/cszn/SCUNet)) - usage: general noise removal.

  SCUNet models behave differently with many types of noise, there is no universal model
  
  GAN and PSNR variants are for general noise, numbered variants are for strength, higher = stronger
  - [Color, GAN](https://huggingface.co/colpona/dejpeg-models/resolve/main/scunet/scunet_color_real_gan_fp16.onnx) 
  - [Color, PSNR](https://huggingface.co/colpona/dejpeg-models/resolve/main/scunet/scunet_color_real_psnr_fp16.onnx)
  - [Color, 15](https://huggingface.co/colpona/dejpeg-models/resolve/main/scunet/scunet_color_15_fp16.onnx)
  - [Color, 25](https://huggingface.co/colpona/dejpeg-models/resolve/main/scunet/scunet_color_25_fp16.onnx)
  - [Color, 50](https://huggingface.co/colpona/dejpeg-models/resolve/main/scunet/scunet_color_50_fp16.onnx)
  - [grayscale 15](https://huggingface.co/colpona/dejpeg-models/resolve/main/scunet/scunet_gray_15_fp16.onnx)
  - [grayscale 25](https://huggingface.co/colpona/dejpeg-models/resolve/main/scunet/scunet_gray_25_fp16.onnx)
  - [grayscale 50](https://huggingface.co/colpona/dejpeg-models/resolve/main/scunet/scunet_gray_50_fp16.onnx)

## Special models

- ✧ ➠ [BriaAI RMBG v1.4](https://huggingface.co/briaai/RMBG-1.4) (license: [bria-rmbg-1.4](https://web.archive.org/web/20240216110248if_/https://bria.ai/wp-content/uploads/2024/01/BRIA_huggingface_model_license_agreement.pdf)) download: [direct link](https://huggingface.co/briaai/RMBG-1.4/resolve/main/onnx/model_quantized.onnx) - usage: background removal
- ✧ ➠ [BriaAI RMBG v2.0](https://huggingface.co/briaai/RMBG-2.0) (license: [CC BY-NC 4.0](https://creativecommons.org/licenses/by-nc/4.0/deed.en)) downlaoad: [link](https://huggingface.co/briaai/RMBG-2.0/resolve/main/onnx/model_quantized.onnx) (requires HF account), download `model_quantized.onnx` - usage: background removal
- ✧ ➤ [u2netp](https://huggingface.co/BritishWerewolf/U-2-Netp/resolve/main/onnx/model.onnx) ([source](https://huggingface.co/BritishWerewolf/U-2-Netp)) - background removal

note: after downloading these models, rename to contain either `u2net` or `rmbg` (e.g. `bria_rmbg_1.4.onnx`) respectively to their type for correct operation otherwise issues may occur when processing

----

# Other compatible models:

## [Small models](https://huggingface.co/colpona/dejpeg-models/tree/main/other-models/nanomodels)
(for low-end devices)

- ✧ ➤ [1x-AnimeUndeint-Compact-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/nanomodels/1x-AnimeUndeint-Compact-fp16.onnx) ([source](https://openmodeldb.info/models/1x-AnimeUndeint-Compact)) - usage: compression, jagged lines
- ✧ ➤ [1x-BroadcastToStudio_Compact-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/nanomodels/1x-BroadcastToStudio_Compact-fp16.onnx) ([source](https://openmodeldb.info/models/1x-BroadcastToStudio-Compact)) - usage: cartoons, broadcast compression
- ✧ ➤ [1x-RGB-max-Denoise-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/nanomodels/1x-RGB-max-Denoise-fp16.onnx) ([source](https://openmodeldb.info/models/1x-RGB-max-Denoise)) - usage: minor compression, general noise
- ✧ ➤ [1x-WB-Denoise-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/nanomodels/1x-WB-Denoise-fp16.onnx) ([source](https://openmodeldb.info/models/1x-BW-Denoise)) - usage: colorless cartoon noise
- ✧ ➤ [1x-span-anime-pretrain-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/nanomodels/1x-span-anime-pretrain-fp16.onnx) ([source](https://openmodeldb.info/models/1x-span-anime-pretrain)) - usage: general compression, general noise, anime
- ✧ ➤ [1xBook-Compact-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/nanomodels/1xBook-Compact-fp16.onnx) ([source](https://openmodeldb.info/models/1x-Book-Compact)) - usage: book scanning
- ✧ ➤ [1xOverExposureCorrection_compact-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/nanomodels/1xOverExposureCorrection_compact-fp16.onnx) ([source](https://openmodeldb.info/models/1x-ExposureCorrection-compact)) - usage: exposure correction
- ✧ ➤ [ArtCNN_R8F64_JPEG420.onnx](https://github.com/Artoriuz/ArtCNN/raw/refs/heads/main/ONNX/ArtCNN_R8F64_JPEG420.onnx), [ArtCNN_R8F64_JPEG444.onnx](https://github.com/Artoriuz/ArtCNN/raw/refs/heads/main/ONNX/ArtCNN_R8F64_JPEG444.onnx) ([source](https://github.com/Artoriuz/ArtCNN)) - usage: JPEG compression, anime

## [Other compression models](https://huggingface.co/colpona/dejpeg-models/tree/main/other-models):

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

### JPEG compression models:

#### General quality range ([source](https://huggingface.co/utnah/esrgan))
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
- ✚ ➠ [1x-Debandurh-FS-Ultra-lite-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x-Debandurh-FS-Ultra-lite-fp16.onnx) ([source](https://openmodeldb.info/models/1x-Debandurh-FS-Ultra-lite)) - usage: debanding
- ✚ ➽ [1x_NMKD-BrightenRedux_200k-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_NMKD-BrightenRedux_200k-fp16.onnx) - usage: brightness reduction
- ✚ ➽ [1x_NMKDDetoon_97500_G-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_NMKDDetoon_97500_G-fp16.onnx) - usage: detooning
- ✚ ➽ [1x_NoiseToner-Poisson-Detailed_108000_G-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_NoiseToner-Poisson-Detailed_108000_G-fp16.onnx) - usage: poisson noise toning (detailed)
- ✚ ➽ [1x_NoiseToner-Poisson-Soft_101000_G-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_NoiseToner-Poisson-Soft_101000_G-fp16.onnx) - usage: poisson noise toning (soft)
- ✚ ➽ [1x_NoiseToner-Uniform-Detailed_100000_G-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_NoiseToner-Uniform-Detailed_100000_G-fp16.onnx) - usage: uniform noise toning (detailed)
- ✚ ➽ [1x_NoiseToner-Uniform-Soft_100000_G-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_NoiseToner-Uniform-Soft_100000_G-fp16.onnx) - usage: uniform noise toning (soft)
- ✚ ➽ [1x_ReDetail_v2_126000_G-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_ReDetail_v2_126000_G-fp16.onnx) - usage: detail "enhancement"
- ✚ ➽ [1x_Repainter_20000_G-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_Repainter_20000_G-fp16.onnx) - usage: repainting(?)
- ✚ ➽ [1x_artifacts_dithering_alsa-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_artifacts_dithering_alsa-fp16.onnx) - usage: dithering
- ✚ ➽ [1x_nmkdbrighten_10000_G-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_nmkdbrighten_10000_G-fp16.onnx) - usage: brightening
- ✚ ➽ [deblurring_nafnet_2025may](https://huggingface.co/opencv/deblurring_nafnet/resolve/main/deblurring_nafnet_2025may.onnx?download=true)  ([source](https://github.com/megvii-research/NAFNet)), additional models [here](https://huggingface.co/deepghs/image_restoration/tree/main) - it's meant for deblurring (and denoising) but this model behaves really weirdly. use at own risk.


<!-- this readme was typed out by a real human -->
