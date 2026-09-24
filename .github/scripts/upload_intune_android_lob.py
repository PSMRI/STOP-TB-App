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
GRAPH_BETA = "https://graph.microsoft.com/beta"
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
    api: str = "beta",
) -> Any:
    if raw_url:
        url = path
    elif api == "beta":
        url = f"{GRAPH_BETA}/{path.lstrip('/')}"
    else:
        url = f"{GRAPH_BASE}/{path.lstrip('/')}"
    encoded = None if body is None else json.dumps(body).encode("utf-8")
    last_error: Exception | None = None
    for attempt in range(1, 6):
        request = urllib.request.Request(url, data=encoded, method=method)
        request.add_header("Authorization", f"Bearer {token}")
        if body is not None:
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
            retryable = (
                exc.code in {429, 500, 502, 503, 504}
                or "ServerBusy" in details
                or "InternalServerError" in details
                or "throttl" in details.lower()
            )
            if retryable and attempt < 5:
                wait_s = min(40, 5 * (2 ** (attempt - 1)))
                retry_after = exc.headers.get("Retry-After") if exc.headers else None
                try:
                    wait_s = max(wait_s, int(retry_after)) if retry_after else wait_s
                except (TypeError, ValueError):
                    pass
                log(f"{message.splitlines()[0]} Retrying in {wait_s}s (attempt {attempt}/5)")
                time.sleep(wait_s)
                last_error = RuntimeError(message)
                continue
            raise RuntimeError(message) from exc
    raise last_error or RuntimeError(f"Graph {method} {url} failed after retries")


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


def _is_transient_graph_error(exc: Exception) -> bool:
    message = str(exc)
    return any(
        token in message
        for token in (
            " failed (429)",
            " failed (500)",
            " failed (502)",
            " failed (503)",
            " failed (504)",
            "InternalServerError",
            "ServerBusy",
            "throttl",
        )
    )


def _is_missing_app_error(exc: Exception) -> bool:
    message = str(exc)
    return " failed (404)" in message or "ResourceNotFound" in message or "not found" in message.lower()


def commit_content_version(token: str, app_id: str, content_version_id: str) -> None:
    path = f"deviceAppManagement/mobileApps/{app_id}"
    body = {
        "@odata.type": f"#{LOB_TYPE}",
        "committedContentVersion": str(content_version_id),
    }
    headers = {"Prefer": "return=minimal"}
    commit_url = f"{GRAPH_BETA}/{path}"
    last_error: Exception | None = None
    for attempt in range(1, 8):
        current: dict[str, Any] | None = None
        try:
            current = refresh_app(token, app_id)
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            if _is_missing_app_error(exc):
                wait_s = min(45, 4 * (2 ** (attempt - 1)))
                log(
                    f"Intune app {app_id} is not visible in Graph beta yet after the APK "
                    f"upload; waiting {wait_s}s before commit ({attempt}/7)"
                )
                time.sleep(wait_s)
                continue
            log(f"Could not read Intune app before commit PATCH: {exc}")
        if current is not None:
            committed = str(current.get("committedContentVersion") or "").strip()
            state = str(current.get("publishingState") or "").lower()
            if committed == str(content_version_id) or state == "published":
                log(
                    f"Intune app {app_id} already has committedContentVersion="
                    f"{committed or content_version_id} publishingState={state or 'unknown'}"
                )
                return
        try:
            log(
                f"Committing Intune content version {content_version_id} "
                f"via {commit_url} (attempt {attempt}/7)"
            )
            graph_request(
                token,
                "PATCH",
                commit_url,
                body,
                raw_url=True,
                extra_headers=headers,
            )
            return
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            log(f"Intune commit PATCH failed: {exc}")
            if not _is_transient_graph_error(exc) and not _is_missing_app_error(exc):
                raise
        wait_s = min(45, 4 * (2 ** (attempt - 1)))
        log(f"Waiting {wait_s}s for Intune app metadata before retrying commit PATCH")
        time.sleep(wait_s)
    try:
        current = refresh_app(token, app_id)
        committed = str(current.get("committedContentVersion") or "").strip()
        state = str(current.get("publishingState") or "").lower()
        if committed == str(content_version_id) or state in {"published", "processing"}:
            log(
                "Intune commit PATCH returned an error, but the app content is already "
                f"committed (committedContentVersion={committed} publishingState={state}). Continuing."
            )
            return
    except Exception as exc:  # noqa: BLE001
        last_error = exc
    raise last_error or RuntimeError(
        f"Could not commit Intune content version {content_version_id} for app {app_id}"
    )


