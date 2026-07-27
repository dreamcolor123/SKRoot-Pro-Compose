#!/usr/bin/env python3
"""Validate branding values used by the manual GitHub Actions build."""
from __future__ import annotations

import argparse
import re
import sys

APPLICATION_ID = re.compile(r"^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$")
RELEASE_TAG = re.compile(r"^v?[0-9A-Za-z][0-9A-Za-z._-]{0,63}$")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--application-id", required=True)
    parser.add_argument("--app-name", required=True)
    parser.add_argument("--release-tag")
    args = parser.parse_args()

    application_id = args.application_id.strip()
    if not APPLICATION_ID.fullmatch(application_id) or len(application_id) > 255:
        parser.error(
            "application_id must use Android syntax, for example com.example.app; "
            "only lowercase letters, digits, underscores and dots are accepted"
        )

    app_name = args.app_name.strip()
    if (
        not app_name
        or len(app_name) > 80
        or any(ord(ch) < 0x20 for ch in app_name)
        or any(ch in "/\\" for ch in app_name)
    ):
        parser.error(
            "app_name must contain 1-80 printable characters without path separators"
        )

    if args.release_tag and not RELEASE_TAG.fullmatch(args.release_tag.strip()):
        parser.error("release_tag contains unsupported characters")

    print(f"validated application_id={application_id}")
    print(f"validated app_name_length={len(app_name)}")
    if args.release_tag:
        print(f"validated release_tag={args.release_tag.strip()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
