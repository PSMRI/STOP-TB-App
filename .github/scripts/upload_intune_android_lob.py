#!/usr/bin/env python3
"""Upload a signed Android LOB APK to Intune and assign it to a group."""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.primitives.padding import PKCS7
from cryptography.hazmat.primitives.hmac import HMAC
from cryptography.hazmat.primitives import hashes


GRAPH_BASE = "https://graph.microsoft.com/v1.0"
LOB_TYPE = "microsoft.graph.androidLobApp"
CHUNK_SIZE = 1024 * 1024
INTUNE_APP_ROLES = (
    "DeviceManagementApps.ReadWrite.All",
    "DeviceManagementApps.Read.All",
)
INTUNE_PERMISSION_HELP = """
Intune returned Forbidden. The Entra app can sign in to Graph, but it is not
allowed to manage Intune LOB apps.

In Entra ID -> App registrations -> this app -> API permissions:
1. Add Microsoft Graph *Application* permission DeviceManagementApps.ReadWrite.All
   (not Delegated / not a group Object ID).
2. Click Grant admin consent for the tenant.
3. Wait a minute, then re-run the workflow.

Optionally assign the Enterprise application the Intune "Application Manager"
or "Intune Administrator" role if admin consent alone is not enough.
""".strip()


def log(message: str) -> None:
    print(message, flush=True)


def graph_token_claims(token: str) -> dict[str, Any]:
    try:
        payload = token.split(".")[1]
        payload += "=" * (-len(payload) % 4)
        return json.loads(base64.urlsafe_b64decode(payload))
    except Exception:  # noqa: BLE001
        return {}


def log_graph_token_grants(token: str) -> None:
    claims = graph_token_claims(token)
    roles = claims.get("roles") or []
    scopes = claims.get("scp") or ""
    app_id = claims.get("appid") or claims.get("azp") or "unknown"
    audience = claims.get("aud") or "unknown"
    log(
        "Graph token: "
        f"aud={audience} appid={app_id} roles={roles or '[]'} scp={scopes or '(none)'}"
    )
    if not any(role in roles for role in INTUNE_APP_ROLES):
        raise PermissionError(
            "Graph token is missing application role DeviceManagementApps.ReadWrite.All. "
            "Client-credential / OIDC tokens only include *Application* permissions "
            "that have admin consent.\n"
            f"{INTUNE_PERMISSION_HELP}"
        )


def graph_request(
    token: str,
    method: str,
    path: str,
    body: dict[str, Any] | None = None,
    raw_url: bool = False,
    extra_headers: dict[str, str] | None = None,
) -> Any:
    url = path if raw_url else f"{GRAPH_BASE}/{path.lstrip('/')}"
    data = None if body is None else json.dumps(body).encode("utf-8")
    request = urllib.request.Request(url, data=data, method=method)
    request.add_header("Authorization", f"Bearer {token}")
    request.add_header("Content-Type", "application/json")
    for key, value in (extra_headers or {}).items():
        request.add_header(key, value)
    try:
        with urllib.request.urlopen(request) as response:
            payload = response.read()
            if not payload:
                return {}
            return json.loads(payload.decode("utf-8"))
    except urllib.error.HTTPError as exc:
        details = exc.read().decode("utf-8", errors="replace")
        message = f"Graph {method} {url} failed ({exc.code}): {details}"
        if exc.code in {401, 403} or "Forbidden" in details:
            raise PermissionError(f"{message}\n{INTUNE_PERMISSION_HELP}") from exc
        raise RuntimeError(message) from exc


def get_token_from_client_secret(tenant_id: str, client_id: str, client_secret: str) -> str:
    url = f"https://login.microsoftonline.com/{tenant_id}/oauth2/v2.0/token"
    data = urllib.parse.urlencode(
        {
            "client_id": client_id,
            "client_secret": client_secret,
            "grant_type": "client_credentials",
            "scope": "https://graph.microsoft.com/.default",
        }
    ).encode("utf-8")
    request = urllib.request.Request(url, data=data, method="POST")
    request.add_header("Content-Type", "application/x-www-form-urlencoded")
    try:
        with urllib.request.urlopen(request) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        details = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Azure token request failed ({exc.code}): {details}") from exc
    token = payload.get("access_token")
    if not token:
        raise RuntimeError("Azure token response did not include access_token")
    return token


