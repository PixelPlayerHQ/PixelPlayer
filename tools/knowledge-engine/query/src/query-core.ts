import { openReadDb } from './db.js';

// ── Shared ────────────────────────────────────────────────────────────────────

export interface BaseOptions {
  dbPath?: string;
  limit?: number;
}

// node:sqlite returns null-prototype objects — cast via unknown
function cast<T>(v: unknown): T {
  return v as T;
}

// ── 1. Overview ───────────────────────────────────────────────────────────────

export interface OverviewResult {
  nodeCounts: Array<{ type: string; count: number }>;
  edgeCounts: Array<{ type: string; count: number }>;
  layers: Array<{ layer: string; count: number }>;
  hubs: Array<{
    id: string;
    name: string;
    type: string;
    file_path: string | null;
    summary: string | null;
    degree: number;
  }>;
}

export function overview(options: BaseOptions = {}): OverviewResult {
  const db = openReadDb(options.dbPath);
  try {
    const nodeCounts = cast<OverviewResult['nodeCounts']>(
      db
        .prepare(
          `SELECT type, COUNT(*) as count FROM nodes GROUP BY type ORDER BY count DESC`,
        )
        .all(),
    );
    const edgeCounts = cast<OverviewResult['edgeCounts']>(
      db
        .prepare(
          `SELECT type, COUNT(*) as count FROM edges GROUP BY type ORDER BY count DESC`,
        )
        .all(),
    );
    const layers = cast<OverviewResult['layers']>(
      db
        .prepare(
          `SELECT layer, COUNT(*) as count FROM nodes WHERE layer IS NOT NULL
           GROUP BY layer ORDER BY count DESC LIMIT 10`,
        )
        .all(),
    );
    const limit = options.limit ?? 10;
    const hubs = cast<OverviewResult['hubs']>(
      db
        .prepare(
          `SELECT id, name, type, file_path, summary,
                  degree_in + degree_out as degree
           FROM nodes ORDER BY degree DESC LIMIT ?`,
        )
        .all(limit),
    );
    return { nodeCounts, edgeCounts, layers, hubs };
  } finally {
    db.close();
  }
}

// ── 2. Search ─────────────────────────────────────────────────────────────────

export interface SearchOptions extends BaseOptions {
  type?: string;
}

export interface SearchResult {
  id: string;
  name: string;
  file_path: string | null;
  type: string;
  summary: string | null;
  score: number;
}

export interface SearchRelation {
  source: string;
  target: string;
  type: string;
  source_label: string;
  target_label: string;
  // Transitive 2-step relations
  intermediate?: string;
  intermediate_label?: string;
  type_b?: string;
  dir_a?: 'forward' | 'reverse';
  dir_b?: 'forward' | 'reverse';
  // Transitive 3-step relations
  intermediate_x_label?: string;
  intermediate_y_label?: string;
  type_c?: string;
  dir_c?: 'forward' | 'reverse';
}

export interface SearchResponse {
  results: SearchResult[];
  relations: SearchRelation[];
}

