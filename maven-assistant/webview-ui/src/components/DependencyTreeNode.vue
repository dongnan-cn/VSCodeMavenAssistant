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
      <span class="dep-label" :class="{ matched: node.matched, selected: isSelected }">
        <span class="gav-info" :style="{ color: nodeColor }">
          <!-- 只有在 showGroupId 为 true 时才显示 groupId -->
          <template v-if="showGroupId">
            <span class="group-id">{{ node.groupId }}</span>
            <span class="separator"> : </span>
          </template>
          <span class="artifact-id">{{ node.artifactId }}</span>
          <span class="separator"> : </span>
          <span class="version">{{ node.version }}</span>
          <!-- scope-badge 移动到 GAV 信息内部，确保紧跟依赖信息 -->
          <span v-if="node.scope" 
                class="scope-badge" 
                :class="node.scope">
            {{ node.scope }}
          </span>
        </span>
        <!-- 移除冲突丢弃文字提示，红色已经足够说明问题 -->
      </span>
    </div>
    <div v-if="node.hasChildren && node.expanded" class="dep-children">
      <ul style="list-style: none;">
        <DependencyTreeNode
          v-for="(child, idx) in node.children"
          :key="idx"
          :node="child"
          :path="[node, ...path]"
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

// 移除了未使用的 nodeLabel 计算属性，因为现在直接在模板中显示 GAV 信息

// 根据 scope 和 droppedByConflict 状态确定节点颜色
const nodeColor = computed(() => {
  const scope = props.node.scope
  const droppedByConflict = props.node.droppedByConflict
  
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
  
  // 先选中当前节点
  selectNode()
  
  // 再显示右键菜单
  menuVisible.value = true
  menuX.value = event.clientX
  menuY.value = event.clientY
  menuNode.value = props.node
  menuItemsRef.value = getMenuItems(props.node)
}

function handleMenuSelect(action: string) {
  if (!menuNode.value) return
  const node = menuNode.value
  const pathInfo = [node, ...props.path].map(n => ({
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
/* 展开/折叠箭头样式 - 与父组件保持一致 */
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

/* 列表样式重置 */
ul, li {
  list-style: none;
  padding-left: 0;
  margin: 0;
}

/* 节点行样式 - 采用卡片式设计，更紧凑的布局，固定宽度 */
.dep-node-row {
  display: flex;
  align-items: center;
  padding: 4px 12px; /* 减小垂直padding */
  margin: 1px 0; /* 减小垂直margin */
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid transparent;
  background: var(--vscode-editor-background);
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 14px; /* 增大字体 */
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  width: 100%; /* 固定宽度为容器100% */
  min-width: 600px; /* 设置最小宽度确保内容不被压缩 */
  white-space: nowrap; /* 防止换行 */
  overflow: hidden; /* 隐藏溢出内容 */
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
  z-index: 2;
}

.dep-node-row.matched {
  background: var(--vscode-editor-findMatchHighlightBackground, #ffe564);
  color: var(--vscode-editor-findMatchHighlightForeground, #000);
  border-color: #fbbf24;
  box-shadow: 0 2px 8px rgba(251, 191, 36, 0.3);
  z-index: 1;
}

/* 依赖标签样式 - 固定布局不换行 */
.dep-label {
  flex: 1;
  cursor: pointer;
  user-select: none;
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
  min-width: 0; /* 允许收缩 */
  overflow: hidden; /* 隐藏溢出 */
  white-space: nowrap; /* 防止换行 */
}

.dep-label.selected {
  font-weight: 600;
  position: relative;
  z-index: 3;
}

.dep-label.matched {
  background: var(--vscode-editor-findMatchHighlightBackground, #ffe564);
  color: var(--vscode-editor-findMatchHighlightForeground, #000);
  border-radius: 4px;
  padding: 0 4px;
  z-index: 2;
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

:deep(.dep-children > ul) {
  padding-left: 24px;
  margin-top: 2px; /* 减小子节点顶部间距 */
}

/* 依赖大小显示样式 - 固定位置不换行 */
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
  flex-shrink: 0; /* 防止收缩 */
  white-space: nowrap; /* 防止换行 */
}

/* GAV信息样式 - 固定在一行显示 */
.gav-info {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1; /* 占据剩余空间 */
  min-width: 0; /* 允许收缩 */
  overflow: hidden; /* 隐藏溢出 */
  text-overflow: ellipsis; /* 文本溢出显示省略号 */
  white-space: nowrap; /* 防止换行 */
}

/* GAV 各部分样式 - 颜色由父元素的 nodeColor 动态设置 */
.group-id {
  opacity: 0.9;
}

.artifact-id {
  font-weight: 600;
}

.version {
  font-weight: 500;
}

.separator {
  color: var(--vscode-descriptionForeground);
  opacity: 0.6;
}

/* scope标识样式 - 紧跟依赖信息，固定位置不换行 */
.scope-badge {
  display: inline-flex;
  align-items: center;
  background: var(--vscode-badge-background);
  color: var(--vscode-badge-foreground);
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 10px;
  font-weight: 600;
  margin-left: 4px; /* 减小左边距，紧跟GAV信息 */
  text-transform: uppercase;
  flex-shrink: 0; /* 防止收缩 */
  white-space: nowrap; /* 防止换行 */
  position: relative; /* 相对定位，确保固定跟随 */
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
  background: #2196F3;
  color: white;
}

/* 移除了冲突标识样式，因为不再显示冲突丢弃文字 */
</style>