import { readFileSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import { DEFAULT_DB_PATH, DEFAULT_JSON_PATH, openWriteDb } from './db.js';

interface GraphNode {
  id: string;
  type: string;
  name: string;
  filePath?: string;
  summary?: string;
  tags?: string[];
  complexity?: string;
  classes?: string[];
  functions?: string[];
}

interface GraphEdge {
  source: string;
  target: string;
  type: string;
  direction?: string;
  weight?: number;
  description?: string;
}

interface KnowledgeGraph {
  nodes: GraphNode[];
  edges: GraphEdge[];
}

const SCHEMA = `
  DROP TABLE IF EXISTS nodes_fts;
  DROP TABLE IF EXISTS node_members;
  DROP TABLE IF EXISTS edges;
  DROP TABLE IF EXISTS nodes;

  CREATE TABLE nodes(
    id TEXT PRIMARY KEY,
    type TEXT,
    name TEXT,
    file_path TEXT,
    summary TEXT,
    tags TEXT,
    layer TEXT,
    complexity TEXT,
    degree_in INTEGER,
    degree_out INTEGER
  );

  CREATE TABLE node_members(
    node_id TEXT,
    kind TEXT,
    name TEXT
  );

  CREATE TABLE edges(
    source TEXT,
    target TEXT,
    type TEXT,
    direction TEXT,
    weight REAL,
    description TEXT
  );

  CREATE INDEX idx_edges_src ON edges(source);
  CREATE INDEX idx_edges_tgt ON edges(target);
  CREATE INDEX idx_edges_type ON edges(type);

  CREATE VIRTUAL TABLE nodes_fts USING fts5(
    id,
    name,
    summary,
    tags,
    members,
    file_path,
    tokenize='porter'
  );
`;

function withTransaction(db: DatabaseSync, fn: () => void): void {
  db.exec('BEGIN');
  try {
    fn();
    db.exec('COMMIT');
  } catch (err) {
    db.exec('ROLLBACK');
    throw err;
  }
}

export function buildDb(
  jsonPath = DEFAULT_JSON_PATH,
  dbPath = DEFAULT_DB_PATH,
): void {
  console.log(`Reading ${jsonPath}...`);
  const raw = readFileSync(jsonPath, 'utf-8');
  const graph: KnowledgeGraph = JSON.parse(raw);
  console.log(`  nodes: ${graph.nodes.length}, edges: ${graph.edges.length}`);

  // Precompute degree counts
  const degreeIn = new Map<string, number>();
  const degreeOut = new Map<string, number>();
  for (const e of graph.edges) {
    degreeOut.set(e.source, (degreeOut.get(e.source) ?? 0) + 1);
    degreeIn.set(e.target, (degreeIn.get(e.target) ?? 0) + 1);
  }

  const db = openWriteDb(dbPath);
  try {
    db.exec(SCHEMA);

    const insertNode = db.prepare(
      `INSERT INTO nodes(id,type,name,file_path,summary,tags,layer,complexity,degree_in,degree_out)
       VALUES (?,?,?,?,?,?,?,?,?,?)`,
    );
    const insertMember = db.prepare(
      `INSERT INTO node_members(node_id,kind,name) VALUES (?,?,?)`,
    );
    const insertFts = db.prepare(
      `INSERT INTO nodes_fts(id,name,summary,tags,members,file_path)
       VALUES (?,?,?,?,?,?)`,
    );
    const insertEdge = db.prepare(
      `INSERT INTO edges(source,target,type,direction,weight,description)
       VALUES (?,?,?,?,?,?)`,
    );

    withTransaction(db, () => {
      for (const node of graph.nodes) {
        const tags = Array.isArray(node.tags) ? node.tags.join(',') : '';
        const layer =
          Array.isArray(node.tags) && node.tags.length > 0
            ? node.tags[0]
            : null;
        insertNode.run(
          node.id,
          node.type,
          node.name,
          node.filePath ?? null,
          node.summary ?? null,
          tags,
          layer,
          node.complexity ?? null,
          degreeIn.get(node.id) ?? 0,
          degreeOut.get(node.id) ?? 0,
        );

        const members: string[] = [];
        for (const cls of node.classes ?? []) {
          insertMember.run(node.id, 'class', cls);
          members.push(cls);
        }
        for (const fn of node.functions ?? []) {
          insertMember.run(node.id, 'function', fn);
          members.push(fn);
        }

        insertFts.run(
          node.id,
          node.name,
          node.summary ?? '',
          tags,
          members.join(' '),
          node.filePath ?? '',
        );
      }
    });
    console.log(`  inserted ${graph.nodes.length} nodes`);

    withTransaction(db, () => {
      for (const e of graph.edges) {
        insertEdge.run(
          e.source,
          e.target,
          e.type,
          e.direction ?? 'forward',
          e.weight ?? null,
          e.description ?? null,
        );
      }
    });
    console.log(`  inserted ${graph.edges.length} edges`);
    console.log(`Database written to ${dbPath}`);
  } finally {
    db.close();
  }
}
