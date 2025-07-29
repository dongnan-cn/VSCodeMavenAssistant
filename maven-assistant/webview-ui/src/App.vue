<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import DependencyTree from './components/DependencyTree.vue'
import DependencyPaths from './components/DependencyPaths.vue'
import DependencyConflicts from './components/DependencyConflicts.vue' // Added

// Declare VSCode API
declare function acquireVsCodeApi(): any

const leftWidth = ref(320)
let dragging = false

const selectedDependency = ref<any>(null)
const dependencyTreeData = ref<any>(null) // Raw dependency tree data

const searchText = ref('')
const dependencyTreeRef = ref()
const dependencyConflictsRef = ref() // Added: conflict component reference

const showGroupId = ref(false)
const filterMode = ref(false)
const searchHistory = ref<string[]>([])
const showHistoryDropdown = ref(false)
const showSize = ref(false)

// Added: display mode selection, default to dependency tree
const displayMode = ref('dependency-tree') // 'dependency-tree' or 'dependency-conflicts'

// Added: cache mechanism related variables
const dependencyTreeCache = ref<any>(null) // Cache dependency tree data
const dependencyTreeLoaded = ref(false) // Mark if dependency tree is loaded
const dependencyTreeKey = ref(0) // Used to force re-render component

// Added: conflict dependency cache variables
const conflictDataCache = ref<any>(null) // Cache conflict data
const conflictDataLoaded = ref(false) // Mark if conflict data is loaded
const conflictDataKey = ref(0) // Used to force re-render conflict component

// Generic search trigger function
function triggerSearchAfterModeSwitch(componentRef: any) {
  if (!searchText.value.trim()) return
  // Use nextTick to ensure target component is rendered
  nextTick(() => {
    if (componentRef.value) {
      // Force reactive update by temporarily clearing and resetting searchText
      const currentSearchText = searchText.value
      searchText.value = ''
      nextTick(() => {
        searchText.value = currentSearchText
      })
    }
  })
}

// Added logging: watch display mode changes
watch(displayMode, (newMode, oldMode) => {
  
  if (newMode === 'dependency-tree') {
    
    // Trigger search when switching from conflict mode to dependency tree mode
    if (oldMode === 'dependency-conflicts') {
      triggerSearchAfterModeSwitch(dependencyTreeRef)
    }
  } else if (newMode === 'dependency-conflicts') {    
    // Trigger search when switching from dependency tree mode to conflict mode
    if (oldMode === 'dependency-tree') {
      triggerSearchAfterModeSwitch(dependencyConflictsRef)
    }
  }
})

function toggleHistoryDropdown() {
  showHistoryDropdown.value = !showHistoryDropdown.value
}

function addToSearchHistory(val: string) {
  const trimmed = val.trim()
  if (!trimmed) return
  // Deduplicate, latest first, max 10 items
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
  // Delay closing to avoid premature closure when clicking history items
  setTimeout(() => { showHistoryDropdown.value = false }, 150)
}
function selectHistoryItem(item: string) {
  searchText.value = item
  showHistoryDropdown.value = false
}

// Modified: clear all cache when refreshing dependency data (merged duplicate function definitions)
function refreshDependencies() {  
  // Clear dependency tree cache
  dependencyTreeCache.value = null
  dependencyTreeLoaded.value = false
  dependencyTreeKey.value++
  
  // Clear conflict data cache
  conflictDataCache.value = null
  conflictDataLoaded.value = false
  conflictDataKey.value++
  
  // Trigger refresh of corresponding component
  if (displayMode.value === 'dependency-tree') {
    dependencyTreeRef.value?.refreshDependencies?.()
  } else if (displayMode.value === 'dependency-conflicts') {
    dependencyConflictsRef.value?.refreshConflicts?.()
  }
}

function expandAll() {
  dependencyTreeRef.value?.expandAll?.()
}
function collapseAll() {
  dependencyTreeRef.value?.collapseAll?.()
}

// Get VSCode API instance
const vscodeApi = acquireVsCodeApi()

// Allow external setting of search box content
function setSearchText(val: string) {
  searchText.value = val
  addToSearchHistory(val)
}
defineExpose({ setSearchText })

// Modified: dependency selection handling, cache data simultaneously
const onSelectDependency = (dep: any, treeData: any) => {
  
  selectedDependency.value = dep
  dependencyTreeData.value = treeData
  
  // Cache dependency tree data
  if (!dependencyTreeCache.value && treeData) {
    dependencyTreeCache.value = treeData
    dependencyTreeLoaded.value = true
  }
}

// Added: handle conflict data cache
const onCacheConflictData = (conflictData: any) => {
  if (!conflictDataCache.value && conflictData) {
    conflictDataCache.value = conflictData
    conflictDataLoaded.value = true
  } 
}

