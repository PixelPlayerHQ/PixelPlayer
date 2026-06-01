import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';

// Resolve directory name
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Target scan folders
const projectRoot = path.resolve(__dirname, '../../');
const scanTargets = [
  { name: 'app', path: path.join(projectRoot, 'app/src') },
  { name: 'wear', path: path.join(projectRoot, 'wear/src') },
  { name: 'shared', path: path.join(projectRoot, 'shared/src') }
];

console.log('--------------------------------------------------------');
console.log('🚀 Starting "understand-anything" Knowledge Graph Scan...');
console.log(`📂 Project Root: ${projectRoot}`);
console.log('--------------------------------------------------------');

// --- 1. LOAD ENV CONFIGURATION (NATIVE INTEGRATION) ---
const envVars = {};
const envPath = path.join(__dirname, '.env');

if (fs.existsSync(envPath)) {
  const envContent = fs.readFileSync(envPath, 'utf-8');
  envContent.split(/\r?\n/).forEach(line => {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) return;
    const equalsIndex = trimmed.indexOf('=');
    if (equalsIndex === -1) return;
    const key = trimmed.substring(0, equalsIndex).trim();
    let value = trimmed.substring(equalsIndex + 1).trim();
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.substring(1, value.length - 1);
    }
    if (key) {
      envVars[key] = value;
      process.env[key] = value; // also expose to environment
    }
  });
}

// --- 2. PARSE GRADLE VERSION CATALOG (libs.versions.toml) ---
const versions = {};
const libraries = {};

const tomlPath = path.join(projectRoot, 'gradle/libs.versions.toml');
if (fs.existsSync(tomlPath)) {
  console.log('📖 Parsing gradle/libs.versions.toml...');
  const tomlContent = fs.readFileSync(tomlPath, 'utf-8');
  let currentSection = '';

  const lines = tomlContent.split(/\r?\n/);
  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;

    // Check section header
    const sectionMatch = trimmed.match(/^\[([A-Za-z0-9_-]+)\]/);
    if (sectionMatch) {
      currentSection = sectionMatch[1];
      continue;
    }

    if (currentSection === 'versions') {
      const match = trimmed.match(/^([A-Za-z0-9_-]+)\s*=\s*"([^"]+)"/);
      if (match) {
        versions[match[1]] = match[2];
      }
    } else if (currentSection === 'libraries') {
      const keyMatch = trimmed.match(/^([A-Za-z0-9_-]+)\s*=\s*\{([^}]+)\}/);
      if (keyMatch) {
        const libKey = keyMatch[1];
        const properties = keyMatch[2];

        let module = '';
        let versionVal = '';
        let versionRef = '';

        const moduleMatch = properties.match(/module\s*=\s*"([^"]+)"/);
        const groupMatch = properties.match(/group\s*=\s*"([^"]+)"/);
        const nameMatch = properties.match(/name\s*=\s*"([^"]+)"/);

        if (moduleMatch) {
          module = moduleMatch[1];
        } else if (groupMatch && nameMatch) {
          module = `${groupMatch[1]}:${nameMatch[1]}`;
        }

        const versionMatch = properties.match(/version\s*=\s*"([^"]+)"/);
        const refMatch = properties.match(/version\.ref\s*=\s*"([^"]+)"/);

        if (versionMatch) {
          versionVal = versionMatch[1];
        } else if (refMatch) {
          versionRef = refMatch[1];
          versionVal = versions[versionRef] || `ref:${versionRef}`;
        }

        libraries[libKey] = { module, version: versionVal };
      }
    }
  }
}

// --- 3. DEFINE EXCLUSIONS (GENERATED CODE & BUILD DIRS) ---
const EXCLUDED_PATTERNS = [
  /\/build\//i,
  /\/\.gradle\//i,
  /\/\.git\//i,
  /\/node_modules\//i,
  /_Impl\.(java|kt)$/i,
  /_Factory\.(java|kt)$/i,
  /_MembersInjector\.(java|kt)$/i,
  /_HiltModules.*\.(java|kt)$/i,
  /_HiltComponents.*\.(java|kt)$/i,
  /Hilt_.*\.(java|kt)$/i,
  /Dagger.*\.(java|kt)$/i,
  /\$\$serializer\.(java|kt)$/i,
  /\.ksp\//i
];

function isGeneratedOrExcluded(filePath) {
  const normalized = filePath.replace(/\\/g, '/');
  return EXCLUDED_PATTERNS.some(pattern => pattern.test(normalized));
}

