import * as vscode from 'vscode';
import { LspClient } from './lspClient';

/**
 * 依赖树节点
 */
class DependencyTreeItem extends vscode.TreeItem {
	constructor(
		public readonly label: string,
		public readonly collapsibleState: vscode.TreeItemCollapsibleState,
		public readonly children?: DependencyTreeItem[],
		public readonly tooltip?: string
	) {
		super(label, collapsibleState);
		if (tooltip) {
			this.tooltip = tooltip;
		}
	}
}

/**
 * 依赖树提供者
 * 提供Maven项目依赖的树形视图
 */
export class DependencyTreeProvider implements vscode.TreeDataProvider<DependencyTreeItem> {
	private _onDidChangeTreeData: vscode.EventEmitter<DependencyTreeItem | undefined | null | void> = new vscode.EventEmitter<DependencyTreeItem | undefined | null | void>();
	readonly onDidChangeTreeData: vscode.Event<DependencyTreeItem | undefined | null | void> = this._onDidChangeTreeData.event;

	private _lspClient: LspClient;
	private _treeData: DependencyTreeItem[] = [];

	constructor(lspClient: LspClient) {
		this._lspClient = lspClient;
	}

	/**
	 * 刷新依赖树
	 */
	public refresh(): void {
		this._onDidChangeTreeData.fire();
	}

	/**
	 * 获取树项
	 */
	getTreeItem(element: DependencyTreeItem): vscode.TreeItem {
		return element;
	}

	/**
	 * 获取子项
	 */
	getChildren(element?: DependencyTreeItem): Thenable<DependencyTreeItem[]> {
		if (!element) {
			// 根节点，返回项目依赖
			return this._getProjectDependencies();
		} else {
			// 子节点，返回依赖的子依赖
			return Promise.resolve(element.children || []);
		}
	}

	/**
	 * 获取项目依赖
	 */
	private async _getProjectDependencies(): Promise<DependencyTreeItem[]> {
		try {
			// 如果LSP客户端未连接，返回模拟数据
			if (!this._lspClient.isConnected()) {
				return this._getMockDependencies();
			}
			// 获取后端返回的依赖树（JSON字符串）
			const dependencyTreeJson = await this._lspClient.getDependencyTree();
			let root: any;
			try {
				root = JSON.parse(dependencyTreeJson);
			} catch (e) {
				console.error('依赖树JSON解析失败:', e, dependencyTreeJson);
				return [new DependencyTreeItem('依赖树JSON解析失败', vscode.TreeItemCollapsibleState.None)];
			}
			// 兼容根节点为 { children: [...] }
			let nodes: any[] = [];
			if (root && !root.groupId && Array.isArray(root.children)) {
				nodes = root.children;
			} else if (root && root.groupId) {
				nodes = [root];
			}
			const items = nodes.map((n) => this._buildTreeItem(n));
			this._treeData = items;
			return items;
		} catch (error) {
			console.error('获取依赖树失败:', error);
			return [new DependencyTreeItem(`获取依赖失败: ${error}`, vscode.TreeItemCollapsibleState.None)];
		}
	}

	/**
	 * 递归构建 TreeItem
	 */
	private _buildTreeItem(node: any): DependencyTreeItem {
		if (!node || !node.groupId) return new DependencyTreeItem('未知依赖', vscode.TreeItemCollapsibleState.None);
		const label = `${node.groupId}:${node.artifactId}:${node.version}` + (node.scope ? ` [${node.scope}]` : '') + (node.droppedByConflict ? ' [DROPPED]' : '');
		const tooltip = label;
		const hasChildren = node.children && node.children.length > 0;
		const children = hasChildren ? node.children.map((c: any) => this._buildTreeItem(c)) : undefined;
		return new DependencyTreeItem(
			label,
			hasChildren ? vscode.TreeItemCollapsibleState.Collapsed : vscode.TreeItemCollapsibleState.None,
			children,
			tooltip
		);
	}

