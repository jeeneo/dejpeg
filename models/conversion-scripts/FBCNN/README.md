mobile conversion script for FBCNN (a JPEG artifact denoiser)

1. clone [FBCNN](https://github.com/jiaxi-jiang/FBCNN)
2. overwrite `models/fbcnn_network.py` with my patched one that is ONNX compatible
3. create a Python env if needed
5. `pip install -r requirements.txt`
6. download the original FNCNN models and move to `model_zoo`
7. then edit `convert_mobile.py` to the correct filenames and run

note: theres a float16 conversion script that may or may not work (ymmv)