"""Checks translation keys in source files and the built jar."""
import io
import json
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".")
src = root / "src/main/java"
lang_dir = root / "src/main/resources/assets/primeitemvalue/lang"

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

used = set()
pattern = re.compile(r'(?:Component\.translatable|I18n\.get|Lang\.get)\(\s*"([\w.]+)"')
constant = re.compile(r'"((?:primeitemvalue|key\.primeitemvalue)\.[\w.]+)"')

for path in src.rglob("*.java"):
    text = path.read_text(encoding="utf-8")

    used.update(k for k in pattern.findall(text) if not k.endswith('.'))

    used.update(k for k in constant.findall(text)
                if not k.endswith('.json') and not k.endswith('.'))

files = sorted(lang_dir.glob("*.json"))
tables = {}

for path in files:
    tables[path.name] = json.loads(path.read_text(encoding="utf-8"))

print(f"Benutzte Schluessel im Code: {len(used)}")
fails = []

for name, table in tables.items():
    missing = sorted(k for k in used if k not in table)
    print(f"  {name}: {len(table)} Eintraege, {len(missing)} fehlend")

    for key in missing:
        fails.append(f"{name}: {key} fehlt")

all_keys = set().union(*tables.values()) if tables else set()

for name, table in tables.items():
    for key in sorted(all_keys - set(table)):
        fails.append(f"{name}: {key} fehlt (steht in einer anderen Sprache)")

unused = sorted(all_keys - used)

if unused:
    print(f"  ungenutzt im Code: {', '.join(unused)}")

jars = sorted((root / "build/libs").glob("*.jar")) if (root / "build/libs").is_dir() else []
jars = [j for j in jars if not j.name.endswith("-sources.jar")]

if jars:
    import zipfile

    jar = jars[0]
    print()
    print(f"== Sprachdateien im gebauten Jar ({jar.name}) ==")

    with zipfile.ZipFile(jar) as z:
        for name, table in tables.items():
            path = "assets/primeitemvalue/lang/" + name

            try:
                packed = json.loads(z.read(path).decode("utf-8"))
            except KeyError:
                fails.append(f"{name}: liegt nicht im Jar")
                print(f"   FAIL {name} fehlt im Jar")
                continue

            missing = sorted(k for k in table if k not in packed)

            if missing:
                fails.append(f"{name}: Jar ist aelter als die Quelle, {len(missing)} Schluessel fehlen"
                             f" (z.B. {missing[0]}) - neu bauen")
                print(f"   FAIL {name}: {len(missing)} Schluessel fehlen im Jar,"
                      f" z.B. {missing[0]}")
            else:
                print(f"   ok   {name}: Jar ist auf dem Stand der Quelle")

print()

if fails:
    print(f"{len(fails)} Problem(e):")
    for f in fails:
        print("  -", f)
    sys.exit(1)

print("Alle Schluessel in allen Sprachen vorhanden.")
