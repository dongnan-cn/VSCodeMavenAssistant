<template>
  <div class="dependency-tree-container">
    <!-- Loading state -->
    <div v-if="loading" class="loading">
      Loading dependency tree...
    </div>
    <!-- Error state -->
    <div v-else-if="error" class="error">
      {{ error }}
    </div>
    <!-- Dependency tree content -->
    <div v-else-if="renderDependencyData && renderDependencyData.length > 0" class="dependency-tree">
      <ul class="dep-tree">
        <DependencyTreeNode
          v-for="(node, index) in renderDependencyData"
          :key="index"
          :node="node"
          :path="[]"
          :dataKey="`node-${index}`"
          :selectedNode="selectedNode"
          :showGroupId="showGroupId"
          :showSize="showSize"
          :vscodeApi="props.vscodeApi"
          @select="handleSelect"
        />
      </ul>
    </div>
    <!-- Empty state -->
    <div v-else class="empty-state">
      No dependency data
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, defineExpose, computed } from 'vue'
import DependencyTreeNode from './DependencyTreeNode.vue'
const emit = defineEmits(['select-dependency', 'update:filterMode'])

// Receive vscodeApi as prop
const props = defineProps({
  vscodeApi: { type: Object, required: true },
  searchText: { type: String, default: '' },
  showGroupId: { type: Boolean, default: false },
  filterMode: { type: Boolean, default: false },
  showSize: { type: Boolean, default: false },
  // Added: receive cached data and loading state
  cachedData: { type: Object, default: null },
  isDataLoaded: { type: Boolean, default: false }
})

// Define dependency node interface
interface DependencyNode {
  groupId: string
  artifactId: string
  version: string
  scope?: string
  children?: DependencyNode[]
  droppedByConflict?: boolean
  expanded?: boolean
  hasChildren?: boolean
  label?: string
  status?: string
  statusClass?: string
  matched?: boolean // Added property: for highlighting
}

// Reactive data
const loading = ref(true)
const error = ref('')
const dependencyData = ref<DependencyNode[]>([])
const selectedNode = ref<any>(null)

// Refresh dependency data
function refreshDependencies() {
  loading.value = true
  error.value = ''
  dependencyData.value = [] // Clear current data
  props.vscodeApi.postMessage({ type: 'refresh' })
}

// Select node (by unique id)
function handleSelect(_: string, node: DependencyNode) {
  selectedNode.value = node
  emit('select-dependency', {
    groupId: node.groupId,
    artifactId: node.artifactId,
    version: node.version,
    scope: node.scope
  }, dependencyData.value)
  props.vscodeApi.postMessage({ 
    type: 'selectNode', 
    node: {
      groupId: node.groupId,
      artifactId: node.artifactId,
      version: node.version,
      scope: node.scope
    }
  })
}

// Check if dependency is excluded
function isExcluded(node: any, parentExclusions: any[]): boolean {
  if (!parentExclusions || parentExclusions.length === 0) {
    return false
  }
  
  return parentExclusions.some(exclusion => 
    exclusion.groupId === node.groupId && exclusion.artifactId === node.artifactId
  )
}

// Recursively filter excluded dependencies
function filterExcludedDependencies(nodes: any[], parentExclusions: any[] = []): any[] {
  if (!nodes || !Array.isArray(nodes)) {
    return []
  }
  
  return nodes.filter(node => {
    // Check if current node is excluded by parent
    if (isExcluded(node, parentExclusions)) {
      return false
    }
    
    // Recursively process child dependencies, pass current node's exclusions
    if (node.children && node.children.length > 0) {
      const currentExclusions = node.exclusions || []
      node.children = filterExcludedDependencies(node.children, currentExclusions)
    }
    
    return true
  })
}

// Process dependency data
function processDependencyData(data: any): DependencyNode[] {
  if (!data || !Array.isArray(data)) {
    return []
  }
  
  // First filter excluded dependencies
  const filteredData = filterExcludedDependencies(data)
  
  const processed = filteredData.map((node: any) => {
    const hasChildren = node.children && node.children.length > 0
    return {
      ...node,
      hasChildren,
      expanded: false,
      children: hasChildren ? processDependencyData(node.children) : undefined
    }
  })
  
  return processed
}

function setAllExpanded(nodes: DependencyNode[], expanded: boolean) {
  nodes.forEach(node => {
    node.expanded = expanded
    if (node.children && node.children.length > 0) {
      setAllExpanded(node.children, expanded)
    }
  })
}

function expandAll() {
  setAllExpanded(dependencyData.value, true)
}

