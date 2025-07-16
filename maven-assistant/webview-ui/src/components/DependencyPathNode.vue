<template>
  <template v-if="node">
    <div
      :style="{ paddingLeft: nodeIdx * 28 + 'px' }"
      :class="['dep-path-node', { 'selected': isSelected }]"
      @click="handleNodeClick"
      @contextmenu="handleNodeContextMenu"
    >
      <span
        v-if="!isLeaf"
        class="arrow"
        :class="{ expanded: isExpanded, collapsed: !isExpanded }"
        @click.stop="toggleExpand"
      >
        ▶
      </span>
      <span v-else class="arrow" style="visibility: hidden;">▶</span>
      <!-- 新增：依赖大小展示 -->
      <span v-if="showSize && nodeSizeText" class="dep-size">{{ nodeSizeText }}</span>
      <span :class="['dep-label', node.droppedByConflict ? 'dropped' : '', nodeIdx === 0 ? 'target' : '']">
        {{ nodeLabel }}<span v-if="node.scope"> [{{ node.scope }}]</span>
      </span>
    </div>
    <!-- 递归渲染下一个节点，仅当存在下一个节点且当前节点展开时 -->
    <template v-if="!isLeaf && isExpanded && path[nodeIdx + 1]">
      <DependencyPathNode
        :node="path[nodeIdx + 1] as Record<string, any> | undefined"
        :path="path"
        :pathIdx="pathIdx"
        :nodeIdx="nodeIdx + 1"
        :expandState="expandState"
        :selectedNodeInfo="selectedNodeInfo"
        :selectedDependency="selectedDependency"
        :menuItemsRef="menuItemsRef"
        :vscodeApi="vscodeApi"
        :showGroupId="showGroupId"
        :showSize="showSize"
        @node-click="emitNodeClick"
        @node-contextmenu="emitNodeContextMenu"
      />
    </template>
  </template>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import DependencyPathNode from './DependencyPathNode.vue'
import { calcNodeAndDirectChildrenSize } from '../utils'

const props = defineProps({
  node: { type: Object as () => Record<string, any> | undefined, required: true },
  path: { type: Array, required: true },
  pathIdx: { type: Number, required: true },
  nodeIdx: { type: Number, required: true },
  expandState: { type: Object, required: true },
  selectedNodeInfo: { type: Object, default: () => ({}) },
  selectedDependency: { type: Object, default: null },
  menuItemsRef: { type: Object, required: true },
  vscodeApi: { type: Object, required: true },
  showGroupId: { type: Boolean, default: false },
  showSize: { type: Boolean, default: false } // 新增：控制是否显示依赖大小
})
const emit = defineEmits(['node-click', 'node-contextmenu'])

const isLeaf = computed(() => props.nodeIdx === props.path.length - 1)
const expandKey = computed(() => `${props.pathIdx}-${props.nodeIdx}`)
const isExpanded = computed(() => props.expandState[expandKey.value] !== false)
function toggleExpand() {
  props.expandState[expandKey.value] = !isExpanded.value
}
function isSameGA(node: any, selected: any): boolean {
  if (!node || !selected) return false
  return node.groupId === selected.groupId && node.artifactId === selected.artifactId
}
const isSelected = computed(() => {
  if (!props.selectedNodeInfo || Object.keys(props.selectedNodeInfo).length === 0) return false
  return props.selectedNodeInfo.pathIndex === props.pathIdx && props.selectedNodeInfo.nodeIndex === props.nodeIdx
})
function handleNodeClick() {
  emit('node-click', props.pathIdx, props.nodeIdx, props.node, props.path)
}
function handleNodeContextMenu(e: MouseEvent) {
  console.log('handleNodeContextMenu====>path', JSON.stringify(props.path), 'nodeIdx', props.nodeIdx, 'pathIdx', props.pathIdx)
  emit('node-contextmenu', props.pathIdx, props.nodeIdx, props.node, props.path, e)
}
function emitNodeClick(...args: any[]) {
  emit('node-click', ...args)
}
function emitNodeContextMenu(...args: any[]) {
  emit('node-contextmenu', ...args)
}

// 本节点自身 jar 大小（单位KB，向上取整）
const selfSizeKB = computed(() => props.showSize ? Math.ceil((props.node?.size || 0) / 1024) : 0)

// 修改依赖大小文本，显示“总和 KB (自身 KB)”
const nodeSizeText = computed(() => {
  if (!props.showSize) return ''
  const kb = calcNodeAndDirectChildrenSize(props.node)
  const selfKB = selfSizeKB.value
  return kb > 0 ? `${kb}KB (${selfKB}KB)` : ''
})

// 动态label
const nodeLabel = computed(() => {
  if (!props.node) return ''
  if (isSameGA(props.node, props.selectedDependency)) {
    return props.node.version
  }
  let base = props.showGroupId
    ? `${props.node.groupId}:${props.node.artifactId}:${props.node.version}`
    : `${props.node.artifactId}:${props.node.version}`
  return base
})
</script>

<style scoped>
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
.dep-path-node {
  line-height: 1.8;
  font-size: 14px;
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 3px;
  transition: all 0.2s ease;
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
.dep-size {
  font-size: 11px;
  color: rgba(128,128,128,0.55);
  margin-right: 6px;
  font-family: monospace;
  vertical-align: middle;
  user-select: none;
}
</style> 