// --- 4. LOAD EXISTING GRAPH FOR HASH SKIP DATABASE ---
const existingGraphPath = path.join(__dirname, 'knowledge-graph.json');
const graphCache = {};
if (fs.existsSync(existingGraphPath)) {
  try {
    console.log('📦 Loading existing knowledge-graph.json for change-caching (hashing)...');
    const rawGraph = JSON.parse(fs.readFileSync(existingGraphPath, 'utf-8'));
    if (rawGraph && Array.isArray(rawGraph.nodes)) {
      rawGraph.nodes.forEach(node => {
        const nodePath = node.filePath || node.path;
        if (nodePath && node.last_analyzed_hash) {
          graphCache[nodePath] = {
            description: node.summary || node.description || '',
            last_analyzed_hash: node.last_analyzed_hash
          };
        }
      });
    }
    console.log(`✅ Loaded ${Object.keys(graphCache).length} cached descriptions from existing graph.`);
  } catch (err) {
    console.warn('⚠️ Warning: Failed to parse existing knowledge-graph.json. Scanner will re-index all nodes.', err.message);
  }
}

// Helper to compute SHA-256
function computeSHA256(content) {
  return crypto.createHash('sha256').update(content).digest('hex');
}

// --- Helper to strip block comments, line comments, and string literals ---
function stripStringsAndComments(content) {
  // Replace block comments with spaces to preserve line offsets if needed
  let cleaned = content.replace(/\/\*[\s\S]*?\*\//g, (match) => match.replace(/[^\r\n]/g, ''));
  // Replace line comments
  cleaned = cleaned.replace(/\/\/.*$/gm, '');
  // Clean double-quoted string literals, handle escaped quotes
  cleaned = cleaned.replace(/"(\\.|[^"\\])*"/g, '""');
  // Clean triple-quoted raw string literals in Kotlin
  cleaned = cleaned.replace(/"""[\s\S]*?"""/g, '""""""');
  return cleaned;
}

// --- 5. INITIALIZE MULTI-PROVIDER LLM ANALYZER ---
class LLMAnalyzerRunner {
  constructor() {
    this.providers = [];
    this.temperature = 0.1;
    this.setupProviders();
  }

  setupProviders() {
    if (envVars['LOCAL_PROVIDER_ENABLED'] !== 'false') {
      this.providers.push({
        name: 'Local Ollama/LMStudio',
        type: 'ollama',
        baseURL: envVars['LOCAL_BASE_URL'] || 'http://localhost:11434/v1',
        apiKey: envVars['LOCAL_API_KEY'] || 'ollama',
        model: envVars['LOCAL_MODEL'] || 'gemma4:24b'
      });
    }
    if (envVars['GEMINI_PROVIDER_ENABLED'] === 'true') {
      this.providers.push({
        name: 'Google Gemini Cloud',
        type: 'gemini',
        baseURL: envVars['GEMINI_BASE_URL'] || 'https://generativelanguage.googleapis.com/v1beta',
        apiKey: envVars['GEMINI_API_KEY'] || '',
        model: envVars['GEMINI_MODEL'] || 'gemini-1.5-flash'
      });
    }
    if (envVars['OPENAI_PROVIDER_ENABLED'] === 'true') {
      this.providers.push({
        name: 'OpenAI Standard API',
        type: 'openai',
        baseURL: envVars['OPENAI_BASE_URL'] || 'https://api.openai.com/v1',
        apiKey: envVars['OPENAI_API_KEY'] || '',
        model: envVars['OPENAI_MODEL'] || 'gpt-4o-mini'
      });
    }

    if (this.providers.length === 0) {
      // Offline fallback
      this.providers.push({
        name: 'Offline Fallback',
        type: 'offline',
        baseURL: '',
        apiKey: '',
        model: ''
      });
    }
  }

  getSystemPrompt() {
    return [
      'Act as a Senior Software Architect expert in Android multi-modular codebases.',
      'Analyze the provided source code file and generate a very concise technical description.',
      'CRITICAL: The description must be a single sentence and MUST NOT exceed 150 characters.',
      'Focus strictly on its core architectural responsibility (e.g., repository, viewmodel, UI component, database DAO, etc.) in the music player.',
      'DO NOT write introductory phrases (like "This class..."). Start directly with the action/responsibility.',
      'DO NOT return markdown, code blocks, or explanations. Only the raw description.'
    ].join('\n');
  }

