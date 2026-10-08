from PIL import Image, ImageEnhance
import os

def process_icon():
    src_path = "/Users/skanakmegha/.gemini/antigravity-ide/brain/ec0c5dc9-e075-4555-80e9-89a6ab76ab1d/media__1791479179015.jpg"
    if not os.path.exists(src_path):
        print("Source image not found.")
        return

    # Open and enhance contrast significantly to avoid "washed out" look
    img = Image.open(src_path).convert("RGBA")
    enhancer = ImageEnhance.Contrast(img)
    img = enhancer.enhance(1.5)
    enhancer = ImageEnhance.Color(img)
    img = enhancer.enhance(1.2)
    enhancer = ImageEnhance.Brightness(img)
    img = enhancer.enhance(1.1)
    
    data = img.getdata()
    
    # We will make pixels close to the background color transparent
    # Background color seems to be around (13, 13, 21) or black.
    # We will use a threshold for brightness to make dark pixels transparent.
    new_data = []
    min_x, min_y, max_x, max_y = img.width, img.height, 0, 0
    
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = data[y * img.width + x]
            brightness = max(r, g, b)
            if brightness > 40:
                min_x = min(min_x, x)
                min_y = min(min_y, y)
                max_x = max(max_x, x)
                max_y = max(max_y, y)
                # Keep color, make fully opaque
                new_data.append((r, g, b, 255))
            else:
                # Transparent
                new_data.append((0, 0, 0, 0))

    img.putdata(new_data)
    
    # Crop to the actual logo bounds
    cropped = img.crop((min_x, min_y, max_x + 1, max_y + 1))
    
    # Adaptive icons use 108dp size, where inner 72dp is the safe zone (66%).
    # We want the logo to fit within roughly 60dp to be perfectly safe and look good.
    adaptive_dp_sizes = {
        "mdpi": 108,
        "hdpi": 162,
        "xhdpi": 216,
        "xxhdpi": 324,
        "xxxhdpi": 432
    }
    
    dp_sizes = {
        "mdpi": 48,
        "hdpi": 72,
        "xhdpi": 96,
        "xxhdpi": 144,
        "xxxhdpi": 192
    }
    
    res_dir = "app/src/main/res"
    
    for density, size in adaptive_dp_sizes.items():
        # Create a blank transparent image of the adaptive size
        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        
        # Target size for the logo inside the 108dp grid (60dp max)
        logo_target_size = int(size * (64.0 / 108.0))
        
        # Scale cropped logo
        aspect = cropped.width / cropped.height
        if aspect > 1:
            w = logo_target_size
            h = int(w / aspect)
        else:
            h = logo_target_size
            w = int(h * aspect)
            
        resized_logo = cropped.resize((w, h), Image.LANCZOS)
        
        # Paste centered
        offset_x = (size - w) // 2
        offset_y = (size - h) // 2
        canvas.paste(resized_logo, (offset_x, offset_y), resized_logo)
        
        out_dir = os.path.join(res_dir, f"mipmap-{density}")
        os.makedirs(out_dir, exist_ok=True)
        out_path = os.path.join(out_dir, "ic_launcher_foreground.png")
        canvas.save(out_path, "PNG")
        
        # Also create legacy icons with a solid background
        legacy_size = dp_sizes[density]
        legacy_canvas = Image.new("RGBA", (legacy_size, legacy_size), (13, 13, 21, 255))
        
        legacy_logo_size = int(legacy_size * 0.75)
        lw = legacy_logo_size
        lh = int(lw / aspect)
        if aspect < 1:
            lh = legacy_logo_size
            lw = int(lh * aspect)
            
        legacy_resized = cropped.resize((lw, lh), Image.LANCZOS)
        legacy_canvas.paste(legacy_resized, ((legacy_size - lw) // 2, (legacy_size - lh) // 2), legacy_resized)
        
        legacy_out_path = os.path.join(out_dir, "ic_launcher.png")
        legacy_canvas.save(legacy_out_path, "PNG")
        
        # Circle icon
        mask = Image.new('L', (legacy_size, legacy_size), 0)
        from PIL import ImageDraw
        draw = ImageDraw.Draw(mask)
        draw.ellipse((0, 0, legacy_size, legacy_size), fill=255)
        legacy_canvas.putalpha(mask)
        legacy_round_out_path = os.path.join(out_dir, "ic_launcher_round.png")
        legacy_canvas.save(legacy_round_out_path, "PNG")

if __name__ == '__main__':
    process_icon()
