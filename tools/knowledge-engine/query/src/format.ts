import type {
  DependencyRow,
  DependentRow,
  NeighborRow,
  NodeResult,
  OverviewResult,
  PathResult,
  SearchResult,
  SearchResponse,
} from './query-core.js';

// Maps edge types to human-readable incoming labels
const INCOMING_LABEL: Record<string, string> = {
  imports: 'imported-by',
  calls: 'called-by',
  depends_on: 'depended-on-by',
  contains: 'contained-by',
};

function inLabel(type: string): string {
  return INCOMING_LABEL[type] ?? `←${type}`;
}

function groupBy<T>(arr: T[], key: keyof T): Map<string, T[]> {
  const map = new Map<string, T[]>();
  for (const item of arr) {
    const k = String(item[key]);
    const bucket = map.get(k);
    if (bucket) bucket.push(item);
    else map.set(k, [item]);
  }
  return map;
}

function truncateList(names: string[], max: number): string {
  const shown = names.slice(0, max);
  const more = names.length - shown.length;
  return shown.join(', ') + (more > 0 ? ` (+${more})` : '');
}

// ── Overview ──────────────────────────────────────────────────────────────────

export function formatOverview(data: OverviewResult): string {
  const lines: string[] = ['# Project Overview', ''];

  lines.push('Nodes:');
  for (const nc of data.nodeCounts) {
    lines.push(`  ${nc.type}: ${nc.count}`);
  }

  lines.push('');
  lines.push('Edges:');
  for (const ec of data.edgeCounts) {
    lines.push(`  ${ec.type}: ${ec.count}`);
  }

  if (data.layers.length > 0) {
    lines.push('');
    lines.push('Layers:');
    for (const l of data.layers) {
      lines.push(`  ${l.layer}: ${l.count}`);
    }
  }

  if (data.hubs.length > 0) {
    lines.push('');
    lines.push('Top hub nodes (by degree):');
    for (const hub of data.hubs) {
      const label = hub.file_path ?? hub.id;
      lines.push(`  [${hub.type}] ${label}  degree=${hub.degree}`);
      if (hub.summary) lines.push(`    ${hub.summary}`);
    }
  }

  return lines.join('\n');
}

// ── Search ────────────────────────────────────────────────────────────────────

export function formatSearch(data: SearchResponse, query: string): string {
  const { results, relations } = data;
  if (results.length === 0) return `No results for "${query}"`;

  const lines = [
    `Search: "${query}" — ${results.length} result(s)`,
    '',
  ];
  for (const r of results) {
    const label = r.file_path ?? r.id;
    lines.push(`  [${r.type}] ${label}`);
    if (r.summary) lines.push(`    ${r.summary}`);
  }

  if (relations && relations.length > 0) {
    lines.push('');
    lines.push('Connections between search results:');
    
    // De-duplicate direct and transitive relations to keep output clean
    const seen = new Set<string>();
    let printedCount = 0;
    const maxConnections = 25;
    
    for (const rel of relations) {
      if (rel.intermediate_x_label && rel.intermediate_y_label) {
        // De-duplicate A - X - Y - C pairs
        const key = `${rel.source_label}-${rel.intermediate_x_label}-${rel.intermediate_y_label}-${rel.target_label}`;
        if (seen.has(key)) continue;
        seen.add(key);

        if (printedCount >= maxConnections) {
          lines.push(`  ... and more connections truncated to keep output concise.`);
          break;
        }
        printedCount++;

        const arrowA = rel.dir_a === 'forward' ? `—[${rel.type}]→` : `←[${rel.type}]—`;
        const arrowB = rel.dir_b === 'forward' ? `—[${rel.type_b}]→` : `←[${rel.type_b}]—`;
        const arrowC = rel.dir_c === 'forward' ? `—[${rel.type_c}]→` : `←[${rel.type_c}]—`;
        lines.push(`  ${rel.source_label} ${arrowA} ${rel.intermediate_x_label} ${arrowB} ${rel.intermediate_y_label} ${arrowC} ${rel.target_label}`);
      } else if (rel.intermediate_label) {
        // De-duplicate A - I - C pairs
        const key = `${rel.source_label}-${rel.intermediate_label}-${rel.target_label}`;
        if (seen.has(key)) continue;
        seen.add(key);

        if (printedCount >= maxConnections) {
          lines.push(`  ... and more connections truncated to keep output concise.`);
          break;
        }
        printedCount++;

        const arrowA = rel.dir_a === 'forward' ? `—[${rel.type}]→` : `←[${rel.type}]—`;
        const arrowB = rel.dir_b === 'forward' ? `—[${rel.type_b}]→` : `←[${rel.type_b}]—`;
        lines.push(`  ${rel.source_label} ${arrowA} ${rel.intermediate_label} ${arrowB} ${rel.target_label}`);
      } else {
        const key = `${rel.source_label}-${rel.target_label}`;
        if (seen.has(key)) continue;
        seen.add(key);

        if (printedCount >= maxConnections) {
          lines.push(`  ... and more connections truncated to keep output concise.`);
          break;
        }
        printedCount++;
        
        lines.push(`  ${rel.source_label} —[${rel.type}]→ ${rel.target_label}`);
      }
    }
  }

  return lines.join('\n');
}

