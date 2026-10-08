# Prepared Roles 3.0.10 homepage (not published)

`index-3.0.10-prepared.html` and the packaging source `index.html` are built
identically by `prepare_site_3010.py` from the
current live timeline source at
`work/psychiatryk-workstations-20261008/bmc5-v53/overlays/item-unlocks/website/index.html`.
The public page at the start of staging had SHA-256
`89b146d5eafbde84beac694b0dbff815ec858eb5f9d8643cb864b7292dc95792`
and differed from that source only by a final newline. Its timetable fetched
`/item-unlocks.json` once per minute and showed Hunter on 8 October and
Enderologist on 1 November. The prepared page preserves that exact timetable
code and changes only the current release labels, loader instructions,
versioned installer/addon links, and a short 3.0.10 update explanation. Older
3.0.8 changelog sections and their historical hashes remain historical.
The current staged `index.html` SHA-256 is
`827194ecf2b724f2ff812120b90598a1409e246d30b960bb2ad4f975c2617861`.

The new links are intentionally unpublished until all three artifacts exist:

- `goplanska-bmc5-v53-installer-3.0.10.mrpack`
- `goplanska-bmc5-v53-curseforge-3.0.10.zip`
- `goplanska-bmc5-v53-roles-3.0.10-addon.zip`

The locally qualified public files are in
`bmc5-v53/overlays/stable-runtime/build/client-installer-3.0.10/ready/`.
Their current SHA-256 and sizes are, respectively:

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| Prism installer | 10,227,365 | `3025198f21f7d47b17242b71e3ae4093d5d5f7e0e923407b3172be0305eb940f` |
| CurseForge installer | 10,226,775 | `dccbb45c1ab7f8aabf1be172b60cfb9266226aab29353889df1bcc3383fb94d4` |
| Manual first-party addon | 10,229,012 | `14e48ee137e62b2f150bdf5b78f422bd20c416cffc4af17db719868d3566ddbb` |

Read-only SFTP preflight on 9 October found none of these versioned names in
`/site35697/public/`. The host filesystem reported 33.3 GB available; the
three public files total 30,683,152 bytes. SFTP does not disclose a separate
per-account quota, so filesystem space is confirmed but a smaller account
quota is not. The private 683 MB full-client ZIP is excluded from publication:
it contains third-party mod binaries and has unresolved non-mod files and
configuration differences. The public Prism and CurseForge files are online
AutoModpack launchers, and the page labels them as installers rather than a
complete offline pack.

Both installer manifests must pin NeoForge **21.1.252** and the frozen Roles
3.0.10 JAR. Before publication, check the public page has not changed from
the above baseline, confirm the live server runs that frozen JAR and its
AutoModpack feed, confirm the displayed AutoModpack trust fingerprint still
matches the live server after restart, and compare each upload's SFTP readback hash with the
qualified local artifact. Upload the versioned artifacts first; publish the
homepage last. If the current public page has changed, rebase this preparation
on the newly downloaded page and inspect the diff before publishing.

Use `work/migration/site_sftp.py` for publication to
`/site35697/public/` on `web1.titanaxe.com`. It uses SFTP with strict SSH
host-key checking, uploads to a temporary name, verifies downloaded bytes,
renames atomically and keeps a prior homepage backup. Avoid the older
`work/publish_website.py` because it uses plaintext FTP.

Local dry-run checks: from `/home/aridlin/Documents/Codex/2026-09-28/the`, run
`python work/psychiatryk-stable-runtime-20261008/bmc5-v53/website/prepare_site_3010.py`,
then validate the HTML anchors,
paired `data-pl`/`data-en` text, JavaScript syntax, and unchanged timeline
markers. A headless Chromium render at 1440 pixels and a narrow 500-pixel
viewport showed the new update section and the existing Paper Ink layout. The
file-origin preview cannot fetch the live JSON feed, so it is visual layout QA,
not proof that the published calendar or client join works.
