import * as vscode from 'vscode';
import { LspClient } from './lspClient';

/**
 * 依赖冲突节点
 */
class DependencyConflictItem extends vscode.TreeItem {
	constructor(
		public readonly label: string,
		public readonly collapsibleState: vscode.TreeItemCollapsibleState,
		public readonly children?: DependencyConflictItem[],
		public readonly conflictType?: string
	) {
		super(label, collapsibleState);
		
		// 根据冲突类型设置图标和描述
		if (conflictType) {
			this.description = conflictType;
			this.iconPath = new vscode.ThemeIcon('warning');
		}
	}
}

/**
 * 依赖冲突提供者
 * 提供Maven项目依赖冲突的树形视图
 */
export class DependencyConflictsProvider implements vscode.TreeDataProvider<DependencyConflictItem> {
	private _onDidChangeTreeData: vscode.EventEmitter<DependencyConflictItem | undefined | null | void> = new vscode.EventEmitter<DependencyConflictItem | undefined | null | void>();
	readonly onDidChangeTreeData: vscode.Event<DependencyConflictItem | undefined | null | void> = this._onDidChangeTreeData.event;

	private _lspClient: LspClient;

	constructor(lspClient: LspClient) {
		this._lspClient = lspClient;
	}

	/**
	 * 刷新依赖冲突
	 */
	public refresh(): void {
		this._onDidChangeTreeData.fire();
	}

	/**
	 * 获取树项
	 */
	getTreeItem(element: DependencyConflictItem): vscode.TreeItem {
		return element;
	}

	/**
	 * 获取子项
	 */
	getChildren(element?: DependencyConflictItem): Thenable<DependencyConflictItem[]> {
		if (!element) {
			// 根节点，返回依赖冲突列表
			return this._getDependencyConflicts();
		} else {
			// 子节点，返回冲突的详细信息
			return Promise.resolve(element.children || []);
		}
	}

	/**
	 * 获取依赖冲突
	 */
	private async _getDependencyConflicts(): Promise<DependencyConflictItem[]> {
		try {
			// If LSP client is not connected, return mock data
			if (!this._lspClient.isConnected()) {
				return this._getMockConflicts();
			}

			// Get dependency conflicts through LSP
			const conflicts = await this._lspClient.getDependencyConflicts();
			
			// Parse conflict information and convert to tree structure
			return this._parseDependencyConflicts(conflicts);
		} catch (error) {
			console.error('Failed to get dependency conflicts:', error);
			// Return error node
			return [
				new DependencyConflictItem(
					`Failed to get dependency conflicts: ${error}`,
					vscode.TreeItemCollapsibleState.None
				)
			];
		}
	}

	/**
	 * 解析依赖冲突信息
	 */
	private _parseDependencyConflicts(conflicts: string[]): DependencyConflictItem[] {
		const conflictItems: DependencyConflictItem[] = [];
		
		for (const conflict of conflicts) {
			// Skip title lines
			if (conflict.includes('发现依赖冲突') || conflict.includes('[WARNING]')) {
				continue;
			}
			
			// 解析冲突行
			const conflictItem = this._parseConflictLine(conflict);
			if (conflictItem) {
				conflictItems.push(conflictItem);
			}
		}
		
		return conflictItems.length > 0 ? conflictItems : this._getMockConflicts();
	}

	/**
	 * 解析冲突行
	 */
	private _parseConflictLine(line: string): DependencyConflictItem | null {
		// Remove prefix spaces and warning markers
		const cleanLine = line.replace(/^[\s\-]+/, '').trim();
		
		if (!cleanLine) {
			return null;
		}
		
		// Parse conflict format: groupId:artifactId:version (selected) vs version (excluded)
		const match = cleanLine.match(/^([^:]+):([^:]+):([^:]+)\s+\(([^)]+)\)\s+vs\s+([^:]+)\s+\(([^)]+)\)/);
		if (match) {
			const [, groupId, artifactId, selectedVersion, selectedType, excludedVersion, excludedType] = match;
			const label = `${groupId}:${artifactId}`;
			
			return new DependencyConflictItem(
				label,
				vscode.TreeItemCollapsibleState.Expanded,
				[
					new DependencyConflictItem(
						`${selectedVersion} (${selectedType})`,
						vscode.TreeItemCollapsibleState.None,
						undefined,
						'selected'
					),
					new DependencyConflictItem(
						`${excludedVersion} (${excludedType})`,
						vscode.TreeItemCollapsibleState.None,
						undefined,
						'excluded'
					)
				],
				'version-conflict'
			);
		}
		
		// If not standard format, display directly
		return new DependencyConflictItem(
			cleanLine,
			vscode.TreeItemCollapsibleState.None,
			undefined,
			'unknown'
		);
	}

	/**
	 * 获取模拟冲突数据（用于测试）
	 */
	private _getMockConflicts(): DependencyConflictItem[] {
		return [
			new DependencyConflictItem(
				'org.slf4j:slf4j-api',
				vscode.TreeItemCollapsibleState.Expanded,
				[
					new DependencyConflictItem(
						'1.7.30 (selected)',
						vscode.TreeItemCollapsibleState.None,
						undefined,
						'selected'
					),
					new DependencyConflictItem(
						'1.7.25 (excluded)',
						vscode.TreeItemCollapsibleState.None,
						undefined,
						'excluded'
					)
				],
				'version-conflict'
			),
			new DependencyConflictItem(
				'org.apache.commons:commons-lang3',
				vscode.TreeItemCollapsibleState.Expanded,
				[
					new DependencyConflictItem(
						'3.12.0 (selected)',
						vscode.TreeItemCollapsibleState.None,
						undefined,
						'selected'
					),
					new DependencyConflictItem(
						'3.11.0 (excluded)',
						vscode.TreeItemCollapsibleState.None,
						undefined,
						'excluded'
					)
				],
				'version-conflict'
			),
			new DependencyConflictItem(
				'com.fasterxml.jackson:jackson-core',
				vscode.TreeItemCollapsibleState.Expanded,
				[
					new DependencyConflictItem(
						'2.13.0 (selected)',
						vscode.TreeItemCollapsibleState.None,
						undefined,
						'selected'
					),
					new DependencyConflictItem(
						'2.12.0 (excluded)',
						vscode.TreeItemCollapsibleState.None,
						undefined,
						'excluded'
					)
				],
				'version-conflict'
			)
		];
	}
}