export function search(
  query: string,
  options: SearchOptions = {},
): SearchResponse {
  const db = openReadDb(options.dbPath);
  try {
    const limit = options.limit ?? 15;

    // 1. Sanitize and build search terms
    const terms = query
      .split(/\s+/)
      .map(t => t.replace(/[^a-zA-Z0-9_\-*]/g, ''))
      .filter(t => t.length > 0);

    if (terms.length === 0) return { results: [], relations: [] };

    const ftsAndQuery = terms.join(' AND ');
    const ftsOrQuery = terms.join(' OR ');

    let sql = `
      SELECT n.id, n.name, n.file_path, n.type, n.summary, fts.rank as score
      FROM nodes_fts fts
      JOIN nodes n ON n.id = fts.id
      WHERE nodes_fts MATCH ?
    `;

    if (options.type) {
      sql += ' AND n.type = ?';
    }

    sql += " ORDER BY (CASE WHEN n.summary LIKE '[DISABLED%' OR n.summary LIKE '[EXPERIMENTAL%' THEN 1 ELSE 0 END) ASC, (CASE WHEN n.file_path LIKE '%/test/%' THEN 1 ELSE 0 END) ASC, fts.rank ASC LIMIT ?";

    let results: SearchResult[] = [];

    // Try the strict AND query first (prioritize nodes containing all terms)
    try {
      const params: (string | number)[] = [ftsAndQuery];
      if (options.type) params.push(options.type);
      params.push(limit);
      
      results = cast<SearchResult[]>(db.prepare(sql).all(...params));
    } catch {
      // Ignore AND error, fall through
    }

    // Try the flexible OR query if AND returned nothing (maximize recall)
    if (results.length === 0) {
      try {
        const params: (string | number)[] = [ftsOrQuery];
        if (options.type) params.push(options.type);
        params.push(limit);
        
        results = cast<SearchResult[]>(db.prepare(sql).all(...params));
      } catch {
        // Ignore OR error, fall through
      }
    }

    // 2. Fallback to LIKE search if FTS5 fails completely
    if (results.length === 0) {
      const likeParam = `%${query}%`;
      let fallback = `
        SELECT id, name, file_path, type, summary, 0 as score
        FROM nodes
        WHERE (name LIKE ? OR summary LIKE ? OR id LIKE ?)
      `;
      const fallbackParams: (string | number)[] = [
        likeParam,
        likeParam,
        likeParam,
      ];
      if (options.type) {
        fallback += ' AND type = ?';
        fallbackParams.push(options.type);
      }
      fallback += " ORDER BY (CASE WHEN summary LIKE '[DISABLED%' OR summary LIKE '[EXPERIMENTAL%' THEN 1 ELSE 0 END) ASC, (CASE WHEN file_path LIKE '%/test/%' THEN 1 ELSE 0 END) ASC LIMIT ?";
      fallbackParams.push(limit);
      results = cast<SearchResult[]>(db.prepare(fallback).all(...fallbackParams));
    }

    // 3. Find connections between the search results (direct 1-step, transitive 2-step, and MVVM 3-step paths)
    let relations: SearchRelation[] = [];
    if (results.length > 1) {
      const resultIds = results.map(r => r.id);
      const placeholders = resultIds.map(() => '?').join(',');
      
      // A. Direct 1-step edges
      const relationsSql = `
        SELECT e.source, e.target, e.type, 
               COALESCE(ns.file_path, ns.name, ns.id) as source_label, 
               COALESCE(nt.file_path, nt.name, nt.id) as target_label
        FROM edges e
        JOIN nodes ns ON ns.id = e.source
        JOIN nodes nt ON nt.id = e.target
        WHERE e.source IN (${placeholders}) AND e.target IN (${placeholders})
      `;
      
      // B. Transitive 2-step connections via a shared file node (e.g. A -> I -> C, A -> I <- C, etc.)
      const transitiveSql = `
        WITH undirected AS (
          SELECT source as s, target as t, type, 'forward' as dir FROM edges
          UNION ALL
          SELECT target as s, source as t, type, 'reverse' as dir FROM edges
        )
        SELECT 
          u1.s as source, 
          u1.t as intermediate, 
          u1.type as type, 
          u2.t as target, 
          u2.type as type_b,
          u1.dir as dir_a,
          u2.dir as dir_b,
          COALESCE(ns.file_path, ns.name, ns.id) as source_label,
          COALESCE(ni.file_path, ni.name, ni.id) as intermediate_label,
          COALESCE(nt.file_path, nt.name, nt.id) as target_label
        FROM undirected u1
        JOIN undirected u2 ON u1.t = u2.s
        JOIN nodes ns ON ns.id = u1.s
        JOIN nodes ni ON ni.id = u1.t
        JOIN nodes nt ON nt.id = u2.t
        WHERE u1.s IN (${placeholders})
          AND u2.t IN (${placeholders})
          AND u1.s < u2.t
          AND ni.type = 'file'
        LIMIT 250
      `;

      // C. Transitive 3-step MVVM connections (e.g. View -> ViewModel -> Repository <- Service)
      const threeStepSql = `
        WITH undirected AS (
          SELECT source as s, target as t, type, 'forward' as dir FROM edges
          UNION ALL
          SELECT target as s, source as t, type, 'reverse' as dir FROM edges
        )
        SELECT 
          u1.s as source, 
          u1.t as intermediate, -- map to intermediate for TS compatibility
          u1.type as type, 
          u3.t as target, 
          u2.type as type_b,
          u3.type as type_c,
          u1.dir as dir_a,
          u2.dir as dir_b,
          u3.dir as dir_c,
          COALESCE(ns.file_path, ns.name, ns.id) as source_label,
          COALESCE(nx.file_path, nx.name, nx.id) as intermediate_x_label,
          COALESCE(ny.file_path, ny.name, ny.id) as intermediate_y_label,
          COALESCE(nt.file_path, nt.name, nt.id) as target_label
        FROM undirected u1
        JOIN undirected u2 ON u1.t = u2.s
        JOIN undirected u3 ON u2.t = u3.s
        JOIN nodes ns ON ns.id = u1.s
        JOIN nodes nx ON nx.id = u1.t
        JOIN nodes ny ON ny.id = u2.t
        JOIN nodes nt ON nt.id = u3.t
        WHERE u1.s IN (${placeholders})
          AND u3.t IN (${placeholders})
          AND u1.s < u3.t
          AND nx.type = 'file'
          AND ny.type = 'file'
          AND u1.t != u3.t
          AND u2.t != u1.s
          AND u1.t != u2.t
        LIMIT 250
      `;

      try {
        const direct = cast<SearchRelation[]>(
          db.prepare(relationsSql).all(...resultIds, ...resultIds)
        );
        relations.push(...direct);
        
        const transitive = cast<SearchRelation[]>(
          db.prepare(transitiveSql).all(...resultIds, ...resultIds)
        );
        relations.push(...transitive);

        const threeStep = cast<SearchRelation[]>(
          db.prepare(threeStepSql).all(...resultIds, ...resultIds)
        );
        relations.push(...threeStep);
      } catch {
        // Ignore relation errors, fall through
      }
    }

    return { results, relations };
  } finally {
    db.close();
  }
}

