<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import DependencyTree from './components/DependencyTree.vue'
import DependencyPaths from './components/DependencyPaths.vue'

// 声明VSCode API
declare function acquireVsCodeApi(): any

const leftWidth = ref(320)
let dragging = false

const selectedDependency = ref<any>(null)
const dependencyTreeData = ref<any>(null) // 依赖树原始数据

const searchText = ref('')
const dependencyTreeRef = ref()

const showGroupId = ref(false)

function refreshDependencies() {
  dependencyTreeRef.value?.refreshDependencies?.()
}
function expandAll() {
  dependencyTreeRef.value?.expandAll?.()
}
function collapseAll() {
  dependencyTreeRef.value?.collapseAll?.()
}

// 获取VSCode API实例
const vscodeApi = acquireVsCodeApi()

const onSelectDependency = (dep: any, treeData: any) => {
  selectedDependency.value = dep
  dependencyTreeData.value = treeData
}

const startDrag = () => {
  dragging = true
  document.body.style.cursor = 'col-resize'
}

const onDrag = (e: MouseEvent) => {
  if (dragging) {
    leftWidth.value = Math.max(180, Math.min(e.clientX, window.innerWidth - 180))
  }
}

const stopDrag = () => {
  dragging = false
  document.body.style.cursor = ''
}

onMounted(() => {
  window.addEventListener('mousemove', onDrag)
  window.addEventListener('mouseup', stopDrag)
})
onBeforeUnmount(() => {
  window.removeEventListener('mousemove', onDrag)
  window.removeEventListener('mouseup', stopDrag)
})
</script>

<template>
  <div>
    <div class="toolbar">
      <div class="toolbar-left">
        <input v-model="searchText" placeholder="Search artifact..." class="search-input" />
        <button @click="refreshDependencies" class="refresh-btn">Refresh</button>
        <button @click="expandAll" class="refresh-btn">Expand All</button>
        <button @click="collapseAll" class="refresh-btn">Collapse All</button>
        <label class="show-groupid-label">
          <input type="checkbox" v-model="showGroupId" /> Show GroupId
        </label>
      </div>
    </div>
    <div class="split-pane">
      <div class="left-pane" :style="{ width: leftWidth + 'px' }">
        <DependencyTree 
          @select-dependency="onSelectDependency" 
          :vscodeApi="vscodeApi"
          :searchText="searchText"
          :showGroupId="showGroupId"
          ref="dependencyTreeRef"
        />
      </div>
      <div class="splitter" @mousedown="startDrag"></div>
      <div class="right-pane">
        <DependencyPaths
          :dependencyTree="dependencyTreeData"
          :selectedDependency="selectedDependency"
          :vscodeApi="vscodeApi"
          :showGroupId="showGroupId"
        />
      </div>
    </div>
  </div>
</template>

<style>
html, body, #app, .split-pane, .left-pane, .right-pane {
  margin: 0 !important;
  padding: 0 !important;
  box-sizing: border-box;
  height: 100vh;
  width: 100vw;
}
.split-pane {
  display: flex;
  height: 100vh;
  width: 100vw;
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}
.left-pane {
  background: var(--vscode-sideBar-background);
  min-width: 180px;
  max-width: 80vw;
  height: 100vh;
  width: 100%;
  box-sizing: border-box;
  padding: 0;
  margin: 0;
  transition: width 0.1s;
}
.splitter {
  width: 5px;
  cursor: col-resize;
  background: var(--vscode-panel-border);
  transition: background 0.2s;
  z-index: 10;
}
.splitter:hover {
  background: var(--vscode-panelTitle-activeBorder);
}
.right-pane {
  flex: 1;
  background: var(--vscode-editor-background);
  height: 100vh;
  width: 100%;
  box-sizing: border-box;
  padding: 0;
  margin: 0;
}
.toolbar {
  width: 100%;
  box-sizing: border-box;
  padding: 0 24px 10px 24px;
  margin: 0;
  border-bottom: 1px solid var(--vscode-panel-border);
  background: var(--vscode-editor-background);
  display: flex;
  align-items: center;
  z-index: 2;
}
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
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
.search-input {
  width: 180px;
  margin-right: 0;
  padding: 4px 8px;
  border: 1px solid var(--vscode-input-border);
  border-radius: 3px;
  font-size: 13px;
  background: var(--vscode-input-background);
  color: var(--vscode-input-foreground);
}
.show-groupid-label {
  display: flex;
  align-items: center;
  font-size: 13px;
  margin-left: 12px;
  user-select: none;
  gap: 4px;
}
</style>
