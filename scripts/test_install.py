# SPDX-License-Identifier: MIT
import importlib.util
import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("install_local", Path(__file__).with_name("install-local.py"))
install = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(install)

def jar(path, modid):
    with zipfile.ZipFile(path, "w") as z:
        z.writestr("fabric.mod.json", json.dumps({"id": modid}))
        z.writestr("payload", path.name)
    return path

class InstallTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.mods = self.root / "mods with spaces"
        self.mods.mkdir()
        self.source = jar(self.root / "lazymod-new.jar", "lazymod")
        self.backups = self.root / "backups"

    def test_only_metadata_identified_lazymod_jars_are_replaced(self):
        old = jar(self.mods / "unusual-old-name.jar", "lazymod")
        other = jar(self.mods / "lazymod-looking-name.jar", "unrelated")
        other_bytes = other.read_bytes()
        install.install_verified(self.source, self.mods, self.backups)
        self.assertFalse(old.exists())
        self.assertEqual(other_bytes, other.read_bytes())
        self.assertEqual(self.source.read_bytes(), (self.mods / self.source.name).read_bytes())
        self.assertEqual(1, len(list(self.backups.rglob(old.name))))

    def test_unrelated_target_collision_is_refused(self):
        target = jar(self.mods / self.source.name, "unrelated")
        before = target.read_bytes()
        with self.assertRaises(ValueError):
            install.install_verified(self.source, self.mods, self.backups)
        self.assertEqual(before, target.read_bytes())

    def test_install_failure_restores_previous_jar(self):
        old = jar(self.mods / "old.jar", "lazymod")
        before = old.read_bytes()
        with patch.object(install.os, "replace", side_effect=OSError("simulated failure")):
            with self.assertRaises(OSError):
                install.install_verified(self.source, self.mods, self.backups)
        self.assertEqual(before, old.read_bytes())
        self.assertFalse((self.mods / self.source.name).exists())
        self.assertEqual([], list(self.mods.glob("*.tmp")))

if __name__ == "__main__":
    unittest.main()
