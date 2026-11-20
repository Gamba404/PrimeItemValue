"""Checks mixin targets and overridden methods against the Minecraft jar."""
import re, subprocess, sys, io, pathlib
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

jar, javap, src = sys.argv[1], sys.argv[2], sys.argv[3]
fails = []

def declared(cls):

    out = subprocess.run([javap, '-p', '-cp', jar, cls], capture_output=True, text=True,
                         errors='replace').stdout
    if not out.strip():
        return None, None
    fields, methods = set(), set()
    for line in out.splitlines()[1:]:
        line = line.strip().rstrip(';')
        if not line or line.endswith('{') or line.startswith('static {'):
            continue
        if '(' in line:
            name = line.split('(')[0].split()[-1]
            methods.add(name)
        else:
            parts = line.split()
            if parts:
                fields.add(parts[-1])
    return fields, methods

for path in sorted(pathlib.Path(src).rglob('*.java')):
    text = path.read_text(encoding='utf-8')
    m = re.search(r'@Mixin\(([\w.]+)\.class\)', text)
    if not m:
        continue
    simple = m.group(1)
    imp = re.search(r'import ([\w.]+\.' + re.escape(simple) + r');', text)
    target = imp.group(1) if imp else simple

    print(f"== {path.name} -> {target}")
    fields, methods = declared(target)
    if fields is None:
        fails.append(f"{path.name}: Zielklasse {target} nicht in der Jar gefunden")
        print("   FAIL Zielklasse nicht gefunden")
        continue

    for fname in re.findall(r'@Shadow\s*(?:@Final\s*)?(?:\n\s*)*(?:protected|private|public)?\s*(?:final\s+)?[\w.<>\[\]]+\s+(\w+)\s*;', text):
        ok = fname in fields
        print(f"   {'ok  ' if ok else 'FAIL'} @Shadow Feld {fname}")
        if not ok:
            fails.append(f"{path.name}: @Shadow {fname} nicht in {target} deklariert")

    for aname in re.findall(r'@Accessor\("(\w+)"\)', text):
        ok = aname in fields
        print(f"   {'ok  ' if ok else 'FAIL'} @Accessor Feld {aname}")
        if not ok:
            fails.append(f"{path.name}: @Accessor {aname} nicht in {target} deklariert")

    for mname in re.findall(r'@Inject\(\s*method\s*=\s*"(\w+)"', text):
        ok = mname in methods
        print(f"   {'ok  ' if ok else 'FAIL'} @Inject Methode {mname}")
        if not ok:
            fails.append(f"{path.name}: @Inject {mname} nicht in {target} deklariert")

print()
print("== Unterklassen, die ein injiziertes Ziel ueberschreiben ==")

import zipfile

gui = "net/minecraft/client/gui/screens/"
with zipfile.ZipFile(jar) as z:
    candidates = [n[:-6].replace("/", ".") for n in z.namelist()
                  if n.startswith(gui) and n.endswith(".class") and "$" not in n]

parents = {}
for cls in candidates:
    out = subprocess.run([javap, '-cp', jar, cls], capture_output=True, text=True,
                         errors='replace').stdout

    lines = out.splitlines()
    head = lines[1] if len(lines) > 1 else ""
    while re.search(r'<[^<>]*>', head):
        head = re.sub(r'<[^<>]*>', '', head)

    m = re.search(r'\bextends\s+([\w.]+)', head)
    if m:
        parents[cls] = (m.group(1), set(re.findall(r'\b(\w+)\s*\(', out)))

bodies = {}

def claims_first(cls, method):
    """Returns whether an override calls super and branches before that call."""
    if cls not in bodies:
        bodies[cls] = subprocess.run([javap, '-p', '-c', '-cp', jar, cls],
                                     capture_output=True, text=True,
                                     errors='replace').stdout

    out = bodies[cls]
    m = re.search(r'\n  (?:public|protected|private).*\b' + method + r'\(', out)

    if not m:
        return True, False

    rest = out[m.end():]
    nxt = re.search(r'\n  (?:public|protected|private|static)', rest)
    code = rest[:nxt.start()] if nxt else rest

    sup = re.search(r'invokespecial.*?\.' + method + r':', code)

    if sup is None:
        return False, False

    return True, re.search(r'\b[ia]return\b', code[:sup.start()]) is not None

def inherits(cls, ancestor, seen=None):
    seen = seen or set()
    while cls in parents and cls not in seen:
        seen.add(cls)
        parent = parents[cls][0]
        if parent.split('.')[-1] == ancestor.split('.')[-1]:
            return True
        cls = parent
    return False

guarded = {}
for path in sorted(pathlib.Path(src).rglob("*.java")):
    text = path.read_text(encoding="utf-8")
    m = re.search(r'@Mixin\(\s*([\w.]+)\.class', text)
    if not m:
        continue
    tgt = m.group(1).split(".")[-1]
    for mname in re.findall(r'@Inject\(\s*method\s*=\s*"(\w+)"', text):
        guarded.setdefault((tgt, mname), []).append(path.name)

for path in sorted(pathlib.Path(src).rglob("*.java")):
    text = path.read_text(encoding="utf-8")
    m = re.search(r'@Mixin\(\s*([\w.]+)\.class', text)
    if not m:
        continue
    target = m.group(1)
    simple = target.split(".")[-1]
    for mname in re.findall(r'@Inject\(\s*method\s*=\s*"(\w+)"', text):
        for cls, (parent, methods) in sorted(parents.items()):
            if cls.split('.')[-1] == simple or not inherits(cls, target):
                continue
            if mname not in methods:
                continue
            sub = cls.split(".")[-1]
            covered = (sub, mname) in guarded
            calls_super, decides_first = claims_first(cls, mname)
            risky = (not calls_super) or decides_first

            if covered:
                print(f"   ok   {simple}.{mname}: {sub} ueberschreibt, eigener Mixin greift vor")
                continue

            if not risky:

                continue

            why = "ruft super nie auf" if not calls_super else "entscheidet vor super"

            print(f"   FAIL {simple}.{mname}: {sub} {why}, kein eigener Mixin")
            fails.append(f"{path.name}: {sub}.{mname} {why};"
                         f" die Injection auf {simple} laeuft dort zu spaet oder gar nicht")

print()
if fails:
    print(f"{len(fails)} Problem(e):")
    for f in fails:
        print("  -", f)
    sys.exit(1)
print("Alle Mixin-Ziele vorhanden.")
