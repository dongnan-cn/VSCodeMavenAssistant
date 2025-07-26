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
                    }" @click="selectConflict(conflict)">
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
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import type { ConflictDependency } from '../types/dependency'

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
    console.log('🎯 选择冲突依赖:', conflict)
    selectedConflict.value = conflict
    emit('select-conflict', conflict)
}

// 监听搜索文本变化
watch(() => props.searchText, (newSearchText) => {
    console.log('🔍 搜索文本变化:', newSearchText)
    // 搜索逻辑已通过计算属性自动处理
}, { immediate: true })

// 修改：刷新冲突数据，支持缓存检查
const refreshConflicts = async () => {
    console.log('[DependencyConflicts] 开始刷新冲突数据');

    // 检查是否有缓存数据
    if (props.cachedData && props.isDataLoaded) {
        console.log('[DependencyConflicts] ✅ 使用缓存的冲突数据');
        conflictData.value = props.cachedData;
        loading.value = false;
        error.value = '';
        return;
    }

    console.log('[DependencyConflicts] ❌ 没有缓存数据，开始加载');
    loading.value = true;
    error.value = '';

    if (props.vscodeApi) {
        console.log('[DependencyConflicts] 使用真实API获取冲突数据');
        // 发送消息到后端获取依赖树数据
        props.vscodeApi.postMessage({
            type: 'getConflictDependencies'
        });
    } else {
        console.warn('[DependencyConflicts] 没有可用的 vscodeApi');
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
    console.log('[DependencyConflicts] 开始分析依赖树冲突');
    console.log('[DependencyConflicts] 依赖树数据:', dependencyTree);

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
    let droppedNodes = 0;
    let validNodes = 0;

    // 递归遍历依赖树，收集所有依赖信息
    function traverseTree(node: any, depth: number = 0) {
        totalNodes++;
        const indent = '  '.repeat(depth);

        console.log(`${indent}[节点 ${totalNodes}] 分析节点:`, {
            groupId: node?.groupId,
            artifactId: node?.artifactId,
            version: node?.version,
            scope: node?.scope, // 添加 scope 调试信息
            droppedByConflict: node?.droppedByConflict,
            droppedType: typeof node?.droppedByConflict,
            hasChildren: node?.children ? node.children.length : 0
        });

        // 检查当前节点是否有有效的依赖信息
        if (node && node.groupId && node.artifactId && node.version) {
            validNodes++;
            const key = `${node.groupId}:${node.artifactId}`;
            const version = node.version;
            const isDropped = node.droppedByConflict === true;

            console.log(`${indent}[节点 ${totalNodes}] ✅ 有效节点: ${key}:${version}, dropped=${isDropped}`);

            if (!dependencyMap.has(key)) {
                dependencyMap.set(key, {
                    usedVersion: null,
                    conflictVersions: new Set(),
                    groupId: node.groupId,
                    artifactId: node.artifactId,
                    size: undefined,
                    scope: undefined
                });
                console.log(`${indent}[节点 ${totalNodes}] 🆕 创建新依赖映射: ${key}`);
            }

            const depInfo = dependencyMap.get(key)!;

            // 如果当前节点有size信息且映射中还没有，则更新
            console.log('node.size', node.size, 'depInfo.size', depInfo.size)

            if (isDropped) {
                // 被冲突丢弃的版本
                depInfo.conflictVersions.add(version);
                console.log(`${indent}[节点 ${totalNodes}] 🔥 添加冲突版本: ${key}:${version}`);
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
                // 收集scope信息（优先使用第一个非冲突节点的scope）
                if (node.scope && !depInfo.scope) {
                    depInfo.scope = node.scope;
                    console.log(`${indent}[节点 ${totalNodes}] 🎯 设置scope: ${key} -> ${node.scope}`);
                } else if (node.scope) {
                    console.log(`${indent}[节点 ${totalNodes}] ⚠️ scope已存在，跳过: ${key} 当前=${depInfo.scope} 节点=${node.scope}`);
                } else {
                    console.log(`${indent}[节点 ${totalNodes}] ❌ 节点无scope信息: ${key}`);
                }
            }
            
            // 无论是否被丢弃，都尝试收集scope信息（如果还没有的话）
            if (node.scope && !dependencyMap.get(key)!.scope) {
                dependencyMap.get(key)!.scope = node.scope;
                console.log(`${indent}[节点 ${totalNodes}] 🔄 补充scope信息: ${key} -> ${node.scope}`);
            }
        } else {
            console.log(`${indent}[节点 ${totalNodes}] ❌ 跳过：缺少必要字段`);
        }

        // 递归处理子依赖
        if (node && node.children && Array.isArray(node.children)) {
            console.log(`${indent}[节点 ${totalNodes}] 📁 处理 ${node.children.length} 个子依赖`);
            node.children.forEach((child: any) => traverseTree(child, depth + 1));
        }
    }

    // 开始遍历 - 如果根节点没有依赖信息，直接遍历其子节点
    if (dependencyTree && dependencyTree.children && Array.isArray(dependencyTree.children)) {
        console.log('[DependencyConflicts] 🌳 根节点是容器，直接遍历子节点');
        dependencyTree.children.forEach((child: any) => traverseTree(child, 0));
    } else {
        console.log('[DependencyConflicts] 🌳 从根节点开始遍历');
        traverseTree(dependencyTree);
    }

    console.log('[DependencyConflicts] 🔍 遍历统计:');
    console.log(`  - 总节点数: ${totalNodes}`);
    console.log(`  - 有效节点数: ${validNodes}`);
    console.log(`  - 被丢弃节点数: ${droppedNodes}`);
    console.log(`  - 依赖映射数量: ${dependencyMap.size}`);

    // 打印依赖映射详情
    console.log('[DependencyConflicts] 📋 依赖映射详情:');
    dependencyMap.forEach((depInfo, key) => {
        console.log(`  ${key}:`, {
            usedVersion: depInfo.usedVersion,
            conflictVersions: Array.from(depInfo.conflictVersions),
            conflictCount: depInfo.conflictVersions.size,
            scope: depInfo.scope, // 添加 scope 信息
            size: depInfo.size // 添加 size 信息
        });
    });

    // 构建冲突列表
    const conflicts: ConflictDependency[] = [];

    dependencyMap.forEach((depInfo) => {
        const hasConflicts = depInfo.conflictVersions.size > 0;
        const hasUsedVersion = depInfo.usedVersion !== null;

        console.log(`[DependencyConflicts] 🔍 检查冲突: ${depInfo.groupId}:${depInfo.artifactId}`);
        console.log(`  - 有冲突版本: ${hasConflicts} (数量: ${depInfo.conflictVersions.size})`);
        console.log(`  - 有使用版本: ${hasUsedVersion} (版本: ${depInfo.usedVersion})`);
        console.log(`  - scope: ${depInfo.scope}`);
        console.log(`  - size: ${depInfo.size}`);

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
            console.log(`  ✅ 添加到冲突列表:`, conflict);
        } else {
            console.log(`  ❌ 不符合冲突条件，跳过`);
        }
    });

    // 按冲突数量降序排序
    conflicts.sort((a, b) => b.conflictCount - a.conflictCount);

    console.log(`[DependencyConflicts] 🎯 分析完成，发现 ${conflicts.length} 个冲突依赖`);
    console.log('[DependencyConflicts] 🔥 最终冲突列表:', conflicts);

    return conflicts;
}

