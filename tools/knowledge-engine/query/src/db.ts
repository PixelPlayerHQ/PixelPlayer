import { existsSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { DatabaseSync } from 'node:sqlite';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// dist/ -> .. -> query/ -> .. -> knowledge-engine/
const KE_DIR = join(__dirname, '..', '..');
export const DEFAULT_DB_PATH = join(KE_DIR, '.understand-anything', 'graph.db');
export const DEFAULT_JSON_PATH = join(KE_DIR, 'knowledge-graph.json');

export function openReadDb(dbPath = DEFAULT_DB_PATH): DatabaseSync {
  if (!existsSync(dbPath)) {
    throw new Error(
      `Knowledge graph DB not found at: ${dbPath}\n` +
        `Run "pnpm kg:build" from tools/knowledge-engine/ to generate it.`,
    );
  }
  return new DatabaseSync(dbPath, { readOnly: true });
}

export function openWriteDb(dbPath = DEFAULT_DB_PATH): DatabaseSync {
  const dir = dirname(dbPath);
  if (!existsSync(dir)) {
    mkdirSync(dir, { recursive: true });
  }
  return new DatabaseSync(dbPath);
}