  async analyzeCode(filePath, content) {
    const systemPrompt = this.getSystemPrompt();
    
    for (let i = 0; i < this.providers.length; i++) {
      const provider = this.providers[i];
      if (provider.type === 'offline') {
        throw new Error('No active LLM providers configured in .env');
      }

      if (i > 0) {
        console.log(`   ⚠️ [LLM Failover] Retrying with "${provider.name}" for: ${filePath}`);
      }
      try {
        let result = '';
        if (provider.type === 'gemini' && provider.baseURL.includes('generativelanguage.googleapis.com')) {
          result = await this.queryGeminiNative(provider, systemPrompt, content);
        } else {
          result = await this.queryOpenAI(provider, systemPrompt, content);
        }

        const trimmed = result.trim().replace(/^["']|["']$/g, '');
        if (trimmed) return trimmed;
      } catch (err) {
        console.warn(`   ⚠️ Provider "${provider.name}" failed: ${err.message}`);
      }
    }
    throw new Error('All configured LLM providers failed.');
  }

  async queryOpenAI(provider, systemPrompt, content) {
    const url = `${provider.baseURL}/chat/completions`.replace(/([^:]\/)\/+/g, '$1');
    const headers = { 'Content-Type': 'application/json' };
    if (provider.apiKey) headers['Authorization'] = `Bearer ${provider.apiKey}`;

    const res = await fetch(url, {
      method: 'POST',
      headers,
      body: JSON.stringify({
        model: provider.model,
        messages: [
          { role: 'system', content: systemPrompt },
          { role: 'user', content: content }
        ],
        temperature: this.temperature
      })
    });

    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    return data.choices?.[0]?.message?.content || '';
  }

  async queryGeminiNative(provider, systemPrompt, content) {
    const url = `${provider.baseURL}/models/${provider.model}:generateContent?key=${provider.apiKey}`;
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        contents: [{ role: 'user', parts: [{ text: `${systemPrompt}\n\nCode:\n${content}` }] }],
        generationConfig: { temperature: this.temperature, maxOutputTokens: 60 }
      })
    });

    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    return data.candidates?.[0]?.content?.parts?.[0]?.text || '';
  }
}

const llmAnalyzer = new LLMAnalyzerRunner();

// --- 6. PROJECT STRUCTURAL SCANNER & ATOMIC ANALYSIS ---
let gitCommitHash = 'unknown';
try {
  gitCommitHash = execSync('git rev-parse HEAD').toString().trim();
} catch (e) {}

const graph = {
  version: '1.0.0',
  kind: 'codebase',
  project: {
    name: 'PixelPlayer',
    languages: ['kotlin', 'java'],
    frameworks: ['android'],
    description: 'Android music player app with multi-modular architecture.',
    analyzedAt: new Date().toISOString(),
    gitCommitHash: gitCommitHash
  },
  nodes: [],
  edges: [],
  layers: [
    {
      id: 'Presentation',
      name: 'Presentation Layer',
      description: 'App UI and player presentation layer (app module)',
      nodeIds: []
    },
    {
      id: 'Wearable',
      name: 'Wearable Layer',
      description: 'WearOS smart watch player module (wear module)',
      nodeIds: []
    },
    {
      id: 'Domain-Shared',
      name: 'Domain-Shared Layer',
      description: 'Core business domain and shared player logic (shared module)',
      nodeIds: []
    }
  ],
  tour: []
};

function walkDir(dir, callback) {
  if (!fs.existsSync(dir)) return;
  const files = fs.readdirSync(dir);
  for (const file of files) {
    const filePath = path.join(dir, file);
    const stat = fs.statSync(filePath);
    if (stat.isDirectory()) {
      if (!isGeneratedOrExcluded(filePath)) {
        walkDir(filePath, callback);
      }
    } else {
      if ((filePath.endsWith('.kt') || filePath.endsWith('.kts') || filePath.endsWith('.java')) && !isGeneratedOrExcluded(filePath)) {
        callback(filePath);
      }
    }
  }
}

console.log('🔍 Indexing application source files and building atomic descriptions...');
let scannedFiles = [];
walkDir(projectRoot, (filePath) => {
  const isTarget = scanTargets.some(t => filePath.startsWith(t.path));
  if (isTarget) scannedFiles.push(filePath);
});

// --- CLASS RESOLVER MAPPINGS FOR DEP/CALL GRAPH ---
const classToFileMap = {}; // Maps FQCN to relativePath (e.g. "com.theveloper.pixelplay.data.PlaylistRepository" -> "shared/src/...")
const shortClassToFilesMap = {}; // Maps short name to array of relativePaths (e.g. "PlaylistRepository" -> ["shared/src/..."])
const fileToPackageMap = {}; // Maps relativePath to package name
const fileToDeclaredClassesMap = {}; // Maps relativePath to declared class names array

