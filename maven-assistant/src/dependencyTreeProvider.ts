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
			// If LSP client is not connected, return error message
			if (!this._lspClient.isConnected()) {
				return [new DependencyTreeItem('LSP service not connected, please check Maven project configuration', vscode.TreeItemCollapsibleState.None, undefined, 'LSP client not connected, unable to get dependency information')];
			}
			// Get dependency tree returned by backend (JSON string)
			const dependencyTreeJson = await this._lspClient.getDependencyTree();
			let root: any;
			try {
				root = JSON.parse(dependencyTreeJson);
			} catch (e) {
				console.error('Dependency tree JSON parsing failed:', e, dependencyTreeJson);
				return [new DependencyTreeItem('Dependency tree JSON parsing failed', vscode.TreeItemCollapsibleState.None)];
			}
			// Compatible with root node as { children: [...] }
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
			console.error('Failed to get dependency tree:', error);
			return [new DependencyTreeItem(`Failed to get dependencies: ${error}`, vscode.TreeItemCollapsibleState.None)];
		}
	}

	/**
	 * 递归构建 TreeItem
	 */
	private _buildTreeItem(node: any): DependencyTreeItem {
		if (!node || !node.groupId) return new DependencyTreeItem('Unknown dependency', vscode.TreeItemCollapsibleState.None);
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

}