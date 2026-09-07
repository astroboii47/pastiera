#!/usr/bin/env python3
"""Generate compact local emoji search assets from Unicode CLDR annotations.

Output format (TSV):
emoji<TAB>tts_name<TAB>keyword1|keyword2|...
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.request
from pathlib import Path


CLDR_BASE = "https://raw.githubusercontent.com/unicode-org/cldr-json/main/cldr-json/cldr-annotations-full/annotations"

MANUAL_ENGLISH_FALLBACKS = {
    "🫱‍🫲": ("handshake", ["handshake", "hands", "agreement"]),
    "🧑‍🐰‍🧑": ("people with bunny ears", ["bunny", "ears", "people"]),
    "👨‍🐰‍👨": ("men with bunny ears", ["bunny", "ears", "men"]),
    "👩‍🐰‍👩": ("women with bunny ears", ["bunny", "ears", "women"]),
    "🧑‍🫯‍🧑": ("people with fight cloud", ["fight", "cloud", "people"]),
    "👨‍🫯‍👨": ("men with fight cloud", ["fight", "cloud", "men"]),
    "👩‍🫯‍👩": ("women with fight cloud", ["fight", "cloud", "women"]),
    "👩‍🤝‍👩": ("women holding hands", ["hands", "holding", "women"]),
    "👩‍🤝‍👨": ("woman and man holding hands", ["hands", "holding", "woman", "man"]),
    "👨‍🤝‍👨": ("men holding hands", ["hands", "holding", "men"]),
    "🧑‍❤️‍💋‍🧑": ("kiss", ["couple", "kiss", "people"]),
    "🧑‍❤️‍🧑": ("couple with heart", ["couple", "heart", "people"]),
}


def emoji_lookup_key(emoji: str) -> str:
    """Match CLDR text/emoji presentation variants to the picker entry."""
    return emoji.replace("\ufe0e", "").replace("\ufe0f", "")


def repo_root_from_script() -> Path:
    return Path(__file__).resolve().parents[1]


def load_project_emojis(emoji_assets_dir: Path) -> set[str]:
    emojis: set[str] = set()
    for path in sorted(emoji_assets_dir.glob("*.txt")):
        if path.name == "minApi.txt":
            continue
        for line in path.read_text(encoding="utf-8").splitlines():
            stripped = line.strip()
            if not stripped:
                continue
            tokens = [token for token in stripped.split(" ") if token]
            emojis.update(tokens)
    return emojis


def emoji_from_unified(value: str) -> str:
    return "".join(chr(int(codepoint, 16)) for codepoint in value.split("-"))


def load_english_fallbacks(emoji_json_path: Path) -> dict[str, tuple[str, list[str]]]:
    """Use the bundled catalog when CLDR has no annotation for a picker emoji."""
    catalog = json.loads(emoji_json_path.read_text(encoding="utf-8"))
    fallbacks: dict[str, tuple[str, list[str]]] = {}
    for item in catalog:
        unified = item.get("unified")
        name = item.get("name")
        if not isinstance(unified, str) or not isinstance(name, str):
            continue
        emoji = emoji_from_unified(unified)
        readable_name = normalize_field(name.lower())
        aliases = [readable_name]
        for alias in item.get("short_names") or []:
            aliases.append(normalize_field(str(alias).replace("_", " ")))
        fallbacks[emoji_lookup_key(emoji)] = (readable_name, list(dict.fromkeys(aliases)))
    for emoji, fallback in MANUAL_ENGLISH_FALLBACKS.items():
        fallbacks[emoji_lookup_key(emoji)] = fallback
    return fallbacks


def fetch_cldr_annotations(locale: str) -> dict[str, dict]:
    url = f"{CLDR_BASE}/{locale}/annotations.json"
    with urllib.request.urlopen(url) as response:
        data = json.load(response)
    return data["annotations"]["annotations"]


def normalize_field(value: str) -> str:
    return " ".join(value.replace("\t", " ").replace("\n", " ").split())


def build_rows(
    locale: str,
    allowed_emojis: set[str],
    english_fallbacks: dict[str, tuple[str, list[str]]],
) -> list[tuple[str, str, list[str]]]:
    raw = fetch_cldr_annotations(locale)
    allowed_by_key = {emoji_lookup_key(emoji): emoji for emoji in allowed_emojis}
    rows: list[tuple[str, str, list[str]]] = []
    for emoji, payload in raw.items():
        output_emoji = allowed_by_key.get(emoji_lookup_key(emoji))
        if output_emoji is None:
            continue
        if not isinstance(payload, dict):
            continue
        tts_values = payload.get("tts") or []
        default_values = payload.get("default") or []
        if not isinstance(tts_values, list):
            tts_values = [str(tts_values)]
        if not isinstance(default_values, list):
            default_values = [str(default_values)]

        name = normalize_field(tts_values[0]) if tts_values else ""
        keywords: list[str] = []
        seen = set()
        for keyword in default_values:
            if not keyword:
                continue
            normalized = normalize_field(str(keyword))
            if not normalized or normalized in seen:
                continue
            seen.add(normalized)
            keywords.append(normalized)

        if not name and not keywords:
            continue
        rows.append((output_emoji, name, keywords))

    if locale == "en":
        present = {emoji_lookup_key(emoji) for emoji, _, _ in rows}
        for emoji in allowed_emojis:
            lookup_key = emoji_lookup_key(emoji)
            fallback = english_fallbacks.get(lookup_key)
            if lookup_key in present or fallback is None:
                continue
            name, keywords = fallback
            rows.append((emoji, name, keywords))

    rows.sort(key=lambda row: row[0])
    return rows


def write_tsv(out_path: Path, rows: list[tuple[str, str, list[str]]]) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with out_path.open("w", encoding="utf-8", newline="\n") as fh:
        for emoji, name, keywords in rows:
            keyword_blob = "|".join(keywords)
            fh.write(f"{emoji}\t{name}\t{keyword_blob}\n")


def main() -> int:
    parser = argparse.ArgumentParser()
    default_locales = ["en", "de", "es", "fr", "hy", "it", "pl", "ru", "uk"]
    parser.add_argument(
        "--locales",
        nargs="+",
        default=default_locales,
        help=f"CLDR locales to generate (default: {' '.join(default_locales)})",
    )
    args = parser.parse_args()

    root = repo_root_from_script()
    emoji_assets_dir = root / "app" / "src" / "main" / "assets" / "common" / "emoji"
    emoji_json_path = root / "app" / "src" / "main" / "assets" / "emoji.json"
    out_dir = root / "app" / "src" / "main" / "assets" / "common" / "emoji_search"

    allowed_emojis = load_project_emojis(emoji_assets_dir)
    if not allowed_emojis:
        print("No project emoji assets found", file=sys.stderr)
        return 1
    english_fallbacks = load_english_fallbacks(emoji_json_path)

    for locale in args.locales:
        rows = build_rows(locale, allowed_emojis, english_fallbacks)
        out_path = out_dir / f"{locale}.tsv"
        write_tsv(out_path, rows)
        print(f"Wrote {out_path} ({len(rows)} rows)")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
