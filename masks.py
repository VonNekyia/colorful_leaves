"""Exports the leaf masks in art/masks/*.aseprite to the PNGs the mod reads,
assets/colorfulleaves/textures/masks/<kind>_leaves_<layer>.png, with Aseprite itself:

    python masks.py [path to Aseprite, default: aseprite on the PATH]

Each mask has three layers over the 16x16 leaf texture; to see the texture while
painting, add it as a reference layer in Aseprite, which is not exported.
- parts: every leaf pixel in one of three classes, red, green or blue, neighbouring
  leaves in different ones. A secondary colour spreads the classes between the colour
  and it. Leaf pixels left out count as red.
- highlights: any opaque pixel - these leaf pixels are drawn halfway to white.
- blossoms: grey, the lighter the lighter the blossom - drawn over the leaves in the
  blossom colour where the server gives one.
"""
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).parent
OUT = ROOT / "src/main/resources/assets/colorfulleaves/textures/masks"
LAYERS = ["parts", "highlights", "blossoms"]


def main(aseprite):
    OUT.mkdir(parents=True, exist_ok=True)
    for mask in sorted((ROOT / "art/masks").glob("*.aseprite")):
        for layer in LAYERS:
            subprocess.run([aseprite, "-b", str(mask), "--layer", layer,
                            "--save-as", str(OUT / f"{mask.stem}_{layer}.png")], check=True)
        print(mask.stem)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "aseprite")
