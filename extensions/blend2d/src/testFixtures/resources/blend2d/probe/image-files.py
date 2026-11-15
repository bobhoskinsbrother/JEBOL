import os
import random
import struct
import sys

from PIL import Image

folder = sys.argv[1]
os.makedirs(folder, exist_ok=True)
random.seed(5)

SAMPLES = {"RGBA": 4, "RGB": 3, "L": 1, "LA": 2}


def noise(wide, high, mode):
    picture = Image.new(mode, (wide, high))
    count = SAMPLES[mode]
    picture.putdata([tuple(random.randrange(256) for _ in range(count)) if count > 1 else random.randrange(256)
                     for _ in range(wide * high)])
    return picture


def pixels(picture):
    return list(picture.convert("RGBA").get_flattened_data())


def write_qoi(name, picture, channels):
    wide, high = picture.size
    out = bytearray(b"qoif" + struct.pack(">IIBB", wide, high, channels, 0))
    for red, green, blue, alpha in pixels(picture):
        out += bytes([0xFF, red, green, blue, alpha]) if channels == 4 else bytes([0xFE, red, green, blue])
    out += bytes(7) + b"\x01"
    open(os.path.join(folder, name), "wb").write(out)


def write_bmp_with_alpha(name, picture, header_size):
    wide, high = picture.size
    data = b"".join(bytes([blue * alpha // 255, green * alpha // 255, red * alpha // 255, alpha])
                    for red, green, blue, alpha in pixels(picture))
    header = struct.pack("<IiiHHIIiiII", header_size, wide, -high, 1, 32, 3, len(data), 0, 0, 0, 0)
    header += struct.pack("<IIIII", 0x00FF0000, 0x0000FF00, 0x000000FF, 0xFF000000, 0x73524742)
    header += bytes(header_size - len(header))
    offset = 14 + len(header)
    out = b"BM" + struct.pack("<IHHI", offset + len(data), 0, 0, offset) + header + data
    open(os.path.join(folder, name), "wb").write(out)


noise(5, 4, "RGBA").save(os.path.join(folder, "rgba.png"))
noise(5, 4, "RGB").save(os.path.join(folder, "rgb.png"))
noise(5, 4, "L").save(os.path.join(folder, "grey.png"))
noise(5, 4, "LA").save(os.path.join(folder, "grey-alpha.png"))
noise(5, 4, "RGB").quantize(6).save(os.path.join(folder, "palette.png"), transparency=2)
noise(5, 4, "RGB").save(os.path.join(folder, "rgb.bmp"))
write_bmp_with_alpha("alpha.bmp", noise(5, 4, "RGBA"), 108)
write_bmp_with_alpha("refused-v5.bmp", noise(5, 4, "RGBA"), 124)
noise(16, 16, "RGB").save(os.path.join(folder, "colour.jpg"), quality=90)
noise(16, 16, "L").save(os.path.join(folder, "grey.jpg"), quality=90)
write_qoi("rgba.qoi", noise(5, 4, "RGBA"), 4)
write_qoi("rgb.qoi", noise(5, 4, "RGB"), 3)
noise(5, 4, "RGB").save(os.path.join(folder, "refused.gif"))
open(os.path.join(folder, "refused.txt"), "w").write("not an image\n")
