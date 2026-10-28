import os
import struct
import zlib

def write_png(file_path, width, height, pixels):
    raw_data = bytearray()
    for y in range(height):
        raw_data.append(0)  # None filter
        for x in range(width):
            r, g, b, a = pixels[y][x]
            raw_data.extend((r, g, b, a))

    def make_chunk(chunk_type, data):
        length = len(data)
        crc = zlib.crc32(chunk_type + data) & 0xffffffff
        return struct.pack(">I", length) + chunk_type + data + struct.pack(">I", crc)

    header = b'\x89PNG\r\n\x1a\n'
    ihdr_data = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    ihdr = make_chunk(b'IHDR', ihdr_data)
    idat = make_chunk(b'IDAT', zlib.compress(bytes(raw_data), 9))
    iend = make_chunk(b'IEND', b'')

    os.makedirs(os.path.dirname(file_path), exist_ok=True)
    with open(file_path, "wb") as f:
        f.write(header + ihdr + idat + iend)

def hex_to_rgba(hex_code, alpha=255):
    hex_code = hex_code.lstrip("#")
    return (int(hex_code[0:2], 16), int(hex_code[2:4], 16), int(hex_code[4:6], 16), alpha)

def create_pocket_base_top():
    W, H = 32, 32
    grid = [[(20, 20, 26, 255) for _ in range(W)] for _ in range(H)]

    # Obsidian frame with copper corners
    C_FRAME = hex_to_rgba("#15131e")
    C_COPPER = hex_to_rgba("#d97724")
    C_COPPER_DARK = hex_to_rgba("#7e3b12")
    C_CYAN_GLOW = hex_to_rgba("#00ffff")
    C_CYAN_DEEP = hex_to_rgba("#005c7a")
    C_PURPLE = hex_to_rgba("#8800ff")
    C_WHITE = hex_to_rgba("#ffffff")

    for y in range(H):
        for x in range(W):
            dist_to_center = ((x - 15.5)**2 + (y - 15.5)**2)**0.5
            # Outer border
            if x < 3 or x >= 29 or y < 3 or y >= 29:
                grid[y][x] = C_FRAME
            # Copper corner rivets
            if (x < 6 or x >= 26) and (y < 6 or y >= 26):
                grid[y][x] = C_COPPER if (x + y) % 2 == 0 else C_COPPER_DARK
            elif dist_to_center < 11:
                # Swirling quantum portal center
                if dist_to_center < 3:
                    grid[y][x] = C_WHITE
                elif dist_to_center < 6:
                    grid[y][x] = C_CYAN_GLOW
                elif dist_to_center < 9:
                    grid[y][x] = C_PURPLE
                else:
                    grid[y][x] = C_CYAN_DEEP

    return W, H, grid

def create_pocket_base_side():
    W, H = 32, 32
    grid = [[hex_to_rgba("#1d1d24") for _ in range(W)] for _ in range(H)]

    C_FRAME = hex_to_rgba("#121118")
    C_COPPER = hex_to_rgba("#d97724")
    C_CYAN = hex_to_rgba("#00e5ff")
    C_PURPLE = hex_to_rgba("#9933ff")

    for y in range(H):
        for x in range(W):
            if x < 2 or x >= 30 or y < 2 or y >= 30:
                grid[y][x] = C_FRAME
            # Vertical copper heat conduits
            elif x in (6, 7, 24, 25):
                grid[y][x] = C_COPPER
            # Central portal eye chamber
            elif 10 <= x <= 21 and 10 <= y <= 21:
                dist = ((x - 15.5)**2 + (y - 15.5)**2)**0.5
                if dist < 2.5:
                    grid[y][x] = hex_to_rgba("#ffffff")
                elif dist < 5.5:
                    grid[y][x] = C_CYAN
                else:
                    grid[y][x] = C_PURPLE

    return W, H, grid

def create_pocket_exit():
    W, H = 32, 32
    grid = [[hex_to_rgba("#161a22") for _ in range(W)] for _ in range(H)]

    C_BORDER = hex_to_rgba("#0a0e14")
    C_AMETHYST = hex_to_rgba("#a366ff")
    C_GREEN_GLOW = hex_to_rgba("#33ff77")
    C_WHITE = hex_to_rgba("#ffffff")

    for y in range(H):
        for x in range(W):
            if x < 3 or x >= 29 or y < 3 or y >= 29:
                grid[y][x] = C_BORDER
            elif 10 <= x <= 21 and 8 <= y <= 23:
                # Glowing holographic exit arrow pointing up
                dx = abs(x - 15.5)
                if y - 8 >= dx * 1.5 or (y >= 16 and dx < 2.5):
                    grid[y][x] = C_GREEN_GLOW if (x + y) % 3 != 0 else C_WHITE
                else:
                    grid[y][x] = C_AMETHYST

    return W, H, grid

