#!/usr/bin/env python3
"""
Creates and activates the Ad-Free monthly subscription product in Google Play Console
using the Android Publisher API (Monetization Subscriptions v3).
"""

import json
import os
import sys
from google.oauth2 import service_account
from googleapiclient.discovery import build

PACKAGE_NAME = "com.tipsybuddy.app"
SUBSCRIPTION_ID = "tipsybuddy_ad_free_monthly"
BASE_PLAN_ID = "monthly-plan"
SCOPES = ["https://www.googleapis.com/auth/androidpublisher"]

KEY_FILE = os.environ.get("GOOGLE_PLAY_KEY_FILE")
if not KEY_FILE:
    raise RuntimeError(
        "Environment variable GOOGLE_PLAY_KEY_FILE is not set. "
        "Please set it to the path of the Google Play service account key file."
    )


def get_service():
    if not os.path.exists(KEY_FILE):
        raise FileNotFoundError(f"Service account key not found at {KEY_FILE}")
    creds = service_account.Credentials.from_service_account_file(KEY_FILE, scopes=SCOPES)
    return build("androidpublisher", "v3", credentials=creds)


def create_or_update_subscription():
    service = get_service()
    subs_service = service.monetization().subscriptions()

    # 1. Check if subscription already exists
    existing = None
    try:
        existing = subs_service.get(packageName=PACKAGE_NAME, productId=SUBSCRIPTION_ID).execute()
        print(f"Subscription '{SUBSCRIPTION_ID}' already exists: state={existing.get('state')}")
    except Exception as e:
        print(f"Subscription '{SUBSCRIPTION_ID}' not yet created ({e}). Creating new subscription...")

    if not existing:
        sub_body = {
            "packageName": PACKAGE_NAME,
            "productId": SUBSCRIPTION_ID,
            "listings": [
                {
                    "languageCode": "en-US",
                    "title": "TipsyBuddy Ad-Free Monthly",
                    "description": "Remove all banner and interstitial ads across TipsyBuddy.",
                    "benefits": [
                        "100% ad-free experience",
                        "Uninterrupted night-out drink tracking",
                        "Support ongoing development"
                    ]
                }
            ],
            "basePlans": [
                {
                    "basePlanId": BASE_PLAN_ID,
                    "autoRenewingBasePlanType": {
                        "billingPeriodDuration": "P1M",
                        "prorationMode": "SUBSCRIPTION_PRORATION_MODE_CHARGE_ON_NEXT_BILLING_DATE",
                        "resubscribeState": "RESUBSCRIBE_STATE_ACTIVE"
                    },
                    "regionalConfigs": [
                        {
                            "regionCode": "US",
                            "newSubscriberAvailability": True,
                            "price": {
                                "currencyCode": "USD",
                                "units": "1",
                                "nanos": 990000000
                            }
                        }
                    ],
                    "otherRegionsConfig": {
                        "usdPrice": {
                            "currencyCode": "USD",
                            "units": "1",
                            "nanos": 990000000
                        },
                        "eurPrice": {
                            "currencyCode": "EUR",
                            "units": "1",
                            "nanos": 990000000
                        },
                        "newSubscriberAvailability": True
                    }
                }
            ],
            "taxAndComplianceSettings": {
                "eeaWithdrawalRightType": "WITHDRAWAL_RIGHT_SERVICE"
            }
        }

        created = subs_service.create(
            packageName=PACKAGE_NAME,
            productId=SUBSCRIPTION_ID,
            body=sub_body,
            regions_version="2022/02"
        ).execute()
        print(f"SUCCESS: Created subscription '{SUBSCRIPTION_ID}'!")
        print(json.dumps(created, indent=2))

    # 2. Activate base plan if inactive/draft
    try:
        print(f"Activating base plan '{BASE_PLAN_ID}'...")
        activate_resp = subs_service.basePlans().activate(
            packageName=PACKAGE_NAME,
            productId=SUBSCRIPTION_ID,
            basePlanId=BASE_PLAN_ID,
            body={}
        ).execute()
        print(f"SUCCESS: Base plan '{BASE_PLAN_ID}' activated!")
        print(json.dumps(activate_resp, indent=2))
    except Exception as e:
        print(f"Base plan activation note: {e}")

    # 3. Retrieve final subscription details
    sub_final = subs_service.get(packageName=PACKAGE_NAME, productId=SUBSCRIPTION_ID).execute()
    print("\n--- FINAL SUBSCRIPTION CONFIGURATION ---")
    print(f"Product ID: {sub_final.get('productId')}")
    for bp in sub_final.get("basePlans", []):
        print(f"Base Plan ID: {bp.get('basePlanId')}, State: {bp.get('state')}")
    print("----------------------------------------")


if __name__ == "__main__":
    create_or_update_subscription()