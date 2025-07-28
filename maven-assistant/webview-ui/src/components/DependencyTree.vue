<template>
  <div class="dependency-tree-container">
    <!-- 加载状态 -->
    <div v-if="loading" class="loading">
      Loading dependency tree...
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
  showSize: { type: Boolean, default: false },
  // 新增：接收缓存数据和加载状态
  cachedData: { type: Object, default: null },
  isDataLoaded: { type: Boolean, default: false }
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
  dependencyData.value = [] // 清空当前数据
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

// 检查依赖是否被排除
function isExcluded(node: any, parentExclusions: any[]): boolean {
  if (!parentExclusions || parentExclusions.length === 0) {
    return false
  }
  
  return parentExclusions.some(exclusion => 
    exclusion.groupId === node.groupId && exclusion.artifactId === node.artifactId
  )
}

// 递归过滤被排除的依赖
function filterExcludedDependencies(nodes: any[], parentExclusions: any[] = []): any[] {
  if (!nodes || !Array.isArray(nodes)) {
    return []
  }
  
  return nodes.filter(node => {
    // 检查当前节点是否被父级排除
    if (isExcluded(node, parentExclusions)) {
      return false
    }
    
    // 递归处理子依赖，传递当前节点的exclusions
    if (node.children && node.children.length > 0) {
      const currentExclusions = node.exclusions || []
      node.children = filterExcludedDependencies(node.children, currentExclusions)
    }
    
    return true
  })
}

// 处理依赖数据
function processDependencyData(data: any): DependencyNode[] {
  if (!data || !Array.isArray(data)) {
    return []
  }
  
  // 首先过滤被排除的依赖
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

// 搜索与高亮递归逻辑
function searchAndHighlight(nodes: DependencyNode[], keyword: string): boolean {
  let foundInChildren = false
  nodes.forEach(node => {
    // 在 groupId 和 artifactId 中搜索
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

// 递归过滤依赖树，仅保留命中节点及其祖先链
function filterDependencyTree(nodes: DependencyNode[], keyword: string): DependencyNode[] {
  if (!nodes) return []
  const result: DependencyNode[] = []
  for (const node of nodes) {
    // 在 groupId 和 artifactId 中搜索
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

// 递归查找 filter 结果树中与 path 匹配的节点
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
      // 路径完全匹配
      if (path.length === 1) return node
      // 递归查找子节点
      if (node.children && node.children.length > 0) {
        const found = findNodeByPath(node.children, path.slice(0, -1))
        if (found) return found
      }
      // 没有子节点或未找到，返回当前
      return node
    }
  }
  return null
}

// 跳转并高亮：严格按 path 逐级递归展开和选中
function gotoAndHighlightNodeByPath(path: any[]) {
  let nodes = dependencyData.value
  let currentNode = null
  // path: 从 root 到 target，正序遍历
  for (let i = path.length - 1; i >= 0; i--) {
    const seg = path[i]
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
  // --- 新增：filter 模式下 selectedNode 指向 filter 结果树中的节点 ---
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
    // 跳转时直接设置搜索框内容，确保搜索高亮和搜索框同步
    if (currentNode) {
      searchAndHighlight(dependencyData.value, currentNode.artifactId)
    }
  }
}

// 递归移除指定GA的依赖及其子依赖
function removeDependencyByGA(nodes: DependencyNode[], groupId: string, artifactId: string): DependencyNode[] {
  return nodes.filter(node => {
    // 如果当前节点匹配要移除的GA，则过滤掉
    if (node.groupId === groupId && node.artifactId === artifactId) {
      return false
    }
    // 递归处理子依赖
    if (node.children && node.children.length > 0) {
      node.children = removeDependencyByGA(node.children, groupId, artifactId)
    }
    return true
  })
}

// 处理exclude成功后的依赖树更新
function handleExcludeSuccess(excludedDependency: { groupId: string, artifactId: string }) {
  // 从当前依赖树中移除指定GA的所有依赖
  dependencyData.value = removeDependencyByGA(dependencyData.value, excludedDependency.groupId, excludedDependency.artifactId)
  
  // 如果当前选中的节点被移除了，清空选中状态
  if (selectedNode.value && 
      selectedNode.value.groupId === excludedDependency.groupId && 
      selectedNode.value.artifactId === excludedDependency.artifactId) {
    selectedNode.value = null
    emit('select-dependency', null, dependencyData.value)
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
          
          // 触发父组件的缓存逻辑
          if (dependencyData.value.length > 0) {
            emit('select-dependency', null, dependencyData.value)
          }
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
      case 'excludeSuccess': {
        // 处理exclude成功的消息
        const { excludedDependency } = message
        if (excludedDependency) {
          handleExcludeSuccess(excludedDependency)
        }
        break
      }
    }
  })
  
  // 修改：检查缓存数据，避免重复加载
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

// 跳转到指定GAV的方法
function jumpToGAV(gav: { groupId: string, artifactId: string, version: string }) {
  
  // 递归查找匹配的节点
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
          // 展开父节点
          node.expanded = true
          return found
        }
      }
    }
    return null
  }
  
  // 查找目标节点
  const targetNode = findGAVNode(dependencyData.value, gav)
  if (targetNode) {
    // 选中目标节点
    selectedNode.value = targetNode
    emit('select-dependency', targetNode, dependencyData.value)
    
    // 高亮搜索结果
    searchAndHighlight(dependencyData.value, gav.artifactId)
  } else {
    console.warn('⚠️ DependencyTree: 未找到GAV:', gav)
  }
}

defineExpose({ refreshDependencies, expandAll, collapseAll, jumpToGAV, gotoAndHighlightNodeByPath })
</script>

<style scoped>
/* 容器样式 - 参考Tailwind卡片式设计 */
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

/* 加载和错误状态样式 */
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

/* 依赖树样式 - 采用现代化卡片式设计 */
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

/* 节点行样式 - 参考Tailwind的卡片设计 */
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

/* 展开/折叠箭头样式 */
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

/* 依赖标签样式 */
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

/* 子节点容器样式 */
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

/* 依赖大小显示样式 */
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

/* GAV信息样式 */
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

/* scope标识样式 */
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

/* 不同scope的颜色区分 */
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

/* 冲突标识样式 */
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