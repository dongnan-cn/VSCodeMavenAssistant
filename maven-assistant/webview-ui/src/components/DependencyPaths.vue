<template>
  <div class="dependency-paths-container">
    <div v-if="!selectedDependency">
      <div class="placeholder">请选择左侧依赖节点，右侧将显示所有来源路径</div>
    </div>
    <div v-else>
      <div class="title">所有来源路径（共 {{ paths.length }} 条）：</div>

      <div v-if="paths.length === 0" class="no-paths">未找到任何路径</div>
      <div v-for="(path, idx) in paths" :key="idx" class="path-block">
        <div v-for="(node, i) in path" :key="i" :style="{ paddingLeft: i * 28 + 'px' }" class="dep-path-node">
          <span :class="['dep-label', node.droppedByConflict ? 'dropped' : '', i === 0 ? 'target' : '']">
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

watch(
  () => [props.dependencyTree, props.selectedDependency],
  ([tree, dep]) => {
    if (tree && dep) {
      paths.value = findAllPaths(tree as any[], dep)
    } else {
      paths.value = []
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
}
.title {
  font-weight: bold;
  margin-bottom: 12px;
  font-size: 16px;
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