def encrypt_intune_file(source: Path, target: Path) -> dict[str, Any]:
    encryption_key = os.urandom(32)
    hmac_key = os.urandom(32)
    initialization_vector = os.urandom(16)
    hmac_length = 32

    padder = PKCS7(128).padder()
    encryptor = Cipher(algorithms.AES(encryption_key), modes.CBC(initialization_vector)).encryptor()
    plaintext = source.read_bytes()
    ciphertext = encryptor.update(padder.update(plaintext) + padder.finalize()) + encryptor.finalize()

    placeholder = bytes(hmac_length) + initialization_vector + ciphertext
    target.write_bytes(placeholder)

    hmac = HMAC(hmac_key, hashes.SHA256())
    hmac.update(target.read_bytes()[hmac_length:])
    mac = hmac.finalize()
    target.write_bytes(mac + initialization_vector + ciphertext)

    file_digest = hashlib.sha256(plaintext).digest()
    return {
        "fileEncryptionInfo": {
            "encryptionKey": base64.b64encode(encryption_key).decode("ascii"),
            "macKey": base64.b64encode(hmac_key).decode("ascii"),
            "initializationVector": base64.b64encode(initialization_vector).decode("ascii"),
            "mac": base64.b64encode(mac).decode("ascii"),
            "profileIdentifier": "ProfileVersion1",
            "fileDigest": base64.b64encode(file_digest).decode("ascii"),
            "fileDigestAlgorithm": "SHA256",
        }
    }


def upload_to_azure_blob(sas_uri: str, filepath: Path) -> None:
    data = filepath.read_bytes()
    chunk_ids: list[str] = []
    total = max(1, (len(data) + CHUNK_SIZE - 1) // CHUNK_SIZE)
    for index in range(total):
        block_id = base64.b64encode(f"{index:04d}".encode("ascii")).decode("ascii")
        chunk_ids.append(block_id)
        start = index * CHUNK_SIZE
        chunk = data[start : start + CHUNK_SIZE]
        separator = "&" if "?" in sas_uri else "?"
        url = f"{sas_uri}{separator}comp=block&blockid={urllib.parse.quote(block_id)}"
        request = urllib.request.Request(url, data=chunk, method="PUT")
        request.add_header("x-ms-blob-type", "BlockBlob")
        request.add_header("Content-Length", str(len(chunk)))
        try:
            urllib.request.urlopen(request).read()
        except urllib.error.HTTPError as exc:
            details = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"Azure blob chunk upload failed ({exc.code}): {details}") from exc
        log(f"Uploaded Intune blob chunk {index + 1}/{total}")

    block_list = "<?xml version=\"1.0\" encoding=\"utf-8\"?><BlockList>" + "".join(
        f"<Latest>{block_id}</Latest>" for block_id in chunk_ids
    ) + "</BlockList>"
    separator = "&" if "?" in sas_uri else "?"
    finalize_url = f"{sas_uri}{separator}comp=blocklist"
    request = urllib.request.Request(finalize_url, data=block_list.encode("utf-8"), method="PUT")
    request.add_header("Content-Type", "application/xml")
    try:
        urllib.request.urlopen(request).read()
    except urllib.error.HTTPError as exc:
        details = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Azure blob finalize failed ({exc.code}): {details}") from exc


