#!/usr/bin/env python3
"""Install only a verified successful build; preserve unrelated files and prior LazyMod builds."""
import importlib.util
import json
import os
import shutil
import sys
import uuid
import zipfile
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("verify_artifact", Path(__file__).with_name("verify-artifact.py"))
verify = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(verify)
DEFAULT_MODS = Path("/mnt/a400/Games/PrismLauncher/instances/OneClient 26.2/minecraft/mods")

def is_lazymod(path):
    try:
        with zipfile.ZipFile(path) as z:
            return json.loads(z.read("fabric.mod.json")).get("id") == "lazymod"
    except (OSError, ValueError, KeyError, zipfile.BadZipFile):
        return False

def install_verified(jar, mods, backups):
    if not mods.is_dir():
        raise FileNotFoundError(f"Mods directory is missing: {mods}")
    target = mods / jar.name
    if target.exists() and not is_lazymod(target):
        raise ValueError(f"Refusing to overwrite an unrelated file: {target}")
    previous = [p for p in mods.iterdir() if p.is_file() and p.suffix == ".jar" and is_lazymod(p)]
    transaction = str(uuid.uuid4())
    staging = mods / f".lazymod-{transaction}.tmp"
    archive = backups / transaction
    moved = []
    installed = False
    try:
        shutil.copy2(jar, staging)
        if verify.sha(staging) != verify.sha(jar):
            raise IOError("Staged copy checksum does not match source")
        archive.mkdir(parents=True)
        for old in previous:
            shutil.move(str(old), archive / old.name)
            moved.append(old)
        os.replace(staging, target)
        installed = True
        if verify.sha(target) != verify.sha(jar):
            raise IOError("Installed checksum does not match source")
        print(f"Installed {target}; archived {len(moved)} previous LazyMod JAR(s) in {archive}")
    except BaseException:
        if installed:
            target.unlink(missing_ok=True)
        for old in reversed(moved):
            shutil.move(str(archive / old.name), old)
        raise
    finally:
        staging.unlink(missing_ok=True)

def main():
    jar = verify.verify(verify.expected_jar())
    receipt = json.loads((verify.ROOT / "build/verified-build.json").read_text())
    if receipt != {"jar": str(jar.relative_to(verify.ROOT)), "sha256": verify.sha(jar), "inputs": verify.fingerprint()}:
        raise ValueError("Build receipt is stale; run ./scripts/build-local.sh before installing")
    mods = Path(os.environ.get("LAZYMOD_LOCAL_MODS", str(DEFAULT_MODS)))
    install_verified(jar, mods, verify.ROOT / ".local/install-backups")

if __name__ == "__main__":
    main()
