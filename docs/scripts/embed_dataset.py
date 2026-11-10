"""Embeds a folder-of-class-folders dataset with DataDroid's MobileNetV3-Small, mimicking decodeSquare.

Output (little-endian): int32 magic 'DDEV', int32 dim, then per record
int32 class, int32 group (source image), int32 frame (0 = original, 1.. = simulated burst frame), dim float32.
Class names go to <out>.classes.txt. Read by shared/src/androidHostTest/.../MatcherBenchmark.kt.

Usage: python embed_dataset.py <model.tflite> <dataset dir> <out.bin> <photos per class> <bursts per class>
Needs ai-edge-litert, numpy and pillow.
"""
import random
import struct
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageEnhance, ImageOps
from ai_edge_litert.interpreter import Interpreter

model_path, root, out, per_class, bursty = sys.argv[1], Path(sys.argv[2]), Path(sys.argv[3]), int(sys.argv[4]), int(sys.argv[5])
FRAMES = 5
SIZE = 224
rng = random.Random(7)

interp = Interpreter(model_path=model_path, num_threads=8)
interp.allocate_tensors()
inp = interp.get_input_details()[0]
outp = interp.get_output_details()[0]
print("input", inp["shape"], inp["dtype"], "output", outp["shape"], file=sys.stderr)


def square(img: Image.Image) -> Image.Image:
    w, h = img.size
    s = min(w, h)
    return img.crop(((w - s) // 2, (h - s) // 2, (w - s) // 2 + s, (h - s) // 2 + s))


def jitter(img: Image.Image) -> Image.Image:
    """A neighbouring camera frame: slight shift, zoom and exposure change."""
    w, h = img.size
    s = min(w, h)
    crop = int(s * rng.uniform(0.88, 0.98))
    cx = (w - crop) // 2 + int(s * rng.uniform(-0.04, 0.04))
    cy = (h - crop) // 2 + int(s * rng.uniform(-0.04, 0.04))
    cx = max(0, min(w - crop, cx))
    cy = max(0, min(h - crop, cy))
    img = img.crop((cx, cy, cx + crop, cy + crop))
    return ImageEnhance.Brightness(img).enhance(rng.uniform(0.9, 1.1))


def embed(img: Image.Image) -> np.ndarray:
    x = np.asarray(img.resize((SIZE, SIZE), Image.BILINEAR), dtype=np.float32) / 255.0
    interp.set_tensor(inp["index"], x[None])
    interp.invoke()
    return interp.get_tensor(outp["index"]).reshape(-1).astype(np.float32)


classes = sorted(p for p in root.iterdir() if p.is_dir())
records = 0
with open(out, "wb") as f:
    f.write(struct.pack("<ii", 0x44444556, outp["shape"][-1]))
    group = 0
    for ci, cdir in enumerate(classes):
        files = sorted(p for p in cdir.iterdir() if p.suffix.lower() in (".jpg", ".jpeg", ".png"))
        rng.shuffle(files)
        for si, path in enumerate(files[:per_class]):
            img = ImageOps.exif_transpose(Image.open(path)).convert("RGB")
            frames = [square(img)] + ([jitter(img) for _ in range(FRAMES - 1)] if si < bursty else [])
            for fi, frame in enumerate(frames):
                f.write(struct.pack("<iii", ci, group, fi))
                f.write(embed(frame).astype("<f4").tobytes())
                records += 1
            group += 1
        print(cdir.name, min(per_class, len(files)), file=sys.stderr)
Path(str(out) + ".classes.txt").write_text("\n".join(c.name for c in classes))
print("records", records, file=sys.stderr)