// 处理来自扩展端的消息
const handleMessage = (event: MessageEvent) => {
    const message = event.data;
    console.log('[DependencyConflicts] 收到消息:', message);

    switch (message.type) {
        case 'dependencyTreeForConflicts':
            try {
                console.log('[DependencyConflicts] 开始处理依赖树数据');
                const dependencyTree = typeof message.data === 'string'
                    ? JSON.parse(message.data)
                    : message.data;

                // 从依赖树中提取冲突信息
                const conflicts = extractConflictsFromTree(dependencyTree);
                conflictData.value = conflicts;
                loading.value = false;

                console.log('[DependencyConflicts] 冲突数据已更新:', conflicts);

                // 新增：触发缓存事件
                if (conflicts && conflicts.length >= 0) {
                    console.log('[DependencyConflicts] 💾 触发缓存事件');
                    emit('cache-conflict-data', conflicts);
                }

                // 新增：将原始依赖树数据也传递给父组件，用于DependencyPaths显示
                console.log('[DependencyConflicts] 📤 传递依赖树数据给父组件');
                emit('cache-dependency-tree', dependencyTree);
            } catch (err) {
                console.error('[DependencyConflicts] 处理依赖树数据失败:', err);
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

                console.log('[DependencyConflicts] 冲突数据已更新 (兼容格式):', conflictDataReceived);

                // 新增：触发缓存事件
                if (conflictDataReceived) {
                    console.log('[DependencyConflicts] 💾 触发缓存事件 (兼容格式)');
                    emit('cache-conflict-data', conflictDataReceived);
                }
            } catch (err) {
                console.error('[DependencyConflicts] 解析冲突数据失败:', err);
                error.value = `解析冲突数据失败: ${err}`;
                loading.value = false;
            }
            break;
        case 'updateConflicts':
            console.log('[DependencyConflicts] 收到冲突数据更新');
            refreshConflicts();
            break;
        case 'refresh':
            refreshConflicts();
            break;
        case 'error':
            console.error('[DependencyConflicts] 收到错误消息:', message.message);
            loading.value = false;
            error.value = message.message || '获取冲突数据失败';
            break;
        default:
            console.log('[DependencyConflicts] 未处理的消息类型:', message.type);
    }
};