// Added: handle dependency tree data passed from conflict component
const onCacheDependencyTreeFromConflicts = (treeData: any) => {
  // Cache this data if there's no dependency tree cache yet
  if (!dependencyTreeCache.value && treeData) {
    dependencyTreeCache.value = treeData
    dependencyTreeLoaded.value = true
  }
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
  // Added: listen for setSearchText, jumpToConflictInTree and gotoTreeNode messages
  window.addEventListener('message', (event) => {
    if (event.data?.type === 'setSearchText') {
      setSearchText(event.data.artifactId)
    } else if (event.data?.type === 'jumpToConflictInTree') {
      displayMode.value = 'dependency-tree'
      // Set search text to artifactId
      setSearchText(event.data.artifactId)
      // Find and select corresponding GAV through dependency tree component
      nextTick(() => {
        if (dependencyTreeRef.value) {
          dependencyTreeRef.value.jumpToGAV({
            groupId: event.data.groupId,
            artifactId: event.data.artifactId,
            version: event.data.version
          })
        }
      })
    } else if (event.data?.type === 'gotoTreeNode') {
      // Switch to dependency tree mode first
      displayMode.value = 'dependency-tree'
      // Wait for component to render then directly call dependency tree component method
      nextTick(() => {
        if (dependencyTreeRef.value && event.data.path) {
          // Directly call dependency tree component's jump method to avoid duplicate messages
          dependencyTreeRef.value.gotoAndHighlightNodeByPath?.(event.data.path)
        }
      })
    }
  })
})

onBeforeUnmount(() => {
  window.removeEventListener('mousemove', onDrag)
  window.removeEventListener('mouseup', stopDrag)
})

// Handle conflict dependency selection
const onSelectConflict = (conflict: any) => {
  // Convert conflict dependency to format compatible with dependency tree
  const dependencyForPaths = {
    groupId: conflict.groupId,
    artifactId: conflict.artifactId,
    version: conflict.usedVersion, // Use currently used version
    scope: conflict.scope, // Use scope information from conflict dependency
    size: conflict.size // Pass size information
  }
  
  // Set selected dependency to make right-side DependencyPaths component show related dependency chain
  selectedDependency.value = dependencyForPaths
}
</script>

<template>
  <div>
    <!-- Main toolbar area -->
    <div class="toolbar">
      <div class="toolbar-left">
        <div class="search-input-wrapper">
          <span class="search-icon">🔍</span>
          <span class="search-history-toggle" @click="toggleHistoryDropdown">▼</span>
          <input v-model="searchText" placeholder="Search artifact..." class="search-input"
            @keydown="handleSearchInputKeydown" @blur="handleSearchInputBlur" />
          <span v-if="searchText" class="search-clear-btn" @click="searchText = ''">×</span>
          <div v-if="showHistoryDropdown && searchHistory.length > 0" class="search-history-dropdown">
            <div v-for="(item, idx) in searchHistory" :key="idx" class="search-history-item"
              @mousedown.prevent="selectHistoryItem(item)">
              {{ item }}
            </div>
          </div>
        </div>
        <label class="filter-label" v-if="displayMode === 'dependency-tree'">
          <input type="checkbox" v-model="filterMode" /> filter
        </label>
        <button @click="refreshDependencies" class="refresh-btn">Refresh</button>
        <!-- Only show expand/collapse buttons in dependency tree mode -->
        <button v-if="displayMode === 'dependency-tree'" @click="expandAll" class="refresh-btn">Expand All</button>
        <button v-if="displayMode === 'dependency-tree'" @click="collapseAll" class="refresh-btn">Collapse All</button>
        <label class="show-groupid-label">
          <input type="checkbox" v-model="showGroupId" /> Show GroupId
        </label>
        <label class="show-size-label">
          <input type="checkbox" v-model="showSize" /> Show size
        </label>
      </div>
      <!-- Display mode selection bar - use same style as toolbar-left -->
      <div class="display-mode-bar">
        <div class="display-mode-group">
          <label class="radio-label">
            <input type="radio" v-model="displayMode" value="dependency-tree" name="displayMode" />
            Dependency Tree
          </label>
          <label class="radio-label">
            <input type="radio" v-model="displayMode" value="dependency-conflicts" name="displayMode" />
            Dependency Conflicts
          </label>
        </div>
      </div>
    </div>



    <!-- Main content split panel -->
    <div class="split-pane">
      <div class="left-pane" :style="{ width: leftWidth + 'px' }">
        <!-- Switch left content based on selected display mode -->
        <!-- Modified: use key and cache mechanism to optimize DependencyTree component -->
        <DependencyTree 
          v-if="displayMode === 'dependency-tree'" 
          :key="dependencyTreeKey"
          @select-dependency="onSelectDependency"
          :vscodeApi="vscodeApi" 
          :searchText="searchText" 
          :showGroupId="showGroupId" 
          :filterMode="filterMode"
          :showSize="showSize" 
          :cachedData="dependencyTreeCache"
          :isDataLoaded="dependencyTreeLoaded"
          ref="dependencyTreeRef" 
        />
        <!-- Dependency conflicts view - add cache support -->
        <DependencyConflicts 
          v-else-if="displayMode === 'dependency-conflicts'"
          :key="conflictDataKey"
          @select-conflict="onSelectConflict"
          @cache-conflict-data="onCacheConflictData"
          @cache-dependency-tree="onCacheDependencyTreeFromConflicts"
          :vscodeApi="vscodeApi" 
          :searchText="searchText"
          :showGroupId="showGroupId"
          :showSize="showSize"
          :cachedData="conflictDataCache"
          :isDataLoaded="conflictDataLoaded"
          ref="dependencyConflictsRef" 
        />
        <div v-else-if="displayMode === 'dependency-conflicts'" class="conflicts-placeholder">
          <div class="placeholder-text">Dependency Conflicts view coming soon...</div>
        </div>
      </div>
      <div class="splitter" @mousedown="startDrag"></div>
      <div class="right-pane">
        <!-- Modified: use cached dependency tree data -->
        <DependencyPaths 
          :dependencyTree="dependencyTreeCache || dependencyTreeData" 
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
html,
body,
#app {
  margin: 0 !important;
  padding: 0 !important;
  box-sizing: border-box;
  height: 100vh;
  width: 100vw;
}