function collapseAll() {
  setAllExpanded(dependencyData.value, false)
}

// Search and highlight recursive logic
function searchAndHighlight(nodes: DependencyNode[], keyword: string): boolean {
  let foundInChildren = false
  nodes.forEach(node => {
    // Search in groupId and artifactId
    const groupIdMatch = keyword && node.groupId.toLowerCase().includes(keyword.toLowerCase())
    const artifactIdMatch = keyword && node.artifactId.toLowerCase().includes(keyword.toLowerCase())
    const matched = groupIdMatch || artifactIdMatch
    
    let childMatched = false
    if (node.children && node.children.length > 0) {
      childMatched = searchAndHighlight(node.children, keyword)
    }
    node.matched = !!matched
    node.expanded = !!(keyword && (childMatched || matched))
    foundInChildren = foundInChildren || matched || childMatched
  })
  return foundInChildren
}

// Recursively filter dependency tree, only keep matched nodes and their ancestor chains
function filterDependencyTree(nodes: DependencyNode[], keyword: string): DependencyNode[] {
  if (!nodes) return []
  const result: DependencyNode[] = []
  for (const node of nodes) {
    // Search in groupId and artifactId
    const groupIdMatch = keyword && node.groupId.toLowerCase().includes(keyword.toLowerCase())
    const artifactIdMatch = keyword && node.artifactId.toLowerCase().includes(keyword.toLowerCase())
    const matched = groupIdMatch || artifactIdMatch
    
    let filteredChildren: DependencyNode[] = []
    if (node.children && node.children.length > 0) {
      filteredChildren = filterDependencyTree(node.children, keyword)
    }
    if (matched || (filteredChildren && filteredChildren.length > 0)) {
      result.push({
        ...node,
        children: filteredChildren
      })
    }
  }
  return result
}

// Calculate actual dependency tree data for rendering
const renderDependencyData = computed(() => {
  if (props.filterMode && props.searchText) {
    return filterDependencyTree(dependencyData.value, props.searchText)
  }
  return dependencyData.value
})

watch(() => props.searchText, (val) => {
  if (!val) {
    // When clearing search, cancel all highlighting and auto-expansion
    setAllExpanded(dependencyData.value, false)
    clearMatched(dependencyData.value)
    return
  }
  searchAndHighlight(dependencyData.value, val)
})

function clearMatched(nodes: DependencyNode[]) {
  nodes.forEach(node => {
    node.matched = false
    if (node.children && node.children.length > 0) {
      clearMatched(node.children)
    }
  })
}

// Recursively find node matching path in filter result tree
function findNodeByPath(nodes: DependencyNode[], path: any[]): DependencyNode | null {
  if (!nodes || !path || path.length === 0) return null
  const seg = path[path.length - 1]
  for (const node of nodes) {
    if (
      node.groupId === seg.groupId &&
      node.artifactId === seg.artifactId &&
      node.version === seg.version &&
      (seg.scope ? node.scope === seg.scope : true)
    ) {
      // Path completely matches
      if (path.length === 1) return node
      // Recursively find child nodes
      if (node.children && node.children.length > 0) {
        const found = findNodeByPath(node.children, path.slice(0, -1))
        if (found) return found
      }
      // No child nodes or not found, return current
      return node
    }
  }
  return null
}

// Jump and highlight: strictly expand and select recursively by path level by level
function gotoAndHighlightNodeByPath(path: any[]) {
  let nodes = dependencyData.value
  let currentNode = null
  // path: from root to target, traverse in order
  for (let i = path.length - 1; i >= 0; i--) {
    const seg = path[i]
    currentNode = nodes.find((n: any) =>
      n.groupId === seg.groupId &&
      n.artifactId === seg.artifactId &&
      n.version === seg.version &&
      (seg.scope ? n.scope === seg.scope : true)
    )
    if (!currentNode) return // Jump failed
    currentNode.expanded = true
    nodes = currentNode.children || []
  }
  // --- Added: in filter mode, selectedNode points to node in filter result tree ---
  if (props.filterMode && props.searchText) {
    const filtered = renderDependencyData.value
    const filteredNode = findNodeByPath(filtered, path)
    if (filteredNode) {
      selectedNode.value = filteredNode
    } else {
      selectedNode.value = null
    }
  } else {
    selectedNode.value = currentNode
  }
  if (selectedNode.value) {
    emit('select-dependency', selectedNode.value, dependencyData.value)
    // When jumping, directly set search box content to ensure search highlighting and search box sync
    if (currentNode) {
      searchAndHighlight(dependencyData.value, currentNode.artifactId)
    }
  }
}

