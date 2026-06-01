#!/usr/bin/env node
import { buildDb } from './build.js';
import { DEFAULT_DB_PATH, DEFAULT_JSON_PATH } from './db.js';
import * as fmt from './format.js';
import * as core from './query-core.js';

// ── Minimal arg parser ────────────────────────────────────────────────────────

interface ParsedArgs {
  command: string;
  positionals: string[];
  flags: Record<string, string | boolean>;
}

function parseArgs(argv: string[]): ParsedArgs {
  const args = argv.slice(2);
  const command = args[0] ?? '';
  const positionals: string[] = [];
  const flags: Record<string, string | boolean> = {};

  for (let i = 1; i < args.length; i++) {
    const arg = args[i];
    if (arg.startsWith('--')) {
      const key = arg.slice(2);
      const next = args[i + 1];
      if (next !== undefined && !next.startsWith('--')) {
        flags[key] = next;
        i++;
      } else {
        flags[key] = true;
      }
    } else {
      positionals.push(arg);
    }
  }

  return { command, positionals, flags };
}

function emit(data: unknown, flags: ParsedArgs['flags'], text: string): void {
  if (flags['json']) {
    process.stdout.write(JSON.stringify(data, null, 2) + '\n');
  } else {
    process.stdout.write(text + '\n');
  }
}

function die(msg: string): never {
  process.stderr.write(msg + '\n');
  process.exit(1);
}

// ── Main ──────────────────────────────────────────────────────────────────────

function main(): void {
  const { command, positionals, flags } = parseArgs(process.argv);

  const dbPath = typeof flags['db'] === 'string' ? flags['db'] : DEFAULT_DB_PATH;
  const limit = typeof flags['limit'] === 'string' ? parseInt(flags['limit'], 10) : undefined;

  try {
    switch (command) {
      case 'build': {
        const jsonPath =
          positionals[0] ??
          (typeof flags['json-path'] === 'string'
            ? flags['json-path']
            : DEFAULT_JSON_PATH);
        buildDb(jsonPath, dbPath);
        break;
      }

      case 'overview': {
        const data = core.overview({ dbPath, limit });
        emit(data, flags, fmt.formatOverview(data));
        break;
      }

      case 'search': {
        const query = positionals[0];
        if (!query) die('Usage: kg search <query> [--type <type>] [--limit <n>]');
        const type =
          typeof flags['type'] === 'string' ? flags['type'] : undefined;
        const data = core.search(query, { dbPath, limit, type });
        emit(data, flags, fmt.formatSearch(data, query));
        break;
      }

      case 'node': {
        const id = positionals[0];
        if (!id) die('Usage: kg node <id>');
        const data = core.node(id, { dbPath, limit });
        emit(data, flags, fmt.formatNode(data));
        break;
      }

      case 'dependents': {
        const id = positionals[0];
        if (!id) die('Usage: kg dependents <id> [--limit <n>]');
        const data = core.dependents(id, { dbPath, limit });
        emit(data, flags, fmt.formatDependents(data, id));
        break;
      }

      case 'deps':
      case 'dependencies': {
        const id = positionals[0];
        if (!id) die('Usage: kg dependencies <id> [--limit <n>]');
        const data = core.dependencies(id, { dbPath, limit });
        emit(data, flags, fmt.formatDependencies(data, id));
        break;
      }

      case 'neighbors': {
        const id = positionals[0];
        if (!id)
          die(
            'Usage: kg neighbors <id> [--dir in|out|both] [--type <edgeType>] [--depth <n>] [--limit <n>]',
          );
        const direction = (['in', 'out', 'both'] as const).find(
          (d) => d === flags['dir'],
        );
        const depth =
          typeof flags['depth'] === 'string'
            ? parseInt(flags['depth'], 10)
            : undefined;
        const edgeType =
          typeof flags['type'] === 'string' ? flags['type'] : undefined;
        const data = core.neighbors(id, {
          dbPath,
          limit,
          direction,
          edgeType,
          depth,
        });
        emit(data, flags, fmt.formatNeighbors(data, id));
        break;
      }

      case 'path': {
        const [srcId, tgtId] = positionals;
        if (!srcId || !tgtId)
          die('Usage: kg path <sourceId> <targetId> [--max-depth <n>]');
        const maxDepth =
          typeof flags['max-depth'] === 'string'
            ? parseInt(flags['max-depth'], 10)
            : undefined;
        const data = core.path(srcId, tgtId, { dbPath, maxDepth });
        emit(data, flags, fmt.formatPath(data));
        break;
      }

      default: {
        const usage = `
Usage: kg <command> [options]

Commands:
  build                    Build graph.db from knowledge-graph.json
  overview                 Project overview (node/edge counts, hub nodes)
  search <query>           Full-text search across nodes
  node <id>                Node detail with connections
  dependents <id>          Impact analysis — who depends on this node
  deps <id>                Dependency analysis — what this node depends on
  dependencies <id>        Alias for "deps"
  neighbors <id>           Connected nodes with optional depth/direction
  path <id1> <id2>         Connection path between two nodes

Options:
  --db <path>              Path to graph.db (default: .understand-anything/graph.db)
  --json                   Output raw JSON instead of compact text
  --limit <n>              Max results to return
  --type <type>            Filter by node type (search) or edge type (neighbors)
  --dir in|out|both        Direction for neighbors (default: both)
  --depth <n>              Traversal depth for neighbors (default: 1, max: 4)
  --max-depth <n>          Max path depth (default: 6, max: 8)
`.trim();
        if (command) {
          process.stderr.write(`Unknown command: ${command}\n\n${usage}\n`);
          process.exit(1);
        }
        process.stdout.write(usage + '\n');
        break;
      }
    }
  } catch (err) {
    die(err instanceof Error ? err.message : String(err));
  }
}

main();
