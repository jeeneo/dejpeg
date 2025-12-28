from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
import torch
from spandrel import ImageModelDescriptor

from api import KeyInfo, NodeContext

from nodes.impl.image_utils import to_uint8
from nodes.impl.pytorch.utils import np2tensor, safe_cuda_cache_empty, tensor2np
from nodes.properties.inputs import ImageInput, NumberInput, SrModelInput
from nodes.properties.outputs import ImageOutput

from ...settings import PyTorchSettings, get_settings
from .. import restoration_group


@torch.inference_mode()
def denoise_fbcnn(
    img: np.ndarray,
    model: ImageModelDescriptor,
    qf_factor: float,
    exec_options: PyTorchSettings,
    device: torch.device,
) -> np.ndarray:
    if model.architecture.id != "FBCNN":
        raise ValueError(
            f"Expected FBCNN model, got {model.architecture.id}. "
            "This node only works with FBCNN models."
        )

    if img.shape[2] == 4:
        img = img[:, :, :3]

    img_t = np2tensor(img, bgr2rgb=False, change_range=False, add_batch=True)
    img_t = img_t.to(device)

    should_use_fp16 = exec_options.use_fp16 and model.supports_half
    if should_use_fp16:
        model.model.half()
        img_t = img_t.half()
    else:
        model.model.float()
        img_t = img_t.float()

    try:
        if qf_factor > 0.0:
            qf_input = torch.tensor([[qf_factor]], dtype=torch.float32, device=device)
            if should_use_fp16:
                qf_input = qf_input.half()
            output, predicted_qf = model.model(img_t, qf_input=qf_input)
        else:
            output, predicted_qf = model.model(img_t)
            qf_value = predicted_qf.item() if isinstance(predicted_qf, torch.Tensor) else predicted_qf
        output_np = output.detach().cpu().float().squeeze(0).permute(1, 2, 0).numpy()
        restored_img = np.clip(output_np, 0, 1).astype(np.float32)

    except Exception as error:
        restored_img = img

    safe_cuda_cache_empty()

    return restored_img
@restoration_group.register(
    schema_id="chainner:pytorch:fbcnn_denoiser",
    name="FBCNN",
    description=(
        "Towards Flexible Blind JPEG Artifacts Removal (FBCNN) is a JPEG artifact denoising model. This node adds additional support in chaiNNer. "
        "The quality factor sets how much artifacts are removed. "
        "Set 0 to use chaiNNer's original detection."
    ),
    icon="PyTorch",
    inputs=[
        ImageInput().with_id(1),
        SrModelInput("Model").with_id(0),
        NumberInput(
            "Quality Factor", # i dislike using Title Case but the other nodes case their inputs like this so oh well
            default=0.0,
            min=0.0,
            max=1.0,
            step=0.05,
            precision=2,
        )
        .with_id(2),
    ],
    outputs=[
        ImageOutput(
            "Image",
            image_type="""
                Image {
                    width: Input1.width,
                    height: Input1.height,
                }
                """,
            channels=3,
        )
    ],
    key_info=KeyInfo.enum(0),
    node_context=True,
)
def fbcnn_denoiser_node(
    context: NodeContext,
    img: np.ndarray,
    model: ImageModelDescriptor,
    qf_factor: float = 0.0,
) -> np.ndarray:
    exec_options = get_settings(context)
    device = exec_options.device

    return denoise_fbcnn(img, model, qf_factor, exec_options, device)