// Recursively remove dependencies with specified GA and their child dependencies
function removeDependencyByGA(nodes: DependencyNode[], groupId: string, artifactId: string): DependencyNode[] {
  return nodes.filter(node => {
    // If current node matches GA to be removed, filter it out
    if (node.groupId === groupId && node.artifactId === artifactId) {
      return false
    }
    // Recursively process child dependencies
    if (node.children && node.children.length > 0) {
      node.children = removeDependencyByGA(node.children, groupId, artifactId)
    }
    return true
  })
}

// Handle dependency tree update after exclude success
function handleExcludeSuccess(excludedDependency: { groupId: string, artifactId: string }) {
  // Remove all dependencies with specified GA from current dependency tree
  dependencyData.value = removeDependencyByGA(dependencyData.value, excludedDependency.groupId, excludedDependency.artifactId)
  
  // If currently selected node was removed, clear selection state
  if (selectedNode.value && 
      selectedNode.value.groupId === excludedDependency.groupId && 
      selectedNode.value.artifactId === excludedDependency.artifactId) {
    selectedNode.value = null
    emit('select-dependency', null, dependencyData.value)
  }
}

// Listen for messages from extension side
onMounted(() => {
  window.addEventListener('message', (event) => {
    const message = event.data
    
    switch (message.type) {
      case 'updateAnalysis':
        loading.value = false
        error.value = ''
        try {
          // Parse dependency tree JSON
          const dependencyTree = JSON.parse(message.data)
          
          // Compatible with root node format { children: [...] }
          let nodes: any[] = []
          if (dependencyTree && !dependencyTree.groupId && Array.isArray(dependencyTree.children)) {
            nodes = dependencyTree.children
          } else if (dependencyTree && dependencyTree.groupId) {
            nodes = [dependencyTree]
          }
          
          dependencyData.value = processDependencyData(nodes)
          
          // Trigger parent component's cache logic
          if (dependencyData.value.length > 0) {
            emit('select-dependency', null, dependencyData.value)
          }
        } catch (err) {
          error.value = `Parse failed: ${err}\n\nOriginal content:\n${message.data}`
        }
        break
      case 'error':
        loading.value = false
        error.value = message.message || 'Failed to get dependency data'
        break
      case 'gotoTreeNode': {
        const { path } = message
        gotoAndHighlightNodeByPath(path)
        break
      }
      case 'excludeSuccess': {
        // Handle exclude success message
        const { excludedDependency } = message
        if (excludedDependency) {
          handleExcludeSuccess(excludedDependency)
        }
        break
      }
    }
  })
  
  // Modified: check cached data, avoid duplicate loading
  if (props.cachedData && props.isDataLoaded) {
    dependencyData.value = processDependencyData(
      Array.isArray(props.cachedData) ? props.cachedData : 
      (props.cachedData.children || [props.cachedData])
    )
    loading.value = false
  } else if (dependencyData.value.length === 0) {
    refreshDependencies()
  }
})

// Method to jump to specified GAV
function jumpToGAV(gav: { groupId: string, artifactId: string, version: string }) {
  
  // Recursively find matching node
  function findGAVNode(nodes: DependencyNode[], targetGAV: any): DependencyNode | null {
    for (const node of nodes) {
      if (node.groupId === targetGAV.groupId && 
          node.artifactId === targetGAV.artifactId && 
          node.version === targetGAV.version) {
        return node
      }
      if (node.children && node.children.length > 0) {
        const found = findGAVNode(node.children, targetGAV)
        if (found) {
          // Expand parent node
          node.expanded = true
          return found
        }
      }
    }
    return null
  }
  
  // Find target node
  const targetNode = findGAVNode(dependencyData.value, gav)
  if (targetNode) {
    // Select target node
    selectedNode.value = targetNode
    emit('select-dependency', targetNode, dependencyData.value)
    
    // Highlight search results
    searchAndHighlight(dependencyData.value, gav.artifactId)
  } else {
    console.warn('⚠️ DependencyTree: GAV not found:', gav)
  }
}

defineExpose({ refreshDependencies, expandAll, collapseAll, jumpToGAV, gotoAndHighlightNodeByPath })
</script>

<style scoped>
/* Container styles - Reference Tailwind card design */
.dependency-tree-container {
  font-family: var(--vscode-font-family);
  color: var(--vscode-foreground);
  background: var(--vscode-editor-background);
  height: 100vh;
  overflow-y: auto;
  padding: 16px;
  margin: 0;
  box-sizing: border-box;
}

