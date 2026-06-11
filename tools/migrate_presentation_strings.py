#!/usr/bin/env python3
"""
One-off helper: apply safe literal→stringResource replacements in Kotlin Compose files.
Run from repo root. Review diff before committing.
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCOPE = [
    ROOT / "app/src/main/java/com/theveloper/pixelplay/presentation",
    ROOT / "app/src/main/java/com/theveloper/pixelplay/ui/glancewidget",
]

STRING_IMPORT = "import androidx.compose.ui.res.stringResource"
R_IMPORT = "import com.theveloper.pixelplay.R"

# (pattern, replacement) regex or (literal_old, literal_new) — order matters (longer first)
REPLACEMENTS: list[tuple[str, str]] = [
    ('contentDescription = "Back"', 'contentDescription = stringResource(R.string.auth_cd_back)'),
    ('contentDescription = "Options"', 'contentDescription = stringResource(R.string.cd_options)'),
    ('contentDescription = "Play Album"', 'contentDescription = stringResource(R.string.cd_play_album)'),
    ('contentDescription = "Shuffle play album"', 'contentDescription = stringResource(R.string.cd_shuffle_play_album)'),
    ('contentDescription = "Shuffle Play"', 'contentDescription = stringResource(R.string.cd_shuffle_play)'),
    ('contentDescription = "Generic Artist"', 'contentDescription = stringResource(R.string.cd_generic_artist)'),
    ('Text("Cancel")', 'Text(stringResource(R.string.cancel))'),
    ('Text("OK")', 'Text(stringResource(R.string.ok))'),
    ('Text("Dismiss")', 'Text(stringResource(R.string.dismiss))'),
    ('contentDescription = "Dismiss"', 'contentDescription = stringResource(R.string.dismiss)'),
    ('contentDescription = "Play"', 'contentDescription = stringResource(R.string.cd_play)'),
    ('contentDescription = "Play/Pause"', 'contentDescription = stringResource(R.string.mashup_cd_play_pause)'),
    ('contentDescription = "Song Cover"', 'contentDescription = stringResource(R.string.cd_song_cover)'),
    ('Text("Delete")', 'Text(stringResource(R.string.delete_action))'),
    # Legacy literals found in some source files; keep these explicit Spanish matches
    # so the migration reliably rewrites them to localized string resources.
    ('contentDescription = "Limpiar"', 'contentDescription = stringResource(R.string.cd_clear_search_query)'),
    ('contentDescription = "Buscar"', 'contentDescription = stringResource(R.string.cd_search_icon)'),
]


def needs_string_resource(text: str) -> bool:
    return "stringResource(" in text and STRING_IMPORT not in text


def needs_r_import(text: str) -> bool:
    return "R.string." in text and R_IMPORT not in text


def insert_imports(text: str) -> str:
    lines = text.splitlines(keepends=True)
    if not lines:
        return text
    insert_at = 0
    for i, line in enumerate(lines):
        if line.startswith("import "):
            insert_at = i + 1
    to_add = []
    if needs_string_resource(text):
        to_add.append(STRING_IMPORT + "\n")
    if needs_r_import(text):
        to_add.append(R_IMPORT + "\n")
    if not to_add:
        return text
    return "".join(lines[:insert_at] + to_add + lines[insert_at:])


def process_file(path: Path) -> bool:
    raw = path.read_text(encoding="utf-8")
    text = raw
    changed = False
    for old, new in REPLACEMENTS:
        if old in text:
            text = text.replace(old, new)
            changed = True
    if not changed:
        return False
    text = insert_imports(text)
    path.write_text(text, encoding="utf-8")
    return True


def main() -> int:
    n = 0
    for base in SCOPE:
        if not base.exists():
            continue
        for path in base.rglob("*.kt"):
            if process_file(path):
                print("updated", path.relative_to(ROOT))
                n += 1
    print(f"Done. Files modified: {n}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
