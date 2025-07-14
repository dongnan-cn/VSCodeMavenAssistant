<template>
  <li :class="{ expanded: node.expanded, collapsed: !node.expanded }" :data-key="dataKey" style="list-style: none;">
    <div 
      class="dep-node-row"
      :class="{ selected: isSelected }"
      @click="selectNode"
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
      <span class="dep-label">
        {{ node.label }}
        <span v-if="node.status" :class="node.statusClass">
          [{{ node.status }}]
        </span>
      </span>
    </div>
    <div v-if="node.hasChildren && node.expanded" class="dep-children">
      <ul style="list-style: none;">
        <DependencyTreeNode
          v-for="(child, idx) in node.children"
          :key="idx"
          :node="child"
          :dataKey="`${dataKey}-${idx}`"
          :selectedNodeId="selectedNodeId"
          @select="emitSelect"
        />
      </ul>
    </div>
  </li>
</template>

<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps({
  node: { type: Object, required: true },
  dataKey: { type: String, default: '' },
  selectedNodeId: { type: String, default: '' }
})
const emit = defineEmits(['select'])

// 判断是否选中（用唯一id artifactId:version:scope）
const nodeId = computed(() => `${props.node.artifactId}:${props.node.version}${props.node.scope ? ':' + props.node.scope : ''}`)
const isSelected = computed(() => nodeId.value === props.selectedNodeId)

function selectNode() {
  emit('select', nodeId.value, props.node)
}
function toggleNode() {
  props.node.expanded = !props.node.expanded
}
function emitSelect(id: string, node: any) {
  emit('select', id, node)
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
  background: var(--vscode-list-activeSelectionBackground);
  color: var(--vscode-list-activeSelectionForeground);
}
.dep-node-row:hover {
  background: var(--vscode-list-hoverBackground);
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
:deep(.dep-children > ul) {
  padding-left: 20px;
}
</style> 