#!/usr/bin/env python3
"""
Fabrique le dictionnaire de Fern Clavier : keyboard/src/main/assets/dico_fr.txt.gz

Deux sources libres, téléchargées une fois :
  1. an-array-of-french-words (MIT) : 336 000 mots français bien orthographiés (avec accents).
     https://raw.githubusercontent.com/words/an-array-of-french-words/master/index.json
  2. FrequencyWords, OpenSubtitles 2018 (CC BY-SA 4.0) : fréquence des mots dans des sous-titres de films,
     donc du français PARLÉ (c'est ce qui donne « ouais », « chelou », « m'ennuie »…).
     https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/fr/fr_full.txt

Règles :
  - tous les mots de la liste 1, avec leur fréquence dans les sous-titres (0 si absents) ;
  - + les mots des sous-titres absents de la liste 1 s'ils sont assez fréquents (≥ 50), SAUF :
      · les formes élidées (« m'ennuie ») : le clavier sépare lui-même « m' » et « ennuie » ;
      · les fautes d'accent courantes des sous-titres (« ete », « deja ») : un mot sans accent qui
        correspond à un vrai mot accentué plus fréquent est écarté (sinon il ne serait jamais corrigé) ;
      · les fautes de frappe courantes (« apeller ») : un mot à UNE faute d'un vrai mot 20 fois plus
        fréquent est écarté ;
  - + une liste de formes familières / SMS (mdr, jsp, tkt, chui…), toujours connues.

Sortie : une ligne « mot<TAB>fréquence », triée par forme « sans accents » (pour la recherche par début de mot).

Usage : python3 tools/build_dictionary.py fr_words.json fr_full.txt
"""
import gzip
import json
import re
import sys
from pathlib import Path

# Doit rester identique à Words.fold() côté Kotlin (un caractère → un caractère).
FOLD = str.maketrans({
    "à": "a", "â": "a", "ä": "a", "á": "a", "æ": "a",
    "é": "e", "è": "e", "ê": "e", "ë": "e",
    "î": "i", "ï": "i", "í": "i", "ì": "i",
    "ô": "o", "ö": "o", "ó": "o", "ò": "o", "œ": "o",
    "ù": "u", "û": "u", "ü": "u", "ú": "u",
    "ç": "c", "ÿ": "y", "ñ": "n",
    "’": "'",
})


def fold(w: str) -> str:
    return w.lower().translate(FOLD)


WORD = re.compile(r"^[a-zàâäáæéèêëîïíìôöóòœùûüúçÿñ'-]+$")
ELIDED = re.compile(r"^(c|d|j|l|m|n|s|t|qu|jusqu|lorsqu|puisqu|quoiqu|quelqu)'.+")

# La liste 1 n'a AUCUN mot avec apostrophe, et les sous-titres coupent « quelqu'un » en deux :
# on ajoute ces mots à la main (fréquence approximative).
APOSTROPHE = {
    "aujourd'hui": 117159, "quelqu'un": 150000, "quelqu'une": 4000, "presqu'île": 300,
    "prud'homme": 50, "prud'hommes": 200, "entr'acte": 30, "grand'mère": 20,
}

# Fautes fréquentes dans les sous-titres, à ne jamais considérer comme justes.
JAMAIS = {"quelquechose", "quelque-chose", "quelquepart", "quelque-part", "quelqun", "quelquun", "aujourdhui",
          "aujourd", "quelqu", "parcque", "parceque", "entrain"}

# Formes familières et SMS : toujours connues (jamais « corrigées »), proposées en complétion.
FAMILIER = """
mdr mdrr mdrrr ptdr lol xd jsp jpp tkt tqt stp svp bcp pk pq pcq psk ptn tlm tt cc slt bjr bsr dsl mrc mrci
jtm nn ui ouais ouai ouep wesh wsh ok oki okay ouf bah ben bon euh heu hein hum mouais nan chui chuis jsuis
chelou relou vénère vener kiffe kiffes kiffent kiffer kiffé kiffée kiffais kiffait kiffons grave frr bg bff
osef askip oklm wtf omg dac dacc bisous biz bizz bisou coucou rdv qqch qqn qlq tjr tjrs mtn mnt auj ajd ajrd
jvais jsais jcrois jpense jveux jfais tfais ptet ptit ptite ptits ptites chanmé zarbi vrm vrmt haha hahaha
ahah ahahah hihi héhé bref genre trop insta snap whatsapp signal vinci esb alternance
resto restau restos ciné appart apparts dispo dispos perso persos prof profs ado ados apéro apéros ordi ordis
dico info infos pub fac manif sympa aprèm coloc frigo labo véto expo promo récré bac gym foot mytho rageux
koi ke ki kan kel kelle pourkoi qd dc ds pr mm jte jme jle jla jvois jmen tinkiet feta ptdrr bb
""".split()


