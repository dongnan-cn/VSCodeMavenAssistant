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
                <div class="conflicts-title">
                    Dependency Conflicts 
                    <span v-if="props.searchText && props.searchText.trim()">
                        ({{ filteredConflictData.length }} of {{ conflictData.length }})
                    </span>
                    <span v-else>({{ conflictData.length }})</span>
                </div>
            </div>

            <div class="conflicts-items">
                <div v-for="conflict in filteredConflictData" :key="`${conflict.groupId}:${conflict.artifactId}`"
                    class="conflict-item" :class="{
                        selected: selectedConflict?.groupId === conflict.groupId && selectedConflict?.artifactId === conflict.artifactId
                    }" @click="selectConflict(conflict)" @contextmenu="handleContextMenu(conflict, $event)">
                    <div class="conflict-main">
                        <div class="conflict-gav" :style="{ color: getConflictColor(conflict) }">
                            <!-- 显示文件大小（如果启用），size已经是KB单位 -->
                            <span v-if="showSize && conflict.size" class="dependency-size">[{{ conflict.size }}
                                KB]</span>
                            <template v-if="showGroupId">
                                <span class="group-id">{{ conflict.groupId }}</span>
                                <span class="separator"> : </span>
                            </template>
                            <span class="artifact-id">{{ conflict.artifactId }}</span>
                            <span class="separator"> : </span>
                            <span class="version">{{ conflict.usedVersion }}</span>
                            <!-- 冲突版本信息移到同一行 -->
                            <span class="separator"> - </span>
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
        
        <!-- 右键菜单组件 -->
        <ContextMenu
            :visible="menuVisible"
            :x="menuX"
            :y="menuY"
            :items="menuItems"
            @select="handleMenuSelect"
            @close="menuVisible = false"
        />
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import type { ConflictDependency } from '../types/dependency'
import ContextMenu from './ContextMenu.vue'

// 组件属性定义
const props = defineProps<{
    vscodeApi?: any
    searchText?: string  // 新增：搜索文本
    showGroupId?: boolean
    showSize?: boolean  // 新增：控制是否显示文件大小
    // 新增：缓存相关属性
    cachedData?: any
    isDataLoaded?: boolean
}>()

// 修改 emit 定义，添加缓存事件
const emit = defineEmits<{
    'select-conflict': [conflict: ConflictDependency]
    'cache-conflict-data': [data: any] // 新增：缓存数据事件
    'cache-dependency-tree': [data: any] // 新增：缓存依赖树数据事件
}>()

// 响应式数据
const loading = ref(false)
const error = ref('')
const conflictData = ref<ConflictDependency[]>([])
const selectedConflict = ref<ConflictDependency | null>(null)

// 右键菜单相关状态
const menuVisible = ref(false)
const menuX = ref(0)
const menuY = ref(0)
const menuConflict = ref<ConflictDependency | null>(null)
const menuItems = ref([{ label: 'Jump to Left Tree', value: 'jump-to-tree' }])

// 搜索过滤函数 - 专门在groupId和artifactId中搜索
function searchConflicts(conflicts: ConflictDependency[], searchText: string): ConflictDependency[] {
    if (!searchText || !searchText.trim()) {
        return conflicts
    }
    
    const searchLower = searchText.toLowerCase().trim()
    
    return conflicts.filter(conflict => {
        // 在groupId中搜索
        const groupIdMatch = conflict.groupId.toLowerCase().includes(searchLower)
        // 在artifactId中搜索
        const artifactIdMatch = conflict.artifactId.toLowerCase().includes(searchLower)
        
        return groupIdMatch || artifactIdMatch
    })
}

// 计算属性：过滤后的冲突数据
const filteredConflictData = computed(() => {
    return searchConflicts(conflictData.value, props.searchText || '')
})