def wait_for_file(token: str, file_uri: str, stage: str) -> dict[str, Any]:
    # Graph returns camelCase values such as azureStorageUriRequestSuccess, not
    # AzureStorageUriRequestSuccess. Compare case-insensitively.
    want_success = f"{stage}Success".lower()
    want_pending = f"{stage}Pending".lower()
    for _ in range(90):
        file_info = graph_request(token, "GET", file_uri)
        state = str(file_info.get("uploadState") or "")
        normalized = state.lower()
        log(f"Intune file {stage} uploadState={state}")
        if normalized == want_success:
            return file_info
        if normalized in {want_pending, ""} or normalized.endswith("pending"):
            time.sleep(2)
            continue
        if normalized.endswith("failed") or normalized.endswith("timedout") or normalized.endswith("error"):
            raise RuntimeError(f"Intune file {stage} failed with state: {state}")
        time.sleep(2)
    raise RuntimeError(f"Timed out waiting for Intune file {stage}")


def wait_until_published(token: str, app_id: str) -> None:
    for _ in range(90):
        app = graph_request(token, "GET", f"deviceAppManagement/mobileApps/{app_id}")
        state = app.get("publishingState")
        log(f"Intune publishingState={state}")
        if state == "published":
            return
        if str(state).lower() == "published":
            return
        if str(state).lower() not in {"processing", "notpublished"}:
            raise RuntimeError(f"Intune app entered unexpected publishing state: {state}")
        time.sleep(2)
    raise RuntimeError("Timed out waiting for Intune app to publish")


def _is_android_lob_app(app: dict[str, Any]) -> bool:
    odata_type = str(app.get("@odata.type", "")).lower()
    return odata_type.endswith("androidlobapp") or bool(app.get("packageId") or app.get("identityName"))


def _list_mobile_apps(
    token: str,
    start_url: str,
    extra_headers: dict[str, str] | None = None,
) -> list[dict[str, Any]]:
    apps: list[dict[str, Any]] = []
    url = start_url
    while url:
        payload = graph_request(token, "GET", url, raw_url=True, extra_headers=extra_headers)
        apps.extend(payload.get("value") or [])
        url = payload.get("@odata.nextLink")
    return apps


def list_android_lob_apps(token: str) -> list[dict[str, Any]]:
    # Intune Graph does not support /mobileApps/microsoft.graph.androidLobApp GET,
    # and $select=packageId on the mobileApp collection returns 400.
    attempts: list[tuple[str, dict[str, str] | None]] = [
        (
            f"{GRAPH_BASE}/deviceAppManagement/mobileApps"
            f"?$filter=isof('{LOB_TYPE}')",
            None,
        ),
        (
            f"{GRAPH_BASE}/deviceAppManagement/mobileApps"
            f"?$count=true&$filter=isof('{LOB_TYPE}')",
            {"ConsistencyLevel": "eventual"},
        ),
        (
            f"{GRAPH_BASE}/deviceAppManagement/mobileApps",
            None,
        ),
    ]
    last_error: Exception | None = None
    empty_result: list[dict[str, Any]] | None = None
    for start_url, headers in attempts:
        try:
            apps = [app for app in _list_mobile_apps(token, start_url, headers) if _is_android_lob_app(app)]
            if apps:
                log(f"Listed {len(apps)} Intune Android LOB app(s)")
                return apps
            empty_result = apps
            log("Intune app list succeeded with 0 Android LOB apps; trying next lookup")
        except PermissionError:
            raise
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            log(f"Retrying Intune app lookup after: {exc}")
    if empty_result is not None:
        log("Listed 0 Intune Android LOB app(s)")
        return empty_result
    if isinstance(last_error, PermissionError):
        raise last_error
    raise RuntimeError(f"Unable to list Intune Android LOB apps: {last_error}")


def _app_package_ids(app: dict[str, Any]) -> set[str]:
    return {
        value
        for value in (app.get("packageId"), app.get("identityName"))
        if isinstance(value, str) and value
    }


def find_existing_app(token: str, package_id: str, app_id: str | None) -> dict[str, Any] | None:
    if app_id:
        return graph_request(token, "GET", f"deviceAppManagement/mobileApps/{app_id}")
    for app in list_android_lob_apps(token):
        detail = app
        if package_id not in _app_package_ids(detail) and detail.get("id"):
            detail = graph_request(token, "GET", f"deviceAppManagement/mobileApps/{detail['id']}")
        if package_id in _app_package_ids(detail):
            return detail
    return None


