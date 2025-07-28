# Maven Assistant

[![Version](https://img.shields.io/vscode-marketplace/v/your-publisher-name.maven-assistant.svg)](https://marketplace.visualstudio.com/items?itemName=your-publisher-name.maven-assistant)
[![Downloads](https://img.shields.io/vscode-marketplace/d/your-publisher-name.maven-assistant.svg)](https://marketplace.visualstudio.com/items?itemName=your-publisher-name.maven-assistant)
[![Rating](https://img.shields.io/vscode-marketplace/r/your-publisher-name.maven-assistant.svg)](https://marketplace.visualstudio.com/items?itemName=your-publisher-name.maven-assistant)

A powerful Maven dependency management and analysis tool for Visual Studio Code with Language Server Protocol (LSP) support.

## ✨ Features

### 🔍 Dependency Analysis
- **Dependency Tree Visualization**: Interactive dependency tree with conflict detection
- **Dependency Conflict Resolution**: Identify and resolve version conflicts
- **Transitive Dependency Analysis**: Deep analysis of indirect dependencies
- **Effective POM Viewer**: View the resolved POM with all inherited configurations

### 🚀 Maven Goal Management
- **Quick Goal Execution**: Run common Maven goals with one click
- **Custom Goal Configuration**: Define and save custom Maven commands
- **Terminal Integration**: Execute goals in integrated terminal
- **Goal History**: Access recently used Maven commands

### 📊 Project Insights
- **Dependency Statistics**: Overview of project dependencies
- **License Analysis**: Track dependency licenses
- **Security Vulnerability Detection**: Identify known vulnerabilities
- **Build Performance Metrics**: Analyze build times and optimization opportunities

### 🛠️ Developer Experience
- **IntelliSense Support**: Auto-completion for Maven configurations
- **Real-time Validation**: Live validation of POM files
- **Quick Fixes**: Automated fixes for common Maven issues
- **Multi-module Support**: Full support for Maven multi-module projects

## 📋 Requirements

### System Requirements
- **Visual Studio Code**: Version 1.101.0 or higher
- **Java Runtime Environment**: Java 21 or higher
- **Maven**: Apache Maven 3.6.0 or higher

### Supported File Types
- `pom.xml` files
- Maven project structures
- Java source files (for context-aware analysis)

## 🚀 Installation

1. Open Visual Studio Code
2. Go to Extensions (Ctrl+Shift+X)
3. Search for "Maven Assistant"
4. Click Install
5. Reload VS Code when prompted

## ⚙️ Configuration

This extension contributes the following settings:

### Maven Configuration
- `maven-assistant.useTerminalCommand`: Use custom terminal command for Maven execution
- `maven-assistant.terminalCommand`: Custom Maven command (default: "mvn")
- `maven-assistant.resolveWorkspaceArtifacts`: Resolve workspace artifacts in dependency analysis

### UI Preferences
- `maven-assistant.autoRefreshDependencies`: Automatically refresh dependency information
- `maven-assistant.showTransitiveDependencies`: Show transitive dependencies in tree view
- `maven-assistant.enableDelete`: Enable delete functionality in dependency management

### Example Configuration
```json
{
  "maven-assistant.useTerminalCommand": true,
  "maven-assistant.terminalCommand": "mvnd",
  "maven-assistant.autoRefreshDependencies": true,
  "maven-assistant.showTransitiveDependencies": true
}
```

## 🎯 Usage

### Opening Maven Assistant
1. Open a Maven project in VS Code
2. Click the Maven Assistant icon in the Activity Bar
3. Or use Command Palette: `Ctrl+Shift+P` → "Maven Assistant: Open Maven Panel"

### Analyzing Dependencies
1. Navigate to the "Dependencies" view
2. Click "Show Dependency Tree" to visualize project dependencies
3. Use "Analyze Dependencies" for detailed conflict analysis
4. Click "Show Dependency Conflicts" to identify version conflicts

### Running Maven Goals
1. Go to the "Maven Goals" view
2. Click "Quick Run" for common goals (compile, test, package)
3. Use "Edit Maven Goal" to create custom commands
4. Access goal history from the panel

## 🐛 Known Issues

- Large multi-module projects may experience slower initial analysis
- Some Maven plugins may not be fully supported in dependency analysis
- Custom repository configurations require manual setup

## 📝 Release Notes

### 0.0.1 (Initial Release)

#### Added
- Maven dependency tree visualization
- Dependency conflict detection and resolution
- Maven goal execution with terminal integration
- Custom Maven command configuration
- Multi-module project support
- LSP-based intelligent analysis
- Real-time POM validation
- Effective POM viewer

---

## Following extension guidelines

Ensure that you've read through the extensions guidelines and follow the best practices for creating your extension.

* [Extension Guidelines](https://code.visualstudio.com/api/references/extension-guidelines)

## Working with Markdown

You can author your README using Visual Studio Code. Here are some useful editor keyboard shortcuts:

* Split the editor (`Cmd+\` on macOS or `Ctrl+\` on Windows and Linux).
* Toggle preview (`Shift+Cmd+V` on macOS or `Shift+Ctrl+V` on Windows and Linux).
* Press `Ctrl+Space` (Windows, Linux, macOS) to see a list of Markdown snippets.

## For more information

* [Visual Studio Code's Markdown Support](http://code.visualstudio.com/docs/languages/markdown)
* [Markdown Syntax Reference](https://help.github.com/articles/markdown-basics/)

**Enjoy!**
