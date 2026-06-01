#!/bin/bash

# Knowledge Graph Auto-Update Script for PixelPlayer
# Integrates with git pre-commit/post-merge hooks

# Exit immediately if a command exits with a non-zero status
set -e

# Get directories relative to script location
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Run the project scanning script
if [ -f "$SCRIPT_DIR/scan-project.mjs" ]; then
  node "$SCRIPT_DIR/scan-project.mjs"
else
  echo "❌ Error: scan-project.mjs not found in $SCRIPT_DIR"
  exit 1
fi

# Build the derived SQLite knowledge-graph DB
if [ -f "$SCRIPT_DIR/query/dist/cli.js" ]; then
  echo "Building graph.db..."
  node "$SCRIPT_DIR/query/dist/cli.js" build
else
  echo "Warning: query/dist/cli.js not found — skipping graph.db build."
  echo "  Run: pnpm kg:compile && pnpm kg:build (from tools/knowledge-engine/)"
fi

echo "🎉 Codebase Knowledge Graph updated successfully!"