// 组件挂载时的初始化
onMounted(() => {
    console.log('[DependencyConflicts] 组件已挂载，开始监听消息');

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
}

.conflicts-title {
    font-size: 14px;
    font-weight: 600;
    color: var(--vscode-foreground);
    margin-bottom: 4px;
}

.conflicts-items {
    display: flex;
    flex-direction: column;
    /* 调整行距与tree模式保持一致 */
    gap: 0;
}

/* 冲突项样式 - 与tree模式保持一致的卡片式设计 */
.conflict-item {
    display: flex;
    flex-direction: column;
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
    width: 100%;
    min-width: 600px; /* 与tree模式相同的最小宽度 */
    white-space: nowrap;
    overflow: hidden;
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
    display: flex;

    align-items: center;
    /* 移除margin-bottom，因为不再有details部分 */
}

.conflict-gav {
    font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
    font-size: 14px; /* 与tree模式相同的字体大小 */
    font-weight: 500;
    flex: 1;
    display: flex;
    align-items: center;
    gap: 4px; /* 与tree模式GAV信息相同的间距 */
    min-width: 0;
    overflow: hidden;
    white-space: nowrap;
    /* 移除右边距，不再需要为badge预留空间 */
}

.group-id {
    /* 继承父元素颜色，用于scope着色 */
    color: inherit;
    opacity: 0.9; /* 与tree模式相同的透明度 */
}

.artifact-id {
    /* 继承父元素颜色，用于scope着色 */
    color: inherit;
    font-weight: 600; /* 与tree模式相同的字重 */
}

.version {
    /* 继承父元素颜色，用于scope着色 */
    color: inherit;
    font-weight: 500; /* 与tree模式相同的字重 */
}

.separator {
    /* 继承父元素颜色，用于scope着色 */
    color: inherit;
    opacity: 0.6;
}

.versions-list {
    color: var(--vscode-errorForeground);
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
    /* 继承父元素颜色，用于scope着色 */
    color: inherit;
    font-size: 0.85em;
    margin-right: 8px;
    font-weight: 500;
}
</style>