// ── 3. Node ───────────────────────────────────────────────────────────────────

export interface NodeMember {
  kind: string;
  name: string;
}

export interface EdgeRow {
  type: string;
  peer: string;
  peer_name: string;
}

export interface NodeData {
  id: string;
  type: string;
  name: string;
  file_path: string | null;
  summary: string | null;
  tags: string | null;
  layer: string | null;
  complexity: string | null;
  degree_in: number;
  degree_out: number;
}

export interface NodeResult {
  node: NodeData;
  members: NodeMember[];
  inEdges: EdgeRow[];
  outEdges: EdgeRow[];
  inTotal: number;
  outTotal: number;
}

export function node(
  id: string,
  options: BaseOptions = {},
): NodeResult | null {
  const db = openReadDb(options.dbPath);
  try {
    const nodeData = cast<NodeData | undefined>(
      db.prepare(`SELECT * FROM nodes WHERE id = ?`).get(id),
    );
    if (!nodeData) return null;

    const limit = options.limit ?? 15;

    const members = cast<NodeMember[]>(
      db
        .prepare(
          `SELECT kind, name FROM node_members WHERE node_id = ? ORDER BY kind, name`,
        )
        .all(id),
    );

    const inEdges = cast<EdgeRow[]>(
      db
        .prepare(
          `SELECT e.type, e.source as peer, n.name as peer_name
           FROM edges e JOIN nodes n ON n.id = e.source
           WHERE e.target = ?
           ORDER BY e.type, e.source
           LIMIT ?`,
        )
        .all(id, limit),
    );

    const outEdges = cast<EdgeRow[]>(
      db
        .prepare(
          `SELECT e.type, e.target as peer, n.name as peer_name
           FROM edges e JOIN nodes n ON n.id = e.target
           WHERE e.source = ?
           ORDER BY e.type, e.target
           LIMIT ?`,
        )
        .all(id, limit),
    );

    const inTotal = cast<{ c: number }>(
      db.prepare(`SELECT COUNT(*) as c FROM edges WHERE target = ?`).get(id),
    ).c;
    const outTotal = cast<{ c: number }>(
      db.prepare(`SELECT COUNT(*) as c FROM edges WHERE source = ?`).get(id),
    ).c;

    return { node: nodeData, members, inEdges, outEdges, inTotal, outTotal };
  } finally {
    db.close();
  }
}

