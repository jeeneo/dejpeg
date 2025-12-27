import torch
import os
from models.network_fbcnn import FBCNN as FBCNNModel
from typing import Tuple, Optional
import numpy as np
import sys

def check_environment():
    if not torch.cuda.is_available():
        print("Warning: CUDA not available, using CPU for conversion")

# Configuration
n_channels = 1  # change to 3 for color model weights
nc = [64, 128, 256, 512]
nb = 4
model_path = 'model_zoo/fbcnn_gray.pth'
output_path = 'fbcnn_gray_mobile.onnx'  # Changed extension to .onnx

# Define a script-friendly wrapper
class FBCNNWrapper(torch.nn.Module):
    def __init__(self):
        super(FBCNNWrapper, self).__init__()
        self.model = FBCNNModel(in_nc=n_channels, out_nc=n_channels, nc=nc, nb=nb, act_mode='R')
        
    def forward(self, x: torch.Tensor, qf: Optional[torch.Tensor] = None) -> Tuple[torch.Tensor, torch.Tensor]:
        return self.model(x, qf)

def convert_model():
    try:
        check_environment()
        # Instantiate and load weights
        device = torch.device('cpu')  # Use CPU for conversion
        model = FBCNNWrapper()
        model.model.load_state_dict(torch.load(model_path, map_location=device))
        model.eval()

        # Create example inputs for export
        example_input = torch.randn(1, n_channels, 256, 256)
        example_qf = torch.tensor([[0.5]])

        # Export to ONNX
        torch.onnx.export(
            model,
            (example_input, example_qf),
            output_path,
            export_params=True,
            opset_version=12,
            do_constant_folding=True,
            input_names=['input', 'qf'],
            output_names=['output', 'features'],
            dynamic_axes={
                'input': {0: 'batch_size', 2: 'height', 3: 'width'},
                'output': {0: 'batch_size', 2: 'height', 3: 'width'}
            }
        )
        print(f"ONNX model exported to: {output_path}")

        # Optional: Validate ONNX export
        try:
            import onnx
            onnx_model = onnx.load(output_path)
            onnx.checker.check_model(onnx_model)
            print("ONNX model is valid!")
        except ImportError:
            print("onnx package not installed, skipping ONNX validation.")
        except Exception as e:
            print(f"ONNX validation failed: {str(e)}")

    except Exception as e:
        print(f"Error during model conversion: {str(e)}")
        sys.exit(1)

if __name__ == '__main__':
    convert_model()
