<template>
    <div class="dependency-conflicts-container">
        <!-- Loading state -->
        <div v-if="loading" class="loading">
            <div class="loading-spinner"></div>
            <div class="loading-text">Loading dependency conflicts...</div>
        </div>

        <!-- Error state -->
        <div v-else-if="error" class="error">
            <div class="error-icon">⚠️</div>
            <div class="error-message">{{ error }}</div>
        </div>

        <!-- Conflicts list -->
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
                            <!-- Show file size (if enabled), size is already in KB -->
                            <span v-if="showSize && conflict.size" class="dependency-size">[{{ conflict.size }}
                                KB]</span>
                            <template v-if="showGroupId">
                                <span class="group-id">{{ conflict.groupId }}</span>
                                <span class="separator"> : </span>
                            </template>
                            <span class="artifact-id">{{ conflict.artifactId }}</span>
                            <span class="separator"> : </span>
                            <span class="version">{{ conflict.usedVersion }}</span>
                            <!-- Move conflict version info to same line -->
                            <span class="separator"> - </span>
                            <span class="versions-list">{{ conflict.conflictVersions.join(', ') }}</span>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- Empty state -->
        <div v-else class="empty-state">
            <div class="empty-icon">✅</div>
            <div class="empty-title">No Dependency Conflicts</div>
            <div class="empty-message">All dependencies are resolved without conflicts.</div>
        </div>
        
        <!-- Context menu component -->
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
import { ref, onMounted, computed } from 'vue'
import type { ConflictDependency } from '../types/dependency'
import ContextMenu from './ContextMenu.vue'

// Component props definition
const props = defineProps<{
    vscodeApi?: any
    searchText?: string  // Added: search text
    showGroupId?: boolean
    showSize?: boolean  // Added: control whether to show file size
    // Added: cache related properties
    cachedData?: any
    isDataLoaded?: boolean
}>()

// Modified emit definition, add cache events
const emit = defineEmits<{
    'select-conflict': [conflict: ConflictDependency]
    'cache-conflict-data': [data: any] // Added: cache data event
    'cache-dependency-tree': [data: any] // Added: cache dependency tree data event
}>()

// Reactive data
const loading = ref(false)
const error = ref('')
const conflictData = ref<ConflictDependency[]>([])
const selectedConflict = ref<ConflictDependency | null>(null)

// Context menu related state
const menuVisible = ref(false)
const menuX = ref(0)
const menuY = ref(0)
const menuConflict = ref<ConflictDependency | null>(null)
const menuItems = ref([{ label: 'Jump to Left Tree', value: 'jump-to-tree' }])

// Search filter function - specifically search in groupId and artifactId
function searchConflicts(conflicts: ConflictDependency[], searchText: string): ConflictDependency[] {
    if (!searchText || !searchText.trim()) {
        return conflicts
    }
    
    const searchLower = searchText.toLowerCase().trim()
    
    return conflicts.filter(conflict => {
        // Search in groupId
        const groupIdMatch = conflict.groupId.toLowerCase().includes(searchLower)
        // Search in artifactId
        const artifactIdMatch = conflict.artifactId.toLowerCase().includes(searchLower)
        
        return groupIdMatch || artifactIdMatch
    })
}

// Computed property: filtered conflict data
const filteredConflictData = computed(() => {
    return searchConflicts(conflictData.value, props.searchText || '')
})

// Computed property: generate color mapping for each conflict dependency
const conflictColors = computed(() => {
    const colorMap = new Map<string, string>()
    
    filteredConflictData.value.forEach(conflict => {
        const key = `${conflict.groupId}:${conflict.artifactId}`
        const scope = conflict.scope
        
        // Show green when scope is test
        if (scope === 'test') {
            colorMap.set(key, '#4CAF50') // 绿色
        }
        // Show purple when scope is runtime
        else if (scope === 'runtime') {
            colorMap.set(key, '#9C27B0') // 紫色
        }
        // Show blue when scope is compile
        else if (scope === 'compile') {
            colorMap.set(key, '#2196F3') // 蓝色
        }
        // Show white in other cases
        else {
            colorMap.set(key, '#FFFFFF')
        }
    })
    
    return colorMap
})

// Get color for specified conflict dependency
function getConflictColor(conflict: ConflictDependency): string {
    const key = `${conflict.groupId}:${conflict.artifactId}`
    return conflictColors.value.get(key) || '#FFFFFF'
}

// Select conflict dependency
function selectConflict(conflict: ConflictDependency) {
    selectedConflict.value = conflict
    emit('select-conflict', conflict)
}

// Handle context menu
function handleContextMenu(conflict: ConflictDependency, event: MouseEvent) {
    event.preventDefault()
    
    // First select current conflict dependency
    selectConflict(conflict)
    
    // Show context menu
    menuVisible.value = true
    menuX.value = event.clientX
    menuY.value = event.clientY
    menuConflict.value = conflict
}