def create_time_remote():
    W, H = 32, 32
    # Transparent background
    grid = [[(0, 0, 0, 0) for _ in range(W)] for _ in range(H)]

    C_OUTLINE = hex_to_rgba("#0c1017")
    C_BODY = hex_to_rgba("#222834")
    C_BODY_LIGHT = hex_to_rgba("#3b4455")
    C_GOLD = hex_to_rgba("#e6ac00")
    C_AMETHYST = hex_to_rgba("#c44dff")
    C_CYAN_DISPLAY = hex_to_rgba("#00f0ff")
    C_RED_BUTTON = hex_to_rgba("#ff2244")
    C_WHITE = hex_to_rgba("#ffffff")

    # Handheld gadget body (x: 8..23, y: 4..27)
    for y in range(5, 27):
        for x in range(9, 23):
            grid[y][x] = C_BODY

    # Beveled casing
    for y in range(6, 26):
        grid[y][9] = C_BODY_LIGHT
        grid[y][22] = C_OUTLINE
    for x in range(9, 23):
        grid[5][x] = C_BODY_LIGHT
        grid[26][x] = C_OUTLINE

    # Top antenna / chrono-emitter (x: 14..17, y: 2..5)
    for y in range(2, 6):
        for x in range(14, 18):
            grid[y][x] = C_GOLD
    grid[1][15] = C_AMETHYST
    grid[1][16] = C_AMETHYST

    # Digital LED Chrono Display (x: 11..20, y: 8..14)
    for y in range(8, 15):
        for x in range(11, 21):
            grid[y][x] = hex_to_rgba("#0a1420")
    # Glowing digital '10.0' or rewind glyphs
    for x in (12, 13, 15, 17, 18, 19):
        grid[9][x] = C_CYAN_DISPLAY
        grid[11][x] = C_CYAN_DISPLAY
        grid[13][x] = C_CYAN_DISPLAY
    grid[10][12] = C_CYAN_DISPLAY
    grid[12][12] = C_CYAN_DISPLAY
    grid[10][19] = C_CYAN_DISPLAY
    grid[12][19] = C_CYAN_DISPLAY
    grid[13][16] = C_CYAN_DISPLAY  # dot

    # Big Rewind Button (x: 13..18, y: 17..22)
    for y in range(17, 23):
        for x in range(13, 19):
            grid[y][x] = C_RED_BUTTON
    grid[17][13] = hex_to_rgba("#ff7788")
    grid[18][14] = hex_to_rgba("#ff7788")
    # Rewind symbol (<<)
    grid[19][15] = C_WHITE
    grid[20][14] = C_WHITE
    grid[21][15] = C_WHITE
    grid[19][17] = C_WHITE
    grid[20][16] = C_WHITE
    grid[21][17] = C_WHITE

    # Bottom Grip Grippers
    for y in (24, 25):
        grid[y][11] = C_GOLD
        grid[y][20] = C_GOLD

    # Outline
    for y in range(H):
        for x in range(W):
            if grid[y][x][3] > 0 and grid[y][x] != C_OUTLINE:
                for dx, dy in ((-1,0), (1,0), (0,-1), (0,1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < W and 0 <= ny < H:
                        if grid[ny][nx][3] == 0:
                            grid[ny][nx] = C_OUTLINE

    return W, H, grid

def main():
    base_dir = os.path.join("src", "main", "resources", "assets", "chronopocket")

    # 1. Blocks
    w, h, p = create_pocket_base_top()
    write_png(os.path.join(base_dir, "textures", "block", "pocket_base_top.png"), w, h, p)

    w, h, p = create_pocket_base_side()
    write_png(os.path.join(base_dir, "textures", "block", "pocket_base_side.png"), w, h, p)

    w, h, p = create_pocket_exit()
    write_png(os.path.join(base_dir, "textures", "block", "pocket_exit.png"), w, h, p)

    # 2. Items
    w, h, p = create_time_remote()
    write_png(os.path.join(base_dir, "textures", "item", "time_remote.png"), w, h, p)

    # 3. Icon
    write_png(os.path.join(base_dir, "icon.png"), w, h, p)

    print("[SUCCESS] All ChronoPocket textures generated successfully!")

if __name__ == "__main__":
    main()
