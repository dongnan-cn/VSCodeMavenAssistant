// 依赖冲突相关类型定义
export interface ConflictDependency {
  groupId: string          // 组织ID
  artifactId: string       // 构件ID
  usedVersion: string      // 实际使用的版本
  conflictVersions: string[] // 冲突的版本列表
  conflictCount: number    // 冲突数量
}

// 扩展现有的 DependencyNode 接口
export interface DependencyNode {
  groupId: string
  artifactId: string
  version: string
  scope?: string
  type?: string
  classifier?: string
  optional?: boolean
  children?: DependencyNode[]
  // 新增字段用于冲突检测
  isConflict?: boolean     // 是否存在版本冲突
  conflictInfo?: ConflictDependency // 冲突详细信息
}

// 消息类型定义
export interface VSCodeMessage {
  type: string
  data?: any
  message?: string
}

// 依赖分析结果
export interface DependencyAnalysis {
  dependencies: DependencyNode[]
  conflicts: ConflictDependency[]
  totalDependencies: number
  conflictCount: number
}