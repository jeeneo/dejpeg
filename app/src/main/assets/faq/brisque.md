## What is BRISQUE?

BRISQUE is a tool for guessing an images perceptual quality, lower score means better quality.

This can be used in tandem with a rough sharpness estimate to 'descale' an image that was resized to a larger resolution without anything other than standard interpolation to extract approximate the original resolution.

Example: an image resized to 800px but originally 500px (currently unknown) can look blurred, if used 'as-is' it couldn't be improved without manually guessing and resizing until it looks right, which can get tedious.

This uses BRISQUE assessment with a sharpness estimation with repeated resizing and assessing to provide automate this guesswork. As a result, these images can be used by other models to remove artifacts.
