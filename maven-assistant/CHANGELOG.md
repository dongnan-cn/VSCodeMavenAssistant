# Change Log

All notable changes to the Maven Assistant extension will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.4] - 2025-07-30

### Improved
- **User Experience Enhancements**
  - Improved "Jump to POM" functionality to open files in new tabs instead of side windows
  - Enhanced code readability with comprehensive English comments throughout Vue components
  - Better navigation experience when jumping between dependency analyzer and POM files
  - Use new icon

### Changed
- **Code Quality**
  - Translated all Chinese comments to English in Vue components (DependencyConflicts.vue, DependencyTree.vue, DependencyPaths.vue)
  - Improved code maintainability and international collaboration support
  - Enhanced developer experience with clearer code documentation


### Technical
- **Editor Integration**
  - Modified `jumpToDependencyInPom` method to use `ViewColumn.Active` with `preview: false` for better tab management
  - Optimized file opening behavior in VS Code editor

