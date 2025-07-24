<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import DependencyTree from './components/DependencyTree.vue'
import DependencyPaths from './components/DependencyPaths.vue'
import DependencyConflicts from './components/DependencyConflicts.vue' // 新增

// 声明VSCode API
declare function acquireVsCodeApi(): any

const leftWidth = ref(320)
let dragging = false

const selectedDependency = ref<any>(null)
const dependencyTreeData = ref<any>(null) // 依赖树原始数据

const searchText = ref('')
const dependencyTreeRef = ref()
const dependencyConflictsRef = ref() // 新增：冲突组件引用

const showGroupId = ref(false)
const filterMode = ref(false)
const searchHistory = ref<string[]>([])
const showHistoryDropdown = ref(false)
const showSize = ref(false)

// 新增：显示模式选择，默认显示依赖树
const displayMode = ref('dependency-tree') // 'dependency-tree' 或 'dependency-conflicts'

// 新增：缓存机制相关变量
const dependencyTreeCache = ref<any>(null) // 缓存依赖树数据
const dependencyTreeLoaded = ref(false) // 标记依赖树是否已加载
const dependencyTreeKey = ref(0) // 用于强制重新渲染组件

// 新增：冲突依赖缓存变量
const conflictDataCache = ref<any>(null) // 缓存冲突数据
const conflictDataLoaded = ref(false) // 标记冲突数据是否已加载
const conflictDataKey = ref(0) // 用于强制重新渲染冲突组件

// 添加日志：监听显示模式变化
watch(displayMode, (newMode, oldMode) => {
  console.log('🔄 显示模式切换:', { from: oldMode, to: newMode })
  console.log('📊 缓存状态:', {
    dependencyTreeCache: !!dependencyTreeCache.value,
    dependencyTreeLoaded: dependencyTreeLoaded.value,
    conflictDataCache: !!conflictDataCache.value,
    conflictDataLoaded: conflictDataLoaded.value
  })
  
  if (newMode === 'dependency-tree') {
    if (dependencyTreeCache.value && dependencyTreeLoaded.value) {
      console.log('✅ 使用依赖树缓存数据，避免重新加载')
    } else {
      console.log('❌ 没有依赖树缓存数据，将触发重新加载')
    }
  } else if (newMode === 'dependency-conflicts') {
    if (conflictDataCache.value && conflictDataLoaded.value) {
      console.log('✅ 使用冲突数据缓存，避免重新加载')
    } else {
      console.log('❌ 没有冲突数据缓存，将触发重新加载')
    }
  }
})

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