// Handle menu item selection
function handleMenuSelect(action: string) {
    if (!menuConflict.value) return
    
    if (action === 'jump-to-tree') {
        // Jump to left dependency tree, send message via window.postMessage
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

// Modified: refresh conflict data, support cache checking
const refreshConflicts = async () => {

    // Check if there is cached data
    if (props.cachedData && props.isDataLoaded) {
        conflictData.value = props.cachedData;
        loading.value = false;
        error.value = '';
        return;
    }
    loading.value = true;
    error.value = '';

    if (props.vscodeApi) {
        // Send message to backend to get dependency tree data
        props.vscodeApi.postMessage({
            type: 'getConflictDependencies'
        });
    } else {
        loading.value = false;
        error.value = 'VSCode API is not available';
    }
};

/**
 * Extract conflict information from dependency tree
 * @param dependencyTree dependency tree root node
 * @returns conflict dependency list
 */
function extractConflictsFromTree(dependencyTree: any): ConflictDependency[] {

    // Store mapping of all dependencies: groupId:artifactId -> version info
    const dependencyMap = new Map<string, {
        usedVersion: string | null,
        conflictVersions: Set<string>,
        groupId: string,
        artifactId: string,
        size?: string,  // JAR file size
        scope?: string  // dependency scope
    }>();

    let totalNodes = 0;
    let validNodes = 0;

    // Recursively traverse dependency tree, collect all dependency info
    function traverseTree(node: any, depth: number = 0) {
        totalNodes++;
        // Check if current node has valid dependency info
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
                // Version dropped by conflict
                depInfo.conflictVersions.add(version);
            } else {
                // Actually used version
                if (depInfo.usedVersion === null) {
                    depInfo.usedVersion = version;
                }
                if (node.size && !depInfo.size) {
                    // Convert bytes to KB (round up), consistent with DependencyTreeNode.vue
                    const sizeInBytes = node.size || 0;
                    depInfo.size = Math.ceil(sizeInBytes / 1024).toString();
                }
                // Collect scope info: prioritize scope of actually used version, ensure consistency with tree mode
                if (depInfo.usedVersion === version && node.scope) {
                    depInfo.scope = node.scope;
                } else if (node.scope && !depInfo.scope) {
                    depInfo.scope = node.scope;
                }
            }
            
            // For conflict versions, also try to collect scope info (if not already available)
            if (node.scope && !dependencyMap.get(key)!.scope) {
                dependencyMap.get(key)!.scope = node.scope;
            }
        }

        // Recursively process child dependencies
        if (node && node.children && Array.isArray(node.children)) {
            node.children.forEach((child: any) => traverseTree(child, depth + 1));
        }
    }

    // Start traversal - if root node has no dependency info, directly traverse its child nodes
    if (dependencyTree && dependencyTree.children && Array.isArray(dependencyTree.children)) {
        dependencyTree.children.forEach((child: any) => traverseTree(child, 0));
    } else {
        traverseTree(dependencyTree);
    }

    // Build conflict list
    const conflicts: ConflictDependency[] = [];

    dependencyMap.forEach((depInfo) => {
        const hasConflicts = depInfo.conflictVersions.size > 0;
        const hasUsedVersion = depInfo.usedVersion !== null;

        // Only dependencies with conflict versions are added to conflict list
        if (hasConflicts && hasUsedVersion) {
            const conflict = {
                groupId: depInfo.groupId,
                artifactId: depInfo.artifactId,
                usedVersion: depInfo.usedVersion!,
                conflictVersions: Array.from(depInfo.conflictVersions).sort(),
                conflictCount: depInfo.conflictVersions.size,
                size: depInfo.size,  // Include size info
                scope: depInfo.scope  // Include scope info
            };
            conflicts.push(conflict);
        }
    });

    // Sort by conflict count in descending order
    conflicts.sort((a, b) => b.conflictCount - a.conflictCount);



    return conflicts;
}