// ── 4. Dependents ─────────────────────────────────────────────────────────────

export interface DependentRow {
  source: string;
  name: string;
  type: string;
  file_path: string | null;
  edge_type: string;
}

export function dependents(
  id: string,
  options: BaseOptions = {},
): DependentRow[] {
  const db = openReadDb(options.dbPath);
  try {
    const limit = options.limit ?? 20;
    return cast<DependentRow[]>(
      db
        .prepare(
          `SELECT e.source, n.name, n.type, n.file_path, e.type as edge_type
           FROM edges e JOIN nodes n ON n.id = e.source
           WHERE e.target = ?
           ORDER BY e.type, e.source
           LIMIT ?`,
        )
        .all(id, limit),
    );
  } finally {
    db.close();
  }
}

// ── 5. Dependencies ───────────────────────────────────────────────────────────

export interface DependencyRow {
  target: string;
  name: string;
  type: string;
  file_path: string | null;
  edge_type: string;
}

export function dependencies(
  id: string,
  options: BaseOptions = {},
): DependencyRow[] {
  const db = openReadDb(options.dbPath);
  try {
    const limit = options.limit ?? 20;
    return cast<DependencyRow[]>(
      db
        .prepare(
          `SELECT e.target, n.name, n.type, n.file_path, e.type as edge_type
           FROM edges e JOIN nodes n ON n.id = e.target
           WHERE e.source = ?
           ORDER BY e.type, e.target
           LIMIT ?`,
        )
        .all(id, limit),
    );
  } finally {
    db.close();
  }
}

// ── 6. Neighbors ──────────────────────────────────────────────────────────────

export interface NeighborsOptions extends BaseOptions {
  direction?: 'in' | 'out' | 'both';
  edgeType?: string;
  depth?: number;
}

export interface NeighborRow {
  neighbor_id: string;
  name: string;
  type: string;
  file_path: string | null;
  edge_type: string;
  depth: number;
}

export function neighbors(
  id: string,
  options: NeighborsOptions = {},
): NeighborRow[] {
  const db = openReadDb(options.dbPath);
  try {
    const limit = options.limit ?? 30;
    const maxDepth = Math.min(options.depth ?? 1, 4);
    const direction = options.direction ?? 'both';

    const edgeFilter = options.edgeType ? `AND e.type = ?` : '';
    const edgeParam: string[] = options.edgeType ? [options.edgeType] : [];

    const getOut = db.prepare(
      `SELECT DISTINCT e.target as neighbor_id, n.name, n.type, n.file_path,
              e.type as edge_type
       FROM edges e JOIN nodes n ON n.id = e.target
       WHERE e.source = ? ${edgeFilter}`,
    );
    const getIn = db.prepare(
      `SELECT DISTINCT e.source as neighbor_id, n.name, n.type, n.file_path,
              e.type as edge_type
       FROM edges e JOIN nodes n ON n.id = e.source
       WHERE e.target = ? ${edgeFilter}`,
    );

    const visited = new Set<string>([id]);
    const queue: Array<{ nodeId: string; depth: number }> = [
      { nodeId: id, depth: 0 },
    ];
    const result: NeighborRow[] = [];

    while (queue.length > 0 && result.length < limit) {
      const current = queue.shift()!;
      if (current.depth >= maxDepth) continue;

      const nextDepth = current.depth + 1;
      const batch: NeighborRow[] = [];

      if (direction === 'out' || direction === 'both') {
        const rows = cast<NeighborRow[]>(
          getOut.all(current.nodeId, ...edgeParam),
        );
        batch.push(...rows.map((r) => ({ ...r, depth: nextDepth })));
      }
      if (direction === 'in' || direction === 'both') {
        const rows = cast<NeighborRow[]>(
          getIn.all(current.nodeId, ...edgeParam),
        );
        batch.push(...rows.map((r) => ({ ...r, depth: nextDepth })));
      }

      for (const row of batch) {
        if (!visited.has(row.neighbor_id)) {
          visited.add(row.neighbor_id);
          result.push(row);
          if (nextDepth < maxDepth) {
            queue.push({ nodeId: row.neighbor_id, depth: nextDepth });
          }
        }
      }
    }

    return result.slice(0, limit);
  } finally {
    db.close();
  }
}