// 修改：刷新依赖数据时清除所有缓存（合并重复的函数定义）
function refreshDependencies() {
  console.log('🔄 手动刷新依赖数据')
  console.log('🗑️ 清除所有缓存数据')
  
  // 清除依赖树缓存
  dependencyTreeCache.value = null
  dependencyTreeLoaded.value = false
  dependencyTreeKey.value++
  
  // 清除冲突数据缓存
  conflictDataCache.value = null
  conflictDataLoaded.value = false
  conflictDataKey.value++
  
  console.log('📊 刷新后缓存状态:', {
    dependencyTreeCache: !!dependencyTreeCache.value,
    conflictDataCache: !!conflictDataCache.value,
    dependencyTreeKey: dependencyTreeKey.value,
    conflictDataKey: conflictDataKey.value
  })
  
  // 触发相应组件的刷新
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

// 获取VSCode API实例
const vscodeApi = acquireVsCodeApi()

// 允许外部设置搜索框内容
function setSearchText(val: string) {
  searchText.value = val
  addToSearchHistory(val)
}
defineExpose({ setSearchText })

// 修改：依赖选择处理，同时缓存数据
const onSelectDependency = (dep: any, treeData: any) => {
  console.log('🎯 选择依赖:', dep)
  console.log('📦 接收到树数据:', {
    hasData: !!treeData,
    dataSize: treeData ? JSON.stringify(treeData).length : 0
  })
  
  selectedDependency.value = dep
  dependencyTreeData.value = treeData
  
  // 缓存依赖树数据
  if (!dependencyTreeCache.value && treeData) {
    console.log('💾 首次缓存依赖树数据')
    dependencyTreeCache.value = treeData
    dependencyTreeLoaded.value = true
    console.log('✅ 缓存完成:', {
      cacheSize: JSON.stringify(dependencyTreeCache.value).length,
      isLoaded: dependencyTreeLoaded.value
    })
  } else if (dependencyTreeCache.value) {
    console.log('📋 已有缓存数据，跳过缓存')
  }
}

// 新增：处理冲突数据缓存
const onCacheConflictData = (conflictData: any) => {
  console.log('💾 缓存冲突数据:', {
    hasData: !!conflictData,
    dataSize: conflictData ? JSON.stringify(conflictData).length : 0
  })
  
  if (!conflictDataCache.value && conflictData) {
    console.log('💾 首次缓存冲突数据')
    conflictDataCache.value = conflictData
    conflictDataLoaded.value = true
    console.log('✅ 冲突数据缓存完成:', {
      cacheSize: JSON.stringify(conflictDataCache.value).length,
      isLoaded: conflictDataLoaded.value
    })
  } else if (conflictDataCache.value) {
    console.log('📋 已有冲突数据缓存，跳过缓存')
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
  // 新增：监听 setSearchText 消息
  window.addEventListener('message', (event) => {
    if (event.data?.type === 'setSearchText') {
      setSearchText(event.data.artifactId)
    }
  })
})

onBeforeUnmount(() => {
  window.removeEventListener('mousemove', onDrag)
  window.removeEventListener('mouseup', stopDrag)
})

// 处理冲突依赖选择
const onSelectConflict = (conflict: any) => {
  console.log('🎯 App: 选择冲突依赖:', conflict)
  // 这里可以设置右侧面板显示相关依赖信息
  // selectedDependency.value = conflict
}
</script>

<template>
  <div>
    <!-- 主工具栏区域 -->
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
      <!-- 显示模式选择栏 - 使用与 toolbar-left 相同的样式 -->
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



    <!-- 主内容分割面板 -->
    <div class="split-pane">
      <div class="left-pane" :style="{ width: leftWidth + 'px' }">
        <!-- 根据选择的显示模式切换左侧内容 -->
        <!-- 修改：使用 key 和缓存机制优化 DependencyTree 组件 -->
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
        <!-- 依赖冲突视图 - 添加缓存支持 -->
        <DependencyConflicts 
          v-else-if="displayMode === 'dependency-conflicts'"
          :key="conflictDataKey"
          @select-conflict="onSelectConflict"
          @cache-conflict-data="onCacheConflictData"
          :vscodeApi="vscodeApi" 
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
        <!-- 修改：使用缓存的依赖树数据 -->
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

/* 主分割面板 - 调整高度以适应新的布局 */
.split-pane {
  display: flex;
  height: calc(100vh - 90px);
  /* 减去工具栏和显示模式栏的高度 */
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

/* 主工具栏样式 - 修改为垂直布局 */
.toolbar {
  width: 100%;
  box-sizing: border-box;
  padding: 8px 24px;
  margin: 0;
  border-bottom: 1px solid var(--vscode-panel-border);
  background: var(--vscode-editor-background);
  display: flex;
  flex-direction: column; /* 改为垂直布局 */
  z-index: 2;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 显示模式栏样式 - 使用与 toolbar-left 相同的样式 */
.display-mode-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px; /* 添加上边距分隔两行 */
}

/* 显示模式选择组样式 */
.display-mode-group {
  display: flex;
  align-items: center;
  gap: 20px;
}

/* Radio button 标签样式 */
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

/* 依赖冲突视图占位符样式 */
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

/* 搜索输入框相关样式 */
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

/* 其他控件样式 */
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
