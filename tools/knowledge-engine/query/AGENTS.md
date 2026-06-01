# PixelPlayer Knowledge Graph — Agent Guide

This project has a queryable knowledge graph backed by a SQLite database.

## When to use the graph

Before broad navigation:
- call `kg_overview`

Before searching manually:
- call `kg_search`

Before explaining or editing a feature:
- call `kg_node` on candidate files to trace their actual callers (`called-by`) and dependencies (`calls`).
- call `kg_dependents` to do impact analysis.

Prefer compact KG queries before loading large files or grepping the whole repository.

---

## 🚫 Evitar Alucinaciones de Arquitectura (Critical Anti-Laziness Rules)

**¡IMPORTANTE!** Los agentes a veces cometen el error de hacer una única llamada a `kg_search`, ver una lista de archivos y "adivinar" cómo se conectan (ej: asumir que una pantalla X llama a un controlador Y solo porque ambos salieron en la búsqueda). Esto produce explicaciones erróneas o desactualizadas.

Sigue estas reglas estrictas para garantizar la veracidad:
1. **`kg_search` es SOLO para encontrar candidatos**: Nunca deduzcas un flujo arquitectónico basándote únicamente en el resumen de `kg_search`.
2. **Verifica conexiones con `kg_node`**: Si crees que el Archivo A llama al Archivo B, ejecuta `kg_node` sobre ellos y confirma que exista una arista real `calls`, `imports` o `called-by` que los conecte.
3. **Rastrea el disparador real**: Si buscas cómo se activa una característica (ej: crossfade), busca quién llama (`called-by`) a los controladores centrales para descubrir el punto de entrada real (ej: servicios de fondo, viewmodels activos) en lugar de asumir componentes de la interfaz de usuario que podrían estar obsoletos o deshabilitados.

---
The graph is deterministic but not perfect; current `calls` edges are regex/file-level
and should be treated as lower confidence than `imports` and `depends_on`.

---

## Setup (one-time)

```bash
cd tools/knowledge-engine

# Install dependencies
pnpm install

# Compile the query layer TypeScript
pnpm kg:compile

# Build the SQLite DB from the existing knowledge-graph.json
pnpm kg:build
```

The DB is written to `tools/knowledge-engine/.understand-anything/graph.db`.
It is gitignored and must be rebuilt after each graph scan.

After a full graph update (scan + DB build):
```bash
pnpm run scan     # regenerates knowledge-graph.json
pnpm kg:build     # rebuilds graph.db from it
```

Or just run the update script which does both:
```bash
# PowerShell (Windows)
.\update-graph.ps1

# Bash (macOS/Linux)
bash update-graph.sh
```

---

## CLI reference

Run all commands from `tools/knowledge-engine/`:

```bash
pnpm run kg -- overview
pnpm run kg -- search "playlist" --limit 5
pnpm run kg -- search "playback queue" --type file --limit 8
pnpm run kg -- node <id>
pnpm run kg -- dependents <id> --limit 20
pnpm run kg -- deps <id> --limit 20
pnpm run kg -- dependencies <id> --limit 20
pnpm run kg -- neighbors <id> --dir both --depth 1
pnpm run kg -- neighbors <id> --dir in --type imports --depth 2
pnpm run kg -- path <idA> <idB> --max-depth 6
```

Or invoke the compiled script directly:
```bash
node tools/knowledge-engine/query/dist/cli.js overview
node tools/knowledge-engine/query/dist/cli.js search "playlist" --limit 5
```

### Flags

| Flag | Description |
|------|-------------|
| `--db <path>` | Path to graph.db (default: `.understand-anything/graph.db`) |
| `--json` | Output raw JSON instead of compact text |
| `--limit <n>` | Max results |
| `--type <type>` | Filter by node type (search) or edge type (neighbors) |
| `--dir in\|out\|both` | Direction for neighbors (default: both) |
| `--depth <n>` | Traversal depth for neighbors, 1–4 (default: 1) |
| `--max-depth <n>` | Max path depth, 1–8 (default: 6) |