def android_app_body(
    display_name: str,
    publisher: str,
    description: str,
    filename: str,
    package_id: str,
    version_code: str,
    version_name: str,
) -> dict[str, Any]:
    return {
        "@odata.type": f"#{LOB_TYPE}",
        "displayName": display_name,
        "publisher": publisher,
        "description": description,
        "fileName": filename,
        "packageId": package_id,
        "identityName": package_id,
        "identityVersion": version_code,
        "versionCode": version_code,
        "versionName": version_name,
        "minimumSupportedOperatingSystem": {
            "@odata.type": "#microsoft.graph.androidMinimumOperatingSystem",
            "v7_1": True,
        },
    }


def manifest_xml(package_id: str, version_code: str, version_name: str, filename: str) -> str:
    xml = (
        '<?xml version="1.0" encoding="utf-8"?>'
        '<AndroidManifestProperties xmlns:xsd="http://www.w3.org/2001/XMLSchema" '
        'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">'
        f"<Package>{package_id}</Package>"
        f"<PackageVersionCode>{version_code}</PackageVersionCode>"
        f"<PackageVersionName>{version_name}</PackageVersionName>"
        f"<ApplicationName>{filename}</ApplicationName>"
        "<MinSdkVersion>25</MinSdkVersion>"
        "<AWTVersion></AWTVersion>"
        "</AndroidManifestProperties>"
    )
    return base64.b64encode(xml.encode("ascii")).decode("ascii")


def ensure_group_assignment(token: str, app_id: str, group_id: str) -> None:
    assignments = graph_request(
        token, "GET", f"deviceAppManagement/mobileApps/{app_id}/assignments"
    ).get("value") or []
    for assignment in assignments:
        target = assignment.get("target") or {}
        if target.get("groupId") == group_id:
            log(f"Intune group {group_id} is already assigned")
            return
    graph_request(
        token,
        "POST",
        f"deviceAppManagement/mobileApps/{app_id}/assignments",
        {
            "@odata.type": "#microsoft.graph.mobileAppAssignment",
            "intent": "required",
            "target": {
                "@odata.type": "#microsoft.graph.groupAssignmentTarget",
                "groupId": group_id,
            },
        },
    )
    log(f"Assigned Intune app as required to group {group_id}")


def acquire_graph_token() -> str:
    token = os.environ.get("GRAPH_TOKEN", "").strip()
    if token:
        return token
    tenant_id = os.environ.get("AZURE_TENANT_ID", "").strip()
    client_id = os.environ.get("AZURE_CLIENT_ID", "").strip()
    client_secret = os.environ.get("AZURE_CLIENT_SECRET", "").strip()
    if not (tenant_id and client_id and client_secret):
        raise RuntimeError(
            "Provide GRAPH_TOKEN from Azure OIDC login, or AZURE_CLIENT_ID, "
            "AZURE_TENANT_ID, and AZURE_CLIENT_SECRET."
        )
    return get_token_from_client_secret(tenant_id, client_id, client_secret)


def preflight() -> None:
    token = acquire_graph_token()
    log_graph_token_grants(token)
    apps = list_android_lob_apps(token)
    log(f"Preflight OK: Intune app list succeeded ({len(apps)} Android LOB app(s)).")


