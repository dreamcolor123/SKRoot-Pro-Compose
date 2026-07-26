#!/usr/bin/env python3
"""Generate Android legacy and adaptive launcher icon resources from a PNG."""
from __future__ import annotations

import argparse
import re
from pathlib import Path

from PIL import Image, ImageColor, ImageOps

DENSITY_SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}
HEX_COLOR = re.compile(r"^#[0-9a-fA-F]{6}$")


def save_png(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=True)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--res-dir", required=True, type=Path)
    parser.add_argument("--background", default="#0F182B")
    args = parser.parse_args()

    if not args.input.is_file():
        parser.error(f"icon file does not exist: {args.input}")
    if not HEX_COLOR.fullmatch(args.background):
        parser.error("background must be a six-digit hex color such as #0F182B")

    source = Image.open(args.input).convert("RGBA")
    if source.width < 32 or source.height < 32:
        parser.error("icon must be at least 32x32 pixels")

    for density, size in DENSITY_SIZES.items():
        density_dir = args.res_dir / f"mipmap-{density}"
        legacy = ImageOps.fit(source, (size, size), method=Image.Resampling.LANCZOS)
        save_png(legacy, density_dir / "ic_launcher.png")
        save_png(legacy, density_dir / "ic_launcher_round.png")

        foreground_size = round(size * 0.66)
        foreground = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        foreground_icon = ImageOps.contain(source, (foreground_size, foreground_size), method=Image.Resampling.LANCZOS)
        left = (size - foreground_icon.width) // 2
        top = (size - foreground_icon.height) // 2
        foreground.alpha_composite(foreground_icon, (left, top))
        save_png(foreground, density_dir / "ic_launcher_foreground.png")

    background = ImageColor.getrgb(args.background)
    background_xml = args.res_dir / "values" / "ic_launcher_background.xml"
    background_xml.parent.mkdir(parents=True, exist_ok=True)
    background_xml.write_text(
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
        "<resources>\n"
        f"    <color name=\"ic_launcher_background\">{args.background.upper()}</color>\n"
        "</resources>\n",
        encoding="utf-8",
    )
    print(f"generated launcher icons from {args.input} with background #{background[0]:02X}{background[1]:02X}{background[2]:02X}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
