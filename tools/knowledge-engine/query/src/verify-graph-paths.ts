import { DatabaseSync } from 'node:sqlite';
import { DEFAULT_DB_PATH } from './db.js';

interface Edge {
  source: string;
  target: string;
  type: string;
}

function verifyPaths() {
  console.log(`Loading database from: ${DEFAULT_DB_PATH}`);
  const db = new DatabaseSync(DEFAULT_DB_PATH, { readOnly: true });

  try {
    // 1. Fetch all edges excluding 'contains' type
    const stmt = db.prepare(`
      SELECT source, target, type 
      FROM edges 
      WHERE type != 'contains'
    `);
    const allEdges = stmt.all() as unknown as Edge[];
    console.log(`Loaded ${allEdges.length} functional edges (excluding 'contains').`);

    // 2. Build adjacency list (directed)
    const adj = new Map<string, Array<{ target: string; type: string }>>();
    for (const e of allEdges) {
      if (!adj.has(e.source)) adj.set(e.source, []);
      adj.get(e.source)!.push({ target: e.target, type: e.type });
    }

    const startNode = 'app/src/main/java/com/theveloper/pixelplay/presentation/screens/MashupScreen.kt';
    const targetNode1 = 'app/src/main/java/com/theveloper/pixelplay/data/service/player/TransitionController.kt';
    const targetNode2 = 'app/src/main/java/com/theveloper/pixelplay/data/service/player/DualPlayerEngine.kt';

    console.log(`\n--- Searching for FUNCTIONAL PATHS from MashupScreen.kt ---`);

    // BFS search
    function findFunctionalPath(start: string, target: string): string[] | null {
      const queue: Array<{ node: string; path: string[] }> = [{ node: start, path: [start] }];
      const visited = new Set<string>([start]);

      while (queue.length > 0) {
        const current = queue.shift()!;
        if (current.node === target) {
          return current.path;
        }

        const neighbors = adj.get(current.node) ?? [];
        for (const neighbor of neighbors) {
          if (!visited.has(neighbor.target)) {
            visited.add(neighbor.target);
            queue.push({
              node: neighbor.target,
              path: [...current.path, neighbor.target]
            });
          }
        }
      }
      return null;
    }

    const path1 = findFunctionalPath(startNode, targetNode1);
    if (path1) {
      console.log(`🔴 FOUND FUNCTIONAL PATH to TransitionController.kt:`);
      console.log(path1.join(' ➔ '));
    } else {
      console.log(`✅ No functional path exists from MashupScreen.kt to TransitionController.kt!`);
    }

    const path2 = findFunctionalPath(startNode, targetNode2);
    if (path2) {
      console.log(`🔴 FOUND FUNCTIONAL PATH to DualPlayerEngine.kt:`);
      console.log(path2.join(' ➔ '));
    } else {
      console.log(`✅ No functional path exists from MashupScreen.kt to DualPlayerEngine.kt!`);
    }

    // Reverse BFS search (does anything from TransitionController call MashupScreen?)
    const path3 = findFunctionalPath(targetNode1, startNode);
    if (path3) {
      console.log(`🔴 FOUND REVERSE FUNCTIONAL PATH:`);
      console.log(path3.join(' ➔ '));
    } else {
      console.log(`✅ No reverse functional path exists from TransitionController.kt to MashupScreen.kt!`);
    }

  } finally {
    db.close();
  }
}

verifyPaths();
