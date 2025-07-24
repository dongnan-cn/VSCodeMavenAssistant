<template>
  <div class="dependency-conflicts-container">
    <!-- 加载状态 -->
    <div v-if="loading" class="loading">
      <div class="loading-spinner"></div>
      <div class="loading-text">Loading dependency conflicts...</div>
    </div>
    
    <!-- 错误状态 -->
    <div v-else-if="error" class="error">
      <div class="error-icon">⚠️</div>
      <div class="error-message">{{ error }}</div>
    </div>
    
    <!-- 冲突列表 -->
    <div v-else-if="conflictData.length > 0" class="conflicts-list">
      <div class="conflicts-header">
        <div class="conflicts-title">Dependency Conflicts ({{ conflictData.length }})</div>
        <div class="conflicts-subtitle">Click on a dependency to view conflict details</div>
      </div>
      
      <div class="conflicts-items">
        <div 
          v-for="conflict in conflictData" 
          :key="`${conflict.groupId}:${conflict.artifactId}`"
          class="conflict-item"
          :class="{ 
            selected: selectedConflict?.groupId === conflict.groupId && selectedConflict?.artifactId === conflict.artifactId 
          }"
          @click="selectConflict(conflict)"
        >
          <div class="conflict-main">
            <div class="conflict-gav">
              <span v-if="showGroupId" class="group-id">{{ conflict.groupId }}:</span>
              <span class="artifact-id">{{ conflict.artifactId }}</span>
              <span class="version">:{{ conflict.usedVersion }}</span>
            </div>
            <div class="conflict-badge">
              <span class="conflict-count">{{ conflict.conflictCount }}</span>
              <span class="conflict-label">conflict{{ conflict.conflictCount > 1 ? 's' : '' }}</span>
            </div>
          </div>
          
          <div class="conflict-details">
            <div class="conflict-versions">
              <span class="versions-label">Conflicted versions:</span>
              <span class="versions-list">{{ conflict.conflictVersions.join(', ') }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 空状态 -->
    <div v-else class="empty-state">
      <div class="empty-icon">✅</div>
      <div class="empty-title">No Dependency Conflicts</div>
      <div class="empty-message">All dependencies are resolved without conflicts.</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { ConflictDependency } from '../types/dependency'

// 组件属性定义
const props = defineProps({
  vscodeApi: { type: Object, required: true },
  showGroupId: { type: Boolean, default: false }
})

// 事件定义
const emit = defineEmits(['select-conflict'])

// 响应式数据
const loading = ref(false)
const error = ref('')
const conflictData = ref<ConflictDependency[]>([])
const selectedConflict = ref<ConflictDependency | null>(null)

// 选择冲突依赖
function selectConflict(conflict: ConflictDependency) {
  console.log('🎯 选择冲突依赖:', conflict)
  selectedConflict.value = conflict
  emit('select-conflict', conflict)
}

// 刷新冲突数据
function refreshConflicts() {
  console.log('🔄 刷新冲突数据')
  loading.value = true
  error.value = ''
  
  // 使用 props.vscodeApi 发送消息（第二阶段实现时启用）
  if (props.vscodeApi) {
    // props.vscodeApi.postMessage({ type: 'getConflicts' })
    console.log('📡 准备发送获取冲突数据请求')
  }
  
  // 模拟数据加载
  setTimeout(() => {
    loadMockData()
  }, 1000)
}

// 加载模拟数据（第一阶段测试用）
function loadMockData() {
  console.log('📊 加载模拟冲突数据')
  
  const mockData: ConflictDependency[] = [
    {
      groupId: 'org.springframework',
      artifactId: 'spring-core',
      usedVersion: '5.3.21',
      conflictVersions: ['5.2.15', '5.1.9'],
      conflictCount: 2
    },
    {
      groupId: 'com.fasterxml.jackson.core',
      artifactId: 'jackson-core',
      usedVersion: '2.13.3',
      conflictVersions: ['2.12.1'],
      conflictCount: 1
    },
    {
      groupId: 'org.slf4j',
      artifactId: 'slf4j-api',
      usedVersion: '1.7.36',
      conflictVersions: ['1.7.30', '1.7.25', '1.6.6'],
      conflictCount: 3
    },
    {
      groupId: 'junit',
      artifactId: 'junit',
      usedVersion: '4.13.2',
      conflictVersions: ['4.12'],
      conflictCount: 1
    },
    {
      groupId: 'org.apache.commons',
      artifactId: 'commons-lang3',
      usedVersion: '3.12.0',
      conflictVersions: ['3.11', '3.9'],
      conflictCount: 2
    }
  ]
  
  conflictData.value = mockData
  loading.value = false
  
  console.log('✅ 模拟数据加载完成，冲突数量:', mockData.length)
}

// 处理来自扩展端的消息（第二阶段实现）
function handleMessage(event: MessageEvent) {
  const message = event.data
  console.log('📨 DependencyConflicts: 收到消息:', message.type)
  
  switch (message.type) {
    case 'updateConflicts':
      console.log('📥 DependencyConflicts: 收到冲突数据')
      loading.value = false
      error.value = ''
      try {
        // TODO: 第三阶段实现真实数据处理
        // const dependencyTree = JSON.parse(message.data)
        // conflictData.value = processConflictData(dependencyTree)
        console.log('⚠️ 真实数据处理将在第三阶段实现')
      } catch (err) {
        console.error('❌ 解析冲突数据失败:', err)
        error.value = `解析冲突数据失败: ${err}`
      }
      break
    case 'error':
      console.error('❌ DependencyConflicts: 收到错误消息:', message.message)
      loading.value = false
      error.value = message.message || '获取冲突数据失败'
      break
  }
}

// 组件挂载
onMounted(() => {
  console.log('🚀 DependencyConflicts: 组件挂载')
  
  // 监听来自扩展端的消息
  window.addEventListener('message', handleMessage)
  
  // 加载初始数据
  refreshConflicts()
})

// 暴露方法给父组件
defineExpose({ refreshConflicts })
</script>

<style scoped>
/* 容器样式 */
.dependency-conflicts-container {
  font-family: var(--vscode-font-family);
  color: var(--vscode-foreground);
  background: var(--vscode-editor-background);
  height: 100vh;
  overflow-y: auto;
  padding: 0;
  margin: 0;
  box-sizing: border-box;
}

/* 加载状态样式 */
.loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 200px;
  gap: 12px;
}

