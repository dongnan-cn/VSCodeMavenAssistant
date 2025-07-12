<template>
  <div class="dependency-tree-container">
    <!-- 工具栏 -->
    <div class="toolbar">
      <h2 style="flex: 1;">依赖分析结果</h2>
      <button @click="refreshDependencies" class="refresh-btn">刷新</button>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading">
      正在加载依赖分析...
    </div>

    <!-- 错误状态 -->
    <div v-else-if="error" class="error">
      {{ error }}
    </div>

    <!-- 依赖树内容 -->
    <div v-else-if="dependencyData" class="dependency-tree">
      <ul class="dep-tree">
        <li 
          v-for="(node, index) in dependencyData" 
          :key="index"
          :class="{ expanded: node.expanded, collapsed: !node.expanded }"
          :data-key="`node-${index}`"
        >
          <div 
            class="dep-node-row"
            :class="{ selected: selectedNode === node }"
            @click="selectNode(node)"
          >
            <span 
              v-if="node.hasChildren"
              class="arrow"
              :class="{ expanded: node.expanded, collapsed: !node.expanded }"
              @click.stop="toggleNode(node)"
            >
              {{ node.expanded ? '▼' : '▶' }}
            </span>
            <span v-else class="arrow" style="visibility: hidden;">▶</span>
            <span class="dep-label">
              {{ node.label }}
              <span 
                v-if="node.status" 
                :class="node.statusClass"
              >
                [{{ node.status }}]
              </span>
            </span>
          </div>
          <div v-if="node.hasChildren" class="dep-children">
            <ul>
              <li 
                v-for="(child, childIndex) in node.children" 
                :key="childIndex"
                :class="{ expanded: child.expanded, collapsed: !child.expanded }"
                :data-key="`node-${index}-${childIndex}`"
              >
                <div 
                  class="dep-node-row"
                  :class="{ selected: selectedNode === child }"
                  @click="selectNode(child)"
                >
                  <span 
                    v-if="child.hasChildren"
                    class="arrow"
                    :class="{ expanded: child.expanded, collapsed: !child.expanded }"
                    @click.stop="toggleNode(child)"
                  >
                    {{ child.expanded ? '▼' : '▶' }}
                  </span>
                  <span v-else class="arrow" style="visibility: hidden;">▶</span>
                  <span class="dep-label">
                    {{ child.label }}
                    <span 
                      v-if="child.status" 
                      :class="child.statusClass"
                    >
                      [{{ child.status }}]
                    </span>
                  </span>
                </div>
                <div v-if="child.hasChildren" class="dep-children">
                  <ul>
                    <li 
                      v-for="(grandChild, grandChildIndex) in child.children" 
                      :key="grandChildIndex"
                      :class="{ expanded: grandChild.expanded, collapsed: !grandChild.expanded }"
                      :data-key="`node-${index}-${childIndex}-${grandChildIndex}`"
                    >
                      <div 
                        class="dep-node-row"
                        :class="{ selected: selectedNode === grandChild }"
                        @click="selectNode(grandChild)"
                      >
                        <span 
                          v-if="grandChild.hasChildren"
                          class="arrow"
                          :class="{ expanded: grandChild.expanded, collapsed: !grandChild.expanded }"
                          @click.stop="toggleNode(grandChild)"
                        >
                          {{ grandChild.expanded ? '▼' : '▶' }}
                        </span>
                        <span v-else class="arrow" style="visibility: hidden;">▶</span>
                        <span class="dep-label">
                          {{ grandChild.label }}
                          <span 
                            v-if="grandChild.status" 
                            :class="grandChild.statusClass"
                          >
                            [{{ grandChild.status }}]
                          </span>
                        </span>
                      </div>
                      <div v-if="grandChild.hasChildren" class="dep-children">
                        <ul>
                          <li 
                            v-for="(greatGrandChild, greatGrandChildIndex) in grandChild.children" 
                            :key="greatGrandChildIndex"
                            class="dep-node-row"
                            :class="{ selected: selectedNode === greatGrandChild }"
                            @click="selectNode(greatGrandChild)"
                          >
                            <span class="arrow" style="visibility: hidden;">▶</span>
                            <span class="dep-label">
                              {{ greatGrandChild.label }}
                              <span 
                                v-if="greatGrandChild.status" 
                                :class="greatGrandChild.statusClass"
                              >
                                [{{ greatGrandChild.status }}]
                              </span>
                            </span>
                          </li>
                        </ul>
                      </div>
                    </li>
                  </ul>
                </div>
              </li>
            </ul>
          </div>
        </li>
      </ul>
    </div>

    <!-- 空状态 -->
    <div v-else class="empty-state">
      暂无依赖数据
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'

// 声明VSCode API
declare function acquireVsCodeApi(): any

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
}

// 响应式数据
const loading = ref(true)
const error = ref('')
const dependencyData = ref<DependencyNode[]>([])
const selectedNode = ref<DependencyNode | null>(null)

// 获取VSCode API
const vscode = acquireVsCodeApi()

// 刷新依赖数据
const refreshDependencies = () => {
  loading.value = true
  error.value = ''
  vscode.postMessage({ type: 'refresh' })
}

// 选择节点
const selectNode = (node: DependencyNode) => {
  selectedNode.value = node
  // 可以在这里发送消息给扩展端，显示依赖详情等
  vscode.postMessage({ 
    type: 'selectNode', 
    node: {
      groupId: node.groupId,
      artifactId: node.artifactId,
      version: node.version,
      scope: node.scope
    }
  })
}

// 切换节点展开/收起状态
const toggleNode = (node: DependencyNode) => {
  if (node.hasChildren) {
    node.expanded = !node.expanded
  }
}

// 处理依赖数据
const processDependencyData = (data: any): DependencyNode[] => {
  if (!data || !Array.isArray(data)) {
    return []
  }

  return data.map((node: any) => {
    const hasChildren = node.children && node.children.length > 0
    const status = node.droppedByConflict ? 'DROPPED' : 'USED'
    const statusClass = node.droppedByConflict ? 'dropped' : 'used'
    
    return {
      ...node,
      label: `${node.groupId}:${node.artifactId}:${node.version}${node.scope ? ` [${node.scope}]` : ''}`,
      status,
      statusClass,
      hasChildren,
      expanded: true, // 默认展开
      children: hasChildren ? processDependencyData(node.children) : undefined
    }
  })
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
    }
  })

  // 初始化时请求数据
  refreshDependencies()
})
</script>

<style scoped>
.dependency-tree-container {
  font-family: var(--vscode-font-family);
  color: var(--vscode-foreground);
  background: var(--vscode-editor-background);
  margin: 0;
  padding: 20px;
  height: 100vh;
  overflow-y: auto;
}

.toolbar {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--vscode-panel-border);
}

.toolbar h2 {
  margin: 0;
  color: var(--vscode-editor-foreground);
}

.refresh-btn {
  background-color: var(--vscode-button-background);
  color: var(--vscode-button-foreground);
  border: 1px solid var(--vscode-button-border);
  padding: 5px 10px;
  border-radius: 3px;
  cursor: pointer;
  font-size: 12px;
}

.refresh-btn:hover {
  background-color: var(--vscode-button-hoverBackground);
}

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
  width: 1em;
  cursor: pointer;
  user-select: none;
  font-size: 12px;
  margin-right: 2px;
  transition: transform 0.2s;
}

.arrow.collapsed {
  transform: rotate(0deg);
}

.arrow.expanded {
  transform: rotate(90deg);
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
</style> 