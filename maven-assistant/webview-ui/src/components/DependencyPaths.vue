<template>
  <div class="dependency-paths-container">
    
    <div v-if="!props.selectedDependency">
      <div class="placeholder">请选择左侧依赖节点，右侧将显示所有来源路径</div>
    </div>
    <div v-else>
      <div class="title">所有来源路径（共 {{ paths.length }} 条）：</div>

      <div v-if="paths.length === 0" class="no-paths">未找到任何路径</div>
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
    <!-- 右键菜单组件 -->
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
  showSize: { type: Boolean, default: false } // 新增：控制依赖大小显示
})

// 展开状态：记录每条路径每个节点的展开状态
const expandState = ref<{ [key: string]: boolean }>({})

// 右键菜单状态
const menuVisible = ref(false)
const menuX = ref(0)
const menuY = ref(0)
const menuNode = ref<any>(null)
const menuPathIndex = ref<number>(-1)
const menuNodeIndex = ref<number>(-1)

// 右键菜单项（注意：不再是常量，需根据节点动态生成）
function getMenuItems(node: any, path: any[]): { label: string, value: string }[] {
  // 判断是否为一级依赖（即 path 的最后一个节点）
  const isTopLevel = path && path.length > 0 && node === path[path.length - 1]
  // 如果是一级依赖，则不显示“排除此依赖”
  if (isTopLevel) {
    return [
      { label: '跳转到 pom.xml', value: 'goto-pom' }
    ]
  } else {
    return [
      { label: '跳转到 pom.xml', value: 'goto-pom' },
      { label: '排除此依赖', value: 'exclude' }
    ]
  }
}

// 右键事件处理
function handleNodeContextMenu(pathIndex: number, nodeIndex: number, node: any, path: any[], event: MouseEvent) {
  event.preventDefault()
  menuVisible.value = true
  menuX.value = event.clientX
  menuY.value = event.clientY
  menuNode.value = { node, path }
  menuPathIndex.value = pathIndex
  menuNodeIndex.value = nodeIndex
  // 动态设置菜单项
  menuItemsRef.value = getMenuItems(node, path)
}

// 菜单项选择
function handleMenuSelect(action: string) {
  if (!menuNode.value) return
  const { node, path } = menuNode.value
  // 通过vscodeApi发送消息
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
      action // 你可以直接传递action到后端
    }
  })
}

// 右键菜单项响应式变量（替换原 menuItems 常量）
const menuItemsRef = ref(getMenuItems(null, []))


// 当前选中的节点信息
const selectedNodeInfo = ref<Record<string, any>>({})

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
  
  // 这里可以emit事件通知父组件，或者后续添加更多逻辑
  // emit('nodeSelected', selectedNodeInfo.value)
}


watch(
  () => [props.dependencyTree, props.selectedDependency],
  ([tree, dep]) => {
    if (tree && dep) {
      paths.value = findAllPaths(tree as any[], dep)
      // 当依赖树或选中依赖改变时，清空右侧选中状态
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
.arrow {
  display: inline-block;
  width: 1.2em;
  font-size: 1.1em;
  color: var(--vscode-foreground);
  margin-right: 6px;
  vertical-align: middle;
  transition: transform 0.2s, color 0.2s;
  cursor: pointer;
  user-select: none;
}
.arrow.collapsed {
  transform: rotate(0deg);
}
.arrow.expanded {
  transform: rotate(90deg);
}
.arrow:hover {
  color: var(--vscode-list-hoverForeground, var(--vscode-foreground));
}
</style> 