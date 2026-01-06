> [!IMPORTANT]
> Links may change from time to time.

support status
- ✦ = officially supported, stable
- ✧ = tested, but can be unstable
- ✚ = didn't crash, was only checked for runability

type:
- ➤ = fast, good on low-end devices
- ➠ = medium, depends on device
- ➽ = slow, resource intensive

Officially supported models:

- ✦ ➠ FBCNN ([source](https://github.com/jiaxi-jiang/FBCNN)) - usage: compression
  - [Color](https://huggingface.co/colpona/dejpeg-models/resolve/main/fbcnn/fbcnn_color_fp16.onnx)
  - [Greyscale](https://huggingface.co/colpona/dejpeg-models/resolve/main/fbcnn/fbcnn_grey_fp16.onnx)
  - [Double greyscale](https://huggingface.co/colpona/dejpeg-models/resolve/main/fbcnn/fbcnn_gray_double_fp16.onnx)

- ✦ ➠ SCUNet ([source](https://github.com/cszn/SCUNet)) - usage: general noise removal
  - [Color, GAN](https://huggingface.co/colpona/dejpeg-models/resolve/main/scunet/scunet_color_real_gan_fp16.onnx)
  - [Color, PSNR](https://huggingface.co/colpona/dejpeg-models/resolve/main/scunet/scunet_color_real_psnr_fp16.onnx)
  - [Greyscale 15](https://huggingface.co/colpona/dejpeg-models/resolve/main/scunet/scunet_gray_15_fp16.onnx)
  - [Greyscale 25](https://huggingface.co/colpona/dejpeg-models/resolve/main/scunet/scunet_gray_25_fp16.onnx)
  - [Greyscale 50](https://huggingface.co/colpona/dejpeg-models/resolve/main/scunet/scunet_gray_50_fp16.onnx)

Check the in-app FAQs for more details on use-cases.

Other models are available below, they have been tested but not guaranteed for stability. Some have very specific use cases.

[Small models](https://huggingface.co/colpona/dejpeg-models/tree/main/other-models/nanomodels) (for low-end devices)
- ✧ ➤ [1x-AnimeUndeint-Compact-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/nanomodels/1x-AnimeUndeint-Compact-fp16.onnx) ([source](https://openmodeldb.info/models/1x-AnimeUndeint-Compact)) - usage: compression, jagged lines
- ✧ ➤ [1x-BroadcastToStudio_Compact-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/nanomodels/1x-BroadcastToStudio_Compact-fp16.onnx) ([source](https://openmodeldb.info/models/1x-BroadcastToStudio-Compact)) - usage: cartoons, broadcast compression
- ✧ ➤ [1x-RGB-max-Denoise-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/nanomodels/1x-RGB-max-Denoise-fp16.onnx) ([source](https://openmodeldb.info/models/1x-RGB-max-Denoise)) - usage: general compression, general noise
- ✧ ➤ [1x-WB-Denoise-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/nanomodels/1x-WB-Denoise-fp16.onnx) ([source](https://openmodeldb.info/models/1x-BW-Denoise)) - usage: colorless cartoon noise
- ✧ ➤ [1x-span-anime-pretrain-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/nanomodels/1x-span-anime-pretrain-fp16.onnx) ([source](https://openmodeldb.info/models/1x-span-anime-pretrain)) - usage: general compression, general noise, anime
- ✧ ➤ [1xBook-Compact-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/nanomodels/1xBook-Compact-fp16.onnx) ([source](https://openmodeldb.info/models/1x-Book-Compact)) - usage: book scanning
- ✧ ➤ [1xOverExposureCorrection_compact-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/nanomodels/1xOverExposureCorrection_compact-fp16.onnx) ([source](https://openmodeldb.info/models/1x-ExposureCorrection-compact)) - usage: exposure correction

[Other models](https://huggingface.co/colpona/dejpeg-models/tree/main/other-models):

- ✚ ➠ [1x-Anti-Aliasing-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x-Anti-Aliasing-fp16.onnx) ([source](https://openmodeldb.info/models/1x-Anti-Aliasing)) - usage: anti-aliasing
- ✚ ➠ [1x-KDM003-scans-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x-KDM003-scans-fp16.onnx) ([source](https://openmodeldb.info/models/1x-KDM003-scans)) - usage: scanned art/drawings, mild general compression, moire
- ✚ ➠ [1x-NMKD-Jaywreck3-Lite-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x-NMKD-Jaywreck3-Lite-fp16.onnx) ([source](https://openmodeldb.info/models/1x-NMKD-Jaywreck3-Lite)) - usage: general compression
- ✚ ➠ [1x-SpongeColor-Lite-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x-SpongeColor-Lite-fp16.onnx) ([source](https://openmodeldb.info/models/1x-SpongeColor-Lite)) - usage: colorization, cartoons
- ✚ ➠ [1x-cinepak-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x-cinepak-fp16.onnx) ([source](https://openmodeldb.info/models/1x-cinepak)) - usage: non-standard compression (cinepak, msvideo1 and roq)
- ✚ ➠ [1x_BCGone-DetailedV2_40-60_115000_G-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_BCGone-DetailedV2_40-60_115000_G-fp16.onnx) ([source](https://openmodeldb.info/models/1x-BCGone-DetailedV2-40-60)) - usage: non-standard compression (BC1 compression)
- ✚ ➠ [1x_BCGone_Smooth_110000_G-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_BCGone_Smooth_110000_G-fp16.onnx) ([source](https://openmodeldb.info/models/1x-BCGone-Smooth)) - usage: non-standard compression (BC1 compression)
- ✧ ➽ [1x_Bandage-Smooth-f16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_Bandage-Smooth-f16.onnx) ([source](https://openmodeldb.info/models/1x-Bandage-Smooth)) - usage: color banding
- ✚ ➠ [1x_Bendel_Halftone-fp32.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_Bendel_Halftone-fp32.onnx) ([source](https://openmodeldb.info/models/1x-Bendel-Halftone)) - usage: halftones
- ✚ ➠ [1x_ColorizerV2_22000G-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_ColorizerV2_22000G-fp16.onnx) ([source](https://openmodeldb.info/models/1x-BS-Colorizer)) - usage: general colorizer
- ✚ ➽ [1x_DeEdge-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_DeEdge-fp16.onnx) ([source](https://openmodeldb.info/models/1x-DeEdge)) - usage: edge removal
- ✚ ➠ [1x_DeSharpen-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_DeSharpen-fp16.onnx) ([source](https://openmodeldb.info/models/1x-DeSharpen)) - usage: removes oversharpening
- ✧ ➽ [1x_DitherDeleterV3-Smooth-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_DitherDeleterV3-Smooth-fp16.onnx) ([source](https://openmodeldb.info/models/1x-DitherDeleterV3-Smooth)) - usage: dithering
- ✧ ➠ [1x_GainresV4-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_GainresV4-fp16.onnx) ([source](https://openmodeldb.info/models/1x-GainRESV4)) - usage: anti-aliasing, general artifacts, CGI
- ✚ ➠ [1x_JPEGDestroyerV2_96000G-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_JPEGDestroyerV2_96000G-fp16.onnx) ([source](https://openmodeldb.info/models/1x-JPEGDestroyer)) - usage: general compression
- ✚ ➠ [1x_NMKD-h264Texturize-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/1x_NMKD-h264Texturize-fp16.onnx) ([source](https://openmodeldb.info/models/1x-NMKD-h264Texturize)) - usage: texturization, h264 compression
- ✚ ➽ [VHS-Sharpen-1x_46000_G-fp16.onnx](https://huggingface.co/colpona/dejpeg-models/resolve/main/other-models/VHS-Sharpen-1x_46000_G-fp16.onnx) ([source](https://openmodeldb.info/models/1x-VHS-Sharpen)) - usage: VHS compression

<!-- this readme was typed out by a real human -->