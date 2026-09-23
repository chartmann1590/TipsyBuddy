#!/usr/bin/env python3
"""
Builds and uploads both Phone and Wear OS release AABs directly to Google Play's
Internal Testing track using the Android Publisher API with resumable chunking.
"""

import glob
import json
import os
import socket
import sys

# Set generous socket timeout for large file uploads
socket.setdefaulttimeout(600)

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

PACKAGE_NAME = "com.tipsybuddy.app"
SCOPES = ["https://www.googleapis.com/auth/androidpublisher"]
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KEY_FILE = os.path.join(r"H:\google-play\.secrets", "play-console-fullaccess-key.json")


def get_service():
    if not os.path.exists(KEY_FILE):
        raise FileNotFoundError(f"Service account key not found at {KEY_FILE}")
    creds = service_account.Credentials.from_service_account_file(KEY_FILE, scopes=SCOPES)
    return build("androidpublisher", "v3", credentials=creds)


def find_aab(subfolder):
    pattern = os.path.join(BASE_DIR, subfolder, "build", "outputs", "bundle", "release", "*.aab")
    matches = glob.glob(pattern)
    if not matches:
        return None
    return matches[0]


def upload_bundle_chunked(service, edit_id, aab_path, label):
    size_mb = os.path.getsize(aab_path) / (1024 * 1024)
    print(f"\nUploading {label}: {os.path.basename(aab_path)} ({size_mb:.2f} MB)...", flush=True)

    # 5MB chunks to avoid socket timeouts
    media = MediaFileUpload(aab_path, mimetype="application/octet-stream", chunksize=5 * 1024 * 1024, resumable=True)
    request = service.edits().bundles().upload(packageName=PACKAGE_NAME, editId=edit_id, media_body=media)

    response = None
    while response is None:
        status, response = request.next_chunk(num_retries=5)
        if status:
            pct = int(status.progress() * 100)
            print(f"  [{label}] Progress: {pct}%", flush=True)

    vc = response["versionCode"]
    print(f"  [{label}] Upload complete! VersionCode: {vc}", flush=True)
    return vc


def main():
    service = get_service()

    phone_aab = find_aab("app")
    wear_aab = find_aab("wear")

    if not phone_aab:
        print("ERROR: Phone AAB not found in app/build/outputs/bundle/release", file=sys.stderr)
        sys.exit(1)

    print(f"Found Phone AAB: {phone_aab}")
    if wear_aab:
        print(f"Found Wear AAB:  {wear_aab}")
    else:
        print("WARNING: Wear AAB not found", file=sys.stderr)

    print("\nCreating new Google Play edit...", flush=True)
    edit = service.edits().insert(packageName=PACKAGE_NAME, body={}).execute()
    edit_id = edit["id"]
    print(f"Active Edit ID: {edit_id}", flush=True)

    version_codes = []

    # 1. Upload Phone AAB
    phone_vc = upload_bundle_chunked(service, edit_id, phone_aab, "Phone")
    version_codes.append(phone_vc)

    # 2. Upload Wear AAB
    if wear_aab:
        wear_vc = upload_bundle_chunked(service, edit_id, wear_aab, "Wear OS")
        version_codes.append(wear_vc)

    # 3. Create Release on Internal Track (Phone)
    print(f"\nAdding Phone release (VC {phone_vc}) to 'internal' track...", flush=True)
    phone_release = {
        "track": "internal",
        "releases": [
            {
                "name": f"TipsyBuddy 1.0.0 ({phone_vc})",
                "versionCodes": [str(phone_vc)],
                "status": "completed",
                "releaseNotes": [
                    {
                        "language": "en-US",
                        "text": "TipsyBuddy initial internal testing release (Phone)."
                    }
                ]
            }
        ]
    }
    service.edits().tracks().update(
        packageName=PACKAGE_NAME,
        editId=edit_id,
        track="internal",
        body=phone_release
    ).execute()
    print("Internal track updated successfully!", flush=True)

    # 4. Create Release on Wear OS Internal Track
    if wear_aab:
        print(f"\nAdding Wear OS release (VC {wear_vc}) to 'wear:internal' track...", flush=True)
        wear_release = {
            "track": "wear:internal",
            "releases": [
                {
                    "name": f"TipsyBuddy Wear OS 1.0.0 ({wear_vc})",
                    "versionCodes": [str(wear_vc)],
                    "status": "completed",
                    "releaseNotes": [
                        {
                            "language": "en-US",
                            "text": "TipsyBuddy initial Wear OS internal testing release."
                        }
                    ]
                }
            ]
        }
        service.edits().tracks().update(
            packageName=PACKAGE_NAME,
            editId=edit_id,
            track="wear:internal",
            body=wear_release
        ).execute()
        print("Wear OS internal track updated successfully!", flush=True)

    # 5. Commit edit
    print("\nCommitting edit to Google Play...", flush=True)
    commit_resp = service.edits().commit(packageName=PACKAGE_NAME, editId=edit_id).execute()
    print("SUCCESS: Google Play Internal Testing releases committed!", flush=True)
    print(f"Committed Release: {commit_resp}", flush=True)


if __name__ == "__main__":
    main()
