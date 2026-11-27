---
name: localization-check
description: Check STOP-TB translations — string keys missing between values (en), values-hi and values-as, format-argument mismatches, and hardcoded user-facing text in layouts and Kotlin. Report only; never edits existing strings.
---

# Localization Check (STOP-TB)

Locales: `app/src/main/res/values/` (en, default), `values-hi/` (Hindi), `values-as/` (Assamese). `resourceConfigurations = ['en', 'hi', 'as']`.

## Steps (read-only)
1. **Missing keys**: compare `<string name>`, `<plurals name>` and `<string-array name>` across the three `strings.xml` files (and any other `values*/` string files):
   ```bash
   cd app/src/main/res
   for f in values values-hi values-as; do
     grep -ohE '<(string|plurals|string-array) name="[^"]+"' $f/*.xml | sed -E 's/.*name="([^"]+)"/\1/' | sort -u > <scratchpad>/keys_$f.txt
   done
   comm -23 <scratchpad>/keys_values.txt <scratchpad>/keys_values-hi.txt   # missing in hi
   comm -23 <scratchpad>/keys_values.txt <scratchpad>/keys_values-as.txt   # missing in as
   ```
   Skip `translatable="false"` keys.
2. **Format args**: the same key must have the same `%1$s` / `%d` placeholders in every locale (a mismatch crashes at runtime).
3. **Hardcoded text**: `android:text="..."` / `android:hint="..."` literals in `res/layout*/`, and `Toast.makeText(..., "literal")`, `setText("literal")`, `.setTitle("literal")` in Kotlin.
4. **Scope to the diff** when asked: only keys added in `git diff main...HEAD`.

## Output
- Missing keys per locale (key + English value)
- Placeholder mismatches
- Hardcoded strings with file:line

## Rules
- Don't edit or reword existing strings. If new keys need translations, propose them and let the user supply or confirm the Hindi/Assamese text.
