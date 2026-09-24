#!/usr/bin/env python3
"""
Publishes the full Google Play store listings (multi-language titles, descriptions,
icon, feature graphic, promo video, and phone/7in/10in screenshots) via the
Android Publisher API.

Runs after the release AAB has been uploaded and committed in the GitHub Actions workflow.
Reads GOOGLE_PLAY_SERVICE_ACCOUNT_JSON from environment or fallback key file.
"""

import glob
import json
import os
import sys
import google.auth.transport.requests
import requests
from google.oauth2 import service_account

PACKAGE_NAME = "com.tipsybuddy.app"
SCOPES = ["https://www.googleapis.com/auth/androidpublisher"]
API_BASE = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE_NAME}"
UPLOAD_BASE = f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{PACKAGE_NAME}"

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS_DIR = os.path.join(BASE_DIR, "play-store-assets")
FALLBACK_KEY_FILE = r"H:\google-play\.secrets\play-console-fullaccess-key.json"


def get_credentials():
    sa_json = os.environ.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON")
    if sa_json:
        try:
            sa_info = json.loads(sa_json)
            return service_account.Credentials.from_service_account_info(sa_info, scopes=SCOPES)
        except Exception as e:
            print(f"Error parsing GOOGLE_PLAY_SERVICE_ACCOUNT_JSON: {e}", file=sys.stderr)

    if os.path.exists(FALLBACK_KEY_FILE):
        print(f"Using local credentials from {FALLBACK_KEY_FILE}")
        return service_account.Credentials.from_service_account_file(FALLBACK_KEY_FILE, scopes=SCOPES)

    raise ValueError("No Google Play service account credentials provided.")


def get_access_token() -> str:
    creds = get_credentials()
    creds.refresh(google.auth.transport.requests.Request())
    return creds.token


def check(response: requests.Response, action: str) -> None:
    if not response.ok:
        print(f"FAILED: {action}", file=sys.stderr)
        print(f"  status: {response.status_code}", file=sys.stderr)
        print(f"  body: {response.text}", file=sys.stderr)
        response.raise_for_status()
    print(f"OK: {action}")


def main() -> None:
    token = get_access_token()
    headers = {"Authorization": f"Bearer {token}"}

    print(f"Creating edit for package {PACKAGE_NAME}...")
    r = requests.post(f"{API_BASE}/edits", headers=headers, json={})
    check(r, "create edit")
    edit_id = r.json()["id"]
    print(f"Active Edit ID: {edit_id}")

    # Load all localized listings
    listings_path = os.path.join(ASSETS_DIR, "listings.json")
    with open(listings_path, "r", encoding="utf-8") as f:
        all_listings = json.load(f)

    # Optional promo video YouTube link if configured
    promo_video_url = os.environ.get("PROMO_VIDEO_URL", "")

    # 1. Update text listings for all languages
    for locale, data in all_listings.items():
        payload = {
            "language": locale,
            "title": data["title"],
            "shortDescription": data["shortDescription"],
            "fullDescription": data["fullDescription"],
        }
        if promo_video_url:
            payload["video"] = promo_video_url

        r = requests.put(
            f"{API_BASE}/edits/{edit_id}/listings/{locale}",
            headers=headers,
            json=payload,
        )
        check(r, f"update store listing text for [{locale}]")

    # 2. Upload images for default locale (en-US)
    locale = "en-US"

    def clear_images(image_type: str) -> None:
        r = requests.delete(f"{API_BASE}/edits/{edit_id}/listings/{locale}/{image_type}", headers=headers)
        check(r, f"clear existing {image_type}")

    def upload_image(image_type: str, path: str) -> None:
        with open(path, "rb") as f:
            data = f.read()
        r = requests.post(
            f"{UPLOAD_BASE}/edits/{edit_id}/listings/{locale}/{image_type}",
            headers={**headers, "Content-Type": "image/png"},
            data=data,
        )
        check(r, f"upload {image_type}: {os.path.basename(path)}")

    for image_type in ["icon", "featureGraphic", "phoneScreenshots", "sevenInchScreenshots", "tenInchScreenshots", "wearScreenshots"]:
        try:
            clear_images(image_type)
        except Exception as e:
            print(f"Note on clear {image_type}: {e}")

    icon_path = os.path.join(ASSETS_DIR, "icon", "icon-512.png")
    if os.path.exists(icon_path):
        upload_image("icon", icon_path)

    feat_path = os.path.join(ASSETS_DIR, "feature-graphic.png")
    if os.path.exists(feat_path):
        upload_image("featureGraphic", feat_path)

    for image_type, folder in [
        ("phoneScreenshots", "phone"),
        ("sevenInchScreenshots", "sevenInch"),
        ("tenInchScreenshots", "tenInch"),
        ("wearScreenshots", "wear"),
    ]:
        paths = sorted(glob.glob(os.path.join(ASSETS_DIR, "screenshots", folder, "*.png")))
        for path in paths:
            upload_image(image_type, path)

    # 3. Commit the listing edit
    print("Committing store listing edit...")
    r = requests.post(f"{API_BASE}/edits/{edit_id}:commit", headers=headers)
    check(r, "commit listing edit")
    print("\nSUCCESS: All store listings and graphic assets committed to Google Play!")


if __name__ == "__main__":
    main()