// Handle messages from extension side
const handleMessage = (event: MessageEvent) => {
    const message = event.data;

    switch (message.type) {
        case 'dependencyTreeForConflicts':
            try {
                const dependencyTree = typeof message.data === 'string'
                    ? JSON.parse(message.data)
                    : message.data;

                // Extract conflict info from dependency tree
                const conflicts = extractConflictsFromTree(dependencyTree);
                conflictData.value = conflicts;
                loading.value = false;

                // Added: trigger cache event
                if (conflicts && conflicts.length >= 0) {
                    emit('cache-conflict-data', conflicts);
                }

                // Added: also pass original dependency tree data to parent component for DependencyPaths display
                emit('cache-dependency-tree', dependencyTree);
            } catch (err) {
                error.value = `Failed to process dependency tree data: ${err}`;
                loading.value = false;
            }
            break;
        case 'conflictDependencies':
            // Compatible with old message format
            try {
                const conflictDataReceived = typeof message.data === 'string'
                    ? JSON.parse(message.data)
                    : message.data;

                conflictData.value = conflictDataReceived || [];
                loading.value = false;

                // Added: trigger cache event
                if (conflictDataReceived) {
                    emit('cache-conflict-data', conflictDataReceived);
                }
            } catch (err) {
                error.value = `Failed to parse conflict data: ${err}`;
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
            error.value = message.message || 'Failed to get conflict data';
            break;
    }
};

// Initialization when component is mounted
onMounted(() => {

    // Listen for messages from VSCode extension
    if (typeof window !== 'undefined') {
        window.addEventListener('message', handleMessage);
    }

    // Initial load of conflict data
    refreshConflicts();
});

// Expose methods for parent component to call
defineExpose({
    refreshConflicts
});
</script>

<style scoped>
/* Container style - consistent with tree mode */
.dependency-conflicts-container {
    font-family: var(--vscode-font-family);
    color: var(--vscode-foreground);
    background: var(--vscode-editor-background);
    height: 100vh; /* Fixed height, not affected by splitter */
    overflow-y: auto; /* Vertical scrolling */
    padding: 16px;
    margin: 0;
    box-sizing: border-box;
}

/* Loading state style */
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

/* Error state style */
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

/* Conflicts list style */
.conflicts-list {
    padding: 8px;
}

.conflicts-header {
    padding: 8px 12px;
    border-bottom: 1px solid var(--vscode-panel-border);
    margin-bottom: 8px;
    white-space: nowrap; /* Force single line display, no wrapping */
    overflow: hidden; /* Hide overflow content */
}

.conflicts-title {
    font-size: 14px;
    font-weight: 600;
    color: var(--vscode-foreground);
    margin-bottom: 4px;
    white-space: nowrap; /* Force single line display, no wrapping */
    overflow: hidden; /* Hide overflow content */
    text-overflow: ellipsis; /* Show ellipsis for overflow content */
}

.conflicts-items {
    display: flex;
    flex-direction: column;
    gap: 0;
}

/* Conflict item style - consistent with tree mode, force single line display */
.conflict-item {
    display: flex; /* Use flex layout, consistent with tree mode */
    align-items: center;
    padding: 4px 12px; /* Same padding as tree mode */
    margin: 1px 0; /* Same margin as tree mode */
    border-radius: 6px; /* Same border radius as tree mode */
    cursor: pointer;
    transition: all 0.2s ease;
    border: 1px solid transparent;
    background: var(--vscode-editor-background); /* Same background as tree mode */
    font-family: 'Consolas', 'Monaco', 'Courier New', monospace; /* Same font as tree mode */
    font-size: 14px; /* Same font size as tree mode */
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1); /* Same shadow as tree mode */
    white-space: nowrap; /* Force single line display, no wrapping */
    overflow: hidden; /* Hide overflow content */
}

.conflict-item:hover {
    background: var(--vscode-list-hoverBackground);
    border-color: var(--vscode-list-hoverBackground);
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15); /* Same hover shadow as tree mode */
    transform: translateY(-1px); /* Same hover effect as tree mode */
}

.conflict-item.selected {
    background: var(--vscode-list-activeSelectionBackground);
    color: var(--vscode-list-activeSelectionForeground);
    border-color: var(--vscode-focusBorder);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2); /* Same selected shadow as tree mode */
    transform: translateY(-1px); /* Same selected effect as tree mode */
    z-index: 2;
    font-weight: 600; /* Same selected font weight as tree mode */
}

.conflict-main {
    flex: 1; /* Occupy remaining space, consistent with dep-label in tree mode */
    cursor: pointer;
    user-select: none;
    display: flex;
    align-items: center;
    gap: 8px;
    font-weight: 500;
}

.conflict-gav {
    font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
    font-size: 14px; /* Same font size as tree mode */
    font-weight: 500;
    display: flex; /* Use flex layout, consistent with gav-info in tree mode */
    align-items: center;
    gap: 4px; /* Same spacing as tree mode */
    white-space: nowrap; /* Force single line display, no wrapping */
    overflow: hidden; /* Hide overflow content */
    flex-shrink: 1; /* Allow shrinking to fit container */
}

.group-id,
.artifact-id,
.version,
.separator,
.dependency-size,
.versions-list {
    /* Unified style for all inline elements */
    display: inline; /* Ensure display on same line */
    color: inherit; /* Inherit parent element color for scope coloring */
}

.group-id {
    opacity: 0.9;
}

.artifact-id {
    font-weight: 600;
}

.version {
    font-weight: 500; /* Same font weight as tree mode */
}

.separator {
    opacity: 0.6;
}

.versions-list {
    color: var(--vscode-errorForeground) !important; /* Conflict versions use red color, override inherited color */
    font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
    font-weight: 500;
}

/* Empty state style */
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