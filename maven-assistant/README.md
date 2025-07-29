# Maven Assistant

A powerful Visual Studio Code Maven dependency management and analysis tool that provides dependency tree visualization, conflict detection, intelligent POM file editing, and more.

## 🚀 Key Features

### 📊 Dependency Analysis
- **Dependency Tree Visualization**: Interactive dependency tree with expand/collapse support
- **Dependency Conflict Detection**: Automatically identify version conflicts and provide solutions
- **Transitive Dependency Analysis**: Deep analysis of indirect dependency relationships
- **Dependency Path Tracking**: Display complete dependency introduction paths

### 🛠️ POM File Management
- **Smart Editor**: Dedicated dependency analysis editor
- **Quick Navigation**: Right-click menu for quick jumps to dependency declarations in POM files
- **Dependency Exclusion**: One-click addition of exclusion tags to resolve conflicts
- **Real-time Validation**: POM file syntax and structure validation


## 📋 System Requirements

- **Visual Studio Code**: Version 1.101.0 or higher
- **Java**: Java 21 or higher
- **Maven**: Apache Maven 3.6.0 or higher

## 🔧 Installation

1. Open Visual Studio Code
2. Go to Extensions (Ctrl+Shift+X)
3. Search for "Maven Assistant"
4. Click Install
5. Restart VS Code

## 📖 Usage Guide

### 1. Opening Maven Assistant

After opening a Maven project in VS Code, right click the pom.xml and select option: "Open with...", and select "Dependency Assistant"

### 2. Dependency Analysis

#### Viewing Dependency Tree
1. Click "Show Dependency Tree" in the "Dependency Management" view
2. Or use command: `Maven Assistant: Show Dependency Tree`
3. In the opened dependency analyzer:
   - Click arrows to expand/collapse dependency nodes
   - Use the search box to quickly locate specific dependencies
   - View dependency GAV information (GroupId:ArtifactId:Version)
   - View dependency scope and size information

#### Conflict Detection and Resolution
1. Click "Show Dependency Conflicts" to view conflict list
2. In the dependency tree, conflicting dependencies are marked in red
3. Right-click on conflicting dependencies and select appropriate actions:
   - **Jump to POM**: Navigate to dependency declaration in POM file
   - **Exclude**: Add exclusion tag to exclude the dependency
   - **Jump to Left Tree**: Locate the dependency in the left dependency tree

#### Dependency Path Analysis
1. Click any dependency node in the dependency tree
2. The right panel will display all introduction paths for that dependency
3. Each path shows the complete dependency chain: Project → Direct Dependency → ... → Target Dependency

## 💡 Usage Tips

1. **Quick Dependency Location**: Use the search box at the top of the dependency tree to quickly find specific dependencies
2. **Batch Exclusion**: For complex dependency conflicts, exclude conflicting dependencies one by one
3. **Path Analysis**: Understand dependency introduction sources through dependency paths to help decide whether to exclude
4. **POM Navigation**: Use right-click menu to quickly jump to POM files for manual editing
5. **Multi-module Support**: The plugin fully supports dependency analysis for Maven multi-module projects

## 🐛 Known Issues

- Initial analysis of large multi-module projects may be slow
- Some Maven plugins may not be fully supported in dependency analysis
- Custom repository configurations require manual setup

## 📄 License

MIT License

---

**Enjoy using Maven Assistant!**
