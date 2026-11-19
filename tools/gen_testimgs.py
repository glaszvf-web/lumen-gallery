# -*- coding: utf-8 -*-
"""光析 LUMEN 测试样张生成器：多格式 + 手写 EXIF"""
from PIL import Image, ImageDraw
import json, io, struct, os

D = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "testimgs")

# 输出目录钉死，文件名白名单，杜绝任何路径拼接逃逸
ALLOWED_FILES = {
    "sample_gps.jpg", "comfyui_test.png", "sample.webp", "anim.gif",
    "sample.bmp", "clean.png", "sd_a1111.jpg", "real_comfyui.png",
}

def out_path(name: str) -> str:
    if name not in ALLOWED_FILES:
        raise ValueError(f"非法输出文件名: {name}")
    p = os.path.join(D, name)
    if os.path.commonpath([os.path.abspath(D), os.path.abspath(p)]) != os.path.abspath(D):
        raise ValueError(f"路径越界: {p}")
    return p

def build_exif():
    hdr = b"II\x2a\x00" + struct.pack("<I", 8)
    def enc_ascii(s): return s.encode("ascii") + b"\x00"
    def enc_short(*v): return struct.pack("<%dH" % len(v), *v)
    def enc_rat(*pairs): return b"".join(struct.pack("<II", n, d) for n, d in pairs)

    ifd0_entries = [
        (0x010F, 2, enc_ascii("Canon")),
        (0x0110, 2, enc_ascii("Canon EOS R5")),
        (0x0112, 3, enc_short(6)),
        (0x0131, 2, enc_ascii("LUMEN Test 1.0")),
        (0x0132, 2, enc_ascii("2026:10:28 03:15:00")),
        (0x8769, 4, b"\x00\x00\x00\x00"),
        (0x8825, 4, b"\x00\x00\x00\x00"),
    ]
    exif_entries = [
        (0x829A, 5, enc_rat((1, 125))),
        (0x829D, 5, enc_rat((28, 10))),
        (0x8827, 3, enc_short(200)),
        (0x920A, 5, enc_rat((35, 1))),
        (0xA403, 3, enc_short(0)),
        (0xA405, 3, enc_short(35)),
        (0xA406, 3, enc_short(3)),
        (0x9209, 3, enc_short(1)),
    ]
    gps_entries = [
        (0x0000, 1, bytes([2, 3, 0, 0])),
        (0x0001, 2, enc_ascii("N")),
        (0x0002, 5, enc_rat((39, 1), (54, 1), (3000, 100))),
        (0x0003, 2, enc_ascii("E")),
        (0x0004, 5, enc_rat((116, 1), (23, 1), (1200, 10))),
        (0x0005, 1, bytes([0])),
        (0x0006, 5, enc_rat((4350, 10))),
    ]

    def build_ifd(entries):
        body = bytearray()
        body += struct.pack("<H", len(entries))
        big = []
        for i, (tag, typ, val) in enumerate(entries):
            if typ == 2 or typ == 1:
                count = len(val)
            elif typ == 3:
                count = len(val) // 2
            elif typ == 5:
                count = len(val) // 8
            else:
                count = 1
            if len(val) <= 4:
                field = val.ljust(4, b"\x00")
            else:
                field = b"\x00\x00\x00\x00"
                big.append(i)
            body += struct.pack("<HHI", tag, typ, count) + field
        body += struct.pack("<I", 0)
        return body, big

    ifd0_body, ifd0_big = build_ifd(ifd0_entries)
    exif_body, exif_big = build_ifd(exif_entries)
    gps_body, gps_big = build_ifd(gps_entries)

    pos = 8
    ifd0_off = pos; pos += len(ifd0_body)
    ifd0_data_off = pos
    pos += sum(len(ifd0_entries[i][2]) for i in ifd0_big); pos += pos % 2
    exif_off = pos; pos += len(exif_body)
    exif_data_off = pos
    pos += sum(len(exif_entries[i][2]) for i in exif_big); pos += pos % 2
    gps_off = pos; pos += len(gps_body)
    gps_data_off = pos

    def patch(body, entries, big_idx, data_off):
        out = bytearray(body)
        o = data_off
        for k, i in enumerate(big_idx):
            out[2 + i * 12 + 8: 2 + i * 12 + 12] = struct.pack("<I", o)
            o += len(entries[i][2])
        return out

    ifd0_body = patch(ifd0_body, ifd0_entries, ifd0_big, ifd0_data_off)
    exif_body = patch(exif_body, exif_entries, exif_big, exif_data_off)
    gps_body = patch(gps_body, gps_entries, gps_big, gps_data_off)

    for i, (tag, typ, val) in enumerate(ifd0_entries):
        if tag == 0x8769:
            ifd0_body[2 + i * 12 + 8: 2 + i * 12 + 12] = struct.pack("<I", exif_off)
        if tag == 0x8825:
            ifd0_body[2 + i * 12 + 8: 2 + i * 12 + 12] = struct.pack("<I", gps_off)

    tiff = bytearray(hdr)
    tiff += ifd0_body
    for i in ifd0_big: tiff += ifd0_entries[i][2]
    if len(tiff) % 2: tiff += b"\x00"
    tiff += exif_body
    for i in exif_big: tiff += exif_entries[i][2]
    if len(tiff) % 2: tiff += b"\x00"
    tiff += gps_body
    for i in gps_big: tiff += gps_entries[i][2]
    return b"Exif\x00\x00" + bytes(tiff)

