#!/usr/bin/env python3
"""
Generates a professional 1920x1080 Play Store promo video for TipsyBuddy:
- Voiceover: Ultra-natural neural voice generated via edge-tts (en-US-ChristopherNeural).
- Visuals: 1920x1080 storyboard slides featuring app mockups, feature cards, and branding.
- Burned-in Captions: Beautiful, high-contrast subtitle pill centered at the bottom.
- Video: H.264 / AAC 1080p MP4 formatted for YouTube and Google Play Store listings.
"""

import asyncio
import json
import os
import subprocess
import sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import edge_tts

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS_DIR = os.path.join(BASE_DIR, "play-store-assets")
VIDEO_DIR = os.path.join(ASSETS_DIR, "video")
SCENES_DIR = os.path.join(VIDEO_DIR, "scenes")
AUDIO_DIR = os.path.join(VIDEO_DIR, "audio")
TEMP_DIR = os.path.join(VIDEO_DIR, "temp")
OUTPUT_VIDEO = os.path.join(ASSETS_DIR, "tipsybuddy-promo.mp4")

for d in [VIDEO_DIR, SCENES_DIR, AUDIO_DIR, TEMP_DIR]:
    os.makedirs(d, exist_ok=True)

FONT_BOLD = r"C:\Windows\Fonts\segoeuib.ttf"
FONT_SEMI = r"C:\Windows\Fonts\segoeui.ttf"
FONT_FALLBACK = r"C:\Windows\Fonts\arialbd.ttf"
FFMPEG = r"C:\ProgramData\chocolatey\bin\ffmpeg.exe"
FFPROBE = r"C:\ProgramData\chocolatey\bin\ffprobe.exe"
VOICE = "en-US-ChristopherNeural"

def get_font(path, size):
    try:
        return ImageFont.truetype(path, size)
    except Exception:
        try:
            return ImageFont.truetype(FONT_FALLBACK, size)
        except Exception:
            return ImageFont.load_default()

SCENE_DEFS = [
    {
        "id": "01_intro",
        "title": "Party Smart, Get Home Safe",
        "tag": "MEET TIPSYBUDDY",
        "sub": "Your smart night-out wingman & safety companion.",
        "screen": None, # Logo / Hero slide
        "narration": "Meet TipsyBuddy, your smart night-out wingman and safety companion.",
        "caption": "Meet TipsyBuddy — your smart night-out wingman & safety companion."
    },
    {
        "id": "02_bac",
        "title": "Know Where You Stand",
        "tag": "LIVE BAC & SOBRIETY ESTIMATOR",
        "sub": "Real-time blood alcohol gauge, hydration tracker & sober countdown.",
        "screen": "01_main_dashboard_with_drinks.png",
        "narration": "Know where you stand at a glance. See your estimated blood alcohol content and know roughly when you'll feel sober again.",
        "caption": "Know where you stand with a real-time BAC gauge and sober countdown."
    },
    {
        "id": "03_log",
        "title": "Track Drinks in Seconds",
        "tag": "1-TAP QUICK LOGGING",
        "sub": "One-tap log for beer, wine, cocktails, shots, or water, with tab tracking.",
        "screen": "02_log_drinks.png",
        "narration": "Log drinks in a single tap. Keep an eye on your spending tab and get smart reminders to stay hydrated.",
        "caption": "Log drinks in one tap and keep track of your spending tab & hydration."
    },
    {
        "id": "04_live",
        "title": "Share Your Night Live",
        "tag": "FRIEND SAFETY TRACKER",
        "sub": "Send friends a live map link with venue & battery. Works in any browser.",
        "screen": "05_live_share.png",
        "narration": "Heading out with friends? Share a live tracking link. They can see your venue and battery level right from any browser — no app required.",
        "caption": "Share a live map link with friends — no app install needed."
    },
    {
        "id": "05_rides",
        "title": "Get Home Safe",
        "tag": "1-TAP RIDES & CONTACTS",
        "sub": "One-tap Uber and Lyft summon, plus instant trusted contact dialing.",
        "screen": "06_rides.png",
        "narration": "When it's time to call it a night, summon Uber or Lyft in one tap, or reach your emergency contact instantly.",
        "caption": "One-tap Uber & Lyft summon, plus instant emergency contact dialing."
    },
    {
        "id": "06_outro",
        "title": "Party Smart. Look Out For Friends.",
        "tag": "GET TIPSYBUDDY FREE",
        "sub": "Download free on Google Play. Built for safer nights out.",
        "screen": None,
        "narration": "Drink responsibly, look out for your friends, and always get home safe. Download TipsyBuddy on Google Play today.",
        "caption": "Party Smart. Get Home Safe. Download TipsyBuddy on Google Play."
    }
]