// --- PASS 1: PRE-INDEX PACKAGES AND CLASS STRUCTURES ---
console.log('📇 [Pass 1] Pre-indexing packages and class structures...');
for (const filePath of scannedFiles) {
  const relativePath = path.relative(projectRoot, filePath).replace(/\\/g, '/');
  try {
    const rawContent = fs.readFileSync(filePath, 'utf-8');
    const content = stripStringsAndComments(rawContent);

    // Extract package name
    const packageMatch = content.match(/package\s+([A-Za-z0-9_.]+)/);
    const packageName = packageMatch ? packageMatch[1] : '';
    if (packageName) {
      fileToPackageMap[relativePath] = packageName;
    }

    // Parse classes, interfaces, objects, enum classes
    const declaredClasses = [];
    const classMatches = content.matchAll(/(?:class|interface|object|enum\s+class)\s+([A-Za-z0-9_]+)/g);
    for (const match of classMatches) {
      const className = match[1];
      declaredClasses.push(className);
      
      if (packageName) {
        const fqcn = `${packageName}.${className}`;
        classToFileMap[fqcn] = relativePath;
      }
      
      if (!shortClassToFilesMap[className]) {
        shortClassToFilesMap[className] = [];
      }
      if (!shortClassToFilesMap[className].includes(relativePath)) {
        shortClassToFilesMap[className].push(relativePath);
      }
    }
    fileToDeclaredClassesMap[relativePath] = declaredClasses;
  } catch (err) {
    console.error(`❌ Error in Pass 1 for ${relativePath}:`, err.message);
  }
}
console.log(`✅ Pre-indexed ${Object.keys(classToFileMap).length} classes across ${scannedFiles.length} files.`);

// --- PASS 2: ATOMIC COMPONENT LLM ANALYSIS & CACHING ---
console.log('📡 [Pass 2] Analyzing code components and generating summaries...');
let cacheHits = 0;
let apiCalls = 0;
let apiFailures = 0;
let currentIndex = 0;
const totalFiles = scannedFiles.length;

