import torch
import litert_torch
from transformers import AutoModelForImageSegmentation
model = AutoModelForImageSegmentation.from_pretrained("briaai/RMBG-1.4", trust_remote_code=True)
model.eval()
sample_inputs = (torch.randn(1, 3, 1024, 1024),)
edge_model = litert_torch.convert(model, sample_inputs)
edge_model.export("rmbg14.tflite")