def render_scene_slide(scene_def):
    """Renders a 1920x1080 slide image with mockup and burned-in caption banner."""
    w, h = 1920, 1080
    im = Image.new("RGBA", (w, h), (11, 15, 25, 255))
    draw = ImageDraw.Draw(im)

    # Gradient background
    for y in range(h):
        factor = y / h
        r = int(11 + (22 - 11) * factor)
        g = int(15 + (32 - 15) * factor)
        b = int(25 + (48 - 25) * factor)
        draw.line([(0, y), (w, y)], fill=(r, g, b, 255))

    # Ambient light
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(glow)
    gdraw.ellipse([-100, 100, 700, 900], fill=(245, 158, 11, 35))
    gdraw.ellipse([1200, 0, 2000, 800], fill=(16, 185, 129, 28))
    glow = glow.filter(ImageFilter.GaussianBlur(80))
    im = Image.alpha_composite(im, glow)
    draw = ImageDraw.Draw(im)

    font_title = get_font(FONT_BOLD, 54)
    font_tag = get_font(FONT_BOLD, 24)
    font_sub = get_font(FONT_SEMI, 30)
    font_caption = get_font(FONT_BOLD, 32)

    screen_file = scene_def.get("screen")

    if screen_file:
        # Side-by-side layout: Text on left (width ~ 900), phone mockup on right (center ~ 1400)
        # 1. Left text
        draw.text((100, 220), scene_def["tag"], font=font_tag, fill=(245, 158, 11, 255))
        draw.text((100, 265), scene_def["title"], font=font_title, fill=(255, 255, 255, 255))
        draw.text((100, 350), scene_def["sub"], font=font_sub, fill=(203, 213, 225, 255))

        # 2. Right phone screen
        src_path = os.path.join(BASE_DIR, "screenshots", "phone", screen_file)
        if os.path.exists(src_path):
            raw_im = Image.open(src_path).convert("RGBA")
            target_h = 750
            scale = target_h / raw_im.height
            target_w = int(raw_im.width * scale)
            scaled = raw_im.resize((target_w, target_h), Image.Resampling.LANCZOS)

            pad = 10
            fw, fh = target_w + pad * 2, target_h + pad * 2
            frame = Image.new("RGBA", (fw, fh), (0, 0, 0, 0))
            fdraw = ImageDraw.Draw(frame)
            fdraw.rounded_rectangle([0, 0, fw - 1, fh - 1], radius=38, fill=(30, 41, 59, 255), outline=(71, 85, 105, 255), width=3)

            mask = Image.new("L", (target_w, target_h), 0)
            ImageDraw.Draw(mask).rounded_rectangle([0, 0, target_w, target_h], radius=28, fill=255)
            frame.paste(scaled, (pad, pad), mask)

            # Drop shadow
            shadow = Image.new("RGBA", (fw + 50, fh + 50), (0, 0, 0, 0))
            ImageDraw.Draw(shadow).rounded_rectangle([25, 25, fw + 25, fh + 25], radius=44, fill=(0, 0, 0, 200))
            shadow = shadow.filter(ImageFilter.GaussianBlur(24))

            px = 1260
            py = 140
            im.paste(shadow, (px - 25, py - 25), shadow)
            im.paste(frame, (px, py), frame)
    else:
        # Center hero layout (Intro / Outro)
        icon_path = os.path.join(ASSETS_DIR, "icon", "icon-512.png")
        if os.path.exists(icon_path):
            icon_raw = Image.open(icon_path).convert("RGBA").resize((160, 160), Image.Resampling.LANCZOS)
            imask = Image.new("L", (160, 160), 0)
            ImageDraw.Draw(imask).rounded_rectangle([0, 0, 160, 160], radius=38, fill=255)
            im.paste(icon_raw, ((w - 160) // 2, 170), imask)

        draw = ImageDraw.Draw(im)
        draw.text(((w - draw.textlength(scene_def["tag"], font=font_tag)) // 2, 360),
                  scene_def["tag"], font=font_tag, fill=(245, 158, 11, 255))
        draw.text(((w - draw.textlength(scene_def["title"], font=font_title)) // 2, 405),
                  scene_def["title"], font=font_title, fill=(255, 255, 255, 255))
        draw.text(((w - draw.textlength(scene_def["sub"], font=font_sub)) // 2, 485),
                  scene_def["sub"], font=font_sub, fill=(203, 213, 225, 255))

    # BURNED-IN CAPTION PILL (Centered at bottom, y ~ 940)
    caption_text = scene_def["caption"]
    draw = ImageDraw.Draw(im)
    bbox = draw.textbbox((0, 0), caption_text, font=font_caption)
    tw = bbox[2] - bbox[0]
    th = bbox[3] - bbox[1]

    pill_pad_x = 40
    pill_pad_y = 18
    pill_w = tw + pill_pad_x * 2
    pill_h = th + pill_pad_y * 2
    pill_x = (w - pill_w) // 2
    pill_y = 935

    # Pill background
    pill = Image.new("RGBA", (pill_w, pill_h), (0, 0, 0, 0))
    pdraw = ImageDraw.Draw(pill)
    pdraw.rounded_rectangle([0, 0, pill_w - 1, pill_h - 1], radius=pill_h // 2,
                            fill=(15, 23, 42, 225), outline=(245, 158, 11, 220), width=2)
    im.paste(pill, (pill_x, pill_y), pill)

    # Draw text
    draw = ImageDraw.Draw(im)
    draw.text((pill_x + pill_pad_x, pill_y + pill_pad_y - 2), caption_text, font=font_caption, fill=(255, 255, 255, 255))

    out_path = os.path.join(SCENES_DIR, f"{scene_def['id']}.png")
    # Convert to 24-bit RGB
    rgb = Image.new("RGB", (w, h), (11, 15, 25))
    rgb.paste(im, mask=im.split()[3])
    rgb.save(out_path, "PNG", optimize=True)
    return out_path


async def generate_narration(scene_def):
    """Generates audio narration via edge-tts."""
    audio_path = os.path.join(AUDIO_DIR, f"{scene_def['id']}.mp3")
    communicate = edge_tts.Communicate(scene_def["narration"], voice=VOICE, rate="+0%")
    await communicate.save(audio_path)
    return audio_path


def get_audio_duration(audio_path):
    """Gets audio duration in seconds using ffprobe."""
    cmd = [
        FFPROBE, "-v", "error", "-show_entries", "format=duration",
        "-of", "default=noprint_wrappers=1:nokey=1", audio_path
    ]
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, check=True)
    return float(res.stdout.strip())


async def main():
    print("=== Generating TipsyBuddy Google Play Promo Video ===")
    clip_files = []

    for i, scene in enumerate(SCENE_DEFS, start=1):
        print(f"\n--- Scene {i}/{len(SCENE_DEFS)}: {scene['id']} ---")
        slide_img = render_scene_slide(scene)
        print(f"Rendered slide: {slide_img}")

        audio_file = await generate_narration(scene)
        duration = get_audio_duration(audio_file)
        print(f"Generated narration: {audio_file} (duration: {duration:.2f}s)")

        # Pad clip duration slightly (0.4s) for natural rhythm
        clip_dur = duration + 0.4
        clip_out = os.path.join(TEMP_DIR, f"clip_{scene['id']}.mp4")

        # Encode single scene clip with ffmpeg
        # Loop image, encode with libx264, audio with aac, pad audio with apad or keep duration
        cmd = [
            FFMPEG, "-y",
            "-loop", "1", "-i", slide_img,
            "-i", audio_file,
            "-c:v", "libx264", "-tune", "stillimage",
            "-c:a", "aac", "-b:a", "192k",
            "-pix_fmt", "yuv420p",
            "-t", str(clip_dur),
            clip_out
        ]
        subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, check=True)
        print(f"Encoded clip: {clip_out}")
        clip_files.append(clip_out)

    # Concat clips into final promo video
    print("\nConcatenating clips into final promo video...")
    concat_list = os.path.join(TEMP_DIR, "concat_list.txt")
    with open(concat_list, "w", encoding="utf-8") as f:
        for clip in clip_files:
            # Escape path for ffmpeg concat demuxer
            clean_path = clip.replace("\\", "/")
            f.write(f"file '{clean_path}'\n")

    cmd = [
        FFMPEG, "-y",
        "-f", "concat", "-safe", "0",
        "-i", concat_list,
        "-c:v", "libx264", "-c:a", "aac",
        "-movflags", "+faststart",
        OUTPUT_VIDEO
    ]
    subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, check=True)

    size_mb = os.path.getsize(OUTPUT_VIDEO) / (1024 * 1024)
    total_dur = get_audio_duration(OUTPUT_VIDEO)
    print(f"\nSUCCESS! Generated promo video: {OUTPUT_VIDEO}")
    print(f"Duration: {total_dur:.1f}s | Size: {size_mb:.2f} MB")


if __name__ == "__main__":
    asyncio.run(main())
