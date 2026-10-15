#!/usr/bin/env python3
"""
Generates an official Google Play Policy Demonstration Video for TipsyBuddy:
- Target Permission: android.permission.FOREGROUND_SERVICE_LOCATION
- Declared Tasks:
    1. Background location updates
    2. User-initiated location sharing
- Visuals: 1920x1080 30fps presentation featuring authentic screenshots captured
           from the physical Google Pixel 8 Pro running TipsyBuddy.
- Audio: Ultra-natural neural voiceover via edge-tts (en-US-ChristopherNeural).
- Captions: Burned-in, high-contrast subtitle pill centered at the bottom of every frame.
- Output: 1080p MP4 formatted for YouTube and Google Play Policy appeal submission.
"""

import asyncio
import os
import subprocess
import sys
import textwrap
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import edge_tts

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS_DIR = os.path.join(BASE_DIR, "play-store-assets")
FGS_VIDEO_DIR = os.path.join(ASSETS_DIR, "fgs_policy_video")
SCENES_DIR = os.path.join(FGS_VIDEO_DIR, "scenes")
AUDIO_DIR = os.path.join(FGS_VIDEO_DIR, "audio")
TEMP_DIR = os.path.join(FGS_VIDEO_DIR, "temp")
OUTPUT_VIDEO = os.path.join(ASSETS_DIR, "tipsybuddy-foreground-service-location.mp4")

for d in [FGS_VIDEO_DIR, SCENES_DIR, AUDIO_DIR, TEMP_DIR]:
    os.makedirs(d, exist_ok=True)

