<template>
  <li :class="{ expanded: node.expanded, collapsed: !node.expanded }" :data-key="dataKey" style="list-style: none;">
    <div 
      class="dep-node-row"
      :class="{ selected: isSelected, matched: node.matched }"
      @click="selectNode"
      @contextmenu="handleContextMenu"
    >
      <span 
        v-if="node.hasChildren"
        class="arrow"
        :class="{ expanded: node.expanded, collapsed: !node.expanded }"
        @click.stop="toggleNode"
      >
        ▶
      </span>
      <span v-else class="arrow" style="visibility: hidden;">▶</span>
      <span v-if="showSize && totalSizeKB > 0" class="dep-size">{{ totalSizeKB }} KB ({{ selfSizeKB }} KB)</span>
      <span class="dep-label" :class="{ matched: node.matched, selected: isSelected }" :style="{ color: nodeColor }">
        {{ nodeLabel }}
      </span>
    </div>
    <div v-if="node.hasChildren && node.expanded" class="dep-children">
      <ul style="list-style: none;">
        <DependencyTreeNode
          v-for="(child, idx) in node.children"
          :key="idx"
          :node="child"
          :path="[...path, node]"
          :dataKey="`${dataKey}-${idx}`"
          :selectedNode="selectedNode"
          :showGroupId="showGroupId"
          :showSize="showSize"
          :vscodeApi="vscodeApi"
          @select="emitSelect"
        />
      </ul>
    </div>
    <ContextMenu
      :visible="menuVisible"
      :x="menuX"
      :y="menuY"
      :items="menuItemsRef"
      @select="handleMenuSelect"
      @close="menuVisible = false"
    />
  </li>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { calcNodeAndDirectChildrenSize } from '../utils'
import ContextMenu from './ContextMenu.vue'
const props = defineProps({
  node: { type: Object, required: true },
  path: { type: Array as () => any[], default: () => [] },
  dataKey: { type: String, default: '' },
  selectedNode: { type: Object, default: null },
  showGroupId: { type: Boolean, default: false },
  showSize: { type: Boolean, default: false },
  vscodeApi: { type: Object, required: true }
})
const emit = defineEmits(['select'])

// 判断是否选中（用唯一标识而不是对象引用）
const isSelected = computed(() => {
  if (!props.selectedNode) return false
  // 以 groupId, artifactId, version, scope, path 唯一标识节点
  const n = props.node
  const s = props.selectedNode
  // 路径可选，如果有 path 字段则也比较
  const pathEqual = !n.path || !s.path || JSON.stringify(n.path) === JSON.stringify(s.path)
  return (
    n.groupId === s.groupId &&
    n.artifactId === s.artifactId &&
    n.version === s.version &&
    (n.scope || '') === (s.scope || '') &&
    pathEqual
  )
})

function selectNode() {
  emit('select', props.node.artifactId, props.node)
}
function toggleNode() {
  props.node.expanded = !props.node.expanded
}
function emitSelect(id: string, node: any) {
  emit('select', id, node)
}

// 动态label
const nodeLabel = computed(() => {
  let base = props.showGroupId
    ? `${props.node.groupId} : ${props.node.artifactId} : ${props.node.version}`
    : `${props.node.artifactId} : ${props.node.version}`
  if (props.node.scope) base += ` [${props.node.scope}]`
  return base
})

// 根据 scope 和 droppedByConflict 状态确定节点颜色
const nodeColor = computed(() => {
  const scope = props.node.scope
  const droppedByConflict = props.node.droppedByConflict
  
  // scope 为 test 时显示绿色
  if (scope === 'test') {
    return '#4CAF50' // 绿色
  }
  // scope 为 runtime 时显示紫色
  if (scope === 'runtime') {
    return '#9C27B0' // 紫色
  }
  // scope 为 compile 且被冲突丢弃时显示红色
  if (scope === 'compile' && droppedByConflict) {
    return '#F44336' // 红色
  }
  // 其他情况显示默认颜色（白色/前景色）
  return 'var(--vscode-foreground)'
})

