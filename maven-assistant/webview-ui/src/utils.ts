// 统计本节点及其直接子依赖的 size 字段总和（单位 KB，向上取整）
// node: 依赖节点对象，需包含 size 字段和 children 数组
export function calcNodeAndDirectChildrenSize(node: any): number {
  if (!node) return 0
  let total = typeof node.size === 'number' ? node.size : 0
  if (Array.isArray(node.children)) {
    for (const child of node.children) {
      if (typeof child.size === 'number') {
        total += child.size
      }
    }
  }
  // 转为 KB，向上取整
  return Math.ceil(total / 1024)
} 