## Which models to use?

### FBCNN

Use for images that have JPEG compression (e.g. low-res images from Google, Pinterest, Instagram, that random forum, etc).

The slider is available for FBCNN models only, and affects how much strength goes into removing compression, values around 30/50 for minor compression, and 70+ for heavy removal, afterward it begins to get noticeably smoother.

The base model is `color`, the `grey` FBCNN models are for black and white images, `grey double` is for images saved and re-saved as JPEG. (edited/resized twice both saving as JPEG)

### SCUNet

`SCUNet` is more adept at removing noise (e.g. ISO noise, grainy looking or speckled).

It can also remove compression and GIF artifacts, to some degree.

The "grey 15/25/50" SCUNet models are only for greyscale images as well, with different levels of noise removal.

Higher the number, the more noise is removed.

The "PSNR" variant tries to pixel-match the original image as closely as possible, so it looks mathematically correct but may appear overly smooth.

"GAN" sacrifices pixel-perfect accuracy to add realistic textures or details, which can look more natural. As with most cases, try both and see which one suits your image better.