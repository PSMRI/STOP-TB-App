#!/usr/bin/env python3
"""List or download a StopTB APK from Firebase App Distribution."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


FAD_BASE = "https://firebaseappdistribution.googleapis.com/v1"
META_RE = re.compile(
    r"stoptb-meta:\s*env=(?P<env>uat|prod)\s+signing=(?P<signing>signed|debug)"
    r"\s+package=(?P<package>\S+)\s+versionName=(?P<versionName>\S+)"
    r"\s+versionCode=(?P<versionCode>\S+)",
    re.IGNORECASE,
)


def log(message: str) -> None:
    print(message, flush=True)


def graph_token() -> str:
    raw = os.environ.get("FIREBASE_SERVICE_ACCOUNT_JSON", "").strip()
    if not raw:
        raise RuntimeError("FIREBASE_SERVICE_ACCOUNT_JSON is required")
    info = json.loads(raw)
    from google.oauth2 import service_account
    from google.auth.transport.requests import Request

    creds = service_account.Credentials.from_service_account_info(
        info,
        scopes=["https://www.googleapis.com/auth/cloud-platform"],
    )
    creds.refresh(Request())
    if not creds.token:
        raise RuntimeError("Failed to mint a Firebase/Google access token")
    return creds.token


def project_and_app(app_id: str) -> tuple[str, str]:
    parts = app_id.split(":")
    if len(parts) < 4 or parts[0] != "1":
        raise RuntimeError(f"Unexpected FIREBASE_APP_ID format: {app_id}")
    return parts[1], app_id


def fad_request(token: str, url: str) -> dict[str, Any]:
    request = urllib.request.Request(url, method="GET")
    request.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(request) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        details = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Firebase App Distribution GET {url} failed ({exc.code}): {details}") from exc


def parse_notes(text: str) -> dict[str, str]:
    meta: dict[str, str] = {}
    match = META_RE.search(text or "")
    if match:
        meta.update({key: value.lower() if key in {"env", "signing"} else value for key, value in match.groupdict().items()})
        return meta
    env_match = re.search(r"^Environment:\s*(UAT|PROD)\s*$", text or "", re.I | re.M)
    sign_match = re.search(r"^Signing:\s*(signed|debug)\s*$", text or "", re.I | re.M)
    pkg_match = re.search(r"^Package:\s*(\S+)\s*$", text or "", re.I | re.M)
    if env_match:
        meta["env"] = env_match.group(1).lower()
    if sign_match:
        meta["signing"] = sign_match.group(1).lower()
    if pkg_match:
        meta["package"] = pkg_match.group(1)
    return meta


def summarize(release: dict[str, Any]) -> dict[str, str]:
    notes = ((release.get("releaseNotes") or {}).get("text") or "").strip()
    meta = parse_notes(notes)
    return {
        "name": release.get("name") or "",
        "versionName": str(release.get("displayVersion") or meta.get("versionName") or ""),
        "versionCode": str(release.get("buildVersion") or meta.get("versionCode") or ""),
        "created": str(release.get("createTime") or ""),
        "env": meta.get("env") or "",
        "signing": meta.get("signing") or "",
        "package": meta.get("package") or "",
        "notes": notes.replace("\n", " | "),
        "download": release.get("binaryDownloadUri") or "",
    }


def list_releases(token: str, app_id: str) -> list[dict[str, Any]]:
    project_number, fad_app = project_and_app(app_id)
    releases: list[dict[str, Any]] = []
    page_token = ""
    while True:
        query = {"pageSize": "100", "orderBy": "createTime desc"}
        if page_token:
            query["pageToken"] = page_token
        url = (
            f"{FAD_BASE}/projects/{project_number}/apps/{urllib.parse.quote(fad_app, safe=':')}"
            f"/releases?{urllib.parse.urlencode(query)}"
        )
        payload = fad_request(token, url)
        releases.extend(payload.get("releases") or [])
        page_token = payload.get("nextPageToken") or ""
        if not page_token:
            break
    return releases


def print_catalog(releases: list[dict[str, Any]]) -> None:
    rows = [summarize(item) for item in releases]
    if not rows:
        log("No Firebase App Distribution releases found for this app.")
        return
    log("Firebase APK catalog (newest first):")
    log(
        f"{'versionName':<14} {'versionCode':<12} {'env':<6} {'signing':<8} {'package':<34} {'created'}"
    )
    for row in rows:
        log(
            f"{row['versionName']:<14} {row['versionCode']:<12} {row['env'] or '-':<6} "
            f"{row['signing'] or '-':<8} {row['package'] or '-':<34} {row['created']}"
        )


def pick_release(
    releases: list[dict[str, Any]],
    version_name: str,
    version_code: str,
) -> dict[str, Any]:
    matches = [
        item
        for item in releases
        if str(item.get("displayVersion") or "") == version_name
        and str(item.get("buildVersion") or "") == version_code
    ]
    if not matches:
        print_catalog(releases)
        raise RuntimeError(
            f"No Firebase release found for versionName={version_name} versionCode={version_code}."
        )
    matches.sort(key=lambda item: str(item.get("createTime") or ""), reverse=True)
    return matches[0]


def download_binary(token: str, url: str, destination: Path) -> None:
    request = urllib.request.Request(url, method="GET")
    request.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(request) as response:
            destination.write_bytes(response.read())
    except urllib.error.HTTPError:
        request = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(request) as response:
            destination.write_bytes(response.read())
    if destination.stat().st_size < 100:
        raise RuntimeError(f"Downloaded Firebase binary is too small: {destination}")


def download_release(args: argparse.Namespace) -> None:
    token = graph_token()
    releases = list_releases(token, args.app_id)
    chosen = pick_release(releases, args.version_name, args.version_code)
    summary = summarize(chosen)
    log(
        "Selected Firebase release: "
        f"env={summary['env'] or '?'} signing={summary['signing'] or '?'} "
        f"{summary['versionName']} ({summary['versionCode']}) package={summary['package'] or '?'}"
    )
    if args.require_signed and summary["signing"] == "debug":
        raise RuntimeError("This Firebase release is tagged debug. Intune only accepts signed APKs.")
    if args.expect_env and summary["env"] and summary["env"] != args.expect_env:
        raise RuntimeError(
            f"Firebase release environment is {summary['env']}, expected {args.expect_env}."
        )
    if args.expect_package and summary["package"] and summary["package"] != args.expect_package:
        raise RuntimeError(
            f"Firebase release package is {summary['package']}, expected {args.expect_package}."
        )
    download_uri = chosen.get("binaryDownloadUri") or ""
    if not download_uri:
        raise RuntimeError("Firebase release did not include binaryDownloadUri")
    destination = Path(args.output).resolve()
    destination.parent.mkdir(parents=True, exist_ok=True)
    download_binary(token, download_uri, destination)
    log(f"Downloaded APK to {destination} ({destination.stat().st_size} bytes)")
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as handle:
            handle.write(f"apk_path={destination}\n")
            handle.write(f"signing={summary['signing']}\n")
            handle.write(f"package_id={summary['package']}\n")
            handle.write(f"version_name={summary['versionName']}\n")
            handle.write(f"version_code={summary['versionCode']}\n")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--list", action="store_true")
    parser.add_argument("--download", action="store_true")
    parser.add_argument("--app-id", required=True)
    parser.add_argument("--version-name")
    parser.add_argument("--version-code")
    parser.add_argument("--output", default="firebase-app.apk")
    parser.add_argument("--require-signed", action="store_true")
    parser.add_argument("--expect-env", choices=["uat", "prod"])
    parser.add_argument("--expect-package")
    args = parser.parse_args()
    if args.download == args.list:
        parser.error("choose exactly one of --list or --download")
    if args.download and not (args.version_name and args.version_code):
        parser.error("--download requires --version-name and --version-code")
    return args


def main() -> None:
    args = parse_args()
    if args.list:
        print_catalog(list_releases(graph_token(), args.app_id))
        return
    download_release(args)


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:  # noqa: BLE001
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)
