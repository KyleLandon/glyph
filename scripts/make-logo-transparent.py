"""Knock out the solid black background on assets/glyph-logo.png."""

from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "assets" / "glyph-logo.png"
BAK = ROOT / "assets" / "glyph-logo-black-bg.png"


def main() -> None:
    original = Image.open(SRC).convert("RGBA")
    if not BAK.exists():
        original.save(BAK)

    arr = np.asarray(original).astype(np.float32)
    rgb = arr[:, :, :3]
    mx = rgb.max(axis=2)
    chroma = mx - rgb.min(axis=2)

    # Pure black -> transparent; soft ramp so purple/cyan glow isn't hard-cut.
    lo, hi = 12.0, 48.0
    alpha = np.clip((mx - lo) / (hi - lo), 0.0, 1.0)

    # Near-neutral dark pixels are background even with tiny channel spikes.
    bg = (mx < 40) & (chroma < 18)
    alpha = np.where(bg, 0.0, alpha)
    soft = (mx < 55) & (chroma < 25)
    alpha = np.where(soft, alpha * np.clip((mx - 8) / 47.0, 0.0, 1.0), alpha)

    out = arr.copy()
    out[:, :, 3] = alpha * 255.0
    out[out[:, :, 3] == 0, 0:3] = 0

    result = Image.fromarray(out.astype(np.uint8), "RGBA")
    result.save(SRC)
    print(f"saved {SRC} (alpha pixels={(out[:, :, 3] > 0).sum()})")

    icon = result.resize((64, 64), Image.Resampling.LANCZOS)
    for rel in ("glyph-velocity/server-icon.png", "glyph-folia/server-icon.png"):
        path = ROOT / rel
        icon.save(path)
        print(f"wrote {path}")


if __name__ == "__main__":
    main()
