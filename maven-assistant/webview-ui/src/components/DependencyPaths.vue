<template>
  <div class="dependency-paths-container">
    <div v-if="!selectedDependency">
      <div class="placeholder">请选择左侧依赖节点，右侧将显示所有来源路径</div>
    </div>
    <div v-else>
      <div class="title">所有来源路径（共 {{ paths.length }} 条）：</div>

      <div v-if="paths.length === 0" class="no-paths">未找到任何路径</div>
      <div v-for="(path, pathIdx) in paths" :key="pathIdx" class="path-block">
        <div 
          v-for="(node, nodeIdx) in path" 
          :key="nodeIdx" 
          :style="{ paddingLeft: nodeIdx * 28 + 'px' }" 
          :class="['dep-path-node', { 'selected': isNodeSelected(pathIdx, nodeIdx) }]"
          @click="handleNodeClick(pathIdx, nodeIdx, node, path)"
        >
          <span :class="['dep-label', node.droppedByConflict ? 'dropped' : '', nodeIdx === 0 ? 'target' : '']">
            {{ node.groupId }}:{{ node.artifactId }}:{{ node.version }} <span v-if="node.scope">[{{ node.scope }}]</span>
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { watch, ref } from 'vue'

const props = defineProps({
  dependencyTree: { type: Array, default: () => [] },
  selectedDependency: { type: Object, default: null }
})

// 定义选中状态的数据结构
interface SelectedNodeInfo {
  pathIndex: number
  nodeIndex: number
  dependency: any // 当前选中的依赖节点
  topLevelNode: any // 所在依赖树的最高节点（非当前项目）
}

// 当前选中的节点信息
const selectedNodeInfo = ref<SelectedNodeInfo | null>(null)

// 路径查找算法：递归遍历所有路径，收集所有到目标依赖（同groupId+artifactId）的路径
function findAllPaths(tree: any[], target: any): any[][] {
  const result: any[][] = []
  function dfs(node: any, path: any[]) {
    if (!node) return
    // 判断是否匹配目标依赖（只比对groupId+artifactId）
    if (node.groupId === target.groupId && node.artifactId === target.artifactId) {
      result.push([node, ...path]) // 子节点在上，父节点在下
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

// 判断节点是否被选中
function isNodeSelected(pathIndex: number, nodeIndex: number): boolean {
  if (!selectedNodeInfo.value) return false
  return selectedNodeInfo.value.pathIndex === pathIndex && selectedNodeInfo.value.nodeIndex === nodeIndex
}

// 处理节点点击事件
function handleNodeClick(pathIndex: number, nodeIndex: number, node: any, path: any[]) {
  // 获取所在依赖树的最高节点（非当前项目）
  // 在path数组中，最后一个元素就是最高节点
  const topLevelNode = path[path.length - 1]
  
  // 保存选中的依赖信息
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
  
  console.log('选中依赖信息：', selectedNodeInfo.value)
  
  // 这里可以emit事件通知父组件，或者后续添加更多逻辑
  // emit('nodeSelected', selectedNodeInfo.value)
}

watch(
  () => [props.dependencyTree, props.selectedDependency],
  ([tree, dep]) => {
    if (tree && dep) {
      paths.value = findAllPaths(tree as any[], dep)
      // 当依赖树或选中依赖改变时，清空右侧选中状态
      selectedNodeInfo.value = null
    } else {
      paths.value = []
      selectedNodeInfo.value = null
    }
  },
  { immediate: true }
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
  text-align: left; /* 内容整体靠左 */
}
.title {
  font-weight: bold;
  margin-bottom: 12px;
  font-size: 16px;
  text-align: center; /* 只让标题居中 */
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
  margin-bottom: 18px;
  border-left: 2px solid var(--vscode-panel-border);
  padding-left: 8px;
}
.dep-path-node {
  line-height: 1.8;
  font-size: 14px;
  cursor: pointer; /* 添加鼠标指针样式 */
  padding: 2px 4px; /* 添加内边距，增加点击区域 */
  border-radius: 3px; /* 圆角边框 */
  transition: all 0.2s ease; /* 添加过渡动画 */
}
.dep-path-node:hover {
  background-color: var(--vscode-list-hoverBackground);
}
.dep-path-node.selected {
  background-color: var(--vscode-list-activeSelectionBackground);
  color: var(--vscode-list-activeSelectionForeground);
  font-weight: bold;
  border: 1px solid var(--vscode-focusBorder);
}
.dep-label.target {
  color: var(--vscode-editor-foreground);
  font-weight: bold;
}
.dep-label.dropped {
  color: var(--vscode-errorForeground);
  font-weight: bold;
}
</style> 