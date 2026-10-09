"""Check that the tiny public installers can load before AutoModpack connects.

Run with ``python -m unittest discover -s tests -p 'test_client_installer_bootstrap.py'``
from the stable-runtime overlay. PSYCHIATRYK_MRPACK and PSYCHIATRYK_CF_ZIP may
point to newly built candidates; by default the test inspects ready-bootstrap-deps/.
"""

from __future__ import annotations

import hashlib
import io
import json
import os
import tomllib
import unittest
import zipfile
from pathlib import Path


HERE = Path(__file__).resolve().parent
RUNTIME = HERE.parent
WORK = HERE.parents[4]
DEFAULT_INSTALLERS = RUNTIME / "build/client-installer-3.0.10/ready-bootstrap-deps"
MRPACK = Path(os.environ.get("PSYCHIATRYK_MRPACK", DEFAULT_INSTALLERS / "goplanska-bmc5-v53-installer-3.0.10.mrpack"))
CF_ZIP = Path(os.environ.get("PSYCHIATRYK_CF_ZIP", DEFAULT_INSTALLERS / "goplanska-bmc5-v53-curseforge-3.0.10.zip"))
SERVER_MODS = WORK / "migration/private_release/workstations-20261008/live-mod-qa/mods"

# These are the exact NeoForge 1.21.1 files in the live server's mod inventory.
# Only official launcher references belong in the public installer.
REQUIRED = {
    "accessories": {
        "filename": "accessories-neoforge-1.1.0-beta.53+1.21.1.jar",
        "sha256": "10017a3da78ea63e9ece27a1ca32f8cf490362f348778cf8cb759e7282f3beb0",
        "sha1": "77d75c2e13cfdf56a45cdd29806c1c97c3d250fc",
        "sha512": "baafa9a5e48c17c243d45b6260095ffef2ad00e4e970aafc5b1ca9ab5f4a542b18b0fb35d4584318791edbaf00a3c44806f62062513d991383192aca4df27a07",
        "size": 1078697,
        "modrinth_project": "jtmvUHXj",
        "modrinth_version": "Fb55Fgjz",
        "curseforge_project": 938917,
        "curseforge_file": 7583320,
    },
    "owo": {
        "filename": "owo-lib-neoforge-0.12.15.5-beta.1+1.21.jar",
        "sha256": "de6ed336bd80154b7241a7b3276694befc1c94550add8bcdfe7f82e5172fd13d",
        "sha1": "48dda11a6710591cf162bdbedf982ea21dd1f2ed",
        "sha512": "4de5c5d52139244b8c5260d641087664d992624b822599a32e03c08eb133be854a2f413667dbca1e55772445b04a70210c17b3bc13e3c88e425e7d928104b9fa",
        "size": 1221583,
        "modrinth_project": "ccKDOlHs",
        "modrinth_version": "NMCHU6DZ",
        "curseforge_project": 532610,
        "curseforge_file": 6785734,
    },
}


def descriptor(jar: bytes) -> dict:
    with zipfile.ZipFile(io.BytesIO(jar)) as archive:
        return tomllib.loads(archive.read("META-INF/neoforge.mods.toml").decode("utf-8"))


class ClientInstallerBootstrapTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        with zipfile.ZipFile(MRPACK) as prism, zipfile.ZipFile(CF_ZIP) as curseforge:
            cls.mr_index = json.loads(prism.read("modrinth.index.json"))
            cls.cf_manifest = json.loads(curseforge.read("manifest.json"))
            cls.mr_roles = prism.read("overrides/mods/psychiatryk_roles-3.0.0-bmc5.jar")
            cls.cf_roles = curseforge.read("overrides/mods/psychiatryk_roles-3.0.0-bmc5.jar")

    def test_loader_and_embedded_roles_match(self) -> None:
        self.assertEqual(self.mr_index["dependencies"], {"minecraft": "1.21.1", "neoforge": "21.1.252"})
        self.assertEqual(self.cf_manifest["minecraft"]["modLoaders"], [{"id": "neoforge-21.1.252", "primary": True}])
        self.assertEqual(self.mr_roles, self.cf_roles)
        self.assertIn("psychiatryk_roles", {mod["modId"] for mod in descriptor(self.mr_roles)["mods"]})

    def test_required_upstream_references_are_exact(self) -> None:
        prism_files = {entry["path"]: entry for entry in self.mr_index["files"]}
        curseforge_files = {(entry["projectID"], entry["fileID"]) for entry in self.cf_manifest["files"]}
        for mod_id, spec in REQUIRED.items():
            with self.subTest(mod=mod_id):
                path = f"mods/{spec['filename']}"
                self.assertIn(path, prism_files)
                entry = prism_files[path]
                self.assertEqual(entry["fileSize"], spec["size"])
                self.assertEqual(entry["hashes"]["sha1"], spec["sha1"])
                self.assertEqual(entry["hashes"]["sha512"], spec["sha512"])
                self.assertEqual(
                    entry["downloads"],
                    [f"https://cdn.modrinth.com/data/{spec['modrinth_project']}/versions/{spec['modrinth_version']}/{spec['filename']}"],
                )
                self.assertIn((spec["curseforge_project"], spec["curseforge_file"]), curseforge_files)
                # The private live-pack inventory is optional on public checkouts.
                source_jar = SERVER_MODS / spec["filename"]
                if source_jar.exists():
                    jar = source_jar.read_bytes()
                    self.assertEqual(hashlib.sha256(jar).hexdigest(), spec["sha256"])
                    self.assertEqual(hashlib.sha1(jar).hexdigest(), spec["sha1"])
                    self.assertEqual(hashlib.sha512(jar).hexdigest(), spec["sha512"])
                    self.assertEqual(len(jar), spec["size"])
                    self.assertIn(mod_id, {mod["modId"] for mod in descriptor(jar)["mods"]})

    def test_embedded_roles_required_dependency_closure(self) -> None:
        prism_files = {entry["path"] for entry in self.mr_index["files"]}
        curseforge_files = {(entry["projectID"], entry["fileID"]) for entry in self.cf_manifest["files"]}
        installed = {"minecraft", "neoforge"}
        installed.update(mod["modId"] for mod in descriptor(self.mr_roles)["mods"])
        for mod_id, spec in REQUIRED.items():
            if (
                f"mods/{spec['filename']}" in prism_files
                and (spec["curseforge_project"], spec["curseforge_file"]) in curseforge_files
            ):
                installed.add(mod_id)
        # The other three bootstrap references were present before this fix.
        installed.update(("automodpack", "kubejs", "rhino"))
        # This edge comes from the pinned Accessories JAR's NeoForge metadata.
        self.assertIn("owo", installed, "accessories requires owo before AutoModpack can connect")
        jars = [self.mr_roles]
        jars.extend(
            source.read_bytes()
            for spec in REQUIRED.values()
            if (source := SERVER_MODS / spec["filename"]).exists()
        )
        for jar in jars:
            info = descriptor(jar)
            for mod in info["mods"]:
                for dep in info.get("dependencies", {}).get(mod["modId"], []):
                    if dep.get("type", "required") == "required" and dep.get("side", "BOTH") in ("BOTH", "CLIENT"):
                        self.assertIn(dep["modId"], installed, f"{mod['modId']} requires {dep['modId']} before AutoModpack can connect")


if __name__ == "__main__":
    unittest.main()