def wait_until_published(token: str, app_id: str) -> None:
    for _ in range(90):
        try:
            app = refresh_app(token, app_id)
        except Exception as exc:  # noqa: BLE001
            if _is_missing_app_error(exc):
                log("Intune publishingState=unknown (Graph beta cannot see the app yet)")
                time.sleep(2)
                continue
            raise
        state = app.get("publishingState")
        log(f"Intune publishingState={state}")
        if str(state).lower() == "published":
            return
        if str(state).lower() not in {"processing", "notpublished"}:
            raise RuntimeError(f"Intune app entered unexpected publishing state: {state}")
        time.sleep(2)
    raise RuntimeError("Timed out waiting for Intune app to publish")


def is_android_lob_type(app: dict[str, Any]) -> bool:
    return str(app.get("@odata.type", "")).lower().endswith("androidlobapp")


def _is_android_lob_app(app: dict[str, Any]) -> bool:
    return is_android_lob_type(app) or bool(app.get("packageId") or app.get("identityName"))


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
    # Android Enterprise LOB apps created with targetedPlatforms live on Graph beta
    # and are often invisible to v1.0. Merge both catalogs so we do not create a
    # new AE app on every run.
    attempts: list[tuple[str, dict[str, str] | None]] = [
        (
            f"{GRAPH_BETA}/deviceAppManagement/mobileApps"
            f"?$filter=isof('{LOB_TYPE}')",
            None,
        ),
        (
            f"{GRAPH_BETA}/deviceAppManagement/mobileApps",
            None,
        ),
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
    found: dict[str, dict[str, Any]] = {}
    last_error: Exception | None = None
    listed_ok = False
    for start_url, headers in attempts:
        try:
            for app in _list_mobile_apps(token, start_url, headers):
                if _is_android_lob_app(app) and app.get("id"):
                    found[str(app["id"])] = app
            listed_ok = True
        except PermissionError:
            raise
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            log(f"Retrying Intune app lookup after: {exc}")
    if found or listed_ok:
        log(f"Listed {len(found)} Intune Android LOB app(s)")
        return list(found.values())
    if isinstance(last_error, PermissionError):
        raise last_error
    raise RuntimeError(f"Unable to list Intune Android LOB apps: {last_error}")


def _app_package_ids(app: dict[str, Any]) -> set[str]:
    return {
        value
        for value in (
            app.get("packageId"),
            app.get("identityName"),
            app.get("appIdentifier"),
            app.get("bundleId"),
        )
        if isinstance(value, str) and value
    }


def canonical_display_name(package_id: str, requested: str | None = None) -> str:
    if ".uat" in package_id:
        return "STOPTB_UAT"
    requested_name = str(requested or "").strip()
    if requested_name.upper() in {"STOPTB", "STOPTB_PROD"}:
        return "STOPTB"
    return "STOPTB"


def _normalized_app_name(app: dict[str, Any]) -> str:
    return "".join(ch for ch in str(app.get("displayName") or "").lower() if ch.isalnum())


def _looks_like_same_stoptb_app(app: dict[str, Any], package_id: str) -> bool:
    if package_id in _app_package_ids(app):
        return True
    name = _normalized_app_name(app)
    if "stoptb" not in name:
        return False
    wants_uat = ".uat" in package_id
    has_uat = "uat" in name
    return has_uat if wants_uat else not has_uat


def rename_app_display_name(token: str, app: dict[str, Any], display_name: str) -> None:
    app_id = str(app.get("id") or "")
    if not app_id:
        return
    odata_type = str(app.get("@odata.type") or f"#{LOB_TYPE}")
    graph_request(
        token,
        "PATCH",
        f"deviceAppManagement/mobileApps/{app_id}",
        {
            "@odata.type": odata_type if odata_type.startswith("#") else f"#{odata_type.lstrip('#')}",
            "displayName": display_name,
        },
    )
    app["displayName"] = display_name


def list_all_mobile_apps(token: str) -> list[dict[str, Any]]:
    found: dict[str, dict[str, Any]] = {}
    for start_url in (
        f"{GRAPH_BETA}/deviceAppManagement/mobileApps",
        f"{GRAPH_BASE}/deviceAppManagement/mobileApps",
    ):
        try:
            for app in _list_mobile_apps(token, start_url):
                if app.get("id"):
                    found[str(app["id"])] = app
        except Exception as exc:  # noqa: BLE001
            log(f"Could not list Intune mobile apps from {start_url}: {exc}")
    apps = list(found.values())
    stoptb_named = [app.get("displayName") for app in apps if "stoptb" in _normalized_app_name(app)]
    log(f"Listed {len(apps)} Intune mobile app(s); StopTB-named: {stoptb_named or 'none'}")
    return apps


def find_apps_for_package(token: str, package_id: str, app_id: str | None) -> list[dict[str, Any]]:
    found: dict[str, dict[str, Any]] = {}
    if app_id:
        found[app_id] = graph_request(token, "GET", f"deviceAppManagement/mobileApps/{app_id}")
    catalog = list_android_lob_apps(token)
    try:
        catalog.extend(list_all_mobile_apps(token))
    except Exception as exc:  # noqa: BLE001
        log(f"Could not list all Intune mobile apps for competing-package check: {exc}")
    for app in catalog:
        detail = app
        app_key = str(detail.get("id") or "")
        if app_key and app_key in found:
            continue
        if _looks_like_same_stoptb_app(detail, package_id):
            found[app_key] = detail
            continue
        if "stoptb" not in _normalized_app_name(detail) or not detail.get("id"):
            continue
        try:
            detail = graph_request(token, "GET", f"deviceAppManagement/mobileApps/{detail['id']}")
        except Exception as exc:  # noqa: BLE001
            log(f"Could not read Intune app {detail.get('id')}: {exc}")
            continue
        if _looks_like_same_stoptb_app(detail, package_id) and detail.get("id"):
            found[str(detail["id"])] = detail
    log(f"Found {len(found)} Intune app(s) for package {package_id}")
    return list(found.values())


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
        "targetedPlatforms": "androidOpenSourceProject",
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


def _assignment_body(group_id: str, intent: str) -> dict[str, Any]:
    return {
        "@odata.type": "#microsoft.graph.mobileAppAssignment",
        "intent": intent,
        "target": {
            "@odata.type": "#microsoft.graph.groupAssignmentTarget",
            "groupId": group_id,
        },
    }


def list_app_assignments(token: str, app_id: str) -> list[dict[str, Any]]:
    return graph_request(
        token, "GET", f"deviceAppManagement/mobileApps/{app_id}/assignments"
    ).get("value") or []


def ensure_group_assignment(token: str, app_id: str, group_id: str, intent: str = "required") -> None:
    want = intent.lower()
    assignments = list_app_assignments(token, app_id)
    for assignment in assignments:
        current_intent = str(assignment.get("intent") or "")
        target = assignment.get("target") or {}
        target_type = str(target.get("@odata.type") or "")
        log(
            f"Existing Intune assignment {assignment.get('id')} "
            f"intent={current_intent or '(none)'} target={target_type} groupId={target.get('groupId') or '-'}"
        )
        if target.get("groupId") != group_id:
            continue
        assignment_id = str(assignment.get("id") or "")
        if current_intent.lower() == want:
            log(f"Intune group {group_id} is already assigned as {want}")
            return
        if not assignment_id:
            break
        # Intent and Target are read-only on PATCH. Replace the assignment instead.
        graph_request(
            token,
            "DELETE",
            f"deviceAppManagement/mobileApps/{app_id}/assignments/{assignment_id}",
        )
        log(f"Removed Intune assignment {assignment_id} (intent={current_intent})")
        break
    last_error: Exception | None = None
    for attempt in range(1, 4):
        try:
            graph_request(
                token,
                "POST",
                f"deviceAppManagement/mobileApps/{app_id}/assignments",
                _assignment_body(group_id, want),
            )
            log(f"Assigned Intune app as {want} to group {group_id}")
            return
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            log(f"Assignment POST as {want} failed (attempt {attempt}/3): {exc}")
            time.sleep(2)
    raise RuntimeError(f"Could not assign Intune app as {want} to group {group_id}: {last_error}")


def _version_int(value: Any) -> int | None:
    try:
        return int(str(value).strip())
    except (TypeError, ValueError):
        return None


def targets_android_enterprise(app: dict[str, Any]) -> bool:
    value = app.get("targetedPlatforms")
    text = str(value or "").lower().replace(" ", "")
    if "opensource" in text or "aosp" in text or "androidenterprise" in text:
        return True
    try:
        return int(value) & 2 == 2
    except (TypeError, ValueError):
        return False


def with_platform_details(token: str, app: dict[str, Any]) -> dict[str, Any]:
    app_id = str(app.get("id") or "")
    if not app_id:
        return app
    try:
        return {**app, **read_app_identity(token, app_id)}
    except Exception as exc:  # noqa: BLE001
        log(f"Could not read Intune targetedPlatforms for {app_id}: {exc}")
        return app


def existing_app_version_code(app: dict[str, Any]) -> int | None:
    candidates = [app.get("identityVersion"), app.get("versionCode")]
    versions = [parsed for parsed in (_version_int(value) for value in candidates) if parsed is not None]
    return max(versions) if versions else None


def app_identity_label(app: dict[str, Any]) -> str:
    identity = app.get("identityVersion")
    version_code = app.get("versionCode")
    if identity not in (None, ""):
        return str(identity)
    if version_code not in (None, ""):
        return f"{version_code} (from versionCode)"
    return "unknown"


def log_existing_version(app: dict[str, Any]) -> None:
    log(
        "Existing Intune app version: "
        f"id={app.get('id')} name={app.get('displayName')} "
        f"identityVersion={app_identity_label(app)} "
        f"versionCode={app.get('versionCode')} "
        f"versionName={app.get('versionName')} "
        f"committedContentVersion={app.get('committedContentVersion')} "
        f"targetedPlatforms={app.get('targetedPlatforms') or 'androidDeviceAdministrator'}"
    )


def assignment_intents_for_group(token: str, app_id: str, group_id: str) -> set[str]:
    intents: set[str] = set()
    for assignment in list_app_assignments(token, app_id):
        target = assignment.get("target") or {}
        if target.get("groupId") == group_id:
            intents.add(str(assignment.get("intent") or "").lower())
    return intents


def pick_in_place_target(
    token: str,
    apps: list[dict[str, Any]],
    group_id: str,
    preferred_id: str | None,
) -> dict[str, Any] | None:
    if preferred_id:
        for app in apps:
            if str(app.get("id")) == preferred_id:
                return app
    ranked: list[tuple[int, int, int, int, dict[str, Any]]] = []
    for app in apps:
        app_id = str(app.get("id") or "")
        intents = assignment_intents_for_group(token, app_id, group_id) if app_id else set()
        required = 1 if "required" in intents else 0
        published = 1 if str(app.get("publishingState") or "").lower() == "published" else 0
        version = existing_app_version_code(app) or 0
        primary_name = 1 if _normalized_app_name(app) in {"stoptbuat", "stoptbuattest"} else 0
        ranked.append((primary_name, required, published, version, app))
    if not ranked:
        return None
    ranked.sort(key=lambda item: (item[0], item[1], item[2], item[3]))
    return ranked[-1][4]


def can_update_in_place(app: dict[str, Any], version_code: str) -> bool:
    if not is_android_lob_type(app):
        return False
    if str(app.get("publishingState") or "").lower() != "published":
        return False
    current_code = existing_app_version_code(app)
    new_code = _version_int(version_code)
    return current_code is not None and new_code is not None and new_code > current_code


def already_has_version(app: dict[str, Any], version_code: str) -> bool:
    if not is_android_lob_type(app):
        return False
    if str(app.get("publishingState") or "").lower() != "published":
        return False
    current_code = existing_app_version_code(app)
    new_code = _version_int(version_code)
    return current_code is not None and new_code is not None and current_code == new_code


def mark_app_for_uninstall(token: str, app: dict[str, Any], group_id: str) -> None:
    app_id = str(app["id"])
    display_name = str(app.get("displayName") or "StopTB")
    odata_type = str(app.get("@odata.type") or f"#{LOB_TYPE}")
    if "(previous)" not in display_name.lower() and is_android_lob_type(app):
        try:
            graph_request(
                token,
                "PATCH",
                f"deviceAppManagement/mobileApps/{app_id}",
                {
                    "@odata.type": odata_type if odata_type.startswith("#") else f"#{odata_type.lstrip('#')}",
                    "displayName": f"{display_name} (previous)",
                },
            )
        except Exception as exc:  # noqa: BLE001
            log(f"Could not rename previous Intune app {app_id}: {exc}")
    ensure_group_assignment(token, app_id, group_id, intent="uninstall")
    log(
        f"Assigned Intune app {app_id} ({display_name}) as uninstall so devices can drop "
        f"the previous package and install the new Required APK"
    )


def uninstall_competing_apps(
    token: str,
    package_id: str,
    keep_app_id: str,
    group_id: str,
    known_apps: list[dict[str, Any]],
) -> None:
    competing = {str(app.get("id")): app for app in known_apps if app.get("id")}
    try:
        for app in find_apps_for_package(token, package_id, None):
            if app.get("id"):
                competing[str(app["id"])] = app
    except Exception as exc:  # noqa: BLE001
        log(f"Could not refresh competing Intune apps: {exc}")
    for app_id, app in competing.items():
        if app_id == keep_app_id:
            continue
        if not _looks_like_same_stoptb_app(app, package_id) and package_id not in _app_package_ids(app):
            continue
        log(
            f"Removing competing Intune app {app_id} ({app.get('displayName')}) "
            "from Required so the new APK can install"
        )
        try:
            mark_app_for_uninstall(token, app, group_id)
        except Exception as exc:  # noqa: BLE001
            log(f"Could not uninstall competing Intune app {app_id}: {exc}")


APP_IDENTITY_SELECT = (
    "id,displayName,publishingState,committedContentVersion,packageId,"
    "identityName,identityVersion,versionCode,versionName,fileName,targetedPlatforms"
)


def refresh_app(token: str, app_id: str, api: str | None = None) -> dict[str, Any]:
    path = f"deviceAppManagement/mobileApps/{app_id}"
    if api:
        return graph_request(token, "GET", path, api=api)
    last_error: Exception | None = None
    for candidate in ("beta", "v1.0"):
        try:
            return graph_request(token, "GET", path, api=candidate)
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            if not _is_missing_app_error(exc):
                raise
    raise last_error or RuntimeError(f"Intune app {app_id} was not found in Graph")


def read_app_identity(token: str, app_id: str) -> dict[str, Any]:
    path = f"deviceAppManagement/mobileApps/{app_id}?$select={APP_IDENTITY_SELECT}"
    merged: dict[str, Any] = {}
    for api in ("beta", "v1.0"):
        try:
            payload = graph_request(token, "GET", path, api=api)
            merged = {**merged, **payload, "_graphApi": api}
            if payload.get("identityVersion") not in (None, ""):
                return merged
        except Exception as exc:  # noqa: BLE001
            log(f"Could not read Intune identity fields from Graph {api}: {exc}")
    if not merged:
        merged = refresh_app(token, app_id)
    return merged


def ensure_identity_version(
    token: str,
    app_id: str,
    package_id: str,
    version_code: str,
    version_name: str,
) -> dict[str, Any]:
    body = {
        "@odata.type": f"#{LOB_TYPE}",
        "identityName": package_id,
        "identityVersion": str(version_code),
        "packageId": package_id,
        "versionCode": str(version_code),
        "versionName": str(version_name),
    }
    try:
        graph_request(
            token,
            "PATCH",
            f"deviceAppManagement/mobileApps/{app_id}",
            body,
            extra_headers={"Prefer": "return=minimal"},
            api="beta",
        )
        log(f"Set Intune identityVersion={version_code} identityName={package_id}")
    except Exception as exc:  # noqa: BLE001
        log(f"Could not PATCH Intune identityVersion={version_code}: {exc}")
    app = read_app_identity(token, app_id)
    if app.get("identityVersion") in (None, ""):
        time.sleep(3)
        app = read_app_identity(token, app_id)
    if app.get("identityVersion") in (None, ""):
        log(
            "Graph still omits identityVersion after PATCH; Intune updates devices "
            f"from versionCode={app.get('versionCode') or version_code} on the committed APK."
        )
    else:
        log(
            f"Intune identityVersion={app.get('identityVersion')} "
            f"identityName={app.get('identityName') or package_id}"
        )
    return app


def log_published_app(token: str, app_id: str, version_code: str, version_name: str) -> None:
    app = read_app_identity(token, app_id)
    try:
        app = {**app, **refresh_app(token, app_id, api="beta")}
    except Exception as exc:  # noqa: BLE001
        log(f"Could not read Intune app from Graph beta: {exc}")
    identity_version = app.get("identityVersion") or app.get("versionCode")
    log(
        "Published Intune app: "
        f"id={app_id} name={app.get('displayName')} publishingState={app.get('publishingState')} "
        f"identityVersion={app_identity_label(app)} "
        f"versionCode={app.get('versionCode')} versionName={app.get('versionName')} "
        f"committedContentVersion={app.get('committedContentVersion')} "
        f"packageId={app.get('packageId')} "
        f"targetedPlatforms={app.get('targetedPlatforms')}"
    )
    if app.get("identityVersion") in (None, ""):
        log(
            "Graph did not return identityVersion; Intune uses versionCode "
            f"{app.get('versionCode') or version_code} from the committed APK for device updates."
        )
    published_code = existing_app_version_code({**app, "identityVersion": identity_version})
    expected_code = _version_int(version_code)
    if published_code is not None and expected_code is not None and published_code != expected_code:
        raise RuntimeError(
            "Intune published a different version than this upload. "
            f"App identity/versionCode={published_code}, upload {version_name} ({version_code}). "
            "MDM devices will keep the published version."
        )
    try:
        summary = graph_request(
            token,
            "GET",
            f"deviceAppManagement/mobileApps/{app_id}/installSummary",
            api="beta",
        )
        log(
            "Intune install summary: "
            f"installed={summary.get('installedDeviceCount')} "
            f"notInstalled={summary.get('notInstalledDeviceCount')} "
            f"failed={summary.get('failedDeviceCount')} "
            f"pending={summary.get('pendingInstallDeviceCount')}"
        )
    except Exception as exc:  # noqa: BLE001
        message = str(exc)
        if "NotSupported" in message or "not found for the segment" in message.lower():
            log("Intune install summary is not available in this tenant Graph API.")
        else:
            log(f"Could not read Intune install summary: {exc}")


def content_versions_path(app_id: str) -> str:
    return f"deviceAppManagement/mobileApps/{app_id}/{LOB_TYPE}/contentVersions"


def list_content_versions(token: str, app_id: str) -> list[dict[str, Any]]:
    return graph_request(token, "GET", content_versions_path(app_id)).get("value") or []


def pending_content_version_ids(token: str, app: dict[str, Any]) -> list[str]:
    app_id = str(app["id"])
    committed = str(app.get("committedContentVersion") or "").strip()
    published = str(app.get("publishingState") or "").lower() == "published"
    committed_num = _version_int(committed)
    pending: list[str] = []
    for version in list_content_versions(token, app_id):
        version_id = str(version.get("id") or "")
        if not version_id:
            continue
        if published and committed and version_id == committed:
            log(f"Keeping committed Intune content version {version_id}")
            continue
        version_num = _version_int(version_id)
        if published and committed_num is not None and version_num is not None and version_num < committed_num:
            log(f"Skipping historical Intune content version {version_id} (committed is {committed})")
            continue
        pending.append(version_id)
    return pending


def delete_pending_content_versions(token: str, app: dict[str, Any]) -> None:
    app_id = str(app["id"])
    pending_ids = pending_content_version_ids(token, app)
    if not pending_ids:
        log("No pending Intune content version. Will upload the current APK.")
        return
    log(
        f"Found {len(pending_ids)} pending Intune content version(s): "
        f"{', '.join(pending_ids)}. Deleting them before uploading the current APK."
    )
    for version_id in pending_ids:
        try:
            graph_request(token, "DELETE", f"{content_versions_path(app_id)}/{version_id}")
            log(f"Deleted pending Intune content version {version_id}")
        except Exception as exc:  # noqa: BLE001
            message = str(exc)
            if "NotSupported" in message or "(501)" in message:
                log(
                    f"Intune does not allow deleting content version {version_id}; "
                    "leaving it in place and uploading a new version."
                )
                continue
            log(f"Could not delete Intune content version {version_id}: {exc}")


def delete_mobile_app(token: str, app_id: str) -> None:
    graph_request(token, "DELETE", f"deviceAppManagement/mobileApps/{app_id}")
    log(f"Deleted unpublished Intune app {app_id}")


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
    app_display_name = canonical_display_name(args.package_id, args.display_name)
    log(f"Intune displayName will stay {app_display_name} (version stays in versionName/versionCode, not the app name)")
    log(
        "Uploading APK with "
        f"versionName={args.version_name} versionCode={args.version_code}. "
        "Android and Intune only replace an installed package when versionCode is higher; "
        "versionName is display-only."
    )

    def free_canonical_display_name(keep_app_id: str | None = None) -> None:
        want = _normalized_app_name({"displayName": app_display_name})
        for app in matching:
            other_id = str(app.get("id") or "")
            if not other_id or other_id == keep_app_id:
                continue
            current = str(app.get("displayName") or "")
            if _normalized_app_name(app) != want:
                continue
            previous_name = f"{app_display_name} (previous)"
            if current == previous_name:
                continue
            try:
                rename_app_display_name(token, app, previous_name)
                log(
                    f"Renamed {current} ({other_id}) to {previous_name} "
                    f"so the Required app can keep the name {app_display_name}"
                )
            except Exception as exc:  # noqa: BLE001
                log(f"Could not rename {current} ({other_id}) off {app_display_name}: {exc}")

    def ensure_canonical_display_name(target_app_id: str) -> None:
        free_canonical_display_name(keep_app_id=target_app_id)
        try:
            current = refresh_app(token, target_app_id)
        except Exception as exc:  # noqa: BLE001
            log(f"Could not read Intune app {target_app_id} to confirm displayName: {exc}")
            return
        current_name = str(current.get("displayName") or "")
        if current_name == app_display_name:
            return
        try:
            rename_app_display_name(token, current, app_display_name)
            log(f"Renamed Intune app {target_app_id} from {current_name} to {app_display_name}")
        except Exception as exc:  # noqa: BLE001
            log(f"Could not set Intune displayName to {app_display_name}: {exc}")

    def create_app() -> str:
        free_canonical_display_name()
        body = android_app_body(
            app_display_name,
            args.publisher,
            args.description,
            filename,
            args.package_id,
            args.version_code,
            args.version_name,
        )
        try:
            created = graph_request(
                token,
                "POST",
                "deviceAppManagement/mobileApps",
                body,
                api="beta",
            )
        except RuntimeError as exc:
            raise RuntimeError(
                "Could not create an Android Enterprise Intune LOB app "
                "(targetedPlatforms=androidOpenSourceProject). "
                "If Graph returned 503 ServerBusy, re-run the job; Intune was temporarily unavailable. "
                f"Graph error: {exc}"
            ) from exc
        created_id = str(created["id"])
        platforms = created.get("targetedPlatforms")
        if not platforms:
            try:
                platforms = refresh_app(token, created_id, api="beta").get("targetedPlatforms")
            except Exception as exc:  # noqa: BLE001
                log(f"Could not read targetedPlatforms for new app {created_id}: {exc}")
        log(
            f"Created Intune app {created_id} displayName={app_display_name} "
            f"targetedPlatforms={platforms or 'androidDeviceAdministrator'}"
        )
        if not targets_android_enterprise({"targetedPlatforms": platforms}):
            raise RuntimeError(
                "Intune created the LOB app as Android device administrator. "
                "Android Enterprise devices will not install it. "
                "Delete this app in Intune and create an Android Enterprise LOB app, "
                "or ensure Graph beta accepts targetedPlatforms=androidOpenSourceProject."
            )
        return created_id

    def create_content_version() -> dict[str, Any]:
        return graph_request(token, "POST", content_versions_path(app_id), {})

    apps = find_apps_for_package(token, args.package_id, args.app_id)
    if args.app_id:
        pinned = next((app for app in apps if str(app.get("id")) == args.app_id), None)
        if pinned is None:
            raise RuntimeError(f"INTUNE_APP_ID {args.app_id} was not found in Intune.")
        found_packages = _app_package_ids(pinned)
        log(
            f"INTUNE_APP_ID {args.app_id} ({pinned.get('displayName')}) "
            f"package={found_packages or '{unknown}'}"
        )
        if found_packages and args.package_id not in found_packages:
            raise RuntimeError(
                f"INTUNE_APP_ID {args.app_id} is package {found_packages}, not {args.package_id}. "
                "MDM devices assigned to that app will not get this APK."
            )

    matching = [with_platform_details(token, app) for app in apps]
    for app in matching:
        log_existing_version(app)

    enterprise_apps = [
        app
        for app in matching
        if is_android_lob_type(app) and targets_android_enterprise(app)
    ]
    target = (
        pick_in_place_target(token, enterprise_apps, args.group_id, args.app_id)
        if enterprise_apps
        else None
    )
    replace_mode = "new"
    skip_upload = False
    if target is not None and already_has_version(target, args.version_code):
        replace_mode = "already"
        skip_upload = True
        app_id = str(target["id"])
        log(
            f"Intune Android Enterprise app {app_id} ({target.get('displayName')}) "
            f"already has versionCode {args.version_code}. Skipping APK upload and "
            "reassigning Required / uninstalling competing apps."
        )
    elif target is not None and can_update_in_place(target, args.version_code):
        replace_mode = "in-place"
        app_id = str(target["id"])
        existing = refresh_app(token, app_id)
        publishing_state = str(existing.get("publishingState") or "unknown")
        current_code = existing_app_version_code(existing)
        log(
            f"In-place Intune upgrade: published versionCode {current_code} -> {args.version_code} "
            f"on Android Enterprise app {app_id} ({existing.get('displayName')}). "
            "New versionCode is higher, so MDM can replace the APK without uninstall."
        )
        delete_pending_content_versions(token, existing)
        leftover = pending_content_version_ids(token, refresh_app(token, app_id))
        if publishing_state.lower() != "published" and leftover:
            log("Pending content could not be cleared; recreating the unpublished Intune app")
            delete_mobile_app(token, app_id)
            app_id = create_app()
    elif enterprise_apps:
        replace_mode = "reinstall"
        existing_codes = [existing_app_version_code(app) for app in enterprise_apps]
        log(
            "Force Intune uninstall+reinstall: new versionCode "
            f"{args.version_code} is not higher than existing Android Enterprise {existing_codes} "
            f"(versionName {args.version_name} is ignored by Android). "
            "Same or lower versionCode cannot replace an installed package in place."
        )
        for app in enterprise_apps:
            other_id = str(app.get("id") or "")
            if not other_id:
                continue
            state = str(app.get("publishingState") or "").lower()
            if state == "published":
                try:
                    mark_app_for_uninstall(token, app, args.group_id)
                except Exception as exc:  # noqa: BLE001
                    log(f"Could not uninstall previous Android Enterprise app {other_id}: {exc}")
            else:
                try:
                    delete_mobile_app(token, other_id)
                except Exception as exc:  # noqa: BLE001
                    log(f"Could not delete unpublished Intune app {other_id}: {exc}")
        app_id = create_app()
    else:
        if matching:
            log(
                "Existing Intune LOB app is Android device administrator only. "
                "TargetedPlatforms is read-only after create, so a new Android Enterprise "
                "LOB app will be created for fully managed/dedicated devices."
            )
        else:
            log("No existing Intune app for this package. Uploading as a new Android Enterprise app.")
        app_id = create_app()

    if not skip_upload:
        try:
            content_version = create_content_version()
        except RuntimeError as exc:
            message = str(exc).lower()
            if "first content version is committed" not in message and "cannot be updated" not in message:
                raise
            log("Intune still has a blocked first content version; recreating the app")
            delete_mobile_app(token, app_id)
            app_id = create_app()
            content_version = create_content_version()
        content_version_id = content_version["id"]
        log(f"Uploading current APK as Intune content version {content_version_id}")

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
            file_info = wait_for_file(token, file_uri, "CommitFile")
            if not file_info.get("isCommitted"):
                log("Waiting for Intune file isCommitted=true before app commit PATCH")
                for _ in range(30):
                    file_info = graph_request(token, "GET", file_uri)
                    if file_info.get("isCommitted"):
                        break
                    time.sleep(2)
                else:
                    raise RuntimeError("Intune file commit finished without isCommitted=true")
            time.sleep(5)

        commit_content_version(token, app_id, content_version_id)
        try:
            graph_request(
                token,
                "PATCH",
                f"deviceAppManagement/mobileApps/{app_id}",
                {
                    "@odata.type": f"#{LOB_TYPE}",
                    "fileName": filename,
                    "displayName": app_display_name,
                    "identityName": args.package_id,
                    "identityVersion": str(args.version_code),
                    "packageId": args.package_id,
                    "versionName": args.version_name,
                    "versionCode": args.version_code,
                    "description": args.description,
                },
                extra_headers={"Prefer": "return=minimal"},
                api="beta",
            )
        except Exception as exc:  # noqa: BLE001
            log(f"Could not patch Intune version metadata after commit: {exc}")
        wait_until_published(token, app_id)

    ensure_identity_version(
        token,
        app_id,
        args.package_id,
        str(args.version_code),
        str(args.version_name),
    )
    if not skip_upload:
        log_published_app(token, app_id, args.version_code, args.version_name)
    ensure_canonical_display_name(app_id)
    ensure_group_assignment(token, app_id, args.group_id, intent="required")
    try:
        uninstall_competing_apps(token, args.package_id, app_id, args.group_id, matching)
    except Exception as exc:  # noqa: BLE001
        log(f"Could not finish competing-app uninstall: {exc}")
    if replace_mode == "in-place":
        log(
            f"Intune in-place upgrade complete for {args.package_id} "
            f"{args.version_name} ({args.version_code})"
        )
    elif replace_mode == "reinstall":
        log(
            f"Intune uninstall+reinstall complete for {args.package_id} "
            f"{args.version_name} ({args.version_code}). "
            "Devices must check in so Uninstall removes the old package before the new "
            "Required APK can install. Android will not silently downgrade over a higher "
            "installed versionCode."
        )
    elif replace_mode == "already":
        log(
            f"Intune already had {args.package_id} {args.version_name} ({args.version_code}); "
            "Required assignment and competing-app uninstall were refreshed."
        )
    else:
        log(
            f"Intune upload complete for {args.package_id} {args.version_name} ({args.version_code}). "
            "Competing apps such as STOPTB_UAT were set to Uninstall so this APK can install."
        )


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
    parser.add_argument("--display-name", default="STOPTB_UAT")
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
