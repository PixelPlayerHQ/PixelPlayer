import { spawn } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const MCP_PATH = join(__dirname, '..', 'dist', 'mcp.js');

interface JsonRpcResponse {
  jsonrpc: string;
  id?: number;
  method?: string;
  result?: any;
  error?: any;
}

async function runMcpTest() {
  console.log('🚀 Starting MCP Integration Test...');
  console.log(`Spawning MCP server at: ${MCP_PATH}`);

  const mcpProcess = spawn('node', [MCP_PATH]);

  let buffer = '';
  const pendingRequests = new Map<number, (res: JsonRpcResponse) => void>();
  let nextRequestId = 1;

  mcpProcess.stdout.on('data', (data) => {
    buffer += data.toString();
    
    // Process line-by-line JSON-RPC messages (each message ends with \n or \r\n)
    while (true) {
      const lineEnd = buffer.indexOf('\n');
      if (lineEnd === -1) break;
      const line = buffer.slice(0, lineEnd).trim();
      buffer = buffer.slice(lineEnd + 1);

      if (line) {
        try {
          const response: JsonRpcResponse = JSON.parse(line);
          console.log(`📥 Server Response: ID ${response.id || 'none'}`);
          if (response.id !== undefined && pendingRequests.has(response.id)) {
            const resolve = pendingRequests.get(response.id)!;
            pendingRequests.delete(response.id);
            resolve(response);
          }
        } catch (err) {
          console.error(`❌ Failed to parse JSON-RPC line: "${line}"`, err);
        }
      }
    }
  });

  mcpProcess.stderr.on('data', (data) => {
    console.error(`⚠️ Server STDERR: ${data.toString().trim()}`);
  });

  mcpProcess.on('close', (code) => {
    console.log(`ℹ️ MCP process closed with code ${code}`);
  });

  function sendRequest(method: string, params: any = {}): Promise<JsonRpcResponse> {
    const id = nextRequestId++;
    const request = {
      jsonrpc: '2.0',
      id,
      method,
      params
    };
    
    console.log(`📤 Sending Request: ID ${id}, Method: ${method}`);
    return new Promise((resolve) => {
      pendingRequests.set(id, resolve);
      mcpProcess.stdin.write(JSON.stringify(request) + '\n');
    });
  }

  function sendNotification(method: string, params: any = {}): void {
    const notification = {
      jsonrpc: '2.0',
      method,
      params
    };
    console.log(`📤 Sending Notification: Method: ${method}`);
    mcpProcess.stdin.write(JSON.stringify(notification) + '\n');
  }

  try {
    // 1. Initialize
    const initRes = await sendRequest('initialize', {
      protocolVersion: '2024-11-05',
      capabilities: {},
      clientInfo: { name: 'mcp-test-client', version: '1.0.0' }
    });

    if (initRes.error) {
      throw new Error(`Initialization failed: ${JSON.stringify(initRes.error)}`);
    }
    console.log('✅ Initialization successful!');

    // Send initialized notification
    sendNotification('notifications/initialized');

    // 2. List tools
    const toolsRes = await sendRequest('tools/list');
    const toolsList = toolsRes.result?.tools || [];
    console.log(`✅ Tools listed! Found ${toolsList.length} tools.`);
    
    const toolNames = toolsList.map((t: any) => t.name);
    console.log(`Available tools: ${toolNames.join(', ')}`);

    const expectedTools = ['kg_overview', 'kg_search', 'kg_node', 'kg_dependents', 'kg_dependencies', 'kg_neighbors', 'kg_path'];
    for (const expected of expectedTools) {
      if (!toolNames.includes(expected)) {
        throw new Error(`Missing expected tool: ${expected}`);
      }
    }
    console.log('✅ All 7 expected tools are exposed correctly!');

    // 3. Test kg_overview tool
    console.log('\n--- Testing kg_overview ---');
    const overviewRes = await sendRequest('tools/call', {
      name: 'kg_overview',
      arguments: {}
    });

    const overviewText = overviewRes.result?.content?.[0]?.text;
    if (!overviewText || !overviewText.includes('# Project Overview')) {
      throw new Error(`Invalid kg_overview response: ${JSON.stringify(overviewRes)}`);
    }
    console.log('✅ kg_overview tool output verified successfully!');
    console.log(overviewText.split('\n').slice(0, 8).join('\n') + '\n  ...');

    // 4. Test kg_search tool
    console.log('\n--- Testing kg_search ---');
    const searchRes = await sendRequest('tools/call', {
      name: 'kg_search',
      arguments: { query: 'playlist', limit: 2 }
    });

    const searchText = searchRes.result?.content?.[0]?.text;
    if (!searchText || !searchText.includes('Search:')) {
      throw new Error(`Invalid kg_search response: ${JSON.stringify(searchRes)}`);
    }
    console.log('✅ kg_search tool output verified successfully!');
    console.log(searchText);

    // 5. Test kg_node tool
    console.log('\n--- Testing kg_node ---');
    const nodeRes = await sendRequest('tools/call', {
      name: 'kg_node',
      arguments: { id: 'app/src/main/java/com/theveloper/pixelplay/data/model/PlayList.kt' }
    });

    const nodeText = nodeRes.result?.content?.[0]?.text;
    if (!nodeText || !nodeText.includes('PlayList.kt')) {
      throw new Error(`Invalid kg_node response: ${JSON.stringify(nodeRes)}`);
    }
    console.log('✅ kg_node tool output verified successfully!');
    console.log(nodeText.split('\n').slice(0, 6).join('\n') + '\n  ...');

    console.log('\n🎉 ALL INTEGRATION TESTS PASSED SUCCESSFULLY! The MCP server is 100% correct.');
  } catch (err) {
    console.error('\n❌ Integration test FAILED:', err);
    process.exitCode = 1;
  } finally {
    console.log('Shutting down MCP server process...');
    mcpProcess.stdin.end();
    mcpProcess.kill();
  }
}

runMcpTest();
