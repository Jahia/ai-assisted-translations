# Glossary CSV Contract

## Goal
Define a stable CSV format for glossary files stored in Jahia.

## Required shape
- UTF-8 CSV file.
- First column must be `key`.
- At least 2 language columns (for example `en`, `fr`, `de`, `pt-BR`).

## Example
```csv
key,en,fr,de,status,domain
checkout_button,Checkout,Paiement,Zur Kasse,active,ecommerce
cancel_action,Cancel,Annuler,Abbrechen,active,common
```

## Validation rules (phase 1)
- Header row is mandatory.
- `key` must be the first column.
- Header names must be unique. Underscore and dash variants of the same language header
  (e.g. `fr_FR` and `fr-FR`) resolve to the same column and are rejected as duplicates.
- Language headers must match: `xx` or `xx-YY` (underscore variant accepted).
- Each non-empty data row must have a non-empty `key` value.
- Each non-empty data row must contain at least one non-empty language value.
- A row with more columns than the header (e.g. an unquoted comma inside a term) is skipped
  and reported, rather than silently truncated.

## Notes
- Non-language columns are ignored by runtime term extraction in phase 1/2.
- For a language pair resolution, rows missing source or target language value are skipped.

