<template>
  <div
    v-if="visible"
    :style="{
      position: 'fixed',
      top: y + 'px',
      left: x + 'px',
      background: 'var(--vscode-editorWidget-background, #fff)',
      border: '1px solid var(--vscode-widget-border, #ccc)',
      zIndex: 9999,
      minWidth: '160px',
      boxShadow: '0 2px 8px rgba(0,0,0,0.15)'
    }"
    @mousedown.stop
  >
    <div
      v-for="item in items"
      :key="item.value"
      @click="onSelect(item.value)"
      style="padding: 8px 16px; cursor: pointer;"
      @mouseenter="hovered = item.value"
      @mouseleave="hovered = null"
      :style="{ background: hovered === item.value ? 'var(--vscode-list-hoverBackground, #eee)' : '' }"
    >
      {{ item.label }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

// 组件props定义
const props = defineProps<{
  visible: boolean
  x: number
  y: number
  items: { label: string; value: string }[]
}>()
const emit = defineEmits(['select', 'close'])

const hovered = ref<string | null>(null)

// 监听菜单显示状态，显示时绑定全局点击事件用于关闭菜单
watch(
  () => props.visible,
  (val) => {
    if (val) {
      setTimeout(() => {
        window.addEventListener('mousedown', onGlobalClick)
      }, 0)
    } else {
      window.removeEventListener('mousedown', onGlobalClick)
    }
  }
)

// 全局点击关闭菜单
function onGlobalClick() {
  emit('close')
}
// 菜单项点击
function onSelect(value: string) {
  emit('select', value)
  emit('close')
}
</script> 