for (const filePath of scannedFiles) {
  currentIndex++;
  const relativePath = path.relative(projectRoot, filePath).replace(/\\/g, '/');
  const progressPercent = Math.round((currentIndex / totalFiles) * 100);
  const progressStr = `[${currentIndex}/${totalFiles}] (${progressPercent}%)`;

  let layer = 'Unknown';
  if (relativePath.startsWith('app/')) {
    layer = 'Presentation';
  } else if (relativePath.startsWith('wear/')) {
    layer = 'Wearable';
  } else if (relativePath.startsWith('shared/')) {
    layer = 'Domain-Shared';
  }

  const node = {
    id: relativePath,
    type: 'file',
    name: path.basename(filePath),
    filePath: relativePath,
    summary: '',
    tags: [layer, filePath.endsWith('.kt') || filePath.endsWith('.kts') ? 'kotlin' : 'java'],
    complexity: 'moderate',
    classes: fileToDeclaredClassesMap[relativePath] || [],
    functions: [],
    hiltRole: 'none',
    last_analyzed_hash: ''
  };

  try {
    const rawContent = fs.readFileSync(filePath, 'utf-8');
    const content = stripStringsAndComments(rawContent);
    const fileHash = computeSHA256(rawContent);

    // Enrich tags with codebase keyword detection for feature-aware search (smart segment prefix matching)
    const KEYWORDS_TO_TAG = [
      { tag: 'crossfade', prefix: 'crossfade' },
      { tag: 'equalizer', prefix: 'equaliz' },
      { tag: 'lyrics', prefix: 'lyric' },
      { tag: 'playlist', prefix: 'playlist' },
      { tag: 'theme', prefix: 'theme' },
      { tag: 'cast', prefix: 'cast' },
      { tag: 'sleep', prefix: 'sleep' },
      { tag: 'widget', prefix: 'widget' },
      { tag: 'replaygain', prefix: 'replaygain' }
    ];

    const normalizedContentForTagging = rawContent
      .replace(/([a-z])([A-Z])/g, '$1 $2')
      .replace(/([A-Z])([A-Z][a-z])/g, '$1 $2')
      .replace(/_/g, ' ');

    KEYWORDS_TO_TAG.forEach(({ tag, prefix }) => {
      const regex = new RegExp(`\\b${prefix}\\w*`, 'i');
      if (regex.test(normalizedContentForTagging)) {
        // Exclude false-positive matches for crossfade from UI transition features (Coil/Compose Crossfade animations)
        if (tag === 'crossfade') {
          const cleanedForCrossfadeCheck = rawContent
            .replace(/import\s+androidx\.compose\.animation\.Crossfade/g, '')
            .replace(/Crossfade\s*\([^)]*\)/g, '')
            .replace(/\.crossfade\s*\([^)]*\)/g, '')
            .replace(/crossfadeDurationMillis/g, '')
            .replace(/Coil/g, ''); // ignore mentions of Coil crossfade
            
          const normalizedForCrossfade = cleanedForCrossfadeCheck
            .replace(/([a-z])([A-Z])/g, '$1 $2')
            .replace(/([A-Z])([A-Z][a-z])/g, '$1 $2')
            .replace(/_/g, ' ');
            
          if (!regex.test(normalizedForCrossfade)) {
            return; // Skip tagging since it's just image transition / UI layout animation crossfade
          }
        }

        if (!node.tags.includes(tag)) {
          node.tags.push(tag);
        }
      }
    });

    // Parse functions
    const funMatches = content.matchAll(/fun\s+([A-Za-z0-9_]+)/g);
    for (const match of funMatches) {
      node.functions.push(match[1]);
    }

    // Identify Hilt Role
    if (content.includes('@AndroidEntryPoint')) {
      node.hiltRole = 'entry_point';
    } else if (content.includes('@Module') && content.includes('@InstallIn')) {
      node.hiltRole = 'module';
    } else if (content.includes('@Inject')) {
      node.hiltRole = 'injectable';
    }

    // --- OPTIMIZATION: HASH SKIP SYSTEM ---
    const cache = graphCache[relativePath];
    let summaryResolved = '';
    if (cache && cache.description && cache.last_analyzed_hash === fileHash) {
      summaryResolved = cache.description;
      cacheHits++;
      console.log(`⚡ ${progressStr} [CACHE HIT] ${relativePath}`);
    } else {
      apiCalls++;
      const activeModel = llmAnalyzer.providers[0]?.model || 'Local Model';
      console.log(`📡 ${progressStr} [LLM QUERY] ${relativePath} (Model: ${activeModel})`);
      try {
        summaryResolved = await llmAnalyzer.analyzeCode(relativePath, rawContent);
        console.log(`   └─ ✅ Description: "${summaryResolved}"`);
      } catch (err) {
        apiFailures++;
        if (cache && cache.description) {
          summaryResolved = cache.description;
          console.warn(`   └─ ⚠️ [LLM Error] Failed to re-analyze: ${relativePath}. Retaining prior description.`);
        } else {
          summaryResolved = `Core component of the ${layer} layer handling specific player business workflows.`;
          console.warn(`   └─ ❌ [LLM Error] Using standard fallback description.`);
        }
      }
    }

    // Natively enforce [DISABLED/EXPERIMENTAL] annotation if present in source comments
    if (rawContent.includes('// [DISABLED/EXPERIMENTAL]') || rawContent.includes('// EXPERIMENTAL') || rawContent.includes('// DISABLED')) {
      if (!summaryResolved.startsWith('[DISABLED/EXPERIMENTAL]')) {
        summaryResolved = `[DISABLED/EXPERIMENTAL] ${summaryResolved}`;
      }
    }

    node.summary = summaryResolved;
    node.last_analyzed_hash = fileHash;
    graph.nodes.push(node);

    // Track node in its respective layer nodeIds
    if (layer === 'Presentation') {
      graph.layers[0].nodeIds.push(node.id);
    } else if (layer === 'Wearable') {
      graph.layers[1].nodeIds.push(node.id);
    } else if (layer === 'Domain-Shared') {
      graph.layers[2].nodeIds.push(node.id);
    }

  } catch (err) {
    console.error(`❌ Error parsing ${relativePath}:`, err.message);
  }
}

console.log(`📊 Scanned source files: ${scannedFiles.length}`);
console.log(`💾 Skip Hashing Cache Hits: ${cacheHits} (Skipped LLM calls)`);

// --- PASS 3: RESOLVE IMPORTS, CALL GRAPHS, AND HILT DI EDGES ---
console.log('🔗 [Pass 3] Generating architectural edges (Imports, Calls, Hilt DI)...');
let importEdgesCount = 0;
let callEdgesCount = 0;
let hiltEdgesCount = 0;

