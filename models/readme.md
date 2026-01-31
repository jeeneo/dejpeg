> [!IMPORTANT]
> Links may change from time to time.

support status
- ✦ = officially supported, stable
- ✧ = tested, but can be unstable
- ✚ = might crash, was only checked for runability

speed:
- ➤ = fast, good on low-end devices
- ➠ = medium, depends on device
- ➽ = slow, resource intensive

Officially supported models:

- ✦ ➠ FBCNN ([source](https://github.com/jiaxi-jiang/FBCNN)) - usage: compression
  - [Color](https://huggingface.co/colpona/dejpeg-models/resolve/main/fbcnn/fbcnn_color_fp16.onnx)
  - [Greyscale](https://huggingface.co/colpona/dejpeg-models/resolve/main/fbcnn/fbcnn_grey_fp16.onnx)
  - [Double greyscale](https://huggingface.co/colpona/dejpeg-models/resolve/main/fbcnn/fbcnn_gray_double_fp16.onnx)

- ✦ ➠ SCUNet ([source](https://github.com/cszn/SCUNet)) - usage: general noise removal.

  SCUNet models behave differently with many types of noise, there is no universal model
  
  GAN and PSNR variants are for general noise, numbered variants are for strength, higher = stronger
  - [Color, GAN](https://huggingface.co/colpona/dejpeg-models/resolve/main/scunet/scunet_color_real_gan_fp16.onnx) 
  - [Color, PSNR](https://huggingface.co/colpona/dejpeg-models/resolve/main/scunet/scunet_color_real_psnr_fp16.onnx)
  - [Color, 15](https://huggingface.co/colpona/dejpeg-models/resolve/main/scunet/scunet_color_15_fp16.onnx)
  - [Color, 25](https://huggingface.co/colpona/dejpeg-models/resolve/main/scunet/scunet_color_25_fp16.onnx)
  - [Color, 50](https://huggingface.co/colpona/dejpeg-models/resolve/main/scunet/scunet_color_50_fp16.onnx)
  - [Greyscale 15](https://huggingface.co/colpona/dejpeg-models/resolve/main/scunet/scunet_gray_15_fp16.onnx)
  - [Greyscale 25](https://huggingface.co/colpona/dejpeg-models/resolve/main/scunet/scunet_gray_25_fp16.onnx)
  - [Greyscale 50](https://huggingface.co/colpona/dejpeg-models/resolve/main/scunet/scunet_gray_50_fp16.onnx)

Check the in-app FAQs for more details on use-cases.

[A larger list of more models are available here, they have been tested but not guaranteed for stability. Some have very specific use cases.](others.md)

<!-- this readme was typed out by a real human -->