/* Main split panel - adjust height to fit new layout */
.split-pane {
  display: flex;
  height: calc(100vh - 90px);
  /* Subtract toolbar and display mode bar height */
  width: 100vw;
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

.left-pane {
  background: var(--vscode-sideBar-background);
  min-width: 180px;
  max-width: 80vw;
  height: 100%;
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
  height: 100%;
  width: 100%;
  box-sizing: border-box;
  padding: 0;
  margin: 0;
}

/* Main toolbar style - modified to vertical layout */
.toolbar {
  width: 100%;
  box-sizing: border-box;
  padding: 8px 24px;
  margin: 0;
  border-bottom: 1px solid var(--vscode-panel-border);
  background: var(--vscode-editor-background);
  display: flex;
  flex-direction: column; /* Changed to vertical layout */
  z-index: 2;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* Display mode bar style - use same style as toolbar-left */
.display-mode-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px; /* Add top margin to separate two rows */
}

/* Display mode selection group style */
.display-mode-group {
  display: flex;
  align-items: center;
  gap: 20px;
}

/* Radio button label style */
.radio-label {
  display: flex;
  align-items: center;
  font-size: 13px;
  user-select: none;
  gap: 6px;
  cursor: pointer;
  color: var(--vscode-foreground);
  font-weight: 500;
  transition: color 0.2s;
}

.radio-label:hover {
  color: var(--vscode-textLink-foreground);
}

.radio-label input[type="radio"] {
  margin: 0;
  cursor: pointer;
  accent-color: var(--vscode-textLink-foreground);
}

/* Dependency conflicts view placeholder style */
.conflicts-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  width: 100%;
}

.placeholder-text {
  color: var(--vscode-descriptionForeground);
  font-size: 14px;
  font-style: italic;
}

/* Search input box related styles */
.search-input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  background: var(--vscode-input-background);
  border: 1px solid var(--vscode-input-border);
  border-radius: 3px;
  padding: 4px 8px;
  min-width: 200px;
}

.search-icon {
  margin-right: 6px;
  color: var(--vscode-input-placeholderForeground);
  font-size: 12px;
}

.search-input {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  color: var(--vscode-input-foreground);
  font-size: 13px;
}

.search-input::placeholder {
  color: var(--vscode-input-placeholderForeground);
}

.search-clear-btn {
  margin-left: 6px;
  cursor: pointer;
  color: var(--vscode-input-placeholderForeground);
  font-size: 16px;
  line-height: 1;
}

.search-clear-btn:hover {
  color: var(--vscode-input-foreground);
}

.search-history-toggle {
  margin-left: 6px;
  cursor: pointer;
  color: var(--vscode-input-placeholderForeground);
  font-size: 10px;
  transform: rotate(0deg);
  transition: transform 0.2s;
}

.search-history-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  background: var(--vscode-dropdown-background);
  border: 1px solid var(--vscode-dropdown-border);
  border-radius: 3px;
  max-height: 200px;
  overflow-y: auto;
  z-index: 1000;
  margin-top: 2px;
}

.search-history-item {
  padding: 6px 12px;
  cursor: pointer;
  color: var(--vscode-dropdown-foreground);
  font-size: 13px;
}

.search-history-item:hover {
  background: var(--vscode-list-hoverBackground);
}

/* Other control styles */
.filter-label,
.show-groupid-label,
.show-size-label {
  display: flex;
  align-items: center;
  font-size: 13px;
  user-select: none;
  gap: 4px;
  cursor: pointer;
  color: var(--vscode-foreground);
}

.refresh-btn {
  background-color: var(--vscode-button-background);
  color: var(--vscode-button-foreground);
  border: none;
  padding: 6px 12px;
  border-radius: 3px;
  cursor: pointer;
  font-size: 13px;
  transition: background-color 0.2s;
}

.refresh-btn:hover {
  background-color: var(--vscode-button-hoverBackground);
}
</style>
