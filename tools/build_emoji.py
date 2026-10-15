#!/usr/bin/env python3
"""
Fabrique la liste d'emoji de Fern Clavier : keyboard/src/main/assets/emoji_fr.tsv

Sources libres, téléchargées une fois :
  1. unicode-emoji-json (MIT) : les emoji par groupe, dans l'ordre officiel.
     https://raw.githubusercontent.com/muan/unicode-emoji-json/main/data-by-group.json
  2. Unicode CLDR, annotations en français (licence Unicode) : nom et mots-clés de chaque emoji.
     https://raw.githubusercontent.com/unicode-org/cldr-json/main/cldr-json/cldr-annotations-full/annotations/fr/annotations.json
  3. (optionnel) CLDR « annotationsDerived » en français : noms des drapeaux, teintes de peau…
     https://raw.githubusercontent.com/unicode-org/cldr-json/main/cldr-json/cldr-annotations-derived-full/annotationsDerived/fr/annotations.json

Sortie : une ligne par emoji « groupe<TAB>emoji<TAB>teintes(0/1)<TAB>nom<TAB>mot-clé|mot-clé|… ».
On garde les emoji jusqu'à la version 15.0 (affichés par Android 14 et 15).

Usage : python3 tools/build_emoji.py data-by-group.json fr_annotations.json [fr_derived.json]
"""
import json
import sys
from pathlib import Path

GROUPS = {
    "smileys_emotion": 0, "people_body": 1, "animals_nature": 2, "food_drink": 3,
    "travel_places": 4, "activities": 5, "objects": 6, "symbols": 7, "flags": 8,
}
MAX_VERSION = 15.0


def main(groups_json: str, annotations_json: str, derived_json: str | None = None) -> None:
    groups = json.load(open(groups_json, encoding="utf-8"))
    ann = dict(json.load(open(annotations_json, encoding="utf-8"))["annotations"]["annotations"])
    if derived_json:
        for k, v in json.load(open(derived_json, encoding="utf-8"))["annotationsDerived"]["annotations"].items():
            ann.setdefault(k, v)
    lines = []
    for g in groups:
        gi = GROUPS.get(g["slug"])
        if gi is None:
            continue
        for e in g["emojis"]:
            if float(e["emoji_version"]) > MAX_VERSION:
                continue
            ch = e["emoji"]
            a = ann.get(ch) or ann.get(ch.replace("️", "")) or {}
            name = (a.get("tts") or [e["name"]])[0]
            keywords = [k.replace("’", "'") for k in a.get("default", [])]
            skin = "1" if e.get("skin_tone_support") else "0"
            lines.append(f"{gi}\t{ch}\t{skin}\t{name}\t{'|'.join(keywords)}")
    target = Path(__file__).resolve().parent.parent / "keyboard/src/main/assets/emoji_fr.tsv"
    target.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{len(lines)} emoji → {target} ({target.stat().st_size // 1024} Ko)")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None)