// 计算属性：为每个冲突依赖生成颜色映射
const conflictColors = computed(() => {
    const colorMap = new Map<string, string>()
    
    filteredConflictData.value.forEach(conflict => {
        const key = `${conflict.groupId}:${conflict.artifactId}`
        const scope = conflict.scope
        
        // scope 为 test 时显示绿色
        if (scope === 'test') {
            colorMap.set(key, '#4CAF50') // 绿色
        }
        // scope 为 runtime 时显示紫色
        else if (scope === 'runtime') {
            colorMap.set(key, '#9C27B0') // 紫色
        }
        // scope 为 compile 时显示蓝色
        else if (scope === 'compile') {
            colorMap.set(key, '#2196F3') // 蓝色
        }
        // 其他情况显示白色
        else {
            colorMap.set(key, '#FFFFFF')
        }
    })
    
    return colorMap
})

// 获取指定冲突依赖的颜色
function getConflictColor(conflict: ConflictDependency): string {
    const key = `${conflict.groupId}:${conflict.artifactId}`
    return conflictColors.value.get(key) || '#FFFFFF'
}

// 选择冲突依赖
function selectConflict(conflict: ConflictDependency) {
    selectedConflict.value = conflict
    emit('select-conflict', conflict)
}

// 处理右键菜单
function handleContextMenu(conflict: ConflictDependency, event: MouseEvent) {
    event.preventDefault()
    
    // 先选中当前冲突依赖
    selectConflict(conflict)
    
    // 显示右键菜单
    menuVisible.value = true
    menuX.value = event.clientX
    menuY.value = event.clientY
    menuConflict.value = conflict
}

// 处理菜单项选择
function handleMenuSelect(action: string) {
    if (!menuConflict.value) return
    
    if (action === 'jump-to-tree') {
        // 跳转到左侧依赖树，通过window.postMessage发送消息
        window.postMessage({
            type: 'setSearchText',
            artifactId: menuConflict.value.artifactId
        }, '*')
        
        window.postMessage({
            type: 'jumpToConflictInTree',
            groupId: menuConflict.value.groupId,
            artifactId: menuConflict.value.artifactId,
            version: menuConflict.value.usedVersion
        }, '*')
    }
    
    menuVisible.value = false
}

// 修改：刷新冲突数据，支持缓存检查
const refreshConflicts = async () => {

    // 检查是否有缓存数据
    if (props.cachedData && props.isDataLoaded) {
        conflictData.value = props.cachedData;
        loading.value = false;
        error.value = '';
        return;
    }
    loading.value = true;
    error.value = '';

    if (props.vscodeApi) {
        // 发送消息到后端获取依赖树数据
        props.vscodeApi.postMessage({
            type: 'getConflictDependencies'
        });
    } else {
        loading.value = false;
        error.value = 'VSCode API 不可用';
    }
};

/**
 * 从依赖树中提取冲突信息
 * @param dependencyTree 依赖树根节点
 * @returns 冲突依赖列表
 */
