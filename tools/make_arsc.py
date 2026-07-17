#!/usr/bin/env python3
"""
Encode a minimal binary resources.arsc (Android's compiled resource table)
without aapt2, just enough to expose one launcher icon:

    @mipmap/ic_launcher  ->  res/mipmap/ic_launcher.png   (id 0x7f010000)

The table has one package (0x7f), one type ("mipmap"), one key ("ic_launcher")
and a single default-config entry whose value is a string pointing at the PNG
path inside the APK. The manifest's android:icon references 0x7f010000.

Usage: make_arsc.py <out/resources.arsc> [icon_path_in_apk]
"""
import struct
import sys

PKG_ID = 0x7F
PKG_NAME = "com.nova.blocks"
TYPE_NAME = "mipmap"
KEY_NAME = "ic_launcher"

TYPE_STRING = 0x03


def enc_string_pool(strings):
    """UTF-16LE string pool chunk (type 0x0001), flags=0."""
    data = bytearray()
    offsets = []
    for s in strings:
        offsets.append(len(data))
        u = s.encode("utf-16-le")
        data += struct.pack("<H", len(s))   # length in UTF-16 code units (BMP)
        data += u
        data += b"\x00\x00"
    while len(data) % 4:
        data += b"\x00"
    header_size = 28
    strings_start = header_size + 4 * len(strings)
    size = strings_start + len(data)
    out = bytearray()
    out += struct.pack("<HHI", 0x0001, header_size, size)
    out += struct.pack("<II", len(strings), 0)        # stringCount, styleCount
    out += struct.pack("<III", 0, strings_start, 0)   # flags, stringsStart, stylesStart
    for o in offsets:
        out += struct.pack("<I", o)
    out += data
    assert len(out) == size
    return bytes(out)


def enc_typespec(type_id, entry_count):
    size = 16 + 4 * entry_count
    out = bytearray()
    out += struct.pack("<HHI", 0x0202, 16, size)      # type, headerSize, size
    out += struct.pack("<BBHI", type_id, 0, 0, entry_count)
    for _ in range(entry_count):
        out += struct.pack("<I", 0)                   # per-entry spec flags
    return bytes(out)


def enc_type(type_id, entries):
    """entries: list of (key_index, global_string_index) simple string values."""
    config_size = 64
    config = bytearray(config_size)
    struct.pack_into("<I", config, 0, config_size)    # size; rest zero = default

    header_size = 8 + 4 + 4 + 4 + config_size          # = 84
    entry_count = len(entries)
    entries_start = header_size + 4 * entry_count

    offsets = bytearray()
    body = bytearray()
    for (key_idx, gstr_idx) in entries:
        offsets += struct.pack("<I", len(body))
        # ResTable_entry (simple)
        body += struct.pack("<HHI", 8, 0, key_idx)     # size, flags, key
        # Res_value
        body += struct.pack("<HBBI", 8, 0, TYPE_STRING, gstr_idx)

    size = entries_start + len(body)
    out = bytearray()
    out += struct.pack("<HHI", 0x0201, header_size, size)
    out += struct.pack("<BBHI", type_id, 0, 0, entry_count)
    out += struct.pack("<I", entries_start)
    out += config
    out += offsets
    out += body
    assert len(out) == size, (len(out), size)
    return bytes(out)


def enc_package(type_pool, key_pool, type_spec, type_chunk):
    header_size = 288
    type_strings_off = header_size
    key_strings_off = header_size + len(type_pool)

    body = type_pool + key_pool + type_spec + type_chunk
    size = header_size + len(body)

    out = bytearray()
    out += struct.pack("<HHI", 0x0200, header_size, size)   # header
    out += struct.pack("<I", PKG_ID)                         # id
    name = PKG_NAME.encode("utf-16-le")[:254]
    name += b"\x00" * (256 - len(name))                     # uint16 name[128]
    out += name
    out += struct.pack("<I", type_strings_off)              # typeStrings
    out += struct.pack("<I", 0)                             # lastPublicType
    out += struct.pack("<I", key_strings_off)               # keyStrings
    out += struct.pack("<I", 0)                             # lastPublicKey
    out += struct.pack("<I", 0)                             # typeIdOffset
    assert len(out) == header_size, len(out)
    out += body
    assert len(out) == size
    return bytes(out)


def main():
    out_path = sys.argv[1]
    icon_path = sys.argv[2] if len(sys.argv) > 2 else "res/mipmap/ic_launcher.png"

    global_pool = enc_string_pool([icon_path])         # value pool (index 0 = path)
    type_pool = enc_string_pool([TYPE_NAME])           # type id 1 = mipmap
    key_pool = enc_string_pool([KEY_NAME])             # key 0 = ic_launcher

    type_spec = enc_typespec(1, 1)
    type_chunk = enc_type(1, [(0, 0)])                 # key 0 -> global string 0
    package = enc_package(type_pool, key_pool, type_spec, type_chunk)

    body = global_pool + package
    size = 12 + len(body)
    out = bytearray()
    out += struct.pack("<HHI", 0x0002, 12, size)       # RES_TABLE_TYPE
    out += struct.pack("<I", 1)                         # packageCount
    out += body
    with open(out_path, "wb") as f:
        f.write(out)
    print("wrote %s (%d bytes) -> id 0x%08X = @mipmap/ic_launcher (%s)" %
          (out_path, size, (PKG_ID << 24) | (1 << 16) | 0, icon_path))


if __name__ == "__main__":
    main()