	/**
	 * 解析依赖树文本
	 */
	private _parseDependencyTree(dependencyTreeText: string): DependencyTreeItem[] {
		const lines = dependencyTreeText.split('\n');
		const dependencies: DependencyTreeItem[] = [];
		
		for (const line of lines) {
			// 跳过空行和INFO行
			if (!line.trim() || line.startsWith('[INFO]')) {
				continue;
			}
			
			// 解析依赖行
			const dependency = this._parseDependencyLine(line);
			if (dependency) {
				dependencies.push(dependency);
			}
		}
		
		return dependencies.length > 0 ? dependencies : this._getMockDependencies();
	}

	/**
	 * 解析依赖行
	 */
	private _parseDependencyLine(line: string): DependencyTreeItem | null {
		// 移除前缀符号
		const cleanLine = line.replace(/^[├└│\s]+/, '').trim();
		
		if (!cleanLine) {
			return null;
		}
		
		// 解析依赖坐标
		const match = cleanLine.match(/^([^:]+):([^:]+):([^:]+)(?::([^:]+))?/);
		if (match) {
			const [, groupId, artifactId, version, scope] = match;
			const label = `${groupId}:${artifactId}:${version}${scope ? ` (${scope})` : ''}`;
			
			// 检查是否有子依赖（通过缩进判断）
			const indentLevel = (line.match(/^[├└│\s]*/)?.[0]?.length || 0) / 4;
			const hasChildren = indentLevel > 0;
			
			return new DependencyTreeItem(
				label,
				hasChildren ? vscode.TreeItemCollapsibleState.Collapsed : vscode.TreeItemCollapsibleState.None
			);
		}
		
		// 如果不是标准格式，直接显示
		return new DependencyTreeItem(
			cleanLine,
			vscode.TreeItemCollapsibleState.None
		);
	}

	/**
	 * 获取模拟依赖数据（用于测试）
	 */
	private _getMockDependencies(): DependencyTreeItem[] {
		return [
			new DependencyTreeItem(
				'com.example:my-project:1.0.0',
				vscode.TreeItemCollapsibleState.Expanded,
				[
					new DependencyTreeItem(
						'org.springframework:spring-core:5.3.0',
						vscode.TreeItemCollapsibleState.Collapsed,
						[
							new DependencyTreeItem(
								'org.springframework:spring-jcl:5.3.0',
								vscode.TreeItemCollapsibleState.None
							)
						]
					),
					new DependencyTreeItem(
						'org.springframework:spring-context:5.3.0',
						vscode.TreeItemCollapsibleState.Collapsed,
						[
							new DependencyTreeItem(
								'org.springframework:spring-aop:5.3.0',
								vscode.TreeItemCollapsibleState.None
							),
							new DependencyTreeItem(
								'org.springframework:spring-beans:5.3.0',
								vscode.TreeItemCollapsibleState.None
							),
							new DependencyTreeItem(
								'org.springframework:spring-core:5.3.0',
								vscode.TreeItemCollapsibleState.None
							),
							new DependencyTreeItem(
								'org.springframework:spring-expression:5.3.0',
								vscode.TreeItemCollapsibleState.None
							)
						]
					),
					new DependencyTreeItem(
						'org.junit:junit:4.13.2 (test)',
						vscode.TreeItemCollapsibleState.None
					),
					new DependencyTreeItem(
						'org.slf4j:slf4j-api:1.7.30',
						vscode.TreeItemCollapsibleState.None
					),
					new DependencyTreeItem(
						'ch.qos.logback:logback-classic:1.2.3',
						vscode.TreeItemCollapsibleState.Collapsed,
						[
							new DependencyTreeItem(
								'ch.qos.logback:logback-core:1.2.3',
								vscode.TreeItemCollapsibleState.None
							),
							new DependencyTreeItem(
								'org.slf4j:slf4j-api:1.7.30',
								vscode.TreeItemCollapsibleState.None
							)
						]
					)
				]
			)
		];
	}
} 