#!/usr/bin/env python3
"""
Generates production-grade Google Play Store graphic assets for TipsyBuddy:
1. App Icon: 512x512 PNG matching TipsyBuddy's signature glowing amber mug & dark slate theme.
2. Feature Graphic: 1024x500 PNG with logo, glowing emblem, value props, and screen mockup.
3. Phone Screenshots: 1080x1920 PNGs (9:16 aspect ratio) with high-contrast headers,
   subtitles, and sleek framed mockups of real app screens.
4. 7-inch Tablet Screenshots: 1200x1920 PNGs.
5. 10-inch Tablet Screenshots: 1600x2560 PNGs.
"""

import math
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS_DIR = os.path.join(BASE_DIR, "play-store-assets")
PHONE_SRC_DIR = os.path.join(BASE_DIR, "screenshots", "phone")

ICON_DIR = os.path.join(ASSETS_DIR, "icon")
SCREEN_PHONE_DIR = os.path.join(ASSETS_DIR, "screenshots", "phone")
SCREEN_7IN_DIR = os.path.join(ASSETS_DIR, "screenshots", "sevenInch")
SCREEN_10IN_DIR = os.path.join(ASSETS_DIR, "screenshots", "tenInch")
SCREEN_WEAR_DIR = os.path.join(ASSETS_DIR, "screenshots", "wear")
WEAR_SRC_DIR = os.path.join(BASE_DIR, "screenshots", "wear")

for d in [ICON_DIR, SCREEN_PHONE_DIR, SCREEN_7IN_DIR, SCREEN_10IN_DIR, SCREEN_WEAR_DIR]:
    os.makedirs(d, exist_ok=True)

FONT_BOLD = r"C:\Windows\Fonts\segoeuib.ttf"
FONT_SEMI = r"C:\Windows\Fonts\segoeui.ttf"
FONT_FALLBACK = r"C:\Windows\Fonts\arialbd.ttf"

def get_font(path, size):
    try:
        return ImageFont.truetype(path, size)
    except Exception:
        try:
            return ImageFont.truetype(FONT_FALLBACK, size)
        except Exception:
            return ImageFont.load_default()


