#!/usr/bin/env python3
"""
Assemble an APK zip with zipalign-style 4-byte alignment for STORED entries,
replacing the SDK's `zipalign`. Android mmaps uncompressed entries such as
resources.arsc and (for apps targeting SDK >= 30) requires them uncompressed
and 4-byte aligned; apksig then preserves this alignment while signing.

Usage: apkzip.py <staging_dir> <out.apk>
"""
import os
import struct
import sys
import time
import zlib

# entries stored uncompressed and 4-byte aligned; everything else is deflated
STORED = {"resources.arsc", "res/mipmap/ic_launcher.png"}
ORDER = ["AndroidManifest.xml", "resources.arsc",
         "res/mipmap/ic_launcher.png", "classes.dex"]


def main():
    src, out = sys.argv[1], sys.argv[2]
    buf = bytearray()
    central = []

    for name in ORDER:
        path = os.path.join(src, name)
        data = open(path, "rb").read()
        crc = zlib.crc32(data) & 0xFFFFFFFF
        if name in STORED:
            method, comp = 0, data
        else:
            method, comp = 8, zlib.compress(data, 9)[2:-4]  # raw deflate

        nm = name.encode("utf-8")
        lho = len(buf)
        extra = b""
        if method == 0:
            pad = (4 - ((lho + 30 + len(nm)) % 4)) % 4
            extra = b"\x00" * pad  # align file data to 4 bytes

        buf += struct.pack("<IHHHHHIIIHH",
                           0x04034B50, 20, 0, method, 0, 0,
                           crc, len(comp), len(data), len(nm), len(extra))
        buf += nm + extra + comp
        central.append((nm, method, crc, len(comp), len(data), lho))

    cd_off = len(buf)
    cd = bytearray()
    for (nm, method, crc, clen, ulen, lho) in central:
        cd += struct.pack("<IHHHHHHIIIHHHHHII",
                          0x02014B50, 20, 20, 0, method, 0, 0,
                          crc, clen, ulen, len(nm), 0, 0, 0, 0, 0, lho)
        cd += nm
    buf += cd
    buf += struct.pack("<IHHHHIIH", 0x06054B50, 0, 0,
                       len(central), len(central), len(cd), cd_off, 0)

    open(out, "wb").write(buf)
    print("apk zip:", out, "(%d entries, %d bytes)" % (len(central), len(buf)))


if __name__ == "__main__":
    main()
