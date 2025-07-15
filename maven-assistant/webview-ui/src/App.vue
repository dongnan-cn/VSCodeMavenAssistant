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
const filterMode = ref(false)
const searchHistory = ref<string[]>([])
const showHistoryDropdown = ref(false)
const showSize = ref(false)

function toggleHistoryDropdown() {
  showHistoryDropdown.value = !showHistoryDropdown.value
}

function addToSearchHistory(val: string) {
  const trimmed = val.trim()
  if (!trimmed) return
  // 去重，最新在前，最多10条
  const idx = searchHistory.value.indexOf(trimmed)
  if (idx !== -1) searchHistory.value.splice(idx, 1)
  searchHistory.value.unshift(trimmed)
  if (searchHistory.value.length > 10) searchHistory.value.length = 10
}

function handleSearchInputKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter') {
    addToSearchHistory(searchText.value)
    showHistoryDropdown.value = false
  }
}
function handleSearchInputBlur() {
  addToSearchHistory(searchText.value)
  // 延迟关闭，避免点击历史项时被提前关闭
  setTimeout(() => { showHistoryDropdown.value = false }, 150)
}
function selectHistoryItem(item: string) {
  searchText.value = item
  showHistoryDropdown.value = false
}

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
        <div class="search-input-wrapper">
          <span class="search-icon">🔍</span>
          <span class="search-history-toggle" @click="toggleHistoryDropdown">▼</span>
          <input
            v-model="searchText"
            placeholder="Search artifact..."
            class="search-input"
            @keydown="handleSearchInputKeydown"
            @blur="handleSearchInputBlur"
          />
          <span v-if="searchText" class="search-clear-btn" @click="searchText = ''">×</span>
          <div v-if="showHistoryDropdown && searchHistory.length > 0" class="search-history-dropdown">
            <div
              v-for="(item, idx) in searchHistory"
              :key="idx"
              class="search-history-item"
              @mousedown.prevent="selectHistoryItem(item)"
            >
              {{ item }}
            </div>
          </div>
        </div>
        <label class="filter-label">
          <input type="checkbox" v-model="filterMode" /> filter
        </label>
        <button @click="refreshDependencies" class="refresh-btn">Refresh</button>
        <button @click="expandAll" class="refresh-btn">Expand All</button>
        <button @click="collapseAll" class="refresh-btn">Collapse All</button>
        <label class="show-groupid-label">
          <input type="checkbox" v-model="showGroupId" /> Show GroupId
        </label>
        <label class="show-size-label">
          <input type="checkbox" v-model="showSize" /> Show size
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
          :filterMode="filterMode"
          :showSize="showSize"
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
          :showSize="showSize"
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
.search-input-wrapper {
  position: relative;
  display: inline-flex;
  align-items: center;
}
.search-icon {
  position: absolute;
  left: 8px;
  font-size: 15px;
  color: var(--vscode-input-foreground);
  pointer-events: none;
  z-index: 2;
}
.search-history-toggle {
  position: absolute;
  left: 28px;
  font-size: 8px;
  color: var(--vscode-input-foreground);
  cursor: pointer;
  z-index: 2;
  user-select: none;
  padding: 0 2px;
}
.search-input {
  width: 180px;
  margin-right: 0;
  padding: 4px 8px 4px 44px;
  border: 1px solid var(--vscode-input-border);
  border-radius: 3px;
  font-size: 13px;
  background: var(--vscode-input-background);
  color: var(--vscode-input-foreground);
  box-sizing: border-box;
}
.search-history-dropdown {
  position: absolute;
  top: 110%;
  left: 0;
  width: 180px;
  background: var(--vscode-editorWidget-background, #252526);
  color: var(--vscode-editorWidget-foreground, #cccccc);
  border: 1px solid var(--vscode-widget-border, #454545);
  border-radius: 3px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
  z-index: 10;
  max-height: 220px;
  overflow-y: auto;
  font-size: 13px;
  padding: 4px 0;
}
.search-history-item {
  padding: 4px 16px 4px 32px;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.2s;
}
.search-history-item:hover {
  background: var(--vscode-list-hoverBackground, #2a2d2e);
  color: var(--vscode-list-hoverForeground, #fff);
}
.search-clear-btn {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 13px;
  color: rgba(128,128,128,0.45); /* 更浅半透明灰色 */
  cursor: pointer;
  z-index: 2;
  user-select: none;
  padding: 0 2px;
  border-radius: 50%;
  transition: background 0.15s, color 0.15s;
}
.search-clear-btn:hover {
  background: var(--vscode-list-hoverBackground, #2a2d2e);
  color: var(--vscode-list-hoverForeground, #fff);
}
.show-groupid-label {
  display: flex;
  align-items: center;
  font-size: 13px;
  margin-left: 12px;
  user-select: none;
  gap: 4px;
}
.filter-label {
  display: flex;
  align-items: center;
  font-size: 13px;
  margin-left: 12px;
  user-select: none;
  gap: 4px;
}
.show-size-label {
  display: flex;
  align-items: center;
  font-size: 13px;
  margin-left: 12px;
  user-select: none;
  gap: 4px;
}
</style>
