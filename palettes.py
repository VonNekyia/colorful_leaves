"""Makes the palettes in assets/colorfulleaves/textures/palettes from the game's leaf textures,
and prints them as tables for the map renderer:

    python palettes.py ~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar

Tinted leaves are grey already: their bright copy is round(60 + c / top * 195) per channel,
top the highest channel over all their colours. Leaves with their colour painted in get a
grey copy first - their luminance, the lightest at oak's lightest grey - and their bright
copy from that grey the same way. The flowers of flowering azalea stay out of both copies,
transparent there, and get a copy of their own with nothing but them.
"""
import io
import sys
import zipfile
from pathlib import Path

from PIL import Image

TINTED = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove"]
PAINTED = ["azalea", "cherry", "pale_oak", "red_poplar", "orange_poplar", "yellow_poplar"]
# The lightest grey of oak leaves, which the server's colours are chosen on.
OAK_TOP = 188
OUT = Path(__file__).parent / "src/main/resources/assets/colorfulleaves/textures/palettes"


def colours(jar, name):
    image = Image.open(io.BytesIO(jar.read(f"assets/minecraft/textures/block/{name}_leaves.png"))).convert("RGBA")
    return {c[:3] for c in image.get_flattened_data() if c[3]}


def luminance(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def bright(c, top):
    return tuple(round(60 + ch / top * 195) for ch in c)


def save(name, palette):
    pixels = [c if len(c) == 4 else (*c, 255) for c in palette]
    path = OUT / f"{name}.png"
    if path.exists() and list(Image.open(path).convert("RGBA").get_flattened_data()) == pixels:
        return  # unchanged: keep the file as it is
    image = Image.new("RGBA", (len(pixels), 1))
    image.putdata(pixels)
    image.save(path)


def hex_(c):
    return "#%02x%02x%02x" % c[:3] + ("" if len(c) == 3 or c[3] else " (transparent)")


def main(jar_path):
    jar = zipfile.ZipFile(jar_path)
    for kind in TINTED:
        key = sorted(colours(jar, kind))
        top = max(max(c) for c in key)
        save(f"{kind}_leaves", key)
        save(f"{kind}_leaves_bright", [bright(c, top) for c in key])
        print(f"{kind}_leaves (tinted): " + ", ".join(f"{hex_(c)} -> bright {hex_(bright(c, top))}" for c in key))

    for kind in PAINTED:
        leaves = sorted(colours(jar, kind), key=luminance)
        flowers = sorted(colours(jar, "flowering_azalea") - set(leaves), key=luminance) if kind == "azalea" else []
        lightest = max(luminance(c) for c in leaves)
        grey = [(round(luminance(c) / lightest * OAK_TOP),) * 3 for c in leaves]
        save(f"{kind}_leaves", leaves + flowers)
        # Transparent, but keeping their colour: opaque leaves show it, tinted.
        save(f"{kind}_leaves_grey", grey + [(*c, 0) for c in flowers])
        save(f"{kind}_leaves_bright", [bright(g, OAK_TOP) for g in grey] + [(*c, 0) for c in flowers])
        if flowers:
            save(f"{kind}_leaves_flowers", [(0, 0, 0, 0)] * len(leaves) + flowers)
        print(f"{kind}_leaves (painted): " + ", ".join(
            f"{hex_(c)} -> grey {hex_(g)}, bright {hex_(bright(g, OAK_TOP))}" for c, g in zip(leaves, grey))
              + "".join(f", {hex_(c)} -> flower, untinted" for c in flowers))


if __name__ == "__main__":
    main(sys.argv[1])
