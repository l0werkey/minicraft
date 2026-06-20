# This is for converting images to kotlin int arrays

from PIL import Image
import sys
import os

path = sys.argv[1]
name = os.path.splitext(os.path.basename(path))[0].upper()

img = Image.open(path).convert("RGB")
pixels = list(img.getdata())

hex_values = ", ".join(
    f"0x{r:02X}{g:02X}{b:02X}" for r, g, b in pixels
)

print(f"val {name} = intArrayOf({hex_values})")