<template>
  <div class="dependency-paths-container">
    
    <div v-if="!props.selectedDependency">
      <div class="placeholder">Please select a dependency node</div>
    </div>
    <div v-else>
      <div class="title">All paths to the dependency (total: {{ paths.length }}):</div>

      <div v-if="paths.length === 0" class="no-paths">No path found</div>
      <div v-for="(path, pathIdx) in paths" :key="pathIdx" class="path-block">
        <DependencyPathNode
          v-if="path.length > 0"
          :node="path[0]"
          :path="path"
          :pathIdx="pathIdx"
          :nodeIdx="0"
          :expandState="expandState"
          :selectedNodeInfo="selectedNodeInfo"
          :selectedDependency="props.selectedDependency"
          :menuItemsRef="menuItemsRef"
          :vscodeApi="props.vscodeApi"
          :showGroupId="props.showGroupId"
          :showSize="props.showSize"
          @node-click="handleNodeClick"
          @node-contextmenu="handleNodeContextMenu"
        />
      </div>
    </div>
    <!-- Right-click menu component -->
    <ContextMenu
      :visible="menuVisible"
      :x="menuX"
      :y="menuY"
      :items="menuItemsRef"
      @select="handleMenuSelect"
      @close="menuVisible = false"
    />
  </div>
</template>

<script setup lang="ts">
import { watch, ref } from 'vue'
import ContextMenu from './ContextMenu.vue'
import DependencyPathNode from './DependencyPathNode.vue'

const props = defineProps({
  dependencyTree: { type: Array, default: () => [] },
  selectedDependency: { type: Object, default: null },
  vscodeApi: { type: Object, required: true },
  showGroupId: { type: Boolean, default: false },
  showSize: { type: Boolean, default: false } // New: control dependency size display
})

// Expand state: record expand state of each node in each path
const expandState = ref<{ [key: string]: boolean }>({})

// Right-click menu state
const menuVisible = ref(false)
const menuX = ref(0)
const menuY = ref(0)
const menuNode = ref<any>(null)
const menuPathIndex = ref<number>(-1)
const menuNodeIndex = ref<number>(-1)

// Right-click menu items (note: no longer constant, need to be generated dynamically based on node)
function getMenuItems(node: any, path: any[]): { label: string, value: string }[] {
  // Check if it's a top-level dependency (i.e., the last node in path)
  const isTopLevel = path && path.length > 0 && node === path[path.length - 1]
  // If it's a top-level dependency, don't show "Exclude this dependency"
  if (isTopLevel) {
    return [
      { label: 'Jump to pom.xml', value: 'goto-pom' },
      { label: 'Jump to Left Tree', value: 'goto-tree' } // New
    ]
  } else {
    return [
      { label: 'Jump to pom.xml', value: 'goto-pom' },
      { label: 'Exclulde', value: 'exclude' },
      { label: 'Jump to Left Tree', value: 'goto-tree' } // New
    ]
  }
}

// Right-click event handling
function handleNodeContextMenu(pathIndex: number, nodeIndex: number, node: any, path: any[], event: MouseEvent) {
  event.preventDefault()
  menuVisible.value = true
  menuX.value = event.clientX
  menuY.value = event.clientY
  menuNode.value = { node, path }
  menuPathIndex.value = pathIndex
  menuNodeIndex.value = nodeIndex
  // Dynamically set menu items
  menuItemsRef.value = getMenuItems(node, path)
}

// Menu item selection
function handleMenuSelect(action: string) {
  if (!menuNode.value) return
  const { node, path } = menuNode.value
  if (action === 'goto-tree') {
    // Jump to left dependency tree, pass complete path, use window.postMessage
    window.postMessage({ type: 'setSearchText', artifactId: node.artifactId }, '*')
    window.postMessage({
      type: 'gotoTreeNode',
      path: JSON.parse(JSON.stringify(path.slice(menuNodeIndex.value, path.length)))
    }, '*')
    return
  }
  // Send message through vscodeApi
  props.vscodeApi.postMessage({
    type: 'showContextMenu',
    data: {
      node: {
        groupId: node.groupId,
        artifactId: node.artifactId,
        version: node.version,
        scope: node.scope
      },
      pathIndex: menuPathIndex.value,
      nodeIndex: menuNodeIndex.value,
      pathInfo: path.map((pathNode: any) => ({
        groupId: pathNode.groupId,
        artifactId: pathNode.artifactId,
        version: pathNode.version,
        scope: pathNode.scope
      })),
      action // You can directly pass action to backend
    }
  })
}

// Right-click menu items reactive variable (replace original menuItems constant)
const menuItemsRef = ref(getMenuItems(null, []))


// Currently selected node information
const selectedNodeInfo = ref<Record<string, any>>({})

// Path finding algorithm: recursively traverse all paths, collect all paths to target dependency (same groupId+artifactId)
function findAllPaths(tree: any[], target: any): any[][] {
  const result: any[][] = []
  function dfs(node: any, path: any[]) {
    if (!node) return
    // Check if it matches target dependency (only compare groupId+artifactId)
    if (node.groupId === target.groupId && node.artifactId === target.artifactId) {
      result.push([node, ...path]) // Child node on top, parent node below
    }
    if (node.children && node.children.length > 0) {
      for (const child of node.children) {
        dfs(child, [node, ...path])
      }
    }
  }
  for (const root of tree) {
    dfs(root, [])
  }
  return result
}

const paths = ref<any[][]>([])

// Handle node click event
function handleNodeClick(pathIndex: number, nodeIndex: number, node: any, path: any[]) {
  // Get the highest node in the dependency tree (not current project)
  // In path array, the last element is the highest node
  const topLevelNode = path[path.length - 1]
  // Save selected dependency information
  selectedNodeInfo.value = {
    pathIndex,
    nodeIndex,
    dependency: {
      groupId: node.groupId,
      artifactId: node.artifactId,
      version: node.version,
      scope: node.scope
    },
    topLevelNode: {
      groupId: topLevelNode.groupId,
      artifactId: topLevelNode.artifactId,
      version: topLevelNode.version,
      scope: topLevelNode.scope
    }
  }
  
  // Here you can emit event to notify parent component, or add more logic later
  // emit('nodeSelected', selectedNodeInfo.value)
}


watch(
  () => [props.dependencyTree, props.selectedDependency],
  ([tree, dep]) => {
    if (tree && dep) {
      paths.value = findAllPaths(tree as any[], dep)
      // When dependency tree or selected dependency changes, clear right side selection state
      selectedNodeInfo.value = {}
    } else {
      paths.value = []
      selectedNodeInfo.value = {}
    }
  },
  { immediate: true, deep: true }
)
</script>

<style scoped>
.dependency-paths-container {
  height: 100vh;
  width: 100%;
  background: var(--vscode-editor-background);
  color: var(--vscode-foreground);
  font-family: var(--vscode-font-family);
  padding: 16px 0 0 0;
  box-sizing: border-box;
  overflow-y: auto;
  padding-left: 3%;
  text-align: left; /* Content overall left-aligned */
}
.title {
  font-weight: bold;
  margin-bottom: 12px;
  font-size: 16px;
  text-align: center; /* Only center the title */
}
.no-paths {
  color: var(--vscode-descriptionForeground);
  padding: 16px;
}
.placeholder {
  color: var(--vscode-descriptionForeground);
  padding: 32px;
  text-align: center;
}
.path-block {
  /* Adjust line spacing to match left dependency tree */
  margin-bottom: 2px;
  /* Remove left vertical line */
  /* border-left: 2px solid var(--vscode-panel-border); */
  padding-left: 8px;
}
</style>