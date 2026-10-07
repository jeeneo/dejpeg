#!/usr/bin/env python3

"""
uv venv
uv pip install torch spandrel spandrel_extra_arches onnx onnxruntime numpy
"""

from __future__ import annotations

import argparse
import os
from io import BytesIO
from pathlib import Path

import numpy as np
import torch
from spandrel import ImageModelDescriptor, MAIN_REGISTRY, ModelLoader, ModelDescriptor
from spandrel_extra_arches import EXTRA_REGISTRY

MAIN_REGISTRY.add(*EXTRA_REGISTRY)
UNSUPPORTED_ARCHS = ("SCUNet", "SAFMN", "CRAFT")

def is_onnx_supported(model: ModelDescriptor) -> bool:
    from spandrel.architectures.CRAFT import CRAFT
    from spandrel.architectures.SAFMN import SAFMN
    from spandrel.architectures.SCUNet import SCUNet
    return not isinstance(model.model, SCUNet | SAFMN | CRAFT)

def convert_to_onnx(
    model: ModelDescriptor,
    device: torch.device,
    use_half: bool = False,
    opset_version: int = 14,
) -> bytes:
    # https://github.com/onnx/onnx/issues/654
    dynamic_axes = {
        "input": {0: "batch_size", 2: "height", 3: "width"},
        "output": {0: "batch_size", 2: "height", 3: "width"},
    }

    size = 3
    size += model.size_requirements.get_padding(size, size)[0]
    dummy_input = torch.rand(1, model.input_channels, size, size)
    dummy_input = dummy_input.to(device)

    if use_half:
        if not model.supports_half:
            raise ValueError(
                f"Model of arch {model.architecture} does not support half precision."
            )
        model.half()
        dummy_input = dummy_input.half()
    else:
        model.float()
        dummy_input = dummy_input.float()

    m = model.model

    if isinstance(model, ImageModelDescriptor):
        class FakeModel(torch.nn.Module):
            def __init__(self, model: ImageModelDescriptor):
                super().__init__()
                self.model = model

            def forward(self, x: torch.Tensor):
                return self.model(x)

        m = FakeModel(model)

    with BytesIO() as f:
        torch.onnx.export(
            m,
            (dummy_input,),
            f,
            opset_version=opset_version,
            verbose=False,
            input_names=["input"],
            output_names=["output"],
            dynamic_axes=dynamic_axes,
            do_constant_folding=True,
            dynamo=False,
        )
        f.seek(0)
        return f.read()

def verify_models(
    pytorch_model: ImageModelDescriptor,
    onnx_model_bytes: bytes,
    use_half: bool,
):
    import onnxruntime as ort

    dummy_size = 16
    dummy_size += pytorch_model.size_requirements.get_padding(dummy_size, dummy_size)[0]
    dummy_input = torch.rand(
        1,
        pytorch_model.input_channels,
        dummy_size,
        dummy_size,
        device=pytorch_model.device,
        dtype=pytorch_model.dtype,
    )

    torch_out = pytorch_model(dummy_input).detach().cpu().numpy()

    session = ort.InferenceSession(onnx_model_bytes, providers=["CPUExecutionProvider"])
    input_name = session.get_inputs()[0].name
    output_name = session.get_outputs()[0].name
    onnx_out = session.run(
        [output_name],
        {input_name: dummy_input.detach().cpu().numpy()},
    )[0]

    np.testing.assert_allclose(torch_out, onnx_out, rtol=0.01, atol=0.001)
    print("Verification passed: ONNX output matches PyTorch output.")

def process_file(input_path: Path, output_path: Path | None, args):
    if not input_path.exists():
        print(f"Error: File not found: {input_path}")
        return

    output_path = output_path or input_path.with_suffix(".onnx")

    device = torch.device("cpu") if args.cpu or not torch.cuda.is_available() else torch.device("cuda")
    print(f"Loading model from {input_path} on {device}...")

    model = ModelLoader(device).load_from_file(input_path)

    assert is_onnx_supported(model), (
        f"Architecture '{model.architecture}' is not supported for ONNX conversion."
    )

    for _, v in model.model.named_parameters():
        v.requires_grad = False

    model.eval().to(device)
    print(f"Detected architecture: {model.architecture}")

    if args.fp16:
        if not model.supports_half:
            print(f"Error: {model.architecture} does not support half precision.")
            return
        print("Converting as fp16...")
    else:
        print("Converting as fp32...")

    onnx_bytes = convert_to_onnx(model, device, use_half=args.fp16, opset_version=args.opset)
    output_path.write_bytes(onnx_bytes)
    print(f"Saved ONNX model to {output_path}")

    if args.verify:
        print("Verifying conversion...")
        verify_models(model, onnx_bytes, args.fp16)

def main():
    parser = argparse.ArgumentParser(
        description="Convert a PyTorch model to ONNX (replicating chaiNNer's conversion)."
    )
    parser.add_argument("input", type=Path, nargs="+", help="Path(s) to .pth/.pt model file(s)")
    parser.add_argument("-o", "--output", type=Path, default=None, help="Output .onnx path (only valid with a single input; default: <input>.onnx)")
    parser.add_argument("--fp16", action="store_true", help="Convert as fp16")
    parser.add_argument("--opset", type=int, default=14, choices=range(14, 18), help="ONNX opset version (default: 14)")
    parser.add_argument("--cpu", action="store_true", help="Force CPU device")
    parser.add_argument("--verify", action="store_true", help="Verify ONNX output matches PyTorch output")
    args = parser.parse_args()

    if args.output and len(args.input) > 1:
        print("Error: -o/--output can only be used with a single input file.")
        return

    for input_path in args.input:
        process_file(input_path, args.output, args)

if __name__ == "__main__":
    main()