FONT_BOLD = r"C:\Windows\Fonts\segoeuib.ttf"
FONT_SEMI = r"C:\Windows\Fonts\segoeui.ttf"
FONT_FALLBACK = r"C:\Windows\Fonts\arialbd.ttf"
FFMPEG = "ffmpeg"
FFPROBE = "ffprobe"
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
        "id": "01_intro_policy",
        "layout": "hero",
        "tag": "GOOGLE PLAY POLICY COMPLIANCE",
        "title": "Foreground Service Location",
        "sub": "Permission Demonstration for TipsyBuddy (com.tipsybuddy.app)",
        "bullets": [
            "Declared Task 1: User-initiated location sharing",
            "Declared Task 2: Background location updates",
            "Device: Google Pixel 8 Pro (Physical Device via ADB)",
            "Core Purpose: Real-time night out safety & friend harm reduction"
        ],
        "narration": "This video demonstrates TipsyBuddy's compliant usage of the Foreground Service Location permission. We will cover the two declared tasks: user-initiated location sharing and background location updates, captured directly on a physical Google Pixel 8 Pro.",
        "caption": "TipsyBuddy (com.tipsybuddy.app) — FOREGROUND_SERVICE_LOCATION Demonstration"
    },
    {
        "id": "02_user_initiated_sharing",
        "layout": "single_phone",
        "tag": "TASK 1: USER-INITIATED LOCATION SHARING",
        "title": "Explicit User Activation",
        "sub": "The user initiates location sharing and configures their night out session.",
        "bullets": [
            "1. User opens 'Venues & Live Sharing' screen",
            "2. User explicitly toggles on 'Live Location Share'",
            "3. Selects session duration (30m, 1h, 2h, or 4h)",
            "4. Unique web link is generated: tipsybuddy.web.app/?session=..."
        ],
        "screen": os.path.join(BASE_DIR, "screenshots", "fgs", "pixel8pro_after_toggle.png"),
        "narration": "In TipsyBuddy, location sharing is strictly user-initiated. Inside the Venues and Live Sharing screen, the user explicitly turns on Live Location Share, chooses their session duration, and generates a secure live link for their trusted contacts.",
        "caption": "Task 1: User explicitly initiates Live Location Share and sets session duration."
    },
    {
        "id": "03_persistent_notification",
        "layout": "single_phone",
        "tag": "USER TRANSPARENCY & ANDROID OS INDICATOR",
        "title": "Persistent Foreground Notification",
        "sub": "LiveShareService starts with type location and displays an ongoing notification.",
        "bullets": [
            "• LiveShareService declares android:foregroundServiceType='location'",
            "• Prominent notification: 'Live sharing active • Last synced • lat/lon'",
            "• Blue Android location indicator (GPS icon) active in status bar",
            "• Full transparency: User is always aware that location is syncing"
        ],
        "screen": os.path.join(BASE_DIR, "screenshots", "fgs", "pixel8pro_notifications.png"),
        "narration": "Immediately upon activation, TipsyBuddy starts an ongoing foreground service of type location. As shown in the notification shade, Android displays a persistent notification with the live sync timestamp and GPS coordinates, alongside the system location indicator.",
        "caption": "Ongoing Foreground Service notification and status bar location indicator."
    },
    {
        "id": "04_background_updates",
        "layout": "dual_phone",
        "tag": "TASK 2: BACKGROUND LOCATION UPDATES",
        "title": "Continuous Background Updates",
        "sub": "Location updates continue seamlessly when the phone is locked or app is minimized.",
        "bullets": [
            "• User minimizes TipsyBuddy and returns to Home Screen",
            "• Foreground service continues GPS sync loop in background",
            "• Pulling down the shade reveals updated sync timestamp (2:32 PM)",
            "• Friends retain active safety beacon while user travels"
        ],
        "screen_left": os.path.join(BASE_DIR, "screenshots", "fgs", "pixel8pro_home.png"),
        "screen_right": os.path.join(BASE_DIR, "screenshots", "fgs", "pixel8pro_bg_notif.png"),
        "label_left": "App Minimized (Home Screen)",
        "label_right": "Updated Timestamp in Background",
        "narration": "While out, users frequently lock their phone or switch to other apps. TipsyBuddy uses Foreground Service Location so GPS coordinates continue syncing in the background. Notice how the notification timestamp updates while the app is in the background.",
        "caption": "Task 2: Background location updates continue reliably while app is minimized."
    },
    {
        "id": "05_web_companion",
        "layout": "single_phone",
        "tag": "SAFETY RECIPIENT ENDPOINT",
        "title": "Live Web Companion for Friends",
        "sub": "Friends monitor live location, venue, battery, and safety status in any browser.",
        "bullets": [
            "• Works in any web browser without installing an app",
            "• Live OpenStreetMap tracking pin with current bar/venue",
            "• Real-time battery indicator & estimated BAC gauge",
            "• 1-Tap Uber & Lyft summon directly to friend's position"
        ],
        "screen": os.path.join(BASE_DIR, "screenshots", "phone", "05_live_share.png"),
        "narration": "Friends open the live web link in any browser without needing to install an app. They can view the user's real-time map pin, current venue, phone battery percentage, and access one-tap taxi summon buttons to ensure they get home safely.",
        "caption": "Recipient view: Friends track live position, venue, battery, and ride options."
    },
    {
        "id": "06_user_termination",
        "layout": "dual_phone",
        "tag": "COMPLETE USER CONTROL & REVOCATION",
        "title": "Instant Stop & Clean Cleanup",
        "sub": "Toggling off live share immediately terminates the service and clears the notification.",
        "bullets": [
            "• User has full control to stop sharing at any second",
            "• Sessions also terminate automatically when timer expires",
            "• LiveShareService stops immediately: GPS polling halts",
            "• Foreground notification is immediately dismissed from shade"
        ],
        "screen_left": os.path.join(BASE_DIR, "screenshots", "fgs", "pixel8pro_turned_off.png"),
        "screen_right": os.path.join(BASE_DIR, "screenshots", "fgs", "pixel8pro_notif_gone.png"),
        "label_left": "Live Share Toggled OFF",
        "label_right": "Notification & Service Dismissed",
        "narration": "The user retains total control and can stop sharing at any moment, or let the safety timer expire. Toggling sharing off immediately terminates the foreground service, stops all GPS queries, and removes the persistent notification completely.",
        "caption": "User control: Turning off live share terminates the service and dismisses notification."
    },
    {
        "id": "07_summary_compliance",
        "layout": "hero",
        "tag": "SUMMARY & POLICY VERIFICATION",
        "title": "Fully Compliant With Play Policy",
        "sub": "TipsyBuddy strictly adheres to Google Play Foreground Service Location requirements.",
        "bullets": [
            "User-Initiated Location Sharing: Explicitly started via in-app toggle",
            "Background Location Updates: Essential for continuous night-out safety",
            "Transparent: Ongoing notification with live timestamps & lat/long coordinates",
            "Complete User Control: Configurable duration timers & immediate stop toggle",
            "App ID: com.tipsybuddy.app | Verified on Google Pixel 8 Pro (Android 14/15)"
        ],
        "narration": "In summary, TipsyBuddy uses the Foreground Service Location permission solely for user-initiated sharing and vital background safety updates, adhering to all Google Play policies. Thank you for your review.",
        "caption": "TipsyBuddy: Compliant, transparent, user-controlled foreground service location."
    }
]