def create_app_icon():
    """Generates 512x512 app icon matching ic_launcher vector drawable."""
    print("Generating 512x512 App Icon...")
    size = 512
    im = Image.new("RGBA", (size, size), (19, 26, 42, 255)) # #131A2A
    draw = ImageDraw.Draw(im)

    # Subtle radial gradient for background depth
    for r in range(size // 2, 0, -2):
        factor = r / (size / 2)
        bg_col = (
            int(19 + (26 - 19) * (1 - factor)),
            int(26 + (38 - 26) * (1 - factor)),
            int(42 + (60 - 42) * (1 - factor)),
            255
        )
        draw.ellipse([size // 2 - r, size // 2 - r, size // 2 + r, size // 2 + r], fill=bg_col)

    # Ambient amber glow behind the mug
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    glow_draw.ellipse([80, 80, 432, 432], fill=(245, 158, 11, 40))
    glow_draw.ellipse([110, 110, 402, 402], fill=(245, 158, 11, 65))
    glow = glow.filter(ImageFilter.GaussianBlur(30))
    im = Image.alpha_composite(im, glow)
    draw = ImageDraw.Draw(im)

    # Scale 108dp viewport to 512px (scale factor ~ 4.2, centered)
    ox, oy = 60, 45
    s = 4.0

    def pt(x, y):
        return (ox + x * s, oy + y * s)

    # Handle
    handle_rect = [ox + 66 * s, oy + 42 * s, ox + (66 + 18) * s, oy + (42 + 25) * s]
    draw.rounded_rectangle(handle_rect, radius=int(6 * s), fill=(217, 119, 6, 255)) # #D97706
    handle_inner = [ox + 66 * s, oy + 46 * s, ox + (66 + 12) * s, oy + (42 + 20) * s]
    draw.rounded_rectangle(handle_inner, radius=int(3 * s), fill=(22, 31, 50, 255))

    # Mug Body (Beer)
    mug_rect = [ox + 32 * s, oy + 32 * s, ox + 68 * s, oy + 78 * s]
    draw.rounded_rectangle(mug_rect, radius=int(8 * s), fill=(245, 158, 11, 255)) # #F59E0B

    # Mug body highlight / shading
    draw.line([ox + 36 * s, oy + 34 * s, ox + 36 * s, oy + 74 * s], fill=(253, 230, 138, 140), width=int(2.5 * s))

    # Beer bubbles
    bubbles = [
        (42, 48, 2.5), (50, 56, 3.0), (45, 66, 2.0), (56, 46, 2.2),
        (58, 62, 2.8), (38, 58, 1.8), (52, 70, 2.2)
    ]
    for bx, by, br in bubbles:
        draw.ellipse([ox + (bx - br) * s, oy + (by - br) * s, ox + (bx + br) * s, oy + (by + br) * s], fill=(253, 230, 138, 220))

    # Foamy Head
    foam_col = (254, 243, 199, 255) # #FEF3C7
    foam_shadow = (245, 230, 175, 255)
    # Cloud puffs for beer foam
    puffs = [
        (32, 32, 8), (40, 25, 10), (49, 23, 11), (58, 25, 9), (67, 31, 7.5),
        (35, 29, 9), (45, 26, 11), (55, 28, 9.5), (63, 30, 8)
    ]
    for px, py, pr in puffs:
        draw.ellipse([ox + (px - pr) * s, oy + (py - pr) * s, ox + (px + pr) * s, oy + (py + pr) * s], fill=foam_shadow)
    for px, py, pr in puffs:
        draw.ellipse([ox + (px - pr) * s, oy + (py - pr - 1) * s, ox + (px + pr) * s, oy + (py + pr - 1) * s], fill=foam_col)

    # Base foam bar
    draw.rectangle([ox + 31 * s, oy + 31 * s, ox + 69 * s, oy + 36 * s], fill=foam_col)

    # Dripping foam detail
    draw.rounded_rectangle([ox + 35 * s, oy + 35 * s, ox + 38 * s, oy + 42 * s], radius=int(1.5 * s), fill=foam_col)
    draw.rounded_rectangle([ox + 55 * s, oy + 35 * s, ox + 58 * s, oy + 44 * s], radius=int(1.5 * s), fill=foam_col)

    icon_path = os.path.join(ICON_DIR, "icon-512.png")
    # Convert to RGB 24-bit for strict Play Console compatibility
    im_rgb = Image.new("RGB", (size, size), (19, 26, 42))
    im_rgb.paste(im, mask=im.split()[3])
    im_rgb.save(icon_path, "PNG", optimize=True)
    print(f"Saved: {icon_path} ({os.path.getsize(icon_path)} bytes)")


def create_feature_graphic():
    """Generates 1024x500 Feature Graphic with branding, tags, and dashboard mockup."""
    print("Generating 1024x500 Feature Graphic...")
    w, h = 1024, 500
    im = Image.new("RGBA", (w, h), (11, 15, 25, 255))
    draw = ImageDraw.Draw(im)

    # Nightlife gradient background (rich dark navy to slate with warm amber glow)
    for y in range(h):
        factor = y / h
        r = int(11 + (22 - 11) * factor)
        g = int(15 + (32 - 15) * factor)
        b = int(25 + (48 - 25) * factor)
        draw.line([(0, y), (w, y)], fill=(r, g, b, 255))

    # Glow accents
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(glow)
    gdraw.ellipse([-50, 50, 450, 550], fill=(245, 158, 11, 45))     # Amber glow on left
    gdraw.ellipse([700, -50, 1150, 400], fill=(16, 185, 129, 35))   # Emerald safe glow on right
    glow = glow.filter(ImageFilter.GaussianBlur(50))
    im = Image.alpha_composite(im, glow)
    draw = ImageDraw.Draw(im)

    # Load and place the small app icon on the left
    icon_512 = Image.open(os.path.join(ICON_DIR, "icon-512.png")).convert("RGBA")
    icon_small = icon_512.resize((100, 100), Image.Resampling.LANCZOS)
    # Mask with rounded corners
    mask = Image.new("L", (100, 100), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, 100, 100], radius=24, fill=255)
    im.paste(icon_small, (60, 65), mask)

    # Typography
    font_title = get_font(FONT_BOLD, 48)
    font_tagline = get_font(FONT_BOLD, 26)
    font_sub = get_font(FONT_SEMI, 17)
    font_badge = get_font(FONT_BOLD, 14)

    # App Title & Beer Emoji
    draw.text((180, 72), "TipsyBuddy", font=font_title, fill=(255, 255, 255, 255))
    draw.text((455, 78), "🍻", font=get_font(FONT_FALLBACK, 36), fill=(245, 158, 11, 255))

    # Tagline
    draw.text((60, 185), "Party Smart, Get Home Safe", font=font_tagline, fill=(245, 158, 11, 255))

    # Sub-bullets
    draw.text((60, 230), "Your smart night-out wingman & safety companion.", font=font_sub, fill=(203, 213, 225, 255))

    # Feature Pill Badges
    badges = [
        ("📊 Live BAC & Sober Countdown", (245, 158, 11)),
        ("⚡ 1-Tap Drink & Tab Tracker", (59, 130, 246)),
        ("📍 Live Location Sharing", (16, 185, 129)),
        ("🚕 1-Tap Uber, Lyft & Safety", (168, 85, 247))
    ]

    bx, by = 60, 275
    for text, col in badges:
        # Measure text
        bbox = draw.textbbox((0, 0), text, font=font_badge)
        tw = bbox[2] - bbox[0]
        th = bbox[3] - bbox[1]
        badge_w = tw + 28
        badge_h = th + 18
        if bx + badge_w > 570:
            bx = 60
            by += badge_h + 12
        # Draw pill
        draw.rounded_rectangle([bx, by, bx + badge_w, by + badge_h], radius=badge_h // 2,
                               fill=(col[0], col[1], col[2], 35), outline=(col[0], col[1], col[2], 180), width=1)
        draw.text((bx + 14, by + 8), text, font=font_badge, fill=(241, 245, 249, 255))
        bx += badge_w + 14

    # Phone Screen Mockup on Right
    phone_src = os.path.join(PHONE_SRC_DIR, "01_main_dashboard_with_drinks.png")
    if not os.path.exists(phone_src):
        phone_src = os.path.join(PHONE_SRC_DIR, "01_main_dashboard.png")

    if os.path.exists(phone_src):
        screen_raw = Image.open(phone_src).convert("RGBA")
        # Scale to fit nicely in 500px height (target height ~460)
        target_h = 440
        ratio = target_h / screen_raw.height
        target_w = int(screen_raw.width * ratio)
        screen_scaled = screen_raw.resize((target_w, target_h), Image.Resampling.LANCZOS)

        # Phone frame
        frame_pad = 8
        frame_w = target_w + frame_pad * 2
        frame_h = target_h + frame_pad * 2
        frame = Image.new("RGBA", (frame_w, frame_h), (0, 0, 0, 0))
        fdraw = ImageDraw.Draw(frame)

        # Outer bezel
        fdraw.rounded_rectangle([0, 0, frame_w - 1, frame_h - 1], radius=32, fill=(30, 41, 59, 255), outline=(71, 85, 105, 255), width=2)
        # Inner screen mask
        mask = Image.new("L", (target_w, target_h), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, target_w, target_h], radius=24, fill=255)
        frame.paste(screen_scaled, (frame_pad, frame_pad), mask)

        # Drop shadow for phone
        shadow = Image.new("RGBA", (frame_w + 40, frame_h + 40), (0, 0, 0, 0))
        ImageDraw.Draw(shadow).rounded_rectangle([20, 20, frame_w + 20, frame_h + 20], radius=36, fill=(0, 0, 0, 160))
        shadow = shadow.filter(ImageFilter.GaussianBlur(18))

        paste_x = 730
        paste_y = 30
        im.paste(shadow, (paste_x - 20, paste_y - 20), shadow)
        im.paste(frame, (paste_x, paste_y), frame)

    feat_path = os.path.join(ASSETS_DIR, "feature-graphic.png")
    # Strict 24-bit RGB PNG (no alpha channel)
    im_rgb = Image.new("RGB", (w, h), (11, 15, 25))
    im_rgb.paste(im, mask=im.split()[3])
    im_rgb.save(feat_path, "PNG", optimize=True)
    print(f"Saved: {feat_path} ({os.path.getsize(feat_path)} bytes)")


def create_screenshots():
    """Generates phone, 7-inch tablet, and 10-inch tablet screenshots with marketing headlines."""
    print("Generating styled multi-form-factor screenshots...")

    configs = [
        {
            "src": "01_main_dashboard_with_drinks.png",
            "fallback": "01_main_dashboard.png",
            "filename": "01_bac_dashboard.png",
            "tag": "LIVE BAC & SOBRIETY ESTIMATOR",
            "headline": "Know Where You Stand",
            "sub": "Real-time BAC gauge, hydration check & sober time forecast."
        },
        {
            "src": "02_log_drinks.png",
            "fallback": "02_log_drinks.png",
            "filename": "02_log_drinks.png",
            "tag": "1-TAP QUICK LOGGING",
            "headline": "Track Drinks & Tab in Seconds",
            "sub": "Beer, wine, cocktails, shots & water with instant cost tracking."
        },
        {
            "src": "05_live_share.png",
            "fallback": "05_live_share.png",
            "filename": "03_live_share.png",
            "tag": "LIVE FRIEND TRACKER",
            "headline": "Share Your Night Live",
            "sub": "Live map, venue check-in & battery level. No app needed for friends."
        },
        {
            "src": "06_rides.png",
            "fallback": "06_rides.png",
            "filename": "04_get_home_safe.png",
            "tag": "RIDE & EMERGENCY SAFETY",
            "headline": "Get Home Safe",
            "sub": "One-tap Uber & Lyft summon plus instant trusted contact dialing."
        },
        {
            "src": "04_health.png",
            "fallback": "04_health.png",
            "filename": "05_health_insights.png",
            "tag": "SMART RECOVERY",
            "headline": "Feel Better Tomorrow",
            "sub": "Hangover risk warnings, smart hydration nudges & recovery tips."
        },
        {
            "src": "03_calendar.png",
            "fallback": "03_calendar.png",
            "filename": "06_calendar_history.png",
            "tag": "NIGHT HISTORY & HABITS",
            "headline": "Look Back & Track Spending",
            "sub": "Simple calendar history, sober streaks & monthly spending overview."
        }
    ]

    for item in configs:
        src_file = os.path.join(PHONE_SRC_DIR, item["src"])
        if not os.path.exists(src_file):
            src_file = os.path.join(PHONE_SRC_DIR, item["fallback"])
        if not os.path.exists(src_file):
            print(f"Skipping {item['filename']} — source not found")
            continue

        raw_im = Image.open(src_file).convert("RGBA")

        # 1. PHONE SCREENSHOT: 1080 x 1920 (9:16 aspect ratio, within Google Play 2:1 limit)
        pw, ph = 1080, 1920
        p_canvas = Image.new("RGBA", (pw, ph), (15, 23, 42, 255)) # #0F172A
        p_draw = ImageDraw.Draw(p_canvas)

        # Gradient
        for y in range(ph):
            factor = y / ph
            r = int(15 + (25 - 15) * factor)
            g = int(23 + (38 - 23) * factor)
            b = int(42 + (65 - 42) * factor)
            p_draw.line([(0, y), (pw, y)], fill=(r, g, b, 255))

        # Amber accent line at top
        p_draw.line([(0, 0), (pw, 0)], fill=(245, 158, 11, 255), width=6)

        # Header Typography
        font_tag = get_font(FONT_BOLD, 22)
        font_head = get_font(FONT_BOLD, 46)
        font_sub = get_font(FONT_SEMI, 26)

        p_draw.text((60, 75), item["tag"], font=font_tag, fill=(245, 158, 11, 255))
        p_draw.text((60, 112), item["headline"], font=font_head, fill=(255, 255, 255, 255))
        p_draw.text((60, 175), item["sub"], font=font_sub, fill=(203, 213, 225, 255))

        # Device Mockup inside screenshot
        target_h = 1580
        scale = target_h / raw_im.height
        target_w = int(raw_im.width * scale)
        scaled_app = raw_im.resize((target_w, target_h), Image.Resampling.LANCZOS)

        # Bezel frame
        bezel_pad = 12
        frame_w = target_w + bezel_pad * 2
        frame_h = target_h + bezel_pad * 2
        bezel = Image.new("RGBA", (frame_w, frame_h), (0, 0, 0, 0))
        bdraw = ImageDraw.Draw(bezel)
        bdraw.rounded_rectangle([0, 0, frame_w - 1, frame_h - 1], radius=44, fill=(30, 41, 59, 255), outline=(71, 85, 105, 255), width=3)

        app_mask = Image.new("L", (target_w, target_h), 0)
        ImageDraw.Draw(app_mask).rounded_rectangle([0, 0, target_w, target_h], radius=34, fill=255)
        bezel.paste(scaled_app, (bezel_pad, bezel_pad), app_mask)

        # Center device horizontally, place at bottom
        center_x = (pw - frame_w) // 2
        top_y = 260

        # Drop shadow
        shadow = Image.new("RGBA", (frame_w + 60, frame_h + 60), (0, 0, 0, 0))
        ImageDraw.Draw(shadow).rounded_rectangle([30, 30, frame_w + 30, frame_h + 30], radius=48, fill=(0, 0, 0, 180))
        shadow = shadow.filter(ImageFilter.GaussianBlur(25))

        p_canvas.paste(shadow, (center_x - 30, top_y - 20), shadow)
        p_canvas.paste(bezel, (center_x, top_y), bezel)

        # Save Phone Screenshot (24-bit RGB)
        phone_out = os.path.join(SCREEN_PHONE_DIR, item["filename"])
        p_rgb = Image.new("RGB", (pw, ph), (15, 23, 42))
        p_rgb.paste(p_canvas, mask=p_canvas.split()[3])
        p_rgb.save(phone_out, "PNG", optimize=True)

        # 2. SEVEN-INCH TABLET SCREENSHOT (1200 x 1920)
        t7_w, t7_h = 1200, 1920
        t7_canvas = Image.new("RGB", (t7_w, t7_h), (15, 23, 42))
        # Paste centered phone mockup with tablet margins
        t7_canvas.paste(p_rgb, ((t7_w - pw) // 2, 0))
        t7_out = os.path.join(SCREEN_7IN_DIR, item["filename"])
        t7_canvas.save(t7_out, "PNG", optimize=True)

        # 3. TEN-INCH TABLET SCREENSHOT (1600 x 2560)
        t10_w, t10_h = 1600, 2560
        t10_canvas = Image.new("RGB", (t10_w, t10_h), (15, 23, 42))
        # Resize high-res phone canvas smoothly into 10-inch format
        p_large = p_rgb.resize((int(pw * (t10_h / ph)), t10_h), Image.Resampling.LANCZOS)
        offset_x = (t10_w - p_large.width) // 2
        t10_canvas.paste(p_large, (offset_x, 0))
        t10_out = os.path.join(SCREEN_10IN_DIR, item["filename"])
        t10_canvas.save(t10_out, "PNG", optimize=True)

    print("Finished generating all phone, 7-inch, and 10-inch screenshots!")


def create_wear_screenshots():
    """Generates Play Store compliant 24-bit RGB Wear OS screenshots."""
    print("Generating Wear OS screenshots...")
    if not os.path.exists(WEAR_SRC_DIR):
        print("No Wear OS screenshots found in source dir.")
        return

    import glob
    wear_files = sorted(glob.glob(os.path.join(WEAR_SRC_DIR, "*.png")))
    for src in wear_files:
        raw = Image.open(src).convert("RGBA")
        # Ensure 454x454 or larger 1:1 ratio
        w, h = raw.size
        # Google Play requires no transparency on screenshots — paste over black background
        canvas = Image.new("RGB", (w, h), (0, 0, 0))
        canvas.paste(raw, mask=raw.split()[3])

        out_path = os.path.join(SCREEN_WEAR_DIR, os.path.basename(src))
        canvas.save(out_path, "PNG", optimize=True)
        print(f"Saved Wear screenshot: {out_path} ({w}x{h})")


if __name__ == "__main__":
    create_app_icon()
    create_feature_graphic()
    create_screenshots()
    create_wear_screenshots()
