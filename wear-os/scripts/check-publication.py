#!/usr/bin/env python3
"""Reject local credentials and device diagnostics from the Git index."""
from pathlib import Path
import re
import subprocess
import sys

root = Path(__file__).resolve().parent.parent
names = subprocess.check_output(["git", "ls-files", "-z"], cwd=root).decode().split("\0")
forbidden = {".android-home", ".build", ".toolchain", ".gradle", ".gradle-home",
             ".github-auth", ".kotlin", ".release", "research", "build"}
secret = re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|"
                    rb"gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}")
errors = []
for name in filter(None, names):
    p = Path(name)
    if forbidden.intersection(p.parts) or p.name in {"local.properties", ".env"}:
        errors.append(name + ": local/generated path")
    if p.suffix.lower() in {".keystore", ".jks", ".pem", ".p12", ".pfx", ".apk", ".aar", ".aab", ".log"}:
        errors.append(name + ": private key, binary artifact or log")
    data = subprocess.check_output(["git", "show", ":" + name], cwd=root)
    if len(data) > 5 * 1024 * 1024:
        errors.append(name + ": unexpectedly large file")
    if secret.search(data):
        errors.append(name + ": possible credential (value withheld)")
    if name.endswith((".md", ".xml", ".kt", ".java", ".sh", ".properties")):
        if b"/home/andrew/" in data or re.search(rb"192\.168\.50\.\d+", data):
            errors.append(name + ": local device/path information")
if errors:
    print("Publication check failed:\n" + "\n".join(errors), file=sys.stderr)
    sys.exit(1)
print(f"Publication check passed ({len(list(filter(None, names)))} indexed files).")
