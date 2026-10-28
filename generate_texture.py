"""
THE GRAVITY GAUNTLET - Autonomous Pixel Art Texture Generator
Generates high-definition 32x32 item texture:
- Dark titanium heavy chassis
- Zero-point energy neon core
- Conductive copper gravity coils
- Front manipulator prongs (Half-Life 2 Zero Point Energy Field Manipulator style)
"""

import os
import struct
import zlib

def write_png(file_path, width, height, pixels):
    """Writes a raw RGBA pixel array into a valid PNG file using pure Python (no dependencies)."""
    raw_data = bytearray()
    for y in range(height):
        raw_data.append(0)  # Filter byte: None
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

    with open(file_path, "wb") as f:
        f.write(header + ihdr + idat + iend)

def hex_to_rgba(hex_code, alpha=255):
    hex_code = hex_code.lstrip("#")
    return (int(hex_code[0:2], 16), int(hex_code[2:4], 16), int(hex_code[4:6], 16), alpha)

def generate_texture():
    W, H = 32, 32
    # Initialize transparent canvas
    grid = [[(0, 0, 0, 0) for _ in range(W)] for _ in range(H)]

    # Palette
    CLR_EMPTY = (0, 0, 0, 0)
    
    # Titanium / Dark Metal
    T_OUTLINE = hex_to_rgba("#121418")
    T_DARK    = hex_to_rgba("#1f232b")
    T_MID     = hex_to_rgba("#2d3340")
    T_BASE    = hex_to_rgba("#3d4554")
    T_LIGHT   = hex_to_rgba("#566175")
    T_HI      = hex_to_rgba("#828fA6")
    T_SPEC    = hex_to_rgba("#c4d0e5")

    # Copper / Energy Coils
    C_DARK    = hex_to_rgba("#663300")
    C_MID     = hex_to_rgba("#aa5511")
    C_LIGHT   = hex_to_rgba("#d97724")
    C_HI      = hex_to_rgba("#ff9e3b")

    # Neon Core & Zero-Point Energy
    N_DEEP    = hex_to_rgba("#00384d")
    N_DARK    = hex_to_rgba("#006d8f")
    N_MID     = hex_to_rgba("#00a3cc")
    N_BRIGHT  = hex_to_rgba("#00e5ff")
    N_GLOW    = hex_to_rgba("#66f0ff")
    N_WHITE   = hex_to_rgba("#e6ffff")

    # Warning / Status Indicator LED
    LED_RED   = hex_to_rgba("#ff3333")
    LED_AMBER = hex_to_rgba("#ffaa00")

    # Pixel Art Matrix Map (32x32)
    # Visual Layout (held diagonally in player's hand):
    # Bottom-left: Stock, handle and wrist brace
    # Center: Barrel chassis, glowing singularity chamber, copper coils
    # Top-right: Dual magnetic emitter claws with crackling arc
    
    def set_p(x, y, color):
        if 0 <= x < W and 0 <= y < H:
            grid[y][x] = color

    def fill_rect(x1, y1, x2, y2, color):
        for y in range(y1, y2 + 1):
            for x in range(x1, x2 + 1):
                set_p(x, y, color)

    # 1. Wrist Brace / Handle (Bottom-Left: x: 2..10, y: 20..30)
    for y in range(24, 30):
        for x in range(3, 11):
            set_p(x, y, T_DARK)
    for x in range(4, 10):
        set_p(x, 29, T_OUTLINE)
        set_p(x, 24, T_MID)
    set_p(3, 27, T_LIGHT)
    set_p(4, 28, T_LIGHT)
    set_p(5, 29, T_MID)
    # Grip texture
    set_p(6, 26, T_BASE)
    set_p(7, 27, T_BASE)
    set_p(8, 28, T_DARK)
    set_p(5, 25, C_MID) # brass/copper rivet

    # 2. Main Gauntlet Frame / Chassis (Center body: x: 8..23, y: 10..24)
    # Background chassis plate
    for y in range(12, 24):
        for x in range(8, 24):
            # Hexagonal tapered contour
            if (x - 8) + (y - 12) >= 3 and (23 - x) + (23 - y) >= 3:
                set_p(x, y, T_BASE)

    # Shading and Titanium Armor Plates
    for y in range(13, 23):
        set_p(8, y, T_OUTLINE)
        set_p(9, y, T_DARK)
        set_p(23, y, T_OUTLINE)
    for x in range(9, 23):
        set_p(x, 12, T_OUTLINE)
        set_p(x, 13, T_LIGHT)
        set_p(x, 23, T_OUTLINE)

    # Armor plating layers
    fill_rect(10, 14, 13, 22, T_MID)
    fill_rect(11, 15, 13, 21, T_BASE)
    set_p(10, 14, T_SPEC)
    set_p(11, 14, T_HI)
    set_p(12, 14, T_HI)

    # Status LEDs on the housing
    set_p(10, 16, LED_RED)
    set_p(10, 18, LED_AMBER)

    # 3. Copper Gravity Induction Coils (x: 13..17)
    for x in (13, 15, 17):
        for y in range(13, 23):
            set_p(x, y, C_MID)
        set_p(x, 13, C_HI)
        set_p(x, 14, C_LIGHT)
        set_p(x, 21, C_DARK)
        set_p(x, 22, T_OUTLINE)

    # 4. Zero-Point Energy Chamber (x: 18..23, y: 14..21)
    # Containment housing
    fill_rect(18, 14, 23, 21, N_DEEP)
    fill_rect(19, 15, 22, 20, N_DARK)
    fill_rect(19, 16, 22, 19, N_MID)
    fill_rect(20, 17, 21, 18, N_BRIGHT)
    set_p(20, 17, N_WHITE) # Singularity Core
    set_p(21, 17, N_GLOW)
    set_p(20, 18, N_GLOW)

    # Glass viewport refraction / reflection line
    set_p(18, 14, T_SPEC)
    set_p(19, 14, N_GLOW)
    set_p(22, 14, T_SPEC)
    set_p(19, 21, T_DARK)
    set_p(22, 21, T_OUTLINE)

    # 5. Half-Life 2 Style Emitter Claws / Prongs (x: 22..30)
    # Top Prong (curves upwards and forward: x: 22..30, y: 7..14)
    prong_top = [
        (22, 11), (23, 10), (24, 9), (25, 8), (26, 7), (27, 7), (28, 8), (29, 9), (30, 9),
        (22, 12), (23, 11), (24, 10), (25, 9), (26, 8), (27, 8), (28, 9), (29, 10),
        (23, 12), (24, 11), (25, 10), (26, 9)
    ]
    for px, py in prong_top:
        set_p(px, py, T_MID)
    # Top claw highlights
    set_p(26, 7, T_SPEC)
    set_p(27, 7, T_HI)
    set_p(28, 8, T_LIGHT)
    set_p(29, 9, T_HI)
    set_p(30, 9, N_BRIGHT) # Emitter tip glow!

    # Bottom Prong (curves downwards and forward: x: 22..30, y: 21..27)
    prong_bottom = [
        (22, 22), (23, 23), (24, 24), (25, 25), (26, 26), (27, 26), (28, 25), (29, 24), (30, 24),
        (22, 21), (23, 22), (24, 23), (25, 24), (26, 25), (27, 25), (28, 24), (29, 23),
        (23, 21), (24, 22), (25, 23), (26, 24)
    ]
    for px, py in prong_bottom:
        set_p(px, py, T_MID)
    # Bottom claw shading
    set_p(26, 26, T_DARK)
    set_p(27, 26, T_DARK)
    set_p(28, 25, T_LIGHT)
    set_p(29, 24, T_HI)
    set_p(30, 24, N_BRIGHT) # Emitter tip glow!

    # 6. Electric Energy Arcs between emitter tips (x: 27..30, y: 13..20)
    set_p(28, 12, N_MID)
    set_p(29, 13, N_BRIGHT)
    set_p(28, 14, N_WHITE)
    set_p(29, 15, N_GLOW)
    set_p(30, 16, N_BRIGHT)
    set_p(29, 17, N_WHITE)
    set_p(28, 18, N_GLOW)
    set_p(29, 19, N_BRIGHT)
    set_p(28, 21, N_MID)

    # Secondary atmospheric ionization sparkles
    set_p(31, 15, N_GLOW)
    set_p(31, 18, N_MID)
    set_p(25, 17, N_GLOW)

    # 7. Add outlines around perimeter to make it pop like a vanilla item
    for y in range(H):
        for x in range(W):
            if grid[y][x][3] > 0 and grid[y][x] != T_OUTLINE:
                for dx, dy in ((-1,0), (1,0), (0,-1), (0,1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < W and 0 <= ny < H:
                        if grid[ny][nx][3] == 0:
                            grid[ny][nx] = (15, 18, 22, 220)

    return W, H, grid

def main():
    target_dir = os.path.join("src", "main", "resources", "assets", "gravitymod", "textures", "item")
    os.makedirs(target_dir, exist_ok=True)
    target_file = os.path.join(target_dir, "gravity_gauntlet.png")
    root_file = "gravity_gauntlet.png"

    width, height, pixels = generate_texture()
    write_png(target_file, width, height, pixels)
    write_png(root_file, width, height, pixels)

    # Also update icon.png for the mod
    icon_file = os.path.join("src", "main", "resources", "assets", "gravitymod", "icon.png")
    write_png(icon_file, width, height, pixels)

    print(f"[SUCCESS] High-res 32x32 pixel-art texture generated at: {target_file}")
    print(f"[SUCCESS] Mod icon updated at: {icon_file}")

if __name__ == "__main__":
    main()
