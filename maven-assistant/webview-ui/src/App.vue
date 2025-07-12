<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import DependencyTree from './components/DependencyTree.vue'
import DependencyPaths from './components/DependencyPaths.vue'

const leftWidth = ref(320)
let dragging = false

const selectedDependency = ref<any>(null)
const dependencyTreeData = ref<any>(null) // 依赖树原始数据

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
  <div class="split-pane">
    <div class="left-pane" :style="{ width: leftWidth + 'px' }">
      <DependencyTree @select-dependency="onSelectDependency" />
    </div>
    <div class="splitter" @mousedown="startDrag"></div>
    <div class="right-pane">
      <DependencyPaths
        :dependencyTree="dependencyTreeData"
        :selectedDependency="selectedDependency"
      />
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
</style>
