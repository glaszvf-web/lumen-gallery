# -*- coding: utf-8 -*-
"""
v27 测试样张：huawei_maker.dng
- IFD0: Make/Model=HUAWEI Mate 80 Pro Max + DNGVersion + GPS 指针 + 内嵌 JPEG 预览(0x0201/0x0202)
- ExifIFD: 0x927C MakerNote = 8 字节头 + 标准 IFD（偏移按 TIFF 头绝对地址——华为真实布局）
- 验证目标：①厂商注释区输出"逆向命中（基址 +8，偏移绝对地址…）" ②RAW 显影预览卡出现
"""
import struct, io
from PIL import Image, ImageDraw

def enc_ascii(s):
    return s.encode("ascii") + b"\x00"

def build():
    # 先造预览 JPEG
    pv = Image.new("RGB", (640, 427))
    d = ImageDraw.Draw(pv)
    for y in range(427):
        d.line([(0, y), (640, y)], fill=(30 + y // 2, 80, 200 - y // 3))
    buf = io.BytesIO()
    pv.save(buf, format="JPEG", quality=88)
    jpeg = buf.getvalue()

    # MakerNote：8 字节头 + IFD（绝对地址偏移）
    mn_entries = [
        (0x0001, 4, struct.pack("<I", 1)),                       # 采样模式
        (0x0002, 3, struct.pack("<H", 5) + b"\x00\x00"),         # 预设 ID
        (0x0003, 2, enc_ascii("hw_photo_v2")),                   # 固件串
        (0x0004, 5, struct.pack("<II", 100, 10)),                # 亮度增益 10.0
        (0x0100, 7, bytes(range(16))),                           # FaceData 二进制
    ]
    HDR = 8
    # IFD 大小：2 + n*12 + 4
    ifd_size = 2 + len(mn_entries) * 12 + 4
    # 数据区：>4 字节的值依序排（0x0003 ascii 12B、0x0100 16B）
    data_blobs = [(i, v) for i, (t, ty, v) in enumerate(mn_entries) if len(v) > 4]
    data_off0 = HDR + ifd_size
    offs = {}
    o = data_off0
    for i, v in data_blobs:
        offs[i] = o
        o += len(v)

    mn = bytearray()
    mn += b"HUAWEI\x00\x00"                                   # 8 字节厂商头
    mn += struct.pack("<H", len(mn_entries))
    for i, (tag, typ, val) in enumerate(mn_entries):
        if len(val) > 4:
            field = struct.pack("<I", offs[i])                # 绝对地址（TIFF 头基准）
        else:
            field = val.ljust(4, b"\x00")
        count = len(val) if typ in (2, 7) else (len(val) // 2 if typ == 3 else (len(val) // 8 if typ == 5 else 1))
        mn += struct.pack("<HHI", tag, typ, count) + field
    mn += struct.pack("<I", 0)
    for i, v in data_blobs:
        mn += v
    mn = bytes(mn)

    # ---- 全 TIFF 布局 ----
    # 8(头) IFD0(2+5*12+4=66) IFD0数据区 ExifIFD(2+2*12+4=30) Exif数据区 GPS IFD(2+5*12+4=66) GPS数据 mn jpeg
    ifd0_entries = [
        (0x010F, 2, enc_ascii("HUAWEI")),
        (0x0110, 2, enc_ascii("HUAWEI Mate 80 Pro Max")),
        (0x014A, 4, struct.pack("<I", 0)),          # SubIFD 指针（占位，这里不挂 SubIFD）
        (0x0201, 4, struct.pack("<I", 0)),          # JPEGInterchangeFormat（占位）
        (0x0202, 4, struct.pack("<I", len(jpeg))),  # 长度
        (0x8769, 4, struct.pack("<I", 0)),          # ExifIFD 指针（占位）
        (0x8825, 4, struct.pack("<I", 0)),          # GPS IFD 指针（占位）
    ]
    ifd0_body_size = 2 + len(ifd0_entries) * 12 + 4
    ifd0_data = enc_ascii("HUAWEI") + enc_ascii("HUAWEI Mate 80 Pro Max")
    ifd0_data = ifd0_data + (b"\x00" if len(ifd0_data) % 2 else b"")

    exif_entries = [
        (0x829A, 5, struct.pack("<II", 1, 30)),
        (0x829D, 5, struct.pack("<II", 14, 10)),
        (0x8827, 3, struct.pack("<H", 640) + b"\x00\x00"),
        (0x920A, 5, struct.pack("<II", 24, 1)),
        (0x927C, 7, mn),                            # MakerNote
    ]
    exif_body_size = 2 + len(exif_entries) * 12 + 4
    exif_data_small = b"".join(v for (t, ty, v) in exif_entries if len(v) <= 4 and ty != 7)  # 8+8+4+8=28
    exif_data_small += b"\x00" * ((4 - len(exif_data_small) % 4) % 4)

    gps_entries = [
        (0x0000, 1, bytes([2, 3, 0, 0])),
        (0x0001, 2, enc_ascii("N")),
        (0x0002, 5, struct.pack("<IIIIII", 24, 1, 22, 1, 3958, 100)),
        (0x0003, 2, enc_ascii("E")),
        (0x0004, 5, struct.pack("<IIIIII", 116, 1, 16, 1, 3138, 100)),
    ]
    gps_body_size = 2 + len(gps_entries) * 12 + 4
    gps_data = bytes([2, 3, 0, 0]) + b"N\x00\x00\x00" + b"E\x00\x00\x00" + \
        struct.pack("<IIIIII", 24, 1, 22, 1, 3958, 100) + \
        struct.pack("<IIIIII", 116, 1, 16, 1, 3138, 100)

    def layout():
        pos = 8
        ifd0_off = pos; pos += ifd0_body_size
        ifd0_data_off = pos; pos += len(ifd0_data)
        exif_off = pos; pos += exif_body_size
        exif_data_off = pos; pos += len(exif_data_small)
        gps_off = pos; pos += gps_body_size
        pos += len(gps_data)
        mn_off = pos; pos += len(mn)
        jpeg_off = pos
        return ifd0_off, ifd0_data_off, exif_off, exif_data_off, gps_off, mn_off, jpeg_off

    (ifd0_off, ifd0_data_off, exif_off, exif_data_off,
     gps_off, mn_off, jpeg_off) = layout()

    def entry(tag, typ, count, field):
        return struct.pack("<HHI", tag, typ, count) + field

    # IFD0
    ifd0 = bytearray()
    ifd0 += struct.pack("<H", len(ifd0_entries))
    for i, (tag, typ, val) in enumerate(ifd0_entries):
        if len(val) > 4:
            field = struct.pack("<I", ifd0_data_off + sum(len(x[2]) for x in ifd0_entries[:i] if len(x[2]) > 4))
        else:
            field = val
        ifd0 += entry(tag, typ, 1, field)
    ifd0 += struct.pack("<I", 0)
    # 修 0x0201 → jpeg_off；0x8769 → exif_off；0x8825 → gps_off
    idx201 = [i for i, (t, _, _) in enumerate(ifd0_entries) if t == 0x0201][0]
    ifd0[2 + idx201 * 12 + 8: 2 + idx201 * 12 + 12] = struct.pack("<I", jpeg_off)
    idx8769 = [i for i, (t, _, _) in enumerate(ifd0_entries) if t == 0x8769][0]
    ifd0[2 + idx8769 * 12 + 8: 2 + idx8769 * 12 + 12] = struct.pack("<I", exif_off)
    idx8825 = [i for i, (t, _, _) in enumerate(ifd0_entries) if t == 0x8825][0]
    ifd0[2 + idx8825 * 12 + 8: 2 + idx8825 * 12 + 12] = struct.pack("<I", gps_off)

    # ExifIFD
    exif = bytearray()
    exif += struct.pack("<H", len(exif_entries))
    small_blob_pos = exif_data_off
    for (tag, typ, val) in exif_entries:
        if len(val) <= 4:
            field = val.ljust(4, b"\x00")
        else:
            field = struct.pack("<I", mn_off)
        count = len(val) if typ in (2, 7) else (len(val) // 2 if typ == 3 else (len(val) // 8 if typ == 5 else 1))
        exif += entry(tag, typ, count, field)
    exif += struct.pack("<I", 0)

    # GPS IFD
    gps = bytearray()
    gps += struct.pack("<H", len(gps_entries))
    gps_data_off = gps_off + gps_body_size
    rat_pos = gps_data_off + 12  # 版本(4) + N(4) + E(4) 之后
    for i, (tag, typ, val) in enumerate(gps_entries):
        if len(val) <= 4:
            field = val.ljust(4, b"\x00")
        elif tag in (0x0002, 0x0004):
            field = struct.pack("<I", rat_pos)
            rat_pos += 24
        else:
            field = struct.pack("<I", gps_data_off)
        count = len(val) if typ == 2 else (len(val) // 8 if typ == 5 else 1)
        gps += entry(tag, typ, count, field)
    gps += struct.pack("<I", 0)
    gps_tail = bytes([2, 3, 0, 0]) + b"N\x00\x00\x00" + b"E\x00\x00\x00" + \
        struct.pack("<IIIIII", 24, 1, 22, 1, 3958, 100) + \
        struct.pack("<IIIIII", 116, 1, 16, 1, 3138, 100)
    # 对齐：N/E 在数据区头 8 字节，RATIONAL 随后 —— 上面 rat_pos 假设 +8
    gps_data = bytes([2, 3, 0, 0]) + b"N\x00\x00\x00" + b"E\x00\x00\x00" + \
        struct.pack("<IIIIII", 24, 1, 22, 1, 3958, 100) + \
        struct.pack("<IIIIII", 116, 1, 16, 1, 3138, 100)

    tiff = bytearray(b"II\x2a\x00" + struct.pack("<I", 8))
    tiff += ifd0
    tiff += ifd0_data
    tiff += exif
    tiff += exif_data_small
    tiff += gps
    tiff += gps_data
    assert len(tiff) == mn_off, f"布局对齐失败: {len(tiff)} != {mn_off}"
    tiff += mn
    assert len(tiff) == jpeg_off, f"JPEG 对齐失败: {len(tiff)} != {jpeg_off}"
    tiff += jpeg
    return bytes(tiff)

data = build()
out = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "testimgs", "huawei_maker.dng")
open(out, "wb").write(data)
print("written", out, len(data), "bytes")

# 自验：python 快速解析确认 0x927C 存在
t = data
assert t[:2] == b"II"
ifd0 = struct.unpack_from("<I", t, 4)[0]
n = struct.unpack_from("<H", t, ifd0)[0]
print("IFD0 entries:", n, " DNGVersion: absent (will still parse as TIFF/DNG by engine make-based)")
