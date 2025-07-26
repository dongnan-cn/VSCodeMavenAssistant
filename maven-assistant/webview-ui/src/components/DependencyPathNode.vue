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
      <!-- 依赖大小展示 -->
      <span v-if="showSize && nodeSizeText" class="dep-size">{{ nodeSizeText }}</span>
      
      <!-- 依赖标签 - 采用与左侧相同的GAV信息布局，应用动态颜色 -->
      <div :class="['dep-label', node.droppedByConflict ? 'dropped' : '', nodeIdx === 0 ? 'target' : '']" :style="{ color: nodeColor }">
        <div class="gav-info">
          <!-- 根据是否为目标依赖显示不同的GAV格式 -->
          <template v-if="isSameGA(node, selectedDependency)">
            <span class="version">{{ node.version }}</span>
          </template>
          <template v-else>
            <!-- 显示完整的GAV信息，artifactId加粗 -->
            <template v-if="showGroupId">
              <span class="group-id">{{ node.groupId }}</span>
              <span class="separator">:</span>
              <span class="artifact-id">{{ node.artifactId }}</span>
              <span class="separator">:</span>
              <span class="version">{{ node.version }}</span>
            </template>
            <template v-else>
              <span class="artifact-id">{{ node.artifactId }}</span>
              <span class="separator">:</span>
              <span class="version">{{ node.version }}</span>
            </template>
          </template>
          <!-- scope徽章 - 与左侧样式保持一致 -->
          <span v-if="node.scope" :class="['scope-badge', node.scope]">
            {{ node.scope }}
          </span>
        </div>
      </div>
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
  // 先选中当前节点
  handleNodeClick()
  
  // 再触发右键菜单
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

// 根据 scope 和 droppedByConflict 状态确定节点颜色
const nodeColor = computed(() => {
  const scope = props.node?.scope
  const droppedByConflict = props.node?.droppedByConflict
  
  // 被冲突丢弃时显示红色
  if (droppedByConflict) {
    return '#F44336' // 红色
  }
  // scope 为 test 时显示绿色
  if (scope === 'test') {
    return '#4CAF50' // 绿色
  }
  // scope 为 runtime 时显示紫色
  if (scope === 'runtime') {
    return '#9C27B0' // 紫色
  }
  // scope 为 compile 时显示蓝色
  if (scope === 'compile') {
    return '#2196F3' // 蓝色
  }
  // 其他情况显示白色
  return '#FFFFFF'
})

</script>

<style scoped>
/* 展开/折叠箭头样式 - 与左侧依赖树保持一致 */
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

/* 节点行样式 - 采用卡片式设计，与左侧依赖树相同 */
.dep-path-node {
  display: flex;
  align-items: center;
  padding: 4px 12px;
  margin: 1px 0;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid transparent;
  background: var(--vscode-editor-background);
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 14px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  width: 100%;
  min-width: 600px;
  white-space: nowrap;
  overflow: hidden;
}

.dep-path-node:hover {
  background: var(--vscode-list-hoverBackground);
  border-color: var(--vscode-list-hoverBackground);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15);
  transform: translateY(-1px);
}

.dep-path-node.selected {
  background: var(--vscode-list-activeSelectionBackground);
  color: var(--vscode-list-activeSelectionForeground);
  border-color: var(--vscode-focusBorder);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  transform: translateY(-1px);
  z-index: 2;
  font-weight: 600;
}

/* 依赖标签样式 - 保持原有颜色逻辑 */
.dep-label {
  flex: 1;
  cursor: pointer;
  user-select: none;
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
}

.dep-label.target {
  color: var(--vscode-editor-foreground);
  font-weight: bold;
}

.dep-label.dropped {
  color: var(--vscode-errorForeground);
  font-weight: bold;
}

/* 依赖大小显示样式 - 与左侧依赖树相同 */
.dep-size {
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
  font-family: monospace;
  vertical-align: middle;
  user-select: text;
  flex-shrink: 0;
  white-space: nowrap;
}

/* GAV信息样式 - 固定宽度布局，确保scope徽章位置稳定 */
.gav-info {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
  min-width: 600px;
  max-width: 600px;
  overflow: hidden;
  white-space: nowrap;
  position: relative;
}

/* GAV 各部分样式 - 颜色由父元素的 nodeColor 动态设置 */
.group-id {
  /* 继承父元素的动态颜色 */
  opacity: 0.9;
}

.artifact-id {
  /* 继承父元素的动态颜色，加粗显示 */
  font-weight: 600;
}

.version {
  /* 继承父元素的动态颜色 */
  font-weight: 500;
}

.separator {
  color: var(--vscode-descriptionForeground);
  opacity: 0.6;
}

/* scope徽章样式 - 固定在GAV文本之后的位置 */
.scope-badge {
  display: inline-flex;
  align-items: center;
  background: var(--vscode-badge-background);
  color: var(--vscode-badge-foreground);
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 10px;
  font-weight: 600;
  margin-left: 4px;
  text-transform: uppercase;
  flex-shrink: 0;
  white-space: nowrap;
  position: relative;
}

/* scope徽章颜色 - 与左侧依赖树相同 */
.scope-badge.test {
  background: #4CAF50;
  color: white;
}

.scope-badge.runtime {
  background: #9C27B0;
  color: white;
}

.scope-badge.compile {
  background: #2196F3;
  color: white;
}
</style>