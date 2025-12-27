import onnx
from onnxconverter_common import float16

def convert_to_fp16(input_path, output_path):
    try:
        model = onnx.load(input_path, load_external_data=True)
        model_fp16 = float16.convert_float_to_float16(
            model,
            keep_io_types=True,
            disable_shape_infer=False
        )
        onnx.save(model_fp16, output_path)
        return True
    except ImportError as e:
        return False
    except Exception as e:
        return False