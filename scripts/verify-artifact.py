#!/usr/bin/env python3
"""Verify a distributable without starting Minecraft; optionally record a successful build."""
import hashlib
import json
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

def properties():
    return dict(line.split("=", 1) for line in (ROOT / "gradle.properties").read_text().splitlines() if "=" in line and not line.startswith("#"))

def expected_jar():
    p = properties()
    return ROOT / "build/libs" / f"lazymod-{p['mod_version']}+{p['minecraft_version']}.jar"

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def fingerprint():
    paths = [p for folder in ("src", "scripts", "licenses", "gradle") for p in (ROOT / folder).rglob("*") if p.is_file() and "__pycache__" not in p.parts]
    paths += [ROOT / p for p in ("build.gradle", "settings.gradle", "gradle.properties", "LICENSE", "THIRD_PARTY_NOTICES.md", "gradlew", "gradlew.bat")]
    digest = hashlib.sha256()
    for p in sorted(paths):
        digest.update(str(p.relative_to(ROOT)).encode()); digest.update(b"\0"); digest.update(p.read_bytes())
    return digest.hexdigest()

def verify(jar):
    expected = expected_jar()
    if jar.name != expected.name:
        raise ValueError(f"Expected distributable {expected.name}, got {jar.name}")
    with zipfile.ZipFile(jar) as z:
        if z.testzip() is not None:
            raise ValueError("JAR integrity check failed")
        meta = json.loads(z.read("fabric.mod.json"))
        p = properties()
        assert meta["id"] == "lazymod" and meta["name"] == "LazyMod"
        assert meta["environment"] == "client" and meta["authors"] == ["liwwyy"]
        assert meta["version"] == f"{p['mod_version']}+{p['minecraft_version']}"
        assert meta["depends"]["minecraft"] == "26.2"
        for resource in (meta["icon"], "LICENSE", "THIRD_PARTY_NOTICES.md", "licenses/GPL-3.0.txt", "licenses/LGPL-3.0.txt", "dev/liwwyy/lazymod/LazyModClient.class"):
            assert resource in z.namelist(), resource
        config = json.loads(z.read("lazymod.client.mixins.json"))
        assert config["required"] and config["injectors"]["defaultRequire"] == 1
        for mixin in config["client"]:
            assert config["package"].replace(".", "/") + "/" + mixin + ".class" in z.namelist()
        assert not any(n.startswith("net/minecraft/") for n in z.namelist()), "Minecraft classes must not be bundled"
    print(f"Verified {jar.name}; SHA-256 {sha(jar)}")
    return jar

def main():
    jar = verify(expected_jar())
    if "--stage" in sys.argv:
        staging = ROOT / "build/artifacts"
        staging.mkdir(parents=True, exist_ok=True)
        for old in staging.glob("*.jar"):
            old.unlink()
        shutil.copy2(jar, staging / jar.name)
    if "--receipt" in sys.argv:
        receipt = ROOT / "build/verified-build.json"
        receipt.write_text(json.dumps({"jar": str(jar.relative_to(ROOT)), "sha256": sha(jar), "inputs": fingerprint()}, indent=2)+"\n")

if __name__ == "__main__":
    main()