for (const node of graph.nodes) {
  if (node.type !== 'file') continue;

  const filePath = path.join(projectRoot, node.filePath);
  if (fs.existsSync(filePath)) {
    try {
      const rawContent = fs.readFileSync(filePath, 'utf-8');
      const contentWithoutComments = stripStringsAndComments(rawContent);
      
      // 1. Resolve explicit imports
      const imports = rawContent.match(/import\s+([A-Za-z0-9_.]+)/g) || [];
      const importedClassNames = new Set();
      const importedPackages = new Set();

      imports.forEach(imp => {
        const importedPackage = imp.replace('import ', '').trim();
        if (importedPackage.startsWith('com.theveloper.pixelplay')) {
          if (importedPackage.endsWith('.*')) {
            importedPackages.add(importedPackage.slice(0, -2));
          } else {
            const targetFileId = classToFileMap[importedPackage];
            const className = importedPackage.split('.').pop();
            if (className) importedClassNames.add(className);
            
            if (targetFileId && targetFileId !== node.id) {
              const edgeExists = graph.edges.some(e => e.source === node.id && e.target === targetFileId && e.type === 'imports');
              if (!edgeExists) {
                graph.edges.push({
                  source: node.id,
                  target: targetFileId,
                  type: 'imports',
                  direction: 'forward',
                  weight: 0.4
                });
                importEdgesCount++;
              }
            }
          }
        }
      });

      // 2. Resolve Hilt DI constructor/field injection edges
      // A. Constructor Injection: `@Inject constructor(...)`
      const constructorRegex = /@Inject\s+(?:internal\s+|private\s+)?constructor\s*\(([^)]+)\)/g;
      let constructorMatch;
      while ((constructorMatch = constructorRegex.exec(contentWithoutComments)) !== null) {
        const paramsText = constructorMatch[1];
        
        // Split parameters by comma ignoring commas inside generics like Map<K, V>
        const params = [];
        let currentParam = '';
        let genericDepth = 0;
        for (let i = 0; i < paramsText.length; i++) {
          const char = paramsText[i];
          if (char === '<') genericDepth++;
          else if (char === '>') genericDepth--;
          
          if (char === ',' && genericDepth === 0) {
            params.push(currentParam.trim());
            currentParam = '';
          } else {
            currentParam += char;
          }
        }
        if (currentParam.trim()) params.push(currentParam.trim());

        params.forEach(param => {
          const colonIndex = param.indexOf(':');
          if (colonIndex !== -1) {
            let typePart = param.substring(colonIndex + 1).trim();
            // Remove annotations
            typePart = typePart.replace(/@[A-Za-z0-9_]+/g, '').trim();
            // Extract core class names
            const typeNames = [...typePart.matchAll(/[A-Za-z0-9_]+/g)].map(m => m[0]);
            
            typeNames.forEach(typeName => {
              if (['String', 'Int', 'Boolean', 'Long', 'Float', 'Double', 'Context', 'Application'].includes(typeName)) return;
              
              const targetFileId = resolveClassToFile(typeName, node.id, importedClassNames, importedPackages);
              if (targetFileId && targetFileId !== node.id) {
                const edgeExists = graph.edges.some(e => e.source === node.id && e.target === targetFileId && e.type === 'depends_on' && e.description === `Injects ${typeName}`);
                if (!edgeExists) {
                  graph.edges.push({
                    source: node.id,
                    target: targetFileId,
                    type: 'depends_on',
                    direction: 'forward',
                    description: `Injects ${typeName}`,
                    weight: 0.8
                  });
                  hiltEdgesCount++;
                }
              }
            });
          }
        });
      }

      // B. Field Injection: `@Inject lateinit var myService: MyService`
      const fieldInjectRegex = /@Inject\s+(?:lateinit\s+var|var|val)\s+[A-Za-z0-9_]+\s*:\s*([A-Za-z0-9_<>\s,.]+)/g;
      let fieldMatch;
      while ((fieldMatch = fieldInjectRegex.exec(contentWithoutComments)) !== null) {
        const typePart = fieldMatch[1].trim();
        const typeNames = [...typePart.matchAll(/[A-Za-z0-9_]+/g)].map(m => m[0]);
        typeNames.forEach(typeName => {
          if (['String', 'Int', 'Boolean', 'Long', 'Float', 'Double', 'Context', 'Application'].includes(typeName)) return;
          const targetFileId = resolveClassToFile(typeName, node.id, importedClassNames, importedPackages);
          if (targetFileId && targetFileId !== node.id) {
            const edgeExists = graph.edges.some(e => e.source === node.id && e.target === targetFileId && e.type === 'depends_on' && e.description === `Injects ${typeName}`);
            if (!edgeExists) {
              graph.edges.push({
                source: node.id,
                target: targetFileId,
                type: 'depends_on',
                direction: 'forward',
                description: `Injects ${typeName}`,
                weight: 0.8
              });
              hiltEdgesCount++;
            }
          }
        });
      }

      // C. Hilt Modules `@Provides` and `@Binds`
      if (node.hiltRole === 'module') {
        const moduleBindingRegex = /@(?:Provides|Binds)[\s\S]*?fun\s+[A-Za-z0-9_]+\s*\(([^)]*)\)\s*:\s*([A-Za-z0-9_<>\s,.]+)/g;
        let moduleMatch;
        while ((moduleMatch = moduleBindingRegex.exec(contentWithoutComments)) !== null) {
          const paramsText = moduleMatch[1];
          const returnTypePart = moduleMatch[2].trim();
          
          const returnTypes = [...returnTypePart.matchAll(/[A-Za-z0-9_]+/g)].map(m => m[0]);
          returnTypes.forEach(returnType => {
            if (['String', 'Int', 'Boolean', 'Long', 'Float', 'Double', 'Context', 'Application'].includes(returnType)) return;
            const targetFileId = resolveClassToFile(returnType, node.id, importedClassNames, importedPackages);
            if (targetFileId && targetFileId !== node.id) {
              const edgeExists = graph.edges.some(e => e.source === node.id && e.target === targetFileId && e.type === 'depends_on' && e.description === `Provides ${returnType}`);
              if (!edgeExists) {
                graph.edges.push({
                  source: node.id,
                  target: targetFileId,
                  type: 'depends_on',
                  direction: 'forward',
                  description: `Provides ${returnType}`,
                  weight: 0.7
                });
                hiltEdgesCount++;
              }
            }
          });

          const paramTypes = [...paramsText.matchAll(/:\s*([A-Za-z0-9_]+)/g)].map(m => m[1]);
          paramTypes.forEach(paramType => {
            if (['String', 'Int', 'Boolean', 'Long', 'Float', 'Double', 'Context', 'Application'].includes(paramType)) return;
            const targetFileId = resolveClassToFile(paramType, node.id, importedClassNames, importedPackages);
            if (targetFileId && targetFileId !== node.id) {
              const edgeExists = graph.edges.some(e => e.source === node.id && e.target === targetFileId && e.type === 'depends_on' && e.description === `Requires ${paramType}`);
              if (!edgeExists) {
                graph.edges.push({
                  source: node.id,
                  target: targetFileId,
                  type: 'depends_on',
                  direction: 'forward',
                  description: `Requires ${paramType}`,
                  weight: 0.6
                });
                hiltEdgesCount++;
              }
            }
          });
        }
      }

      // 3. Resolve active Call Graph edges
      const declaredInCurrentFile = fileToDeclaredClassesMap[node.id] || [];

      Object.keys(shortClassToFilesMap).forEach(className => {
        if (declaredInCurrentFile.includes(className)) return;

        const targetFileId = resolveClassToFile(className, node.id, importedClassNames, importedPackages);
        if (targetFileId && targetFileId !== node.id) {
          const classRefRegex = new RegExp(`\\b${className}\\b`, 'g');
          if (classRefRegex.test(contentWithoutComments)) {
            const edgeExists = graph.edges.some(e => e.source === node.id && e.target === targetFileId && e.type === 'calls');
            if (!edgeExists) {
              graph.edges.push({
                source: node.id,
                target: targetFileId,
                type: 'calls',
                direction: 'forward',
                weight: 0.6
              });
              callEdgesCount++;
            }
          }
        }
      });

    } catch (e) {
      console.warn(`⚠️ Error building edges for ${node.id}:`, e.message);
    }
  }
}

