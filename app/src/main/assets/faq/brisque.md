## What is BRISQUE?

BRISQUE is a tool for guessing an images perceptual quality, lower score means better quality.

This can be used in tandem with a rough sharpness estimate to 'descale' an image that was resized to a larger resolution without anything more than standard interpolation to extract approximate the original resolution.

This uses BRISQUE assessment with a sharpness estimation with repeated resizing and assessment to automate this guesswork. As a result, these images can be used by other models to remove artifacts. More information under the (i) button in the descaler