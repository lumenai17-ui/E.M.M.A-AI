import os
from PIL import Image, ImageOps

source_path = r"C:\Users\Usuario\.gemini\antigravity\brain\31c53372-459e-4a41-b407-842594646d88\media__1775837918794.png"
base_res_dir = r"C:\Users\Usuario\.gemini\antigravity\scratch\E.M.M.A. Ai\app\src\main\res"

sizes = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

if not os.path.exists(source_path):
    print("Source image not found!")
    exit(1)

img = Image.open(source_path).convert("RGBA")

# Crop to square
width, height = img.size
min_dim = min(width, height)
left = (width - min_dim) / 2
top = (height - min_dim) / 2
right = (width + min_dim) / 2
bottom = (height + min_dim) / 2

img_square = img.crop((left, top, right, bottom))

for mipmap, size in sizes.items():
    out_dir = os.path.join(base_res_dir, mipmap)
    if not os.path.exists(out_dir):
        os.makedirs(out_dir)
        
    resized = img_square.resize((size, size), Image.Resampling.LANCZOS)
    resized.save(os.path.join(out_dir, "ic_launcher.png"))
    
    # create a circular one for launcher_round just in case
    # Create mask for circular crop
    mask = Image.new('L', (size, size), 0)
    from PIL import ImageDraw
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size, size), fill=255)
    
    round_img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    round_img.paste(resized, (0, 0), mask=mask)
    round_img.save(os.path.join(out_dir, "ic_launcher_round.png"))

print("Icons generated successfully!")