// Helper to resolve class name to file ID
function resolveClassToFile(className, sourceFileId, importedClassNames, importedPackages) {
  const currentPackage = fileToPackageMap[sourceFileId];
  if (currentPackage) {
    const fqcn = `${currentPackage}.${className}`;
    if (classToFileMap[fqcn]) return classToFileMap[fqcn];
  }

  for (const importedClass of importedClassNames) {
    if (importedClass === className) {
      for (const [fqcn, fileId] of Object.entries(classToFileMap)) {
        if (fqcn.endsWith(`.${className}`)) {
          return fileId;
        }
      }
    }
  }

  for (const pkg of importedPackages) {
    const fqcn = `${pkg}.${className}`;
    if (classToFileMap[fqcn]) return classToFileMap[fqcn];
  }

  const candidateFiles = shortClassToFilesMap[className] || [];
  if (candidateFiles.length === 1) {
    return candidateFiles[0];
  } else if (candidateFiles.length > 1) {
    const sourceModule = sourceFileId.split('/')[0];
    const matchingModuleFile = candidateFiles.find(f => f.startsWith(`${sourceModule}/`));
    if (matchingModuleFile) return matchingModuleFile;
    
    const sharedFile = candidateFiles.find(f => f.startsWith('shared/'));
    if (sharedFile) return sharedFile;
    
    return candidateFiles[0];
  }

  return null;
}

