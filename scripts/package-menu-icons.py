#!/usr/bin/env python3
"""Emballe les icônes natives ; --base fusionne un pack existant sans écraser ses autres assets."""
from pathlib import Path, PurePosixPath
import argparse, zipfile
parser=argparse.ArgumentParser()
parser.add_argument('--output',default='dist/ClickHunter-icones.zip')
parser.add_argument('--base',help='Pack existant à fusionner (Minecraft 1.21.4)')
args=parser.parse_args()
root=Path(__file__).resolve().parents[1]/'resource-pack'
output=Path(args.output);output.parent.mkdir(parents=True,exist_ok=True)
files={}
if args.base:
 with zipfile.ZipFile(args.base) as z:
  for entry in z.infolist():
   name=PurePosixPath(entry.filename)
   if name.is_absolute() or '..' in name.parts:raise ValueError('Chemin ZIP invalide')
   if not entry.is_dir():files[str(name)]=z.read(entry)
for path in root.rglob('*'):
 if path.is_file():
  name=path.relative_to(root).as_posix()
  if name=='pack.mcmeta' and args.base:continue
  files[name]=path.read_bytes()
with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as z:
 for name,data in sorted(files.items()):z.writestr(name,data)
print(output,len(files),'fichiers')
