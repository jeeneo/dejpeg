import tensorflow as tf

for path in ["fbcnn_color_float16.tflite"]:
    print(f"\n=== {path} ===")
    interp = tf.lite.Interpreter(model_path=path)
    interp.allocate_tensors()
    for t in interp.get_input_details():
        print(f"  IN  [{t['index']}] {t['name']}: {t['dtype'].__name__} {t['shape']}")
    for t in interp.get_output_details():
        print(f"  OUT [{t['index']}] {t['name']}: {t['dtype'].__name__} {t['shape']}")

import numpy as np
from PIL import Image
from ai_edge_litert.interpreter import Interpreter

MODEL_PATH = "fbcnn_color_float16.tflite"
IMAGE_PATH = "lightning-bolt.jpg"
OUTPUT_PATH = "output.png"

img = Image.open(IMAGE_PATH).convert("RGB")
img = img.crop((
    (img.width - 256) / 2,
    (img.height - 256) / 2,
    (img.width + 256) / 2,
    (img.height + 256) / 2,
))
img = np.array(img, dtype=np.float32) / 255.0
img = img[:, :, ::-1]
img = np.expand_dims(img, axis=0)

qf = np.array([[1.0]], dtype=np.float32)

interp = Interpreter(model_path=MODEL_PATH)
interp.allocate_tensors()

for detail in interp.get_input_details():
    if "qf" in detail["name"].lower():
        interp.set_tensor(detail["index"], qf)
    else:
        interp.set_tensor(detail["index"], img)

interp.invoke()

for detail in interp.get_output_details():
    out = interp.get_tensor(detail["index"])
    if out.ndim == 4 and out.shape[1] > 1:
        out = np.clip(out[0], 0, 1)
        out = (out * 255).astype(np.uint8)
        Image.fromarray(out).save(OUTPUT_PATH)
        print(f"Saved image output to {OUTPUT_PATH}")