console.log(`✅ Resolved:`);
console.log(`   ├─ ${importEdgesCount} explicit import edges`);
console.log(`   ├─ ${hiltEdgesCount} Hilt DI dependency edges`);
console.log(`   └─ ${callEdgesCount} method/class calls edges`);

// --- 7. PARSE GRADLE DEPENDENCY RELATIONSHIPS ---
console.log('🐘 Parsing module build.gradle.kts files for external libraries...');

const moduleDirs = ['app', 'wear', 'shared'];
moduleDirs.forEach(moduleName => {
  const gradlePath = path.join(projectRoot, moduleName, 'build.gradle.kts');
  if (fs.existsSync(gradlePath)) {
    const content = fs.readFileSync(gradlePath, 'utf-8');
    const moduleLayer = moduleName === 'app' ? 'Presentation' : (moduleName === 'wear' ? 'Wearable' : 'Domain-Shared');

    graph.nodes.push({
      id: `${moduleName}-module`,
      type: 'module',
      name: `${moduleName.charAt(0).toUpperCase() + moduleName.slice(1)} Module`,
      summary: `Core ${moduleName} Gradle module of PixelPlayer`,
      complexity: 'moderate',
      tags: [moduleLayer]
    });

    if (moduleLayer === 'Presentation') {
      graph.layers[0].nodeIds.push(`${moduleName}-module`);
    } else if (moduleLayer === 'Wearable') {
      graph.layers[1].nodeIds.push(`${moduleName}-module`);
    } else if (moduleLayer === 'Domain-Shared') {
      graph.layers[2].nodeIds.push(`${moduleName}-module`);
    }

    const libRegex = /libs\.([A-Za-z0-9._]+)/g;
    const matches = [...content.matchAll(libRegex)];
    const detectedDeps = new Set();

    matches.forEach(match => {
      const catalogKey = match[1].replace(/\./g, '-');
      detectedDeps.add(catalogKey);
    });

    detectedDeps.forEach(catalogKey => {
      const resolvedLib = libraries[catalogKey];
      if (resolvedLib) {
        const libNodeId = `ext-lib:${resolvedLib.module}`;
        const libCategory = resolvedLib.module.includes('media3') ? 'ExoPlayer/Media' 
                          : (resolvedLib.module.includes('hilt') ? 'DI/Hilt' 
                          : (resolvedLib.module.includes('room') ? 'Database/Room' 
                          : (resolvedLib.module.includes('compose') ? 'UI/Compose' : 'Other')));
        
        if (!graph.nodes.some(n => n.id === libNodeId)) {
          graph.nodes.push({
            id: libNodeId,
            type: 'resource',
            name: resolvedLib.module.split(':')[1] || resolvedLib.module,
            summary: `External dependency: ${resolvedLib.module} (v${resolvedLib.version})`,
            complexity: 'simple',
            tags: ['external-library', libCategory]
          });
        }

        graph.edges.push({
          source: `${moduleName}-module`,
          target: libNodeId,
          type: 'depends_on',
          direction: 'forward',
          weight: 0.6
        });
      }
    });

    graph.nodes.forEach(n => {
      if (n.type === 'file' && n.filePath && n.filePath.startsWith(`${moduleName}/`)) {
        graph.edges.push({
          source: `${moduleName}-module`,
          target: n.id,
          type: 'contains',
          direction: 'forward',
          weight: 0.8
        });
      }
    });
  }
});

// --- 8. SAVE KNOWLEDGE GRAPH ---
const outputPath = path.join(__dirname, 'knowledge-graph.json');
fs.writeFileSync(outputPath, JSON.stringify(graph, null, 2), 'utf-8');

console.log('--------------------------------------------------------');
console.log(`✅ Scan completed successfully!`);
console.log(`📁 Graph saved to: ${outputPath}`);
console.log(`📊 Total Nodes: ${graph.nodes.length} (Files, Modules, External Libraries)`);
console.log(`📊 Total Edges: ${graph.edges.length} (Imports, Calls, Hilt DI, Module relationships)`);
console.log('--------------------------------------------------------');