// ── 7. Path ───────────────────────────────────────────────────────────────────

export interface PathOptions extends BaseOptions {
  maxDepth?: number;
}

export interface PathResult {
  found: boolean;
  depth?: number;
  path?: string[];
  edgeTypes?: string[];
  nodes?: unknown[];
  reason?: string;
}

interface EdgeRecord {
  source: string;
  target: string;
  edge_type: string;
}

export function path(
  sourceId: string,
  targetId: string,
  options: PathOptions = {},
): PathResult {
  const db = openReadDb(options.dbPath);
  try {
    const maxDepth = Math.min(options.maxDepth ?? 6, 8);

    const checkNode = db.prepare(
      `SELECT id, name, type, file_path FROM nodes WHERE id = ?`,
    );
    const src = checkNode.get(sourceId);
    const tgt = checkNode.get(targetId);

    if (!src)
      return { found: false, reason: `source node "${sourceId}" not found` };
    if (!tgt)
      return { found: false, reason: `target node "${targetId}" not found` };
    if (sourceId === targetId)
      return {
        found: true,
        depth: 0,
        path: [sourceId],
        edgeTypes: [],
        nodes: [src],
      };

    // Preload full adjacency list for BFS
    const allEdges = cast<EdgeRecord[]>(
      db
        .prepare(`SELECT source, target, type as edge_type FROM edges`)
        .all(),
    );

    const adj = new Map<string, Array<{ neighbor: string; edgeType: string }>>();
    for (const e of allEdges) {
      if (!adj.has(e.source)) adj.set(e.source, []);
      if (!adj.has(e.target)) adj.set(e.target, []);
      adj.get(e.source)!.push({ neighbor: e.target, edgeType: e.edge_type });
      adj.get(e.target)!.push({ neighbor: e.source, edgeType: e.edge_type });
    }

    // BFS
    const queue: Array<{
      nodeId: string;
      path: string[];
      edgeTypes: string[];
    }> = [{ nodeId: sourceId, path: [sourceId], edgeTypes: [] }];
    const visited = new Set<string>([sourceId]);

    while (queue.length > 0) {
      const current = queue.shift()!;
      if (current.path.length > maxDepth) continue;

      for (const { neighbor, edgeType } of adj.get(current.nodeId) ?? []) {
        if (neighbor === targetId) {
          const fullPath = [...current.path, neighbor];
          const fullEdgeTypes = [...current.edgeTypes, edgeType];
          const nodes = fullPath.map((nid) => checkNode.get(nid));
          return {
            found: true,
            depth: fullPath.length - 1,
            path: fullPath,
            edgeTypes: fullEdgeTypes,
            nodes,
          };
        }
        if (!visited.has(neighbor)) {
          visited.add(neighbor);
          queue.push({
            nodeId: neighbor,
            path: [...current.path, neighbor],
            edgeTypes: [...current.edgeTypes, edgeType],
          });
        }
      }
    }

    return { found: false, reason: `no path found within depth ${maxDepth}` };
  } finally {
    db.close();
  }
}
