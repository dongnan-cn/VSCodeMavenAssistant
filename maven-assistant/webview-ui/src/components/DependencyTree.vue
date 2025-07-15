<template>
  <div class="dependency-tree-container">
    <!-- 加载状态 -->
    <div v-if="loading" class="loading">
      正在加载依赖分析...
    </div>
    <!-- 错误状态 -->
    <div v-else-if="error" class="error">
      {{ error }}
    </div>
    <!-- 依赖树内容 -->
    <div v-else-if="renderDependencyData && renderDependencyData.length > 0" class="dependency-tree">
      <ul class="dep-tree">
        <DependencyTreeNode
          v-for="(node, index) in renderDependencyData"
          :key="index"
          :node="node"
          :dataKey="`node-${index}`"
          :selectedNode="selectedNode"
          :showGroupId="showGroupId"
          :showSize="showSize"
          @select="handleSelect"
        />
      </ul>
    </div>
    <!-- 空状态 -->
    <div v-else class="empty-state">
      暂无依赖数据
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, defineExpose, computed } from 'vue'
import DependencyTreeNode from './DependencyTreeNode.vue'
const emit = defineEmits(['select-dependency', 'update:filterMode'])

// 接收vscodeApi作为prop
const props = defineProps({
  vscodeApi: { type: Object, required: true },
  searchText: { type: String, default: '' },
  showGroupId: { type: Boolean, default: false },
  filterMode: { type: Boolean, default: false },
  showSize: { type: Boolean, default: false } // 新增：控制依赖大小显示
})

// 定义依赖节点接口
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
  matched?: boolean // 新增属性：用于高亮
}

// 响应式数据
const loading = ref(true)
const error = ref('')
const dependencyData = ref<DependencyNode[]>([])
const selectedNode = ref<any>(null)

// 刷新依赖数据
function refreshDependencies() {
  loading.value = true
  error.value = ''
  props.vscodeApi.postMessage({ type: 'refresh' })
}

// 选择节点（通过唯一id）
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

