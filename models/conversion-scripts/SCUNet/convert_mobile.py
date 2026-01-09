import os
import sys
import torch
import torch.onnx
from models.network_scunet import SCUNetWrapper

def convert_model(model_path, output_path, config=[4, 4, 4, 4, 4, 4, 4], dim=64, input_resolution=256, in_nc=3):
    try:
        device = torch.device('cpu')
        print(f"Creating SCUNetWrapper with config={config}, dim={dim}, in_nc={in_nc}")
        model = SCUNetWrapper(in_nc=in_nc, config=config, dim=dim, input_resolution=input_resolution)

        print(f"Loading weights from {model_path}")
        state_dict = torch.load(model_path, map_location=device)

        if any(k.startswith('model.') for k in state_dict.keys()):
            print("State dict has 'model.' prefix, removing it for proper loading")
            new_state_dict = {k.replace('model.', ''): v for k, v in state_dict.items()}
            model.model.load_state_dict(new_state_dict)
        else:
            model.model.load_state_dict(state_dict)

        model.eval()
        print("Model loaded successfully")
        example_input = torch.randn(1, in_nc, 256, 256)
        print(f"Exporting model to ONNX: {output_path}")
        
        torch.onnx.export(
            model,
            example_input,
            output_path,
            export_params=True,
            opset_version=14,
            do_constant_folding=True,
            input_names=['input'],
            output_names=['output'],
            dynamic_axes={
                'input': {2: 'height', 3: 'width'},
                'output': {2: 'height', 3: 'width'}
            },
            verbose=False
        )

        print("Model exported successfully!")

        import onnx
        print("Loading ONNX model with external data...")
        onnx_model = onnx.load(output_path, load_external_data=True)
        onnx.checker.check_model(onnx_model)
        print("ONNX model check passed!")

        print("Saving as single file...")
        onnx.save(onnx_model, output_path, save_as_external_data=False)

        data_file = output_path + ".data"
        if os.path.exists(data_file):
            os.remove(data_file)
            print(f"Removed external data file: {data_file}")

        return True

    except Exception as e:
        print(f"Error during model conversion: {str(e)}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

if __name__ == '__main__':
    # default: color model
    # model_path = os.path.join('model_zoo', 'scunet_color_50.pth')
    # output_path = os.path.join('model_zoo', 'scunet_color_50.onnx')
    # in_nc = 3

    # model_path = os.path.join('model_zoo', 'scunet_color_real_psnr.pth')
    # output_path = os.path.join('model_zoo', 'scunet_color_real_psnr_mobile.onnx')
    # in_nc = 3

    # uncomment below for greyscale models
    # model_path = os.path.join('model_zoo', 'scunet_gray_15.pth')
    # output_path = os.path.join('model_zoo', 'scunet_gray_15_mobile.onnx')
    # in_nc = 1

    # model_path = os.path.join('model_zoo', 'scunet_gray_25.pth')
    # output_path = os.path.join('model_zoo', 'scunet_gray_25_mobile.onnx')
    # in_nc = 1

    # model_path = os.path.join('model_zoo', 'scunet_gray_50.pth')
    # output_path = os.path.join('model_zoo', 'scunet_gray_50_mobile.onnx')
    # in_nc = 1

    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    convert_model(model_path, output_path, in_nc=in_nc)