function extractConflictsFromTree(dependencyTree: any): ConflictDependency[] {

    // 存储所有依赖的映射：groupId:artifactId -> 版本信息
    const dependencyMap = new Map<string, {
        usedVersion: string | null,
        conflictVersions: Set<string>,
        groupId: string,
        artifactId: string,
        size?: string,  // JAR文件大小
        scope?: string  // 依赖范围
    }>();

    let totalNodes = 0;
    let validNodes = 0;

    // 递归遍历依赖树，收集所有依赖信息
    function traverseTree(node: any, depth: number = 0) {
        totalNodes++;
        const indent = '  '.repeat(depth);



        // 检查当前节点是否有有效的依赖信息
        if (node && node.groupId && node.artifactId && node.version) {
            validNodes++;
            const key = `${node.groupId}:${node.artifactId}`;
            const version = node.version;
            const isDropped = node.droppedByConflict === true;

            if (!dependencyMap.has(key)) {
                dependencyMap.set(key, {
                    usedVersion: null,
                    conflictVersions: new Set(),
                    groupId: node.groupId,
                    artifactId: node.artifactId,
                    size: undefined,
                    scope: undefined
                });
            }

            const depInfo = dependencyMap.get(key)!;

            if (isDropped) {
                // 被冲突丢弃的版本
                depInfo.conflictVersions.add(version);
            } else {
                // 实际使用的版本
                if (depInfo.usedVersion === null) {
                    depInfo.usedVersion = version;
                }
                if (node.size && !depInfo.size) {
                    // 将字节转换为KB（向上取整），与DependencyTreeNode.vue保持一致
                    const sizeInBytes = node.size || 0;
                    depInfo.size = Math.ceil(sizeInBytes / 1024).toString();
                }
                // 收集scope信息：优先使用实际使用版本的scope，确保与tree模式一致
                if (depInfo.usedVersion === version && node.scope) {
                    depInfo.scope = node.scope;
                } else if (node.scope && !depInfo.scope) {
                    depInfo.scope = node.scope;
                }
            }
            
            // 对于冲突版本，也尝试收集scope信息（如果还没有的话）
            if (node.scope && !dependencyMap.get(key)!.scope) {
                dependencyMap.get(key)!.scope = node.scope;
            }
        }

        // 递归处理子依赖
        if (node && node.children && Array.isArray(node.children)) {
            node.children.forEach((child: any) => traverseTree(child, depth + 1));
        }
    }

    // 开始遍历 - 如果根节点没有依赖信息，直接遍历其子节点
    if (dependencyTree && dependencyTree.children && Array.isArray(dependencyTree.children)) {
        dependencyTree.children.forEach((child: any) => traverseTree(child, 0));
    } else {
        traverseTree(dependencyTree);
    }

    // 构建冲突列表
    const conflicts: ConflictDependency[] = [];

    dependencyMap.forEach((depInfo) => {
        const hasConflicts = depInfo.conflictVersions.size > 0;
        const hasUsedVersion = depInfo.usedVersion !== null;

        // 只有存在冲突版本的依赖才加入冲突列表
        if (hasConflicts && hasUsedVersion) {
            const conflict = {
                groupId: depInfo.groupId,
                artifactId: depInfo.artifactId,
                usedVersion: depInfo.usedVersion!,
                conflictVersions: Array.from(depInfo.conflictVersions).sort(),
                conflictCount: depInfo.conflictVersions.size,
                size: depInfo.size,  // 包含size信息
                scope: depInfo.scope  // 包含scope信息
            };
            conflicts.push(conflict);
        }
    });

    // 按冲突数量降序排序
    conflicts.sort((a, b) => b.conflictCount - a.conflictCount);



    return conflicts;
}

// 处理来自扩展端的消息
const handleMessage = (event: MessageEvent) => {
    const message = event.data;

    switch (message.type) {
        case 'dependencyTreeForConflicts':
            try {
                const dependencyTree = typeof message.data === 'string'
                    ? JSON.parse(message.data)
                    : message.data;

                // 从依赖树中提取冲突信息
                const conflicts = extractConflictsFromTree(dependencyTree);
                conflictData.value = conflicts;
                loading.value = false;

                // 新增：触发缓存事件
                if (conflicts && conflicts.length >= 0) {
                    emit('cache-conflict-data', conflicts);
                }

                // 新增：将原始依赖树数据也传递给父组件，用于DependencyPaths显示
                emit('cache-dependency-tree', dependencyTree);
            } catch (err) {
                error.value = `处理依赖树数据失败: ${err}`;
                loading.value = false;
            }
            break;
        case 'conflictDependencies':
            // 兼容旧的消息格式
            try {
                const conflictDataReceived = typeof message.data === 'string'
                    ? JSON.parse(message.data)
                    : message.data;

                conflictData.value = conflictDataReceived || [];
                loading.value = false;

                // 新增：触发缓存事件
                if (conflictDataReceived) {
                    emit('cache-conflict-data', conflictDataReceived);
                }
            } catch (err) {
                error.value = `解析冲突数据失败: ${err}`;
                loading.value = false;
            }
            break;
        case 'updateConflicts':
            refreshConflicts();
            break;
        case 'refresh':
            refreshConflicts();
            break;
        case 'error':
            loading.value = false;
            error.value = message.message || '获取冲突数据失败';
            break;
    }
};

// 组件挂载时的初始化
onMounted(() => {

    // 监听来自VSCode扩展的消息
    if (typeof window !== 'undefined') {
        window.addEventListener('message', handleMessage);
    }

    // 初始加载冲突数据
    refreshConflicts();
});

