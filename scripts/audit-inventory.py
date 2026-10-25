#!/usr/bin/env python3
"""Inventaire statique reproductible ; ne remplace pas une revue sémantique ni les tests."""
from pathlib import Path
from collections import defaultdict
import hashlib

root = Path(__file__).resolve().parents[1]
groups = defaultdict(list)
for file in sorted((root / "src/main/java").rglob("*.java")):
    relative = file.relative_to(root / "src/main/java/fr/cliker/clikerv2")
    groups[str(relative.parent)].append(file)
resources = sorted((root / "src/main/resources").rglob("*.yml"))
lines = ["# Inventaire statique de l’audit ClickHunter", "",
         "Produit par `python3 scripts/audit-inventory.py`. Chaque fichier de production est indexé ; cette liste n’est pas une certification de son comportement.", "",
         "| Composant | Fichiers Java | Lignes |", "| --- | ---: | ---: |"]
for package, files in sorted(groups.items()):
    lines.append(f"| {package if package != '.' else 'noyau'} | {len(files)} | {sum(len(p.read_text().splitlines()) for p in files)} |")
lines += ["", f"Total : **{sum(map(len, groups.values()))} fichiers Java**, **{len(resources)} configurations YAML**.", "",
          "## Fichiers indexés", "", "| Fichier | SHA-256 au moment de l’inventaire |", "| --- | --- |"]
for file in [p for paths in groups.values() for p in paths] + resources:
    path = file.relative_to(root).as_posix()
    lines.append(f"| [{path}](../{path}) | `{hashlib.sha256(file.read_bytes()).hexdigest()}` |")
(root / "docs/INVENTAIRE-AUDIT.md").write_text("\n".join(lines) + "\n")
print(f"Inventaire : {sum(map(len, groups.values()))} fichiers Java, {len(resources)} fichiers YAML.")
