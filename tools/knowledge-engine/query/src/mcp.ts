#!/usr/bin/env node
import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from '@modelcontextprotocol/sdk/types.js';
import { DEFAULT_DB_PATH } from './db.js';
import * as fmt from './format.js';
import * as core from './query-core.js';

// ── Tool definitions ──────────────────────────────────────────────────────────

const TOOLS = [
  {
    name: 'kg_overview',
    description:
      'Call this first when entering an unfamiliar project or before planning broad changes. ' +
      'Returns node/edge counts by type, architectural layers, and the top hub nodes by degree.',
    inputSchema: {
      type: 'object' as const,
      properties: {
        limit: {
          type: 'number',
          description: 'Max hub nodes to show (default 10)',
        },
      },
    },
  },
  {
    name: 'kg_search',
    description:
      'Finds relevant files/components by concept using full-text search. ' +
      'CRITICAL: Check the "Connections between search results" in the output. ' +
      'If a result has no connections to the core files, it is isolated or experimental. ' +
      'Do NOT assume or hallucinate connections that are not explicitly listed in the output!',
    inputSchema: {
      type: 'object' as const,
      required: ['query'],
      properties: {
        query: { type: 'string', description: 'Search terms or phrase' },
        type: {
          type: 'string',
          enum: ['file', 'resource', 'module'],
          description: 'Optional node type filter',
        },
        limit: { type: 'number', description: 'Max results (default 15)' },
      },
    },
  },
  {
    name: 'kg_node',
    description:
      'Inspects a node\'s details, members, and connected edges. ' +
      'CRITICAL: Use the incoming (← called-by) and outgoing (→ calls) lists to verify ' +
      'real dependencies. If another file has no edge to this node, they are completely ' +
      'disconnected — do NOT assume or invent a connection between them!',
    inputSchema: {
      type: 'object' as const,
      required: ['id'],
      properties: {
        id: { type: 'string', description: 'Node id (usually a file path)' },
        limit: {
          type: 'number',
          description: 'Max edges per direction (default 15)',
        },
      },
    },
  },
  {
    name: 'kg_dependents',
    description:
      'Call this before editing a file to see what may depend on it (impact analysis). ' +
      'Returns nodes that have an edge pointing TO the given node.',
    inputSchema: {
      type: 'object' as const,
      required: ['id'],
      properties: {
        id: { type: 'string', description: 'Node id to analyse' },
        limit: { type: 'number', description: 'Max results (default 20)' },
      },
    },
  },
  {
    name: 'kg_dependencies',
    description:
      'Use this to understand what a file or component relies on. ' +
      'Returns nodes that the given node has an edge pointing TO.',
    inputSchema: {
      type: 'object' as const,
      required: ['id'],
      properties: {
        id: { type: 'string', description: 'Node id to analyse' },
        limit: { type: 'number', description: 'Max results (default 20)' },
      },
    },
  },
  {
    name: 'kg_neighbors',
    description:
      'Explore the local neighbourhood of a node with configurable depth and direction. ' +
      'Useful for understanding a component\'s immediate context without loading full files.',
    inputSchema: {
      type: 'object' as const,
      required: ['id'],
      properties: {
        id: { type: 'string', description: 'Node id' },
        direction: {
          type: 'string',
          enum: ['in', 'out', 'both'],
          description: 'Edge direction (default: both)',
        },
        edgeType: {
          type: 'string',
          description: 'Filter by edge type (calls, imports, depends_on, contains)',
        },
        depth: {
          type: 'number',
          description: 'Traversal depth 1–4 (default 1)',
        },
        limit: { type: 'number', description: 'Max results (default 30)' },
      },
    },
  },
  {
    name: 'kg_path',
    description:
      'Use this to understand how two components are connected. ' +
      'Finds the shortest undirected path between two nodes in the graph.',
    inputSchema: {
      type: 'object' as const,
      required: ['sourceId', 'targetId'],
      properties: {
        sourceId: { type: 'string', description: 'Starting node id' },
        targetId: { type: 'string', description: 'Ending node id' },
        maxDepth: {
          type: 'number',
          description: 'Max path length (default 6, max 8)',
        },
      },
    },
  },
];

// ── Server setup ──────────────────────────────────────────────────────────────

const server = new Server(
  { name: 'pixelplayer-kg', version: '0.1.0' },
  { capabilities: { tools: {} } },
);

server.setRequestHandler(ListToolsRequestSchema, async () => ({ tools: TOOLS }));

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args = {} } = request.params;
  const dbPath = DEFAULT_DB_PATH;

  const text = (t: string) => ({
    content: [{ type: 'text' as const, text: t }],
  });

  try {
    switch (name) {
      case 'kg_overview': {
        const data = core.overview({ dbPath, limit: args['limit'] as number | undefined });
        return text(fmt.formatOverview(data));
      }

      case 'kg_search': {
        const query = String(args['query'] ?? '');
        if (!query) return text('Error: query is required');
        const data = core.search(query, {
          dbPath,
          limit: args['limit'] as number | undefined,
          type: args['type'] as string | undefined,
        });
        return text(fmt.formatSearch(data, query));
      }

      case 'kg_node': {
        const id = String(args['id'] ?? '');
        if (!id) return text('Error: id is required');
        const data = core.node(id, { dbPath, limit: args['limit'] as number | undefined });
        return text(fmt.formatNode(data));
      }

      case 'kg_dependents': {
        const id = String(args['id'] ?? '');
        if (!id) return text('Error: id is required');
        const data = core.dependents(id, { dbPath, limit: args['limit'] as number | undefined });
        return text(fmt.formatDependents(data, id));
      }

      case 'kg_dependencies': {
        const id = String(args['id'] ?? '');
        if (!id) return text('Error: id is required');
        const data = core.dependencies(id, { dbPath, limit: args['limit'] as number | undefined });
        return text(fmt.formatDependencies(data, id));
      }

      case 'kg_neighbors': {
        const id = String(args['id'] ?? '');
        if (!id) return text('Error: id is required');
        const direction = (['in', 'out', 'both'] as const).find(
          (d) => d === args['direction'],
        );
        const data = core.neighbors(id, {
          dbPath,
          limit: args['limit'] as number | undefined,
          direction,
          edgeType: args['edgeType'] as string | undefined,
          depth: args['depth'] as number | undefined,
        });
        return text(fmt.formatNeighbors(data, id));
      }

      case 'kg_path': {
        const sourceId = String(args['sourceId'] ?? '');
        const targetId = String(args['targetId'] ?? '');
        if (!sourceId || !targetId)
          return text('Error: sourceId and targetId are required');
        const data = core.path(sourceId, targetId, {
          dbPath,
          maxDepth: args['maxDepth'] as number | undefined,
        });
        return text(fmt.formatPath(data));
      }

      default:
        return text(`Unknown tool: ${name}`);
    }
  } catch (err) {
    return text(
      `Error: ${err instanceof Error ? err.message : String(err)}`,
    );
  }
});

// ── Start ─────────────────────────────────────────────────────────────────────

const transport = new StdioServerTransport();
server.connect(transport).catch((err) => {
  process.stderr.write(`MCP server error: ${err}\n`);
  process.exit(1);
});
