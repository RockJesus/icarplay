#!/usr/bin/env bash
# Release gate for the closed head unit: fail the build if the app starts asking for
# system-level power or tries to take over the launcher.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"
GRADLE="$ROOT/app/build.gradle.kts"

python3 - "$MANIFEST" "$GRADLE" <<'PY'
import sys, xml.etree.ElementTree as ET
from pathlib import Path

manifest, gradle = map(Path, sys.argv[1:3])
AND = "{http://schemas.android.com/apk/res/android}"
forbidden_permissions = {
    "android.permission.WRITE_SETTINGS",
    "android.permission.WRITE_SECURE_SETTINGS",
    "android.permission.BIND_DEVICE_ADMIN",
    "android.permission.MANAGE_USB",
    "android.permission.REBOOT",
    "android.permission.FACTORY_TEST",
    "android.permission.MASTER_CLEAR",
    "android.permission.INSTALL_PACKAGES",
    "android.permission.DELETE_PACKAGES",
    "android.permission.MOUNT_UNMOUNT_FILESYSTEMS",
}
forbidden_categories = {
    "android.intent.category.HOME",
    "android.intent.category.LEANBACK_LAUNCHER",
}

root = ET.parse(manifest).getroot()
problems = []

if root.get(AND + "sharedUserId"):
    problems.append("manifest declares sharedUserId (system UID sharing)")

for node in root.iter():
    if node.tag == "uses-permission":
        name = node.get(AND + "name", "")
        if name in forbidden_permissions:
            problems.append(f"forbidden permission: {name}")
    if node.tag == "device-admin":
        problems.append("manifest contains a device-admin declaration")
    if node.tag == "category":
        name = node.get(AND + "name", "")
        if name in forbidden_categories:
            problems.append(f"forbidden launcher category: {name}")

boot_receivers = [
    n for n in root.iter("receiver")
    if n.get(AND + "name") == "local.hicarlab.BootReceiver"
]
if not boot_receivers:
    problems.append("local.hicarlab.BootReceiver missing")
else:
    for node in boot_receivers:
        if node.get(AND + "enabled") != "false":
            problems.append("HiCar boot receiver must be android:enabled=\"false\" by default")

gradle_text = gradle.read_text(errors="replace")
for needle in ("deviceAdmin", "DeviceAdminReceiver", "sharedUserId"):
    if needle in gradle_text:
        problems.append(f"build.gradle.kts mentions {needle}")

if problems:
    print("SAFETY PREFLIGHT FAILED")
    for problem in problems:
        print(" - " + problem)
    sys.exit(1)
print("SAFETY PREFLIGHT OK: no system permissions, device admin, default-home binding, or boot takeover")
PY

# No code path may silently replace the home app or invoke a privileged shell.
if grep -R -n -E \
    'replacePreferredActivity|addPreferredActivity|setHomeActivity|createRequestRoleIntent|Runtime\.getRuntime\(\)\.exec|ProcessBuilder|mount -o remount|settings put|pm install' \
    "$ROOT/app/src/main/java"; then
    echo "SAFETY PREFLIGHT FAILED: launcher/system shell primitive found in app sources" >&2
    exit 1
fi

echo "SAFETY PREFLIGHT OK: no launcher takeover or privileged shell primitive in app sources"