ALPHABET = "abcdefghijklmnopqrstuvwxyzàâäéèêëîïôöùûüçœæ'-"


def edits1(w: str):
    """Tous les mots à une faute de w (lettre en trop, en moins, inversée ou remplacée)."""
    splits = [(w[:i], w[i:]) for i in range(len(w) + 1)]
    for a, b in splits:
        if b:
            yield a + b[1:]
        if len(b) > 1:
            yield a + b[1] + b[0] + b[2:]
        for c in ALPHABET:
            if b:
                yield a + c + b[1:]
            yield a + c + b


def main(words_json: str, subtitles: str) -> None:
    valid = {w.lower() for w in json.load(open(words_json, encoding="utf-8"))}
    counts: dict[str, int] = {}
    for line in open(subtitles, encoding="utf-8"):
        parts = line.rstrip("\n").split(" ")
        if len(parts) != 2 or not parts[1].isdigit():
            continue
        w = parts[0].lower().replace("’", "'")
        counts[w] = counts.get(w, 0) + int(parts[1])

    entries: dict[str, int] = {w: counts.get(w, 0) for w in valid}

    # Meilleure fréquence par forme sans accents parmi les mots valides (pour repérer les fautes d'accent).
    best_by_fold: dict[str, int] = {}
    for w, c in entries.items():
        f = fold(w)
        best_by_fold[f] = max(best_by_fold.get(f, -1), c)

    def reference(e: str) -> int:
        """Fréquence d'un vrai mot voisin : de la liste valide, ou très courant dans les sous-titres."""
        if e in valid:
            return entries.get(e, 0)
        n = counts.get(e, 0)
        return n if n >= 1000 and not ELIDED.match(e) else 0

    def glued(w: str) -> bool:
        """Deux mots courants collés (« quelquechose » = « quelque » + « chose »)."""
        return any(
            entries.get(w[:i], 0) >= 1000 and entries.get(w[i:], 0) >= 1000 and w[:i] in valid and w[i:] in valid
            for i in range(2, len(w) - 1)
        )

    extras: list[str] = []
    for w, c in counts.items():
        if w in entries or c < 50 or len(w) < 2:
            continue
        if not WORD.match(w) or ELIDED.match(w) or w.endswith("'") or w.startswith(("'", "-")) or w.endswith("-"):
            continue
        f = fold(w)
        if w == f and f in best_by_fold and best_by_fold[f] >= c:
            continue  # « ete », « deja » : faute d'accent d'un mot valide plus fréquent
        if any(reference(e) >= 20 * c for e in edits1(w)):
            continue  # « apeller » : faute de frappe d'un mot valide bien plus fréquent
        if w in JAMAIS or ("-" not in w and glued(w)):
            continue  # « quelquechose » : deux mots collés
        extras.append(w)
        entries[w] = c

    for w, c in APOSTROPHE.items():
        entries[w] = max(entries.get(w, 0), c)
    for w in JAMAIS:
        if w not in valid:
            entries.pop(w, None)

    for w in FAMILIER:
        entries[w] = max(entries.get(w, 0), 500)

    out = sorted(entries.items(), key=lambda kv: (fold(kv[0]), kv[0]))
    target = Path(__file__).resolve().parent.parent / "keyboard/src/main/assets/dico_fr.txt.gz"
    target.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(target, "wt", encoding="utf-8", compresslevel=9) as f:
        for w, c in out:
            f.write(f"{w}\t{c}\n")
    print(f"{len(out)} mots ({len(extras)} venus des sous-titres) → {target} ({target.stat().st_size // 1024} Ko)")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
