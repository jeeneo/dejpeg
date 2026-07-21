import onnx2tf
onnx2tf.convert(
    input_onnx_file_path='model.onnx',
    output_folder_path='debug_out',
    overwrite_input_shape=['input:1,3,256,256','qf:1,1'],
    tflite_backend='tf_converter',
    non_verbose=False,
)