/* Loading and error state styles */
.loading, .empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 200px;
  gap: 12px;
  color: var(--vscode-descriptionForeground);
  font-size: 14px;
}

.error {
  color: var(--vscode-errorForeground);
  background-color: var(--vscode-inputValidation-errorBackground);
  border: 1px solid var(--vscode-inputValidation-errorBorder);
  border-radius: 6px;
  padding: 12px 16px;
  margin: 16px 0;
  text-align: center;
}

/* Dependency tree styles - Modern card design */
.dependency-tree {
  background: var(--vscode-editor-background);
  border-radius: 8px;
  padding: 8px;
}

.dep-tree, .dep-tree ul {
  list-style: none;
  margin: 0;
  padding: 0;
}

.dep-tree > ul {
  padding-left: 0;
}

.dep-tree ul ul {
  padding-left: 24px;
  margin-top: 4px;
}

/* Node row styles - Reference Tailwind card design */
.dep-node-row {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  margin: 2px 0;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid transparent;
  background: var(--vscode-editor-background);
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.dep-node-row:hover {
  background: var(--vscode-list-hoverBackground);
  border-color: var(--vscode-list-hoverBackground);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15);
  transform: translateY(-1px);
}

.dep-node-row.selected {
  background: var(--vscode-list-activeSelectionBackground);
  color: var(--vscode-list-activeSelectionForeground);
  border-color: var(--vscode-focusBorder);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  transform: translateY(-1px);
}

.dep-node-row.matched {
  background: var(--vscode-editor-findMatchHighlightBackground, #ffe564);
  color: var(--vscode-editor-findMatchHighlightForeground, #000);
  border-color: #fbbf24;
  box-shadow: 0 2px 8px rgba(251, 191, 36, 0.3);
}

/* Expand/collapse arrow styles */
.arrow {
  display: inline-block;
  width: 20px;
  height: 20px;
  font-size: 12px;
  color: var(--vscode-foreground);
  margin-right: 8px;
  vertical-align: middle;
  transition: all 0.2s ease;
  cursor: pointer;
  user-select: none;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.arrow:hover {
  background: var(--vscode-list-hoverBackground);
  color: var(--vscode-list-hoverForeground, var(--vscode-foreground));
}

.arrow.collapsed {
  transform: rotate(0deg);
}

.arrow.expanded {
  transform: rotate(90deg);
}

/* Dependency label styles */
.dep-label {
  flex: 1;
  cursor: pointer;
  user-select: none;
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
}

.dep-label .dropped {
  color: var(--vscode-errorForeground);
}

.dep-label .used {
  color: var(--vscode-textPreformat-foreground);
}

/* Child node container styles */
.dep-children {
  overflow: hidden;
  transition: max-height 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  max-height: 2000px;
}

li.collapsed > .dep-children {
  max-height: 0;
}

li.collapsed > .dep-children {
  display: block;
}

/* Dependency size display styles */
.dependency-size {
  color: var(--vscode-descriptionForeground);
  font-size: 11px;
  margin-right: 8px;
  font-weight: 500;
  min-width: 80px;
  text-align: right;
  background: var(--vscode-badge-background);
  color: var(--vscode-badge-foreground);
  padding: 2px 6px;
  border-radius: 12px;
}

/* GAV information styles */
.gav-info {
  display: flex;
  align-items: center;
  gap: 4px;
}

.group-id {
  color: var(--vscode-descriptionForeground);
  opacity: 0.8;
}

.artifact-id {
  color: var(--vscode-foreground);
  font-weight: 600;
}

.version {
  font-weight: 500;
}

.separator {
  color: var(--vscode-descriptionForeground);
  opacity: 0.6;
}

/* Scope badge styles */
.scope-badge {
  display: inline-flex;
  align-items: center;
  background: var(--vscode-badge-background);
  color: var(--vscode-badge-foreground);
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 10px;
  font-weight: 600;
  margin-left: 8px;
  text-transform: uppercase;
}

/* Color differentiation for different scopes */
.scope-badge.test {
  background: #4CAF50;
  color: white;
}

.scope-badge.runtime {
  background: #9C27B0;
  color: white;
}

.scope-badge.compile {
  background: var(--vscode-badge-background);
  color: var(--vscode-badge-foreground);
}

/* Conflict indicator styles */
.conflict-indicator {
  color: #F44336;
  font-weight: 600;
  margin-left: 8px;
  font-size: 11px;
  background: rgba(244, 67, 54, 0.1);
  padding: 2px 6px;
  border-radius: 8px;
  border: 1px solid rgba(244, 67, 54, 0.3);
}
</style>