// ── Node ──────────────────────────────────────────────────────────────────────

export function formatNode(data: NodeResult | null): string {
  if (!data) return 'Node not found.';

  const { node, members, inEdges, outEdges, inTotal, outTotal } = data;
  const label = node.file_path ?? node.id;
  const lines: string[] = [];

  lines.push(`${label} — ${node.summary ?? 'no summary'}`);
  lines.push(`  type: ${node.type}`);
  lines.push(`  id: ${node.id}`);
  if (node.complexity) lines.push(`  complexity: ${node.complexity}`);
  if (node.tags) lines.push(`  tags: ${node.tags}`);

  if (members.length > 0) {
    const memberNames = members.map((m) => m.name);
    lines.push(`  members: ${truncateList(memberNames, 12)}`);
  }

  lines.push('');

  // Incoming edges
  const byInType = groupBy(inEdges, 'type');
  for (const [type, edges] of byInType) {
    const names = edges.map((e) => e.peer_name);
    const shownStr = truncateList(names, 5);
    const totalNote = inTotal > inEdges.length ? ` (${inTotal} total)` : '';
    lines.push(`  ← ${inLabel(type)}: ${shownStr}${totalNote}`);
  }

  // Outgoing edges
  const byOutType = groupBy(outEdges, 'type');
  for (const [type, edges] of byOutType) {
    const names = edges.map((e) => e.peer_name);
    const shownStr = truncateList(names, 5);
    const totalNote = outTotal > outEdges.length ? ` (${outTotal} total)` : '';
    lines.push(`  → ${type}: ${shownStr}${totalNote}`);
  }

  return lines.join('\n');
}

// ── Dependents ────────────────────────────────────────────────────────────────

export function formatDependents(rows: DependentRow[], id: string): string {
  if (rows.length === 0) return `No dependents found for "${id}"`;

  const lines = [`Dependents of "${id}" — ${rows.length} result(s)`, ''];
  const byType = groupBy(rows, 'edge_type');
  for (const [type, edges] of byType) {
    lines.push(`  [${inLabel(type)}]`);
    for (const e of edges) {
      const label = e.file_path ?? e.source;
      lines.push(`    ${label}`);
    }
  }
  return lines.join('\n');
}

// ── Dependencies ──────────────────────────────────────────────────────────────

export function formatDependencies(rows: DependencyRow[], id: string): string {
  if (rows.length === 0) return `No dependencies found for "${id}"`;

  const lines = [`Dependencies of "${id}" — ${rows.length} result(s)`, ''];
  const byType = groupBy(rows, 'edge_type');
  for (const [type, edges] of byType) {
    lines.push(`  [${type}]`);
    for (const e of edges) {
      const label = e.file_path ?? e.target;
      lines.push(`    ${label}`);
    }
  }
  return lines.join('\n');
}

// ── Neighbors ─────────────────────────────────────────────────────────────────

export function formatNeighbors(rows: NeighborRow[], id: string): string {
  if (rows.length === 0) return `No neighbors found for "${id}"`;

  const lines = [`Neighbors of "${id}" — ${rows.length} result(s)`, ''];
  const byDepth = groupBy(rows, 'depth');
  for (const [depth, nodes] of [...byDepth.entries()].sort()) {
    lines.push(`  depth ${depth}:`);
    for (const n of nodes) {
      const label = n.file_path ?? n.neighbor_id;
      lines.push(`    [${n.type}] ${label}  via ${n.edge_type}`);
    }
  }
  return lines.join('\n');
}

// ── Path ──────────────────────────────────────────────────────────────────────

export function formatPath(result: PathResult): string {
  if (!result.found) {
    return `No path found: ${result.reason ?? 'unknown'}`;
  }

  const lines = [
    `Path found (depth ${result.depth}):`,
    '',
  ];

  const pathIds = result.path ?? [];
  const edgeTypes = result.edgeTypes ?? [];
  const nodes = result.nodes as Array<{
    id: string;
    name: string;
    type: string;
  } | null> | undefined;

  for (let i = 0; i < pathIds.length; i++) {
    const nodeInfo = nodes?.[i];
    const label = nodeInfo ? `[${nodeInfo.type}] ${nodeInfo.name}` : pathIds[i];
    if (i < edgeTypes.length) {
      lines.push(`  ${label}`);
      lines.push(`    —${edgeTypes[i]}→`);
    } else {
      lines.push(`  ${label}`);
    }
  }

  return lines.join('\n');
}