.loading-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid var(--vscode-progressBar-background);
  border-top: 2px solid var(--vscode-progressBar-foreground);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.loading-text {
  color: var(--vscode-descriptionForeground);
  font-size: 13px;
}

/* 错误状态样式 */
.error {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px;
  gap: 8px;
}

.error-icon {
  font-size: 24px;
}

.error-message {
  color: var(--vscode-errorForeground);
  background-color: var(--vscode-inputValidation-errorBackground);
  border: 1px solid var(--vscode-inputValidation-errorBorder);
  border-radius: 3px;
  padding: 8px 12px;
  font-size: 13px;
  text-align: center;
}

/* 冲突列表样式 */
.conflicts-list {
  padding: 8px;
}

.conflicts-header {
  padding: 8px 12px;
  border-bottom: 1px solid var(--vscode-panel-border);
  margin-bottom: 8px;
}

.conflicts-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--vscode-foreground);
  margin-bottom: 4px;
}

.conflicts-subtitle {
  font-size: 12px;
  color: var(--vscode-descriptionForeground);
}

.conflicts-items {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

/* 冲突项样式 */
.conflict-item {
  padding: 10px 12px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid transparent;
  background: var(--vscode-list-inactiveSelectionBackground);
}

.conflict-item:hover {
  background: var(--vscode-list-hoverBackground);
  border-color: var(--vscode-list-hoverBackground);
}

.conflict-item.selected {
  background: var(--vscode-list-activeSelectionBackground);
  color: var(--vscode-list-activeSelectionForeground);
  border-color: var(--vscode-focusBorder);
}

.conflict-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.conflict-gav {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  font-weight: 500;
  flex: 1;
}

.group-id {
  color: var(--vscode-descriptionForeground);
}

.artifact-id {
  color: var(--vscode-foreground);
  font-weight: 600;
}

.version {
  color: var(--vscode-textLink-foreground);
  font-weight: 500;
}

.conflict-badge {
  display: flex;
  align-items: center;
  gap: 4px;
  background: var(--vscode-badge-background);
  color: var(--vscode-badge-foreground);
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 500;
}

.conflict-count {
  font-weight: 600;
}

.conflict-details {
  padding-left: 8px;
}

.conflict-versions {
  display: flex;
  gap: 6px;
  font-size: 11px;
}

.versions-label {
  color: var(--vscode-descriptionForeground);
  font-weight: 500;
}

.versions-list {
  color: var(--vscode-errorForeground);
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
}

/* 空状态样式 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 300px;
  gap: 12px;
  padding: 20px;
  text-align: center;
}

.empty-icon {
  font-size: 48px;
  opacity: 0.6;
}

.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--vscode-foreground);
}

.empty-message {
  font-size: 13px;
  color: var(--vscode-descriptionForeground);
  max-width: 300px;
  line-height: 1.4;
}
</style>