// 依赖大小（单位KB，向上取整）
const totalSizeKB = computed(() => props.showSize ? calcNodeAndDirectChildrenSize(props.node) : 0)
// 本节点自身 jar 大小（单位KB，向上取整）
const selfSizeKB = computed(() => props.showSize ? Math.ceil((props.node.size || 0) / 1024) : 0)

// 右键菜单相关响应式状态
const menuVisible = ref(false)
const menuX = ref(0)
const menuY = ref(0)
const menuNode = ref<any>(null)
const menuItemsRef = ref<{ label: string, value: string }[]>([])

// 生成菜单项（不含“跳转到左侧树”）
function getMenuItems(_node: any) {
  // 判断是否为顶级依赖（即当前 pom.xml 直接依赖）
  const isTopLevel = props.path.length === 0
  if (isTopLevel) {
    return [
      { label: 'Jump to pom.xml', value: 'goto-pom' }
    ]
  } else {
    return [
      { label: 'Jump to pom.xml', value: 'goto-pom' },
      { label: 'Exclude', value: 'exclude' }
    ]
  }
}

function handleContextMenu(event: MouseEvent) {
  event.preventDefault()
  menuVisible.value = true
  menuX.value = event.clientX
  menuY.value = event.clientY
  menuNode.value = props.node
  menuItemsRef.value = getMenuItems(props.node)
}

function handleMenuSelect(action: string) {
  if (!menuNode.value) return
  const node = menuNode.value
  console.log('node ', node, ' props.path: ', props.path)
  const pathInfo = [...props.path, node].map(n => ({
    groupId: n.groupId,
    artifactId: n.artifactId,
    version: n.version,
    scope: n.scope
  }))
  props.vscodeApi.postMessage({
    type: 'showContextMenu',
    data: {
      node: {
        groupId: node.groupId,
        artifactId: node.artifactId,
        version: node.version,
        scope: node.scope
      },
      action,
      pathInfo,
      nodeIndex: 0
    }
  })
  menuVisible.value = false
}
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
ul, li {
  list-style: none;
  padding-left: 0;
  margin: 0;
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
  /* 跳转高亮，宽度略窄，仅label区域 */
  background: linear-gradient(to right, var(--vscode-list-activeSelectionBackground) 80%, transparent 100%);
  color: var(--vscode-list-activeSelectionForeground);
  border-left: 3px solid var(--vscode-focusBorder);
  z-index: 2;
}
.dep-node-row.matched {
  /* 搜索高亮，覆盖整行 */
  background: var(--vscode-editor-findMatchHighlightBackground, #ffe564);
  color: var(--vscode-editor-findMatchHighlightForeground, #000);
  border-radius: 2px;
  z-index: 1;
}
.dep-label {
  flex: 1;
  cursor: pointer;
  user-select: none;
}
.dep-label.selected {
  /* 跳转高亮，label区域再加一层边框或阴影 */
  box-shadow: 0 0 0 2px var(--vscode-focusBorder) inset;
  border-radius: 2px;
  position: relative;
  z-index: 3;
}
.dep-label.matched {
  /* 搜索高亮，label区域也有背景色 */
  background: var(--vscode-editor-findMatchHighlightBackground, #ffe564);
  color: var(--vscode-editor-findMatchHighlightForeground, #000);
  border-radius: 2px;
  padding: 0 2px;
  z-index: 2;
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
:deep(.dep-children > ul) {
  padding-left: 20px;
}
.dep-size {
  color: rgba(128,128,128,0.45);
  font-size: 12px;
  margin-right: 6px;
  min-width: 48px;
  display: inline-block;
  text-align: left;
  font-family: monospace;
  vertical-align: middle;
  user-select: text;
}
</style>