// 处理依赖数据
function processDependencyData(data: any): DependencyNode[] {
  if (!data || !Array.isArray(data)) {
    return []
  }
  return data.map((node: any) => {
    const hasChildren = node.children && node.children.length > 0
    const status = node.droppedByConflict ? 'DROPPED' : 'USED'
    const statusClass = node.droppedByConflict ? 'dropped' : 'used'
    // 不再拼接label，交由子组件处理
    return {
      ...node,
      status,
      statusClass,
      hasChildren,
      expanded: false, // 默认全部收起
      children: hasChildren ? processDependencyData(node.children) : undefined
    }
  })
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

// 搜索与高亮递归逻辑
function searchAndHighlight(nodes: DependencyNode[], keyword: string): boolean {
  let foundInChildren = false
  nodes.forEach(node => {
    const matched = keyword && node.artifactId.toLowerCase().includes(keyword.toLowerCase())
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

// 递归过滤依赖树，仅保留命中节点及其祖先链
function filterDependencyTree(nodes: DependencyNode[], keyword: string): DependencyNode[] {
  if (!nodes) return []
  const result: DependencyNode[] = []
  for (const node of nodes) {
    const matched = keyword && node.artifactId.toLowerCase().includes(keyword.toLowerCase())
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

// 计算实际用于渲染的依赖树数据
const renderDependencyData = computed(() => {
  if (props.filterMode && props.searchText) {
    return filterDependencyTree(dependencyData.value, props.searchText)
  }
  return dependencyData.value
})

watch(() => props.searchText, (val) => {
  if (!val) {
    // 清空搜索时，全部取消高亮和自动展开
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

// 跳转并高亮：严格按 path 逐级递归展开和选中
function gotoAndHighlightNodeByPath(path: any[]) {
  console.log('gotoAndHighlightNodeByPath', path)
  let nodes = dependencyData.value
  let currentNode = null
  // path: 从 root 到 target，正序遍历
  for (let i = path.length - 1; i >= 0; i--) {
    const seg = path[i]
    console.log(`trying to find ${seg} from ${nodes}`)
    currentNode = nodes.find((n: any) =>
      n.groupId === seg.groupId &&
      n.artifactId === seg.artifactId &&
      n.version === seg.version &&
      (seg.scope ? n.scope === seg.scope : true)
    )
    if (!currentNode) return // 跳转失败
    currentNode.expanded = true
    nodes = currentNode.children || []
  }
  if (currentNode) {
    selectedNode.value = currentNode
    emit('select-dependency', currentNode, dependencyData.value)
    // 跳转时关闭filterMode，保证全树高亮
    if (props.filterMode) {
      emit('update:filterMode', false)
    }
    // 全树搜索高亮
    searchAndHighlight(dependencyData.value, currentNode.artifactId)
  }
}

// 监听来自扩展端的消息
onMounted(() => {
  window.addEventListener('message', (event) => {
    const message = event.data
    switch (message.type) {
      case 'updateAnalysis':
        loading.value = false
        error.value = ''
        try {
          // 解析依赖树JSON
          const dependencyTree = JSON.parse(message.data)
          // 兼容根节点为 { children: [...] } 的格式
          let nodes: any[] = []
          if (dependencyTree && !dependencyTree.groupId && Array.isArray(dependencyTree.children)) {
            nodes = dependencyTree.children
          } else if (dependencyTree && dependencyTree.groupId) {
            nodes = [dependencyTree]
          }
          dependencyData.value = processDependencyData(nodes)
        } catch (err) {
          error.value = `解析失败: ${err}\n\n原始内容:\n${message.data}`
        }
        break
      case 'error':
        loading.value = false
        error.value = message.message || '获取依赖数据失败'
        break
      case 'gotoTreeNode': {
        const { path } = message
        gotoAndHighlightNodeByPath(path)
        break
      }
    }
  })
  // 初始化时请求数据
  refreshDependencies()
})

defineExpose({ refreshDependencies, expandAll, collapseAll })
</script>

<style scoped>
.dependency-tree-container {
  font-family: var(--vscode-font-family);
  color: var(--vscode-foreground);
  background: var(--vscode-editor-background);
  margin: 0;
  padding: 0;
  height: 100vh;
  width: 100%;
  overflow-y: auto;
  box-sizing: border-box;
  padding-left: 3%;
  text-align: left; /* 内容整体靠左 */
}

/* 移除.toolbar相关样式 */

.loading, .error, .empty-state {
  text-align: center;
  padding: 40px 20px;
  color: var(--vscode-descriptionForeground);
}

.error {
  color: var(--vscode-errorForeground);
  background-color: var(--vscode-notificationsErrorBackground);
  border-radius: 3px;
  margin: 10px 0;
}

.dependency-tree {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.4;
}

.dep-tree, .dep-tree ul {
  list-style: none;
  margin: 0;
  padding-left: 1em;
}

.dep-node-row {
  display: flex;
  align-items: center;
  padding: 2px 4px;
  border-radius: 3px;
  transition: background 0.2s;
  cursor: pointer;
}

.dep-node-row.selected {
  background: var(--vscode-list-activeSelectionBackground);
  color: var(--vscode-list-activeSelectionForeground);
}

.dep-node-row:hover {
  background: var(--vscode-list-hoverBackground);
}

.arrow {
  display: inline-block;
  width: 1.2em; /* 增大宽度，保证对齐 */
  font-size: 1.1em; /* 加大三角图标字体 */
  color: var(--vscode-foreground); /* 适配主题色 */
  margin-right: 6px; /* 图标与依赖名间距 */
  vertical-align: middle;
  transition: transform 0.2s, color 0.2s;
  cursor: pointer;
  user-select: none;
}
.arrow.collapsed {
  transform: rotate(0deg); /* 闭合时向右 */
}
.arrow.expanded {
  transform: rotate(90deg); /* 展开时向下 */
}
.arrow:hover {
  color: var(--vscode-list-hoverForeground, var(--vscode-foreground)); /* 悬停时高亮 */
}

.dep-label {
  flex: 1;
  cursor: pointer;
  user-select: none;
}

.dep-label .dropped {
  color: var(--vscode-errorForeground);
}

.dep-label .used {
  color: var(--vscode-textPreformat-foreground);
}

.dep-children {
  overflow: hidden;
  transition: max-height 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  max-height: 2000px;
}

li.collapsed > .dep-children {
  max-height: 0;
}

li.collapsed > .dep-children {
  display: block;
}

/* 移除.search-input相关样式 */
</style> 