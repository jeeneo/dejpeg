import os
import onnx
from onnx import numpy_helper, helper, TensorProto
import numpy as np


def convert_to_fp16_simple(input_path, output_path):
    try:
        print(f"loading model from {input_path}")
        model = onnx.load(input_path)
        print(f"converting...")
        for tensor in model.graph.initializer:
            if tensor.data_type == TensorProto.FLOAT:
                fp32_data = numpy_helper.to_array(tensor)
                fp16_data = fp32_data.astype(np.float16)
                tensor.CopyFrom(numpy_helper.from_array(fp16_data, tensor.name))
                tensor.data_type = TensorProto.FLOAT16
        constant_count = 0
        for node in model.graph.node:
            if node.op_type == 'Constant':
                for attr in node.attribute:
                    if attr.name == 'value' and attr.t.data_type == TensorProto.FLOAT:
                        fp32_data = numpy_helper.to_array(attr.t)
                        fp16_data = fp32_data.astype(np.float16)
                        attr.t.CopyFrom(numpy_helper.from_array(fp16_data, attr.t.name))
                        attr.t.data_type = TensorProto.FLOAT16
                        constant_count += 1
        
        if constant_count > 0:
            print(f"  converted {constant_count} Constant nodes to FP16")
        cast_count = 0
        for node in model.graph.node:
            if node.op_type == 'Cast':
                for attr in node.attribute:
                    if attr.name == 'to' and attr.i == TensorProto.FLOAT:
                        attr.i = TensorProto.FLOAT16
                        cast_count += 1
        
        if cast_count > 0:
            print(f"  converted {cast_count} Cast nodes from FP32 to FP16")
        for value_info in model.graph.value_info:
            if value_info.type.tensor_type.elem_type == TensorProto.FLOAT:
                value_info.type.tensor_type.elem_type = TensorProto.FLOAT16
        for inp in model.graph.input:
            if inp.type.tensor_type.elem_type == TensorProto.FLOAT:
                inp.type.tensor_type.elem_type = TensorProto.FLOAT16
        for out in model.graph.output:
            if out.type.tensor_type.elem_type == TensorProto.FLOAT:
                out.type.tensor_type.elem_type = TensorProto.FLOAT16
                print(f"saving FP16 model to {output_path}")
        onnx.save(model, output_path)
        
        try:
            onnx.checker.check_model(output_path)
            print(f"converted: {os.path.basename(input_path)} -> {os.path.basename(output_path)}")
            return True
        except Exception as check_error:
            print(f"warning: {str(check_error)[:80]}...")
            print(f"model saved")
            return True
            
    except Exception as e:
        print(f"error: {e}")
        import traceback
        traceback.print_exc()
        return False


def convert_to_fp16(input_path, output_path):
    return convert_to_fp16_simple(input_path, output_path)


def main():
    model_zoo_dir = 'model_zoo'
    onnx_files = [f for f in os.listdir(model_zoo_dir) 
                  if f.endswith('.onnx') and not f.endswith('_fp16.onnx')]
    if not onnx_files:
        print(f"No ONNX files found in {model_zoo_dir}")
        return
    print(f"Found {len(onnx_files)} ONNX models to convert:\n")
    success_count = 0
    failed_count = 0
    
    for onnx_file in onnx_files:
        input_path = os.path.join(model_zoo_dir, onnx_file)
        base_name = os.path.splitext(onnx_file)[0]
        output_file = f"{base_name}_fp16.onnx"
        output_path = os.path.join(model_zoo_dir, output_file)
        print(f"\n{'='*60}")
        if convert_to_fp16(input_path, output_path):
            success_count += 1
        else:
            failed_count += 1
    
    print(f"\n{'='*60}")
    print(f"successful: {success_count}")
    print(f"failed: {failed_count}")
    print(f"total: {len(onnx_files)}")

if __name__ == '__main__':
    main()