// 暴露方法供父组件调用
defineExpose({
    refreshConflicts
});
</script>

<style scoped>
/* 容器样式 - 与tree模式保持一致 */
.dependency-conflicts-container {
    font-family: var(--vscode-font-family);
    color: var(--vscode-foreground);
    background: var(--vscode-editor-background);
    height: 100vh; /* 固定高度，不受分割条影响 */
    overflow-y: auto; /* 垂直滚动 */
    padding: 16px;
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
    0% {
        transform: rotate(0deg);
    }

    100% {
        transform: rotate(360deg);
    }
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
    white-space: nowrap; /* 强制单行显示，不换行 */
    overflow: hidden; /* 隐藏超出部分 */
}

.conflicts-title {
    font-size: 14px;
    font-weight: 600;
    color: var(--vscode-foreground);
    margin-bottom: 4px;
    white-space: nowrap; /* 强制单行显示，不换行 */
    overflow: hidden; /* 隐藏超出部分 */
    text-overflow: ellipsis; /* 超出部分显示省略号 */
}

.conflicts-items {
    display: flex;
    flex-direction: column;
    gap: 0;
}

/* 冲突项样式 - 与tree模式保持一致，强制单行显示 */
.conflict-item {
    display: flex; /* 使用flex布局，与tree模式一致 */
    align-items: center;
    padding: 4px 12px; /* 与tree模式相同的padding */
    margin: 1px 0; /* 与tree模式相同的margin */
    border-radius: 6px; /* 与tree模式相同的圆角 */
    cursor: pointer;
    transition: all 0.2s ease;
    border: 1px solid transparent;
    background: var(--vscode-editor-background); /* 与tree模式相同的背景 */
    font-family: 'Consolas', 'Monaco', 'Courier New', monospace; /* 与tree模式相同的字体 */
    font-size: 14px; /* 与tree模式相同的字体大小 */
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1); /* 与tree模式相同的阴影 */
    white-space: nowrap; /* 强制单行显示，不换行 */
    overflow: hidden; /* 隐藏超出部分 */
}

.conflict-item:hover {
    background: var(--vscode-list-hoverBackground);
    border-color: var(--vscode-list-hoverBackground);
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15); /* 与tree模式相同的hover阴影 */
    transform: translateY(-1px); /* 与tree模式相同的hover效果 */
}

.conflict-item.selected {
    background: var(--vscode-list-activeSelectionBackground);
    color: var(--vscode-list-activeSelectionForeground);
    border-color: var(--vscode-focusBorder);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2); /* 与tree模式相同的选中阴影 */
    transform: translateY(-1px); /* 与tree模式相同的选中效果 */
    z-index: 2;
    font-weight: 600; /* 与tree模式相同的选中字重 */
}

.conflict-main {
    flex: 1; /* 占据剩余空间，与tree模式的dep-label一致 */
    cursor: pointer;
    user-select: none;
    display: flex;
    align-items: center;
    gap: 8px;
    font-weight: 500;
}

.conflict-gav {
    font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
    font-size: 14px; /* 与tree模式相同的字体大小 */
    font-weight: 500;
    display: flex; /* 使用flex布局，与tree模式的gav-info一致 */
    align-items: center;
    gap: 4px; /* 与tree模式相同的间距 */
    white-space: nowrap; /* 强制单行显示，不换行 */
    overflow: hidden; /* 隐藏超出部分 */
    flex-shrink: 1; /* 允许收缩以适应容器 */
}

.group-id,
.artifact-id,
.version,
.separator,
.dependency-size,
.versions-list {
    /* 所有内联元素统一样式 */
    display: inline; /* 确保在同一行显示 */
    color: inherit; /* 继承父元素颜色，用于scope着色 */
}

.group-id {
    opacity: 0.9;
}

.artifact-id {
    font-weight: 600;
}

.version {
    font-weight: 500; /* 与tree模式相同的字重 */
}

.separator {
    opacity: 0.6;
}

.versions-list {
    color: var(--vscode-errorForeground) !important; /* 冲突版本使用红色，覆盖继承的颜色 */
    font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
    font-weight: 500;
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

.dependency-size {
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
}
</style>