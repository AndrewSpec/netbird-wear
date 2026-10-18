#!/usr/bin/env python3
"""Package APKs and corresponding source trees without local data or signing keys."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import tarfile

ROOT = Path(__file__).resolve().parent.parent
BASE = "ad7865a2c3cc1bc940bf63512ccab1819ebf53a6"
ENGINE = "01185ba3536bcc037481578420d73cb7cee96094"


def git(tree, *args):
    return subprocess.check_output(["git", "-C", str(tree), *args])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("tag", help="Existing local release tag, e.g. v1.0.3")
    parser.add_argument("--phone-source", default="research/android-client")
    args = parser.parse_args()
    if not re.fullmatch(r"v\d+\.\d+\.\d+", args.tag):
        parser.error("Expected a tag such as v1.0.3")
    phone = (ROOT / args.phone_source).resolve()
    if ROOT not in phone.parents:
        parser.error("Phone source must be inside the workspace")
    if git(phone, "rev-parse", "HEAD").decode().strip() != BASE:
        parser.error("Unexpected Android base commit")
    if git(phone / "netbird", "rev-parse", "HEAD").decode().strip() != ENGINE:
        parser.error("Unexpected Go engine commit")
    if git(phone / "netbird", "status", "--porcelain").strip():
        parser.error("Go engine has local changes not covered by the release patch")
    actual_patch = git(phone, "diff", "--", "app", "tool")
    if actual_patch != (ROOT / "patches/netbird-wear.patch").read_bytes():
        parser.error("Phone source differs from the checked-in patch")
    commit = git(ROOT, "rev-parse", f"{args.tag}^{{commit}}").decode().strip()
    if git(ROOT, "rev-parse", "HEAD").decode().strip() != commit:
        parser.error("Check out the release tag before packaging")
    if git(ROOT, "status", "--porcelain", "--untracked-files=no").strip():
        parser.error("Commit all tracked changes before packaging")
    out = ROOT / ".release" / args.tag
    out.mkdir(parents=True, exist_ok=False)
    prefix = f"netbird-wear-{args.tag}"
    archive_path = out / f"{prefix}-sources.tar.gz"
    modules = ROOT / ".toolchain/gopath/pkg/mod/cache/download"
    zips = sorted(modules.rglob("*.zip"))
    if not zips:
        parser.error("Go dependency source cache is missing; build the AAR first")

    with tarfile.open(archive_path, "w:gz", compresslevel=6) as target:
        def add_git_tree(tree, revision, destination):
            raw = git(tree, "archive", "--format=tar", revision)
            with tarfile.open(fileobj=io.BytesIO(raw), mode="r:") as source:
                for member in source:
                    if member.issym() or member.islnk():
                        # Preserve repository symlinks only, never follow host files.
                        if member.linkname.startswith("/") or ".." in Path(member.linkname).parts:
                            raise ValueError("Unsafe source symlink: " + member.name)
                    member.name = f"{prefix}/{destination}/{member.name}"
                    target.addfile(member, source.extractfile(member) if member.isfile() else None)

        add_git_tree(ROOT, commit, "project")
        add_git_tree(phone, BASE, "android-client-upstream")
        add_git_tree(phone / "netbird", ENGINE, "netbird-engine")
        # The archive supplies the untouched base plus the exact patch. This avoids
        # copying ignored local.properties, AARs, build output or device data.
        for source in zips:
            target.add(source, arcname=f"{prefix}/go-module-sources/{source.relative_to(modules)}", recursive=False)
        instructions = f"""NetBird Wear {args.tag}: corresponding sources
Project commit: {commit}
Android base: {BASE}
Go engine: {ENGINE}

To reconstruct the patched client without downloading upstream Git sources:
1. Copy android-client-upstream to project/.build/android-client.
2. Copy netbird-engine into project/.build/android-client/netbird.
3. Apply project/patches/netbird-wear.patch with git apply in that client directory.
4. Build gomobile/netbird.aar as described in project/scripts/build-netbird-aar.sh.
   go-module-sources contains dependency source ZIPs in Go module proxy layout.
5. Build the client and watch following project/README.md. Install JDK 17,
   Android SDK 36, NDK r23b and Go >=1.26; remote Gradle/SDK dependencies are
   still required. Scripts can instead clone the same pinned Git sources.

Signing keys are deliberately NOT distributed. Use your own key for both APKs.
Read project/LICENSE, NOTICE.md, SECURITY.md and LICENSES/ before redistributing.
""".encode()
        info = tarfile.TarInfo(f"{prefix}/SOURCE-README.txt")
        info.size = len(instructions)
        target.addfile(info, io.BytesIO(instructions))

    for name in ("netbird-wear-release.apk", "netbird-watch-release.apk"):
        source = ROOT / "dist" / name
        (out / name).write_bytes(source.read_bytes())
    provenance = {"tag": args.tag, "project_commit": commit, "android_base": BASE,
                  "netbird_engine": ENGINE, "go_source_archives": len(zips),
                  "phone_version": "1.0.3-wear", "watch_version": "1.0.3",
                  "signing": "private local development key; no private keys included"}
    (out / "PROVENANCE.json").write_text(json.dumps(provenance, indent=2) + "\n")
    for name in ("NOTICE.md", "LICENSE", "SECURITY.md"):
        (out / name).write_bytes((ROOT / name).read_bytes())
    lines = []
    for path in sorted(out.iterdir()):
        if path.is_file():
            h = hashlib.sha256()
            with path.open("rb") as source:
                for block in iter(lambda: source.read(1024 * 1024), b""):
                    h.update(block)
            lines.append(f"{h.hexdigest()}  {path.name}\n")
    (out / "SHA256SUMS").write_text("".join(lines))
    print(out)
    for path in sorted(out.iterdir()):
        print(path.name, path.stat().st_size)


if __name__ == "__main__":
    main()
