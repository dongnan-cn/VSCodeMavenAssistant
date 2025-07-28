# Change Log

All notable changes to the Maven Assistant extension will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.0.1] - 2024-12-19

### Added
- **Maven Dependency Management**
  - Interactive dependency tree visualization
  - Dependency conflict detection and resolution
  - Transitive dependency analysis
  - Effective POM viewer with inherited configurations

- **Maven Goal Execution**
  - Quick execution of common Maven goals (compile, test, package, install)
  - Custom Maven goal configuration and management
  - Terminal integration for goal execution
  - Maven goal execution history

- **Language Server Protocol (LSP) Integration**
  - Real-time POM file validation
  - IntelliSense support for Maven configurations
  - Context-aware code completion
  - Quick fixes for common Maven issues

- **User Interface**
  - Maven Assistant activity bar with dedicated views
  - Maven Goals view for goal management
  - Dependencies view for dependency analysis
  - Dependency Conflicts view for conflict resolution
  - Custom dependency analyzer editor for POM files

- **Configuration Options**
  - Custom Maven command support (mvn, mvnd, etc.)
  - Workspace artifact resolution settings
  - Auto-refresh dependency information
  - Transitive dependency display options
  - Delete functionality controls

- **Multi-module Project Support**
  - Full support for Maven multi-module projects
  - Cross-module dependency analysis
  - Aggregated dependency reporting

### Technical Features
- Java 21+ LSP server with Maven Resolver integration
- TypeScript-based VSCode extension frontend
- React-based webview UI components
- Comprehensive test suite with 11 test cases
- Maven Shade plugin for standalone JAR distribution

### Documentation
- Comprehensive README with feature descriptions
- Detailed configuration guide
- Usage examples and best practices
- MIT license

## [Unreleased]

### Planned Features
- Dependency vulnerability scanning
- License compliance checking
- Build performance analytics
- Maven archetype integration
- Plugin management interface
- Dependency update suggestions