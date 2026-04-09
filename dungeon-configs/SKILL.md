# arcadia-dungeon-admin-skill

Use this skill when the user wants to create, edit, validate, or repair a dungeon JSON for Arcadia Dungeon from natural language.

## Scope

- Only read or write JSON files inside `dungeon-configs/`
- Validate every generated or modified config against `dungeon-configs/dungeon-schema.json` before writing
- Preserve untouched fields when editing an existing dungeon file
- Never edit Java source, runtime state, Gradle files, or BMAD artifacts

## Inputs

- Natural-language dungeon requirements
- Optional existing dungeon JSON in `dungeon-configs/`
- The schema at `dungeon-configs/dungeon-schema.json`
- The example at `dungeon-configs/examples/example_dungeon.json`

## Workflow

1. Identify the target JSON file in `dungeon-configs/`
2. If the file exists, read it fully and treat it as the base document
3. Map the user request to the schema fields only
4. Generate the smallest valid JSON change set that satisfies the request
5. Validate structure, field types, enum-like values, and required objects against `dungeon-schema.json`
6. If validation fails, report the exact invalid field and do not write the file
7. If validation passes, write the final JSON to `dungeon-configs/`

## Validation Rules

- `id` must stay stable unless the user explicitly requests a rename
- Message templates may use `%player%`, `%dungeon%`, `%id%`
- `announceIntervalMinutes` must be an integer `>= 0`
- `cooldownSeconds`, `recruitmentDurationSeconds`, `availableEverySeconds` must be integers `>= 0`
- `spawnPoint` and area positions must include a dimension string
- Reward item ids must use `namespace:path`
- Do not invent unsupported fields outside the schema

## Output Contract

- When creating a config: produce one complete valid JSON document
- When editing a config: preserve all non-requested fields exactly
- When validation fails: return a blocking error with the field path and reason, and do not write

## File Boundaries

- Allowed:
  - `dungeon-configs/*.json`
  - `dungeon-configs/examples/*.json`
  - `dungeon-configs/dungeon-schema.json`
- Forbidden:
  - `src/main/java/**`
  - `_bmad-output/**`
  - build or runtime folders