def upload_apk(args: argparse.Namespace) -> None:
    token = acquire_graph_token()
    log_graph_token_grants(token)

    apk = Path(args.apk).resolve()
    if not apk.is_file():
        raise RuntimeError(f"APK not found: {apk}")

    filename = apk.name
    existing = find_existing_app(token, args.package_id, args.app_id)
    if existing:
        app_id = existing["id"]
        publishing_state = str(existing.get("publishingState") or "unknown")
        log(
            f"Updating existing Intune app {app_id} "
            f"({existing.get('displayName')}) publishingState={publishing_state}"
        )
        # Do not PATCH version/file metadata while unpublished. The previous run
        # created this app and then failed, so Intune rejects that PATCH until a
        # content version is committed.
    else:
        created = graph_request(
            token,
            "POST",
            "deviceAppManagement/mobileApps",
            android_app_body(
                args.display_name,
                args.publisher,
                args.description,
                filename,
                args.package_id,
                args.version_code,
                args.version_name,
            ),
        )
        app_id = created["id"]
        log(f"Created Intune app {app_id}")

    content_version = graph_request(
        token,
        "POST",
        f"deviceAppManagement/mobileApps/{app_id}/{LOB_TYPE}/contentVersions",
        {},
    )
    content_version_id = content_version["id"]
    log(f"Created Intune content version {content_version_id}")

    with tempfile.TemporaryDirectory() as tmp:
        encrypted_path = Path(tmp) / f"{apk.stem}_temp.bin"
        encryption_info = encrypt_intune_file(apk, encrypted_path)
        file_body = {
            "@odata.type": "#microsoft.graph.mobileAppContentFile",
            "name": filename,
            "size": apk.stat().st_size,
            "sizeEncrypted": encrypted_path.stat().st_size,
            "manifest": manifest_xml(args.package_id, args.version_code, args.version_name, filename),
        }
        content_file = graph_request(
            token,
            "POST",
            f"deviceAppManagement/mobileApps/{app_id}/{LOB_TYPE}/contentVersions/{content_version_id}/files",
            file_body,
        )
        file_id = content_file["id"]
        file_uri = (
            f"deviceAppManagement/mobileApps/{app_id}/{LOB_TYPE}/contentVersions/"
            f"{content_version_id}/files/{file_id}"
        )
        file_info = wait_for_file(token, file_uri, "AzureStorageUriRequest")
        sas_uri = file_info.get("azureStorageUri")
        if not sas_uri:
            raise RuntimeError("Intune did not return azureStorageUri")
        log("Uploading encrypted APK to Intune storage")
        upload_to_azure_blob(sas_uri, encrypted_path)
        graph_request(token, "POST", f"{file_uri}/commit", encryption_info)
        wait_for_file(token, file_uri, "CommitFile")

    graph_request(
        token,
        "PATCH",
        f"deviceAppManagement/mobileApps/{app_id}",
        {
            "@odata.type": f"#{LOB_TYPE}",
            "committedContentVersion": content_version_id,
            "fileName": filename,
            "versionName": args.version_name,
            "versionCode": args.version_code,
            "description": args.description,
        },
    )
    wait_until_published(token, app_id)
    ensure_group_assignment(token, app_id, args.group_id)
    log(f"Intune upload complete for {args.package_id} {args.version_name} ({args.version_code})")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--preflight",
        action="store_true",
        help="Test Graph/Intune authentication and app listing only; do not upload an APK.",
    )
    parser.add_argument("--apk")
    parser.add_argument("--package-id")
    parser.add_argument("--version-name")
    parser.add_argument("--version-code")
    parser.add_argument("--group-id")
    parser.add_argument("--display-name", default="StopTB UAT")
    parser.add_argument("--publisher", default="Piramal Swasthya")
    parser.add_argument("--description", default="StopTB UAT signed build")
    parser.add_argument("--app-id", default="")
    args = parser.parse_args()
    args.app_id = args.app_id.strip() or None
    if args.preflight:
        return args
    missing = [
        name
        for name in ("apk", "package_id", "version_name", "version_code", "group_id")
        if not getattr(args, name)
    ]
    if missing:
        parser.error("the following arguments are required unless --preflight: " + ", ".join(f"--{name.replace('_', '-')}" for name in missing))
    return args


if __name__ == "__main__":
    try:
        args = parse_args()
        if args.preflight:
            preflight()
        else:
            upload_apk(args)
    except Exception as exc:  # noqa: BLE001 - surface a short CI error
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)