def render_scene_slide(scene_def):
    """Renders a 1920x1080 slide image with device mockup(s) and burned-in caption banner."""
    w, h = 1920, 1080
    im = Image.new("RGBA", (w, h), (11, 15, 25, 255))
    draw = ImageDraw.Draw(im)

    # High quality dark gradient background
    for y in range(h):
        factor = y / h
        r = int(10 + (20 - 10) * factor)
        g = int(14 + (28 - 14) * factor)
        b = int(24 + (44 - 24) * factor)
        draw.line([(0, y), (w, y)], fill=(r, g, b, 255))

    # Ambient subtle glow
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(glow)
    gdraw.ellipse([-100, 100, 700, 900], fill=(245, 158, 11, 28))
    gdraw.ellipse([1200, 0, 2000, 800], fill=(16, 185, 129, 24))
    glow = glow.filter(ImageFilter.GaussianBlur(80))
    im = Image.alpha_composite(im, glow)
    draw = ImageDraw.Draw(im)

    font_title = get_font(FONT_BOLD, 44)
    font_tag = get_font(FONT_BOLD, 22)
    font_sub = get_font(FONT_SEMI, 24)
    font_bullet = get_font(FONT_SEMI, 21)
    font_label = get_font(FONT_BOLD, 20)
    font_caption = get_font(FONT_BOLD, 30)

    layout = scene_def.get("layout", "single_phone")

    def draw_wrapped_text(drawer, text, font, x, y, max_w, line_spacing=6, fill=(203, 213, 225, 255)):
        words = text.split()
        lines = []
        cur_line = []
        for word in words:
            test_line = " ".join(cur_line + [word])
            if drawer.textlength(test_line, font=font) <= max_w:
                cur_line.append(word)
            else:
                if cur_line:
                    lines.append(" ".join(cur_line))
                cur_line = [word]
        if cur_line:
            lines.append(" ".join(cur_line))

        cur_y = y
        bbox = drawer.textbbox((0, 0), "Ag", font=font)
        line_h = (bbox[3] - bbox[1]) + line_spacing
        for line in lines:
            drawer.text((x, cur_y), line, font=font, fill=fill)
            cur_y += line_h
        return cur_y

    def create_framed_mockup(src_path, target_h=720):
        if not os.path.exists(src_path):
            return None
        raw = Image.open(src_path).convert("RGBA")
        scale = target_h / raw.height
        target_w = int(raw.width * scale)
        scaled = raw.resize((target_w, target_h), Image.Resampling.LANCZOS)

        pad = 8
        fw, fh = target_w + pad * 2, target_h + pad * 2
        frame = Image.new("RGBA", (fw, fh), (0, 0, 0, 0))
        fdraw = ImageDraw.Draw(frame)
        fdraw.rounded_rectangle([0, 0, fw - 1, fh - 1], radius=34, fill=(30, 41, 59, 255), outline=(71, 85, 105, 255), width=3)

        mask = Image.new("L", (target_w, target_h), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, target_w, target_h], radius=26, fill=255)
        frame.paste(scaled, (pad, pad), mask)

        shadow = Image.new("RGBA", (fw + 40, fh + 40), (0, 0, 0, 0))
        ImageDraw.Draw(shadow).rounded_rectangle([20, 20, fw + 20, fh + 20], radius=38, fill=(0, 0, 0, 190))
        shadow = shadow.filter(ImageFilter.GaussianBlur(20))

        return frame, shadow, fw, fh

    if layout == "hero":
        icon_path = os.path.join(ASSETS_DIR, "icon", "icon-512.png")
        if os.path.exists(icon_path):
            icon_raw = Image.open(icon_path).convert("RGBA").resize((130, 130), Image.Resampling.LANCZOS)
            imask = Image.new("L", (130, 130), 0)
            ImageDraw.Draw(imask).rounded_rectangle([0, 0, 130, 130], radius=32, fill=255)
            im.paste(icon_raw, ((w - 130) // 2, 105), imask)

        draw = ImageDraw.Draw(im)
        draw.text(((w - draw.textlength(scene_def["tag"], font=font_tag)) // 2, 255),
                  scene_def["tag"], font=font_tag, fill=(245, 158, 11, 255))
        draw.text(((w - draw.textlength(scene_def["title"], font=font_title)) // 2, 295),
                  scene_def["title"], font=font_title, fill=(255, 255, 255, 255))
        draw.text(((w - draw.textlength(scene_def["sub"], font=font_sub)) // 2, 355),
                  scene_def["sub"], font=font_sub, fill=(203, 213, 225, 255))

        # Card container for bullets
        bullets = scene_def.get("bullets", [])
        if bullets:
            card_w, card_h = 1040, 48 + len(bullets) * 44
            cx = (w - card_w) // 2
            cy = 425
            card = Image.new("RGBA", (card_w, card_h), (0, 0, 0, 0))
            cdraw = ImageDraw.Draw(card)
            cdraw.rounded_rectangle([0, 0, card_w - 1, card_h - 1], radius=24, fill=(21, 30, 48, 225), outline=(51, 65, 85, 255), width=2)
            im.paste(card, (cx, cy), card)

            draw = ImageDraw.Draw(im)
            for i, bullet in enumerate(bullets):
                by = cy + 24 + i * 44
                # Draw stylish green check circle
                draw.ellipse([cx + 36, by + 4, cx + 52, by + 20], fill=(16, 185, 129, 255))
                # Small white inner check mark dot
                draw.ellipse([cx + 42, by + 10, cx + 46, by + 14], fill=(255, 255, 255, 255))
                draw.text((cx + 64, by), bullet, font=font_bullet, fill=(241, 245, 249, 255))

    elif layout == "single_phone":
        # Left side: Text & Bullets (width ~ 900)
        draw.text((90, 140), scene_def["tag"], font=font_tag, fill=(245, 158, 11, 255))
        draw.text((90, 180), scene_def["title"], font=font_title, fill=(255, 255, 255, 255))
        next_y = draw_wrapped_text(draw, scene_def["sub"], font_sub, 90, 240, max_w=820, line_spacing=6)

        bullets = scene_def.get("bullets", [])
        if bullets:
            card_w, card_h = 850, 36 + len(bullets) * 48
            cx, cy = 90, max(330, next_y + 20)
            card = Image.new("RGBA", (card_w, card_h), (0, 0, 0, 0))
            cdraw = ImageDraw.Draw(card)
            cdraw.rounded_rectangle([0, 0, card_w - 1, card_h - 1], radius=20, fill=(21, 30, 48, 210), outline=(51, 65, 85, 255), width=2)
            im.paste(card, (cx, cy), card)

            draw = ImageDraw.Draw(im)
            for i, b in enumerate(bullets):
                draw.text((cx + 30, cy + 20 + i * 48), b, font=font_bullet, fill=(241, 245, 249, 255))

        # Right side: Single Phone Mockup (target_h = 760)
        res = create_framed_mockup(scene_def["screen"], target_h=760)
        if res:
            frame, shadow, fw, fh = res
            px = 1260
            py = 130
            im.paste(shadow, (px - 20, py - 20), shadow)
            im.paste(frame, (px, py), frame)

    elif layout == "dual_phone":
        # Left side: Text & Bullets (width ~ 740)
        draw.text((70, 130), scene_def["tag"], font=font_tag, fill=(245, 158, 11, 255))
        draw.text((70, 170), scene_def["title"], font=font_title, fill=(255, 255, 255, 255))
        next_y = draw_wrapped_text(draw, scene_def["sub"], font_sub, 70, 230, max_w=730, line_spacing=6)

        bullets = scene_def.get("bullets", [])
        if bullets:
            card_w, card_h = 740, 36 + len(bullets) * 48
            cx, cy = 70, max(325, next_y + 20)
            card = Image.new("RGBA", (card_w, card_h), (0, 0, 0, 0))
            cdraw = ImageDraw.Draw(card)
            cdraw.rounded_rectangle([0, 0, card_w - 1, card_h - 1], radius=20, fill=(21, 30, 48, 210), outline=(51, 65, 85, 255), width=2)
            im.paste(card, (cx, cy), card)

            draw = ImageDraw.Draw(im)
            for i, b in enumerate(bullets):
                draw.text((cx + 25, cy + 20 + i * 48), b, font=font_bullet, fill=(241, 245, 249, 255))

        # Right side: Two Phone Mockups side by side (target_h = 680)
        res_left = create_framed_mockup(scene_def["screen_left"], target_h=680)
        res_right = create_framed_mockup(scene_def["screen_right"], target_h=680)

        if res_left and res_right:
            fl, sl, fwl, fhl = res_left
            fr, sr, fwr, fhr = res_right

            px1 = 880
            px2 = px1 + fwl + 40
            py = 175

            # Labels above phones
            draw = ImageDraw.Draw(im)
            lbl_l = scene_def.get("label_left", "")
            lbl_r = scene_def.get("label_right", "")
            draw.text((px1 + (fwl - draw.textlength(lbl_l, font=font_label)) // 2, py - 38), lbl_l, font=font_label, fill=(245, 158, 11, 255))
            draw.text((px2 + (fwr - draw.textlength(lbl_r, font=font_label)) // 2, py - 38), lbl_r, font=font_label, fill=(16, 185, 129, 255))

            im.paste(sl, (px1 - 20, py - 20), sl)
            im.paste(fl, (px1, py), fl)

            im.paste(sr, (px2 - 20, py - 20), sr)
            im.paste(fr, (px2, py), fr)

    # BURNED-IN CAPTION PILL (Centered at bottom, y ~ 945)
    caption_text = scene_def["caption"]
    draw = ImageDraw.Draw(im)
    bbox = draw.textbbox((0, 0), caption_text, font=font_caption)
    tw = bbox[2] - bbox[0]
    th = bbox[3] - bbox[1]

    pill_pad_x = 36
    pill_pad_y = 16
    pill_w = tw + pill_pad_x * 2
    pill_h = th + pill_pad_y * 2
    pill_x = (w - pill_w) // 2
    pill_y = 945

    pill = Image.new("RGBA", (pill_w, pill_h), (0, 0, 0, 0))
    pdraw = ImageDraw.Draw(pill)
    pdraw.rounded_rectangle([0, 0, pill_w - 1, pill_h - 1], radius=pill_h // 2,
                            fill=(15, 23, 42, 235), outline=(245, 158, 11, 240), width=2)
    im.paste(pill, (pill_x, pill_y), pill)

    draw = ImageDraw.Draw(im)
    draw.text((pill_x + pill_pad_x, pill_y + pill_pad_y - 2), caption_text, font=font_caption, fill=(255, 255, 255, 255))

    out_path = os.path.join(SCENES_DIR, f"{scene_def['id']}.png")
    rgb = Image.new("RGB", (w, h), (11, 15, 25))
    rgb.paste(im, mask=im.split()[3])
    rgb.save(out_path, "PNG", optimize=True)
    return out_path

async def generate_narration(scene_def):
    """Generates crystal clear neural voiceover via edge-tts."""
    audio_path = os.path.join(AUDIO_DIR, f"{scene_def['id']}.mp3")
    if os.path.exists(audio_path) and os.path.getsize(audio_path) > 1000:
        return audio_path
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
    print("=== Generating TipsyBuddy Foreground Service Location Policy Video ===")
    clip_files = []

    for i, scene in enumerate(SCENE_DEFS, start=1):
        print(f"\n--- Scene {i}/{len(SCENE_DEFS)}: {scene['id']} ---")
        slide_img = render_scene_slide(scene)
        print(f"Rendered slide: {slide_img}")

        audio_file = await generate_narration(scene)
        duration = get_audio_duration(audio_file)
        print(f"Generated narration: {audio_file} (duration: {duration:.2f}s)")

        clip_dur = duration + 0.5
        clip_mp4 = os.path.join(TEMP_DIR, f"clip_{scene['id']}.mp4")

        # Encode scene to 1080p 30fps H.264 + AAC
        cmd = [
            FFMPEG, "-y",
            "-loop", "1", "-i", slide_img,
            "-i", audio_file,
            "-t", f"{clip_dur:.3f}",
            "-c:v", "libx264", "-tune", "stillimage", "-preset", "fast", "-crf", "18",
            "-c:a", "aac", "-b:a", "192k", "-ar", "44100",
            "-pix_fmt", "yuv420p",
            "-shortest",
            clip_mp4
        ]
        res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        if res.returncode != 0:
            print(f"FFmpeg error on scene {scene['id']}:\n{res.stderr}")
            sys.exit(1)

        clip_files.append(clip_mp4)
        print(f"Created clip: {clip_mp4}")

    # Concatenate all clips
    concat_list = os.path.join(TEMP_DIR, "concat.txt")
    with open(concat_list, "w", encoding="utf-8") as f:
        for clip in clip_files:
            clean_path = clip.replace("\\", "/")
            f.write(f"file '{clean_path}'\n")

    print("\n--- Assembling Final Demonstration Video ---")
    final_cmd = [
        FFMPEG, "-y",
        "-f", "concat", "-safe", "0",
        "-i", concat_list,
        "-c", "copy",
        OUTPUT_VIDEO
    ]
    res = subprocess.run(final_cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if res.returncode != 0:
        print(f"FFmpeg concat error:\n{res.stderr}")
        sys.exit(1)

    print(f"\n[OK] Video successfully generated at:\n{OUTPUT_VIDEO}")
    total_dur = sum(get_audio_duration(os.path.join(AUDIO_DIR, f"{s['id']}.mp3")) + 0.5 for s in SCENE_DEFS)
    size_mb = os.path.getsize(OUTPUT_VIDEO) / (1024 * 1024)
    print(f"Total Duration: {total_dur:.1f}s | File Size: {size_mb:.2f} MB")

if __name__ == "__main__":
    asyncio.run(main())