# ---- 1) JPEG + EXIF + GPS ----
exif_blob = build_exif()
img = Image.new("RGB", (1600, 1000))
dr = ImageDraw.Draw(img)
for y in range(0, 1000, 60):
    dr.rectangle([0, y, 1600, y + 55], fill=(int(255 * y / 1000), 80, 200 - int(y / 5)))
buf = io.BytesIO()
img.save(buf, format="JPEG", quality=92)
data = bytearray(buf.getvalue())
seg = b"\xFF\xE1" + struct.pack(">H", len(exif_blob) + 2) + exif_blob
data = data[:2] + seg + data[2:]
open(out_path("sample_gps.jpg"), "wb").write(bytes(data))
im = Image.open(out_path("sample_gps.jpg"))
ex = im.getexif()
print("sample_gps.jpg ok; readback Make:", ex.get(0x010F), "Model:", ex.get(0x0110))

# ---- 2) PNG + ComfyUI ----
comfy_prompt = {
    "3": {"class_type": "KSampler", "inputs": {"seed": 20261028, "steps": 28, "cfg": 7.5,
          "sampler_name": "dpmpp_2m", "scheduler": "karras", "model": ["4", 0],
          "positive": ["6", 0], "negative": ["7", 0], "latent_image": ["5", 0]}},
    "4": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": "juggernautXL_v9.safetensors"}},
    "5": {"class_type": "EmptyLatentImage", "inputs": {"width": 1216, "height": 832, "batch_size": 1}},
    "6": {"class_type": "CLIPTextEncode", "inputs": {"text": "masterpiece, girl in neon city, rain, cinematic lighting"}},
    "7": {"class_type": "CLIPTextEncode", "inputs": {"text": "lowres, bad anatomy, watermark"}},
}
img2 = Image.new("RGB", (1024, 640), (20, 40, 90))
img2.info["prompt"] = json.dumps(comfy_prompt, separators=(",", ":"))
img2.info["Software"] = "ComfyUI"
from PIL.PngImagePlugin import PngInfo
meta = PngInfo()
meta.add_text("prompt", json.dumps(comfy_prompt, separators=(",", ":")))
meta.add_text("Software", "ComfyUI")
img2.save(out_path("comfyui_test.png"), pnginfo=meta)
print("comfyui_test.png ok")

# ---- 3) WebP ----
img3 = Image.new("RGB", (800, 600))
dr3 = ImageDraw.Draw(img3)
for x in range(0, 800, 10):
    dr3.line([(x, 0), (x, 600)], fill=(x % 255, 200, 255 - x % 255))
img3.save(out_path("sample.webp"), quality=90)
print("sample.webp ok")

# ---- 4) GIF ----
frames = []
for i in range(4):
    frames.append(Image.new("RGB", (320, 320), (i * 60, 120, 200 - i * 40)))
frames[0].save(out_path("anim.gif"), save_all=True, append_images=frames[1:], duration=200, loop=0, comment="LUMEN GIF comment 测试")
print("anim.gif ok")

# ---- 5) BMP ----
Image.new("RGB", (640, 480), (10, 200, 120)).save(out_path("sample.bmp"))
print("sample.bmp ok")

# ---- 6) clean PNG ----
Image.new("RGB", (512, 512), (240, 240, 245)).save(out_path("clean.png"))
print("clean.png ok")

# ---- 7) A1111 JPEG（COM 注入）----
img7 = Image.new("RGB", (768, 512))
d7 = ImageDraw.Draw(img7)
for i in range(768):
    d7.line([(i, 0), (i, 512)], fill=(120 + i % 128, 40, 200 - i % 160))
buf = io.BytesIO()
img7.save(buf, format="JPEG", quality=88)
data = bytearray(buf.getvalue())
sd_text = ("neon arcade at midnight, rain reflections, ultra detailed\n"
           "Negative prompt: blurry, low quality\n"
           "Steps: 30, Sampler: DPM++ 2M Karras, CFG scale: 7, Seed: 778899, Size: 768x512, "
           "Model: dreamshaper_8, Model hash: 879db523c3, Version: v1.9.0")
com = sd_text.encode("utf-8")
seg = b"\xFF\xFE" + struct.pack(">H", len(com) + 2) + com
data = data[:2] + seg + data[2:]
open(out_path("sd_a1111.jpg"), "wb").write(bytes(data))
print("sd_a1111.jpg ok")

# ---- 8) 真实 ComfyUI 产物（用户的库）----
import shutil, os
src = os.environ.get("LUMEN_SAMPLE_IMG", "sample_input.png")  # 外部样张请自行放置
if os.path.exists(src):
    shutil.copy(src, out_path("real_comfyui.png"))
    print("real_comfyui.png copied", os.path.getsize(src))
else:
    print("real comfyui not found, skip")