---

## MCP server

The MCP server exposes the same 7 operations as tools over stdio.

Start it with:
```bash
node tools/knowledge-engine/query/dist/mcp.js
```

### Tools

| Tool | When to use |
|------|-------------|
| `kg_overview` | Call first when entering an unfamiliar project or before planning broad changes |
| `kg_search` | Find relevant files/components by concept before grepping or opening many files |
| `kg_node` | Inspect a node's summary, members, and local graph connections |
| `kg_dependents` | Before editing a file — see what depends on it (impact analysis) |
| `kg_dependencies` | Understand what a file/component relies on |
| `kg_neighbors` | Explore a node's local neighbourhood with configurable depth/direction |
| `kg_path` | Understand how two components are connected |

---

## MCP config snippets

Replace `<REPO_ROOT>` with the absolute path to the PixelPlayer repository root.

### Claude Code

Add to `.mcp.json` in the project root, or run:
```bash
claude mcp add pixelplayer-kg -- node <REPO_ROOT>/tools/knowledge-engine/query/dist/mcp.js
```

Or manually in `.mcp.json`:
```json
{
  "mcpServers": {
    "pixelplayer-kg": {
      "command": "node",
      "args": ["<REPO_ROOT>/tools/knowledge-engine/query/dist/mcp.js"]
    }
  }
}
```

### Cursor

`.cursor/mcp.json`:
```json
{
  "mcpServers": {
    "pixelplayer-kg": {
      "command": "node",
      "args": ["<REPO_ROOT>/tools/knowledge-engine/query/dist/mcp.js"]
    }
  }
}
```

### Codex

`codex.json` (or the `mcpServers` block in your config):
```json
{
  "mcpServers": {
    "pixelplayer-kg": {
      "command": "node",
      "args": ["<REPO_ROOT>/tools/knowledge-engine/query/dist/mcp.js"]
    }
  }
}
```

### OpenCode

`~/.config/opencode/config.json` or project config:
```json
{
  "mcpServers": {
    "pixelplayer-kg": {
      "command": "node",
      "args": ["<REPO_ROOT>/tools/knowledge-engine/query/dist/mcp.js"]
    }
  }
}
```

### Antigravity

```json
{
  "mcpServers": {
    "pixelplayer-kg": {
      "command": "node",
      "args": ["<REPO_ROOT>/tools/knowledge-engine/query/dist/mcp.js"]
    }
  }
}
```

---

## Data quality notes

### Edge reliability (highest to lowest)

1. **`imports`** — derived from explicit import statements; high confidence
2. **`depends_on`** — derived from Gradle module dependencies; high confidence
3. **`contains`** — module-to-file containment; high confidence
4. **`calls`** — derived from regex name-matching at file granularity; **lower confidence**

For impact analysis, lean on `imports` and `depends_on`.
`calls` edges are exposed faithfully but treat them as approximate until Phase 3
(tree-sitter extractors) replaces the regex scanner.

### Phase 2 / 3 work (deferred)

- **Phase 2**: semantic vector search via sqlite-vec (hybrid BM25 + vector)
- **Phase 3**: replace regex scanner with tree-sitter extractors for function-level
  nodes and precise `calls` edges; incremental DB updates

---

## File layout

```
tools/knowledge-engine/
  knowledge-graph.json          # canonical dashboard artifact (never remove)
  .understand-anything/
    graph.db                    # derived SQLite DB (gitignored, rebuild anytime)
  query/
    src/
      build.ts                  # JSON → graph.db
      db.ts                     # DB open/path helpers
      query-core.ts             # 7 query operations
      format.ts                 # compact text serializers
      cli.ts                    # kg CLI entrypoint
      mcp.ts                    # MCP stdio server
    dist/                       # compiled output (gitignored)
    package.json
    tsconfig.json
    AGENTS.md                   # this file
```
