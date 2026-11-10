"""Exports DINOv2 ViT-S/14 as a DataDroid backbone: NHWC 224x224 RGB in [0, 1] -> 384-d embedding.

The embedding is the layer-normed CLS token. ImageNet mean/std normalisation is part of the graph, so the app
feeds the model exactly what it feeds the MobileNet embedders.

Usage: python export_dinov2.py <out.tflite> [fp32|fp16|int8w]
  fp32   ~87 MB
  fp16   ~44 MB, fp16 weights (embeddings match fp32 to cosine 0.999999)
  int8w  ~23 MB, int8 weights, float activations (cosine about 0.996)
Needs torch, transformers, litert-torch and ai-edge-litert (tested with torch 2.14.1, transformers 5.22.2,
litert-torch 0.10.0, ai-edge-litert 2.3.0, Python 3.11).
"""
import sys

import numpy as np
import torch
import litert_torch
from ai_edge_litert.interpreter import Interpreter
from litert_torch.generative.quantize import quant_recipes
from transformers import Dinov2Model

WEIGHTS = "facebook/dinov2-small"
REVISION = "ed25f3a31f01632728cabb09d1542f84ab7b0056"
SIZE = 224

out = sys.argv[1]
precision = sys.argv[2] if len(sys.argv) > 2 else "fp32"

model = Dinov2Model.from_pretrained(WEIGHTS, revision=REVISION, attn_implementation="eager").eval()

# The pretrained position embeddings are for 518 px and are resized (bicubic) on every forward pass.
# Resize once for 224 px and freeze the result, so the exported graph holds a constant instead.
embeddings = model.embeddings
with torch.no_grad():
    tokens = 1 + (SIZE // model.config.patch_size) ** 2
    positions = embeddings.interpolate_pos_encoding(torch.zeros(1, tokens, model.config.hidden_size), SIZE, SIZE).clone()
embeddings.interpolate_pos_encoding = lambda _embeddings, _height, _width: positions


class Backbone(torch.nn.Module):
    def __init__(self, dino):
        super().__init__()
        self.dino = dino
        self.register_buffer("mean", torch.tensor([0.485, 0.456, 0.406]).view(1, 3, 1, 1))
        self.register_buffer("std", torch.tensor([0.229, 0.224, 0.225]).view(1, 3, 1, 1))

    def forward(self, nhwc):
        x = (nhwc.permute(0, 3, 1, 2) - self.mean) / self.std
        return self.dino(pixel_values=x).pooler_output


backbone = Backbone(model).eval()
sample = torch.rand(1, SIZE, SIZE, 3)
with torch.no_grad():
    expected = backbone(sample).numpy()

quant = {
    "fp32": None,
    "fp16": quant_recipes.full_fp16_recipe(),
    "int8w": quant_recipes.full_weight_only_recipe(),
}[precision]
litert_torch.convert(backbone, (sample,), quant_config=quant).export(out)

# Checked from the written file, as litert-torch 0.10's in-memory interpreter can't load quantised models.
interpreter = Interpreter(model_path=out)
interpreter.allocate_tensors()
interpreter.set_tensor(interpreter.get_input_details()[0]["index"], sample.numpy())
interpreter.invoke()
got = interpreter.get_tensor(interpreter.get_output_details()[0]["index"])
cosine = float((got * expected).sum() / np.linalg.norm(got) / np.linalg.norm(expected))
print(f"{out}: output {got.shape}, cosine to PyTorch {cosine:.6f}")
