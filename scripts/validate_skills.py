#!/usr/bin/env python3
"""Validate .claude/skills wiring: frontmatter, knowledge paths, relative links, catalog rows.

Usage: python3 scripts/validate_skills.py   (exit code 1 on any error)
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SKILLS = ROOT / ".claude" / "skills"
errors, warnings = [], []


def frontmatter(text):
    m = re.match(r"^---\n(.*?)\n---\n", text, re.S)
    if not m:
        return None, text
    fm, body = m.group(1), text[m.end():]
    data, key = {}, None
    for line in fm.splitlines():
        if re.match(r"^[A-Za-z_]+:", line):
            key, _, val = line.partition(":")
            key, val = key.strip(), val.strip()
            data[key] = [] if val == "" else (val if val not in (">-", "|") else "")
        elif key and line.strip().startswith("- "):
            if not isinstance(data[key], list):
                data[key] = []
            data[key].append(line.strip()[2:].strip())
        elif key and isinstance(data.get(key), str):
            data[key] = (data[key] + " " + line.strip()).strip()
    return data, body


catalog = (SKILLS / "README.md").read_text() if (SKILLS / "README.md").exists() else ""
if not catalog:
    errors.append(".claude/skills/README.md missing")

for skill_dir in sorted(p for p in SKILLS.iterdir() if p.is_dir()):
    f = skill_dir / "SKILL.md"
    name = skill_dir.name
    if not f.exists():
        errors.append(f"{name}: SKILL.md missing")
        continue
    text = f.read_text()
    fm, body = frontmatter(text)
    if fm is None:
        errors.append(f"{name}: no YAML frontmatter")
        continue
    if fm.get("name") != name:
        errors.append(f"{name}: frontmatter name '{fm.get('name')}' != folder name")
    desc = fm.get("description") or ""
    if "Use when" not in desc:
        errors.append(f"{name}: description lacks 'Use when'")
    if len(desc) > 1024:
        errors.append(f"{name}: description > 1024 chars ({len(desc)})")
    knowledge = fm.get("knowledge") or []
    if not knowledge:
        errors.append(f"{name}: no knowledge: paths")
    for k in knowledge:
        if not (ROOT / k).exists():
            errors.append(f"{name}: knowledge path not found: {k}")
    for link in re.findall(r"\]\(([^)#\s]+)(?:#[^)]*)?\)", text):
        if link.startswith(("http://", "https://")):
            continue
        if not (f.parent / link).resolve().exists():
            errors.append(f"{name}: broken link {link}")
    if f"]({name}/SKILL.md)" not in catalog:
        errors.append(f"{name}: not listed in .claude/skills/README.md")
    lines = body.count("\n")
    if lines > 110:
        warnings.append(f"{name}: body {lines} lines (> ~100, consider REFERENCE.md)")
    if re.search(r"\.cursor/|make test|FastAPI|Vite|Alembic", text):
        errors.append(f"{name}: references web-stack leftovers (.cursor/, make test, FastAPI, Vite, Alembic)")

for doc in [ROOT / "CLAUDE.md", *sorted((ROOT / "knowledge").glob("*.md"))]:
    if not doc.exists():
        errors.append(f"missing {doc.relative_to(ROOT)}")
        continue
    for link in re.findall(r"\]\(([^)#\s]+)(?:#[^)]*)?\)", doc.read_text()):
        if not link.startswith("http") and not (doc.parent / link).resolve().exists():
            errors.append(f"{doc.relative_to(ROOT)}: broken link {link}")

for w in warnings:
    print("WARN ", w)
for e in errors:
    print("ERROR", e)
count = len([p for p in SKILLS.iterdir() if p.is_dir()])
print(f"{count} skills checked — {len(errors)} error(s), {len(warnings)} warning(s)")
sys.exit(1 if errors else 0)
