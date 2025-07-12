# MavenHelper IntelliJ 插件技术实现要点细化

## 1. 插件核心功能点
- 支持自定义和管理常用 Maven Goals（如 clean、install、jetty:run 等），并可通过界面进行增删改。
- 支持通过别名和宏变量，动态生成 Maven 命令，提升命令复用性和灵活性。
- 在 IDE 的菜单、右键菜单等集成自定义的 Maven 操作入口，便于快速执行常用操作。
- 支持运行、调试、测试等多种 Maven 操作，并能根据当前文件、类、方法等上下文智能生成命令。
- 插件配置持久化，支持用户自定义，重启后依然保留。

## 2. IntelliJ 插件实现机制
- 通过实现 `PersistentStateComponent` 接口，实现插件配置的持久化（如 ApplicationSettings）。
- 通过 `ActionManager` 注册/注销自定义 Action（如 RunGoalAction、DebugGoalAction），并将其集成到 IDE 的菜单、右键菜单等。
- 使用 `@State` 注解声明插件的持久化存储文件（如 mavenRunHelper.xml）。
- 通过 Swing 实现插件的设置界面（如 ApplicationSettingsForm、GoalEditor 等），支持用户交互。
- 依赖 IntelliJ 的 Maven API，获取项目结构、当前文件、类、方法等上下文信息，动态生成 Maven 命令。

## 3. 关键类和数据流
- `MavenHelperApplicationService`：插件的核心服务，负责 Action 的注册、配置的加载与保存。
- `ApplicationSettings`：插件的配置模型，包含 Goals、别名、界面行为等，支持序列化和克隆。
- `RunGoalAction` 等 Action 类：根据当前上下文和用户配置，动态生成并执行 Maven 命令。
- `Goal`、`Goals`、`Alias`、`Aliases` 等模型类：描述 Maven 目标和别名的结构与操作。
- 数据流：用户在界面配置 Goals/别名 → 配置持久化到 XML → 右键菜单等触发 Action → 解析当前上下文 → 生成并执行 Maven 命令。

## 4. 技术选型与依赖
- 语言：Java
- 构建工具：Gradle（可迁移为 Maven）
- 主要依赖：
  - IntelliJ Platform SDK
  - Apache Maven 相关库
  - commons-beanutils、commons-lang3、commons-text 等工具库
  - Swing（界面）

## 5. 对应 VSCode 插件开发的可迁移思路
- VSCode 插件推荐用 TypeScript/JavaScript 开发，但也可用 Java 通过 Language Server Protocol（LSP）实现后端逻辑。
- 可将核心 Maven 逻辑（如命令生成、配置管理）用 Java 实现，前端用 VSCode API 实现界面和交互。
- 配置持久化可用 VSCode 的全局/工作区设置，或自定义 JSON 文件。
- Action 注册可通过 VSCode 的命令（commands）和右键菜单（menus）扩展实现。
- 需要设计前后端通信机制（如通过 LSP、WebSocket、进程间通信等），实现 VSCode 与 Java 后端的协作。
- Swing 界面需迁移为 VSCode 的 WebView 或原生设置面板。

# VSCode 前端 + Java 后端 + LSP 架构设计

## 1. 架构分层说明
- 采用 VSCode 插件前端（TypeScript/JavaScript）+ Java 后端（Maven 项目）+ LSP（Language Server Protocol）通信的分层架构。
- 前端负责界面、命令注册、与用户交互，后端负责核心 Maven 逻辑、命令生成、配置管理等。
- 前后端通过 LSP 协议通信，保证高效、标准、易扩展。

## 2. 目录结构建议
```
VSCodeMavenAssistant/
│
├─ vscode-extension/      # VSCode 插件前端（TypeScript/JS）
│   ├─ src/
│   ├─ package.json
│   └─ ... 
│
├─ java-backend/          # Java 后端（Maven 项目）
│   ├─ src/main/java/
│   ├─ pom.xml
│   └─ ...
│
├─ plan.md                # 技术方案与过程记录
└─ README.md
```

## 3. 各层职责
### 3.1 VSCode 前端（vscode-extension）
- 注册命令（commands），如"运行 Maven 目标"、"编辑命令模板"等。
- 提供设置界面（WebView/原生设置）。
- 负责与 Java 后端通信，传递用户操作、获取结果并展示。
- 负责配置的读取/保存（可与后端协作）。

### 3.2 Java 后端（java-backend）
- 复用/迁移原有 MavenHelper 的核心逻辑（如命令生成、别名、宏替换、配置管理等）。
- 提供对外接口（LSP 协议实现，推荐使用 Eclipse LSP4J 等 Java LSP 框架）。
- 负责数据持久化（如配置、历史命令等）。
- 保持与前端的协议兼容和高效通信。

## 4. LSP 通信机制与技术选型
- LSP（Language Server Protocol）是 VSCode 与后端服务通信的标准协议，支持跨语言、跨平台。
- 推荐 Java 端使用 [Eclipse LSP4J](https://github.com/eclipse/lsp4j) 实现 LSP Server，前端用 VSCode 官方 API 启动和管理 LSP Client。
- 通信方式可选 stdio（标准输入输出）或 TCP 端口，推荐 stdio，兼容性好。
- LSP 支持自定义方法，可扩展插件专属的命令、配置、数据交互。

## 5. 迁移实现建议
- 先将原有 MavenHelper 的核心 Java 逻辑迁移到 java-backend，封装为 LSP Server 的服务方法。
- 前端用最小实现（如命令面板触发、简单 WebView）与后端打通通信链路。
- 保证功能对标原有插件，后续再做体验和功能增强。
- 配置、命令、别名等数据可通过 LSP 方法在前后端同步。

---

本节内容为 VSCode + Java + LSP 架构的技术落地方案，后续如有细节调整将持续补充。

# 去除右键菜单的设计调整

## 1. 为什么去除右键菜单
- IntelliJ 和 VSCode 都有官方或社区的 Maven 支持插件，已经在右键菜单中集成了丰富的 Maven 操作（如生命周期、依赖、插件、仓库管理等）。
- 如果插件再集成类似的右键菜单功能，容易造成功能重叠，用户体验反而变差，插件的独特价值被稀释。
- 因此，本插件将不再在编辑器、项目视图等右键菜单中集成自定义 Maven 操作。

## 2. 插件聚焦的特色功能建议
- 智能生成复杂 Maven 命令（如带 profile、参数、宏替换等），一键运行。
- 提供更强的命令行模板、别名、批量操作等能力。
- 提供更友好的可视化配置界面（如 WebView），让用户自定义常用命令、别名、快捷方式。
- 支持一键复制/粘贴/分享 Maven 命令。
- 提供历史命令、常用命令收藏等功能。
- 支持与团队共享命令模板等。

## 3. 推荐的交互方式
- 命令面板（Ctrl+Shift+P）触发插件功能。
- 独立侧边栏/面板，集中展示和管理插件功能。
- 状态栏按钮，快速访问常用操作。
- 快捷键自定义，提升高频操作效率。

---

本文件将持续记录实现过程中的关键技术细节和迁移方案，便于后续查阅和总结。

# 项目包结构与开发细节建议

## 1. Java 后端包结构建议
建议采用如下包结构，便于分层管理和后续维护：
```
nd.mavenassistant.lsp         # LSP Server 启动与协议适配
nd.mavenassistant.core        # 核心业务逻辑（命令生成、别名、宏、配置等）
nd.mavenassistant.model       # 数据模型（Goal、Alias、Settings等）
nd.mavenassistant.util        # 工具类
```
- lsp 包：负责 LSP Server 的启动、请求分发、与 VSCode 前端的数据协议适配。
- core 包：迁移原有核心功能代码。
- model 包：数据结构定义，便于前后端数据序列化/反序列化。
- util 包：通用工具类。

## 2. VSCode 前端目录结构建议
```
vscode-extension/
  ├─ src/
  │   ├─ extension.ts         # 插件主入口
  │   ├─ lspClient.ts         # LSP 客户端封装
  │   ├─ commands/            # 各类命令实现
  │   ├─ views/               # WebView/面板相关
  │   └─ utils/               # 工具函数
  ├─ server/                  # 存放 Java 后端 jar
  ├─ package.json
  └─ ...
```

## 3. 配置与数据同步
- 插件配置（如常用命令、别名等）建议通过 LSP 方法在前后端同步，保证数据一致性。
- 建议采用 JSON 作为配置文件格式，便于 VSCode/Java 双端解析。

## 4. 跨平台兼容性
即使采用了 fat jar 并将前后端打包在一起，仍需关注以下跨平台兼容性问题：

#### 1. Java 运行环境依赖
- fat jar 只解决了依赖问题，但运行 fat jar 仍然需要用户本地有 Java 运行环境（JRE/JDK）。
- 不同操作系统用户的 Java 安装路径、环境变量（如 JAVA_HOME）、可执行文件名（java.exe vs java）等可能不同。
- 有些用户可能没有安装 Java，或者 Java 版本不兼容（如只装了 Java 8，但插件需要 Java 11+）。

#### 2. 启动命令差异
- Windows 下通常用 java.exe，Linux/macOS 用 java。
- 路径分隔符（\\ vs /）、文件权限（可执行权限）、shell 语法等有差异。
- VSCode 插件前端在用 child_process.spawn 启动 jar 时，命令行参数、工作目录等要兼容不同平台。

#### 3. 文件路径与权限
- jar 包路径、临时文件、配置文件等在不同操作系统下路径格式不同。
- Windows 下路径区分大小写与 Linux/macOS 不同。
- 某些系统下，插件目录可能只读，写入配置/日志时要注意。

#### 4. 字符编码与本地化
- 不同操作系统默认字符集不同，命令行参数、文件读写时可能出现乱码。
- 日志、输出内容要注意编码兼容。

#### 5. 进程管理
- 进程启动、关闭、信号处理等在不同平台有差异。
- Windows 下有时需要特殊处理进程终止、子进程继承等问题。

#### 6. 解决思路
- 在插件前端动态检测 Java 是否可用，给出友好提示。
- 启动 jar 时用 Node.js 的 path/join 等 API 处理路径，避免硬编码。
- 尽量用标准 Java 代码处理文件、路径、编码，避免平台相关写法。
- 文档中注明最低 Java 版本要求，或考虑集成 OpenJDK（如 GraalVM native image，适合更高要求时）。

---

本节内容为项目包结构和开发细节建议，所有命名均采用 nd.mavenassistant 前缀，避免出现 krasa 和 mavenhelper 两个词。后续如有细节调整将持续补充。

# 当前实现情况记录

## 已完成的功能

### 1. 项目架构搭建
- ✅ 采用 VSCode 前端（TypeScript）+ Java 后端（LSP4J）+ LSP 通信架构
- ✅ 目录结构：maven-assistant/（VSCode 前端）+ java-backend/（Java LSP 后端）
- ✅ 包名统一使用 nd.mavenassistant，避免 krasa/mavenhelper

### 2. Java 后端实现
- ✅ Maven 项目初始化，添加 LSP4J、JUnit 依赖
- ✅ 配置 maven-shade-plugin 打包 fat jar，解决 META-INF 签名文件冲突
- ✅ 实现 LspServerMain（主入口）和 SimpleLanguageServer（基础 LSP 实现）
- ✅ JUnit 测试验证 SimpleLanguageServer.initialize 方法
- ✅ fat jar 能独立运行并阻塞等待前端连接

### 3. VSCode 前端实现
- ✅ 使用 yo code 脚手架生成 TypeScript 项目
- ✅ 集成 vscode-languageclient 建立 LSP 通信
- ✅ 实现 lspClient.ts，自动用 child_process 启动后端 shaded jar
- ✅ extension.ts 集成 LSP 客户端，插件激活时启动后端，停用时关闭
- ✅ package.json 正确注册 activationEvents 和 contributes.commands

### 4. 插件调试与激活
- ✅ 解决 VSCode 插件激活机制，理解 activationEvents 作用
- ✅ 确认必须用"打开文件夹"方式打开插件目录进行调试
- ✅ 验证 F5 启动时 extensionDevelopmentPath 指向插件根目录
- ✅ 检查 launch.json、package.json、out/extension.js 等配置
- ✅ 使用 Developer: Show Running Extensions 验证插件激活状态
- ✅ 用 console.log/vscode.window.showInformationMessage 验证 activate 方法调用
- ✅ 发现 Cursor 环境下 F5 无法激活插件，官方 VSCode 可正常激活

## 遇到的关键问题与解决方案

### 1. Maven Shade Plugin 签名文件冲突
**问题**：打包时出现 SecurityException，META-INF 下签名文件冲突
**解决**：在 maven-shade-plugin 中配置精确的 filters，排除 META-INF 下所有签名和无关文件

### 2. VSCode 插件激活问题
**问题**：插件无法激活，命令面板找不到注册的命令
**解决**：
- 确保用"打开文件夹"方式打开插件目录
- 检查 activationEvents 包含 "*"、onCommand、onLanguage
- 验证 package.json 中 main 字段指向正确的入口文件
- 确认 out/extension.js 编译产物存在

### 3. 开发环境兼容性
**问题**：Cursor 环境下 F5 无法激活插件
**解决**：使用官方 VSCode 进行插件开发和调试，或使用命令行方式启动

## 当前技术栈确认

### Java 后端
- **构建工具**：Maven
- **核心依赖**：LSP4J、JUnit
- **打包方式**：maven-shade-plugin fat jar
- **包结构**：nd.mavenassistant.lsp、nd.mavenassistant.core 等

### VSCode 前端
- **语言**：TypeScript
- **核心依赖**：vscode、vscode-languageclient
- **构建工具**：npm + webpack
- **调试方式**：F5 启动 Extension Development Host

### 通信协议
- **协议**：Language Server Protocol (LSP)
- **传输方式**：stdio（标准输入输出）
- **Java 框架**：Eclipse LSP4J

## 下一步开发计划

### 短期目标
1. 实现基础的 Maven 命令执行功能
2. 添加命令面板入口，支持常用 Maven 生命周期命令
3. 实现 pom.xml 文件解析和依赖树展示

### 中期目标
1. 实现自定义 Maven 命令模板和别名功能
2. 添加 WebView 配置界面
3. 实现依赖冲突分析和解决建议

### 长期目标
1. 完善所有 MavenHelper 核心功能
2. 优化用户体验和界面设计
3. 添加团队协作和命令分享功能

## 开发环境要求

### 必需环境
- Node.js 16+
- Java 11+
- Maven 3.6+
- VSCode 1.60+ 或官方 VSCode（推荐用于插件开发）

### 推荐工具
- 官方 VSCode（插件开发调试）
- IntelliJ IDEA（Java 后端开发）
- Git（版本控制）

---

**最后更新**：2024年12月
**当前状态**：基础架构搭建完成，前后端通信打通，插件可正常激活和调试

# 前端实现情况记录

## 已完成的前端功能

### 1. 插件配置与命令注册
- ✅ 更新 package.json，添加 Maven Assistant 相关命令和配置
- ✅ 注册侧边栏视图容器（Maven Assistant）
- ✅ 配置三个主要视图：Maven 目标、依赖管理、依赖冲突
- ✅ 添加命令面板入口，支持常用 Maven 操作
- ✅ 配置插件设置项（终端命令、删除功能等）

### 2. 核心命令实现
- ✅ 打开 Maven 面板命令（maven-assistant.openMavenPanel）
- ✅ 运行 Maven 目标命令（maven-assistant.runMavenGoal）
- ✅ 编辑 Maven 目标命令（maven-assistant.editMavenGoal）
- ✅ 快速运行命令（maven-assistant.quickRun）
- ✅ 显示依赖树命令（maven-assistant.showDependencyTree）
- ✅ 分析依赖命令（maven-assistant.analyzeDependencies）
- ✅ 显示依赖冲突命令（maven-assistant.showDependencyConflicts）
- ✅ 显示有效 POM 命令（maven-assistant.showEffectivePom）

### 3. LSP 客户端增强
- ✅ 重构 LspClient 类，支持面向对象设计
- ✅ 添加 Maven 相关的 LSP 方法调用
- ✅ 实现依赖分析、命令执行、配置管理等功能
- ✅ 添加错误处理和模拟数据支持
- ✅ 支持连接状态检查

### 4. WebView 面板实现
- ✅ 实现 MavenPanelProvider，提供 WebView 界面
- ✅ 参考 MavenHelper 风格，实现目标管理界面
- ✅ 支持常用目标和自定义目标的分类显示
- ✅ 实现目标运行、编辑、添加、删除功能
- ✅ 添加状态提示和错误处理
- ✅ 使用 VSCode 主题变量，适配深色/浅色主题

### 5. 依赖树视图实现
- ✅ 实现 DependencyTreeProvider，提供树形依赖视图
- ✅ 支持依赖树的层级显示和展开/折叠
- ✅ 解析 Maven 依赖树输出格式
- ✅ 提供模拟数据用于测试
- ✅ 支持依赖坐标解析（groupId:artifactId:version）

### 6. 依赖冲突视图实现
- ✅ 实现 DependencyConflictsProvider，提供冲突检测视图
- ✅ 支持版本冲突的可视化显示
- ✅ 区分选择和排除的版本
- ✅ 使用警告图标标识冲突项
- ✅ 提供冲突类型分类

### 7. 用户界面特色
- ✅ 完全中文界面，符合用户习惯
- ✅ 参考 MavenHelper 的设计风格
- ✅ 使用 VSCode 原生主题变量
- ✅ 响应式布局，适配不同屏幕尺寸
- ✅ 友好的错误提示和状态反馈

### 8. 自定义Webview编辑器（Dependency Analyzer）
- ✅ 在 package.json 注册 customEditors，仅匹配 pom.xml
- ✅ 实现 DependencyAnalyzerEditorProvider，显示"Hello Dependency Analyzer"
- ✅ 激活插件后，右键 pom.xml 可用"Dependency Analyzer"方式打开
- ✅ 打开后底部tab显示自定义Webview，内容可独立于主编辑器
- ✅ 已在界面实际测试通过，用户体验与 IntelliJ 的 Dependency Analyzer 类似

## 前端架构设计

### 1. 模块化设计
```
src/
├── extension.ts              # 插件主入口
├── lspClient.ts              # LSP 客户端封装
├── mavenPanelProvider.ts     # Maven 面板提供者
├── dependencyTreeProvider.ts # 依赖树提供者
└── dependencyConflictsProvider.ts # 依赖冲突提供者
```

### 2. 数据流设计
- 用户操作 → 命令处理器 → LSP 客户端 → Java 后端
- Java 后端 → LSP 客户端 → 视图提供者 → 用户界面
- 配置变更 → 设置管理器 → 持久化存储

### 3. 错误处理策略
- LSP 连接失败时提供模拟数据
- 网络错误时显示友好提示
- 解析失败时降级到基础显示
- 所有异步操作都有超时处理

## 与 MavenHelper 的对比

### 1. 相似之处
- ✅ 目标管理界面风格相似
- ✅ 依赖分析功能对应
- ✅ 命令执行机制类似
- ✅ 配置管理方式相近

### 2. 差异之处
- ✅ 使用 WebView 而非 Swing 界面
- ✅ 采用 VSCode 原生主题系统
- ✅ 支持实时刷新和动态更新
- ✅ 更好的跨平台兼容性

### 3. 优势
- ✅ 更现代的界面设计
- ✅ 更好的集成体验
- ✅ 更灵活的扩展性
- ✅ 更丰富的交互方式

## 下一步开发计划

### 1. 短期目标
- 完善 LSP 通信协议
- 添加更多 Maven 命令支持
- 优化依赖解析性能
- 添加配置持久化

### 2. 中期目标
- 实现依赖搜索功能
- 添加依赖更新建议
- 支持多模块项目
- 添加插件市场发布

### 3. 长期目标
- 支持团队协作功能
- 添加依赖安全扫描
- 实现智能依赖推荐
- 支持其他构建工具

---

**最后更新**：2024年12月
**当前状态**：前端核心功能实现完成，界面风格参考 MavenHelper，支持依赖分析和命令管理

# VSCode Maven Assistant 插件开发进度

## 已完成
- 前端 Webview 能展示依赖分析结果，并有刷新按钮
- TypeScript 前端与 Java LSP 后端通信正常，能调用自定义 analyzeDependencies 方法
- 后端日志通过 LSP logMessage 正确输出到 VSCode 输出面板
- 解决了 VSCode 发送 $/setTrace 时 LSP4J 抛出异常的问题，已实现空 setTrace 方法
- 依赖分析功能已能端到端展示（目前为模拟数据）
- 【第1步】后端已引入 Maven Resolver (Aether) 相关依赖，准备好进行依赖树解析开发

## 下一步计划
- 后端实现真实的 pom.xml 依赖解析与分析
- 前端优化依赖展示样式，支持依赖树、冲突、重复依赖等
- 增加更多交互功能，如依赖搜索、跳转、右键操作等

---
如需详细功能拆解或下步开发建议，请随时告知！

# VSCode Maven Assistant 项目进展与计划

## 当前项目进展

- 已完成 VSCode 插件前端与 Java 后端（LSP Server）的通信打通。
- 前端自定义 Webview（依赖分析器）可正常显示并与后端交互。
- 后端 SimpleLanguageServer 已实现 maven/analyzeDependencies 方法，能够解析 pom.xml 并返回依赖树（JSON）。
- 依赖项（Maven Resolver 相关）已统一版本，解决了 SPI 加载和依赖冲突问题。
- LspServerMain 作为主入口，负责启动 LSP4J Launcher 并注入 LanguageClient。
- 前后端消息传递、日志输出、异常处理等基础功能已打通。

## 后续要做的事（下一步建议）

1. **依赖树可视化**：前端用树形结构展示依赖关系，提升可读性和交互体验。
2. **依赖分析健壮性提升**：后端完善对多模块、Profile、依赖冲突、可选/排除依赖等复杂场景的支持。
3. **前端交互优化**：支持点击依赖节点查看详情、错误提示美化、刷新/导出等功能。
4. **异常与边界处理**：后端对 pom 解析失败、依赖缺失等情况给出更友好的提示。
5. **测试与文档**：补充单元测试、集成测试和开发/用户文档，便于维护和推广。
6. **功能扩展**：如依赖冲突分析、依赖升级建议、与 VSCode 其他功能集成等。

---

# 后端依赖树实现重大突破

## 最新进展（2024年12月）

### 1. 完整依赖树建立 ✅
- **问题解决**：修复了 `rootNode.getChildren()` 为空的问题，现在能正确获取 pom.xml 中的所有直接依赖。
- **核心改进**：将 `CollectRequest.setRoot(Dependency)` 改为 `setRootArtifact(DefaultArtifact)` + `addDependency()` 的方式，确保所有 `<dependencies>` 里的依赖都被正确收集。
- **技术细节**：
  ```java
  // 设置项目自身为根节点
  collectRequest.setRootArtifact(new DefaultArtifact(coords));
  // 把所有 pom.xml 里的依赖都加进去
  for (org.apache.maven.model.Dependency dep : model.getDependencies()) {
      collectRequest.addDependency(new Dependency(
          new DefaultArtifact(dep.getGroupId(), dep.getArtifactId(), dep.getClassifier(), dep.getType(), dep.getVersion()),
          dep.getScope()
      ));
  }
  ```

### 2. 递归树形结构生成 ✅
- **实现功能**：`buildDependencyTree()` 方法递归生成完整的嵌套树形结构，每个依赖节点包含：
  - `groupId`、`artifactId`、`version`、`scope`、`depth`
  - `children` 数组（递归子依赖）
- **数据结构**：返回嵌套 JSON，支持无限层级的依赖关系展示。
- **与 Maven 命令行一致**：依赖树结构现在与 `mvn dependency:tree` 输出完全一致。

### 3. 测试验证与调试 ✅
- **增强测试**：`DependencyTreeTest` 新增 `testAnalyzeDependenciesTreeStructure()` 方法，递归打印完整依赖树。
- **调试功能**：每个依赖节点显示 children 数量，便于直观观察依赖层级。
- **测试输出示例**：
  ```
  nd.mavenassistant:java-backend:1.0-SNAPSHOT  [children: 8]
    org.apache.maven.resolver:maven-resolver-connector-basic:2.0.9  [children: 4]
      org.apache.maven.resolver:maven-resolver-api:2.0.9  [children: 0]
      org.apache.maven.resolver:maven-resolver-spi:2.0.9  [children: 1]
        com.google.code.gson:gson:2.13.1  [children: 1]
  ```

### 4. LSP 通信优化 ✅
- **参数修复**：前端 `lspClient.ts` 中 `analyzeDependencies()` 方法参数由 `{}` 改为 `null`，解决 LSP 消息解析失败问题。
- **错误处理**：完善了前后端通信的异常处理和降级机制。

### 5. 开发环境问题解决 ✅
- **Java 版本兼容**：解决了 Java 版本设置问题，确保 LSP 后端能正常启动。
- **jar 包管理**：完善了 fat jar 的打包和部署流程。

## 技术突破总结

### 1. 依赖收集机制
- **问题根源**：Aether 的 `CollectRequest.setRoot()` 只会以指定依赖为根，不会自动收集 pom.xml 里的所有 dependencies。
- **解决方案**：使用 `setRootArtifact()` + `addDependency()` 组合，确保所有声明的依赖都被收集到依赖树中。

### 2. 递归结构设计
- **树形 JSON**：每个节点包含基本信息和 children 数组，支持无限层级嵌套。
- **深度控制**：通过 `depth` 字段记录依赖层级，便于前端展示和调试。
- **完整遍历**：递归遍历所有依赖节点，确保不遗漏任何传递依赖。

### 3. 测试与验证
- **单元测试**：JUnit 测试验证依赖树解析的正确性。
- **调试工具**：增强的递归打印功能，便于开发时观察依赖结构。
- **对比验证**：与 `mvn dependency:tree` 输出对比，确保解析结果准确。

## 下一步计划

### 1. 前端树形展示优化
- 将后端返回的嵌套 JSON 在前端渲染为可展开/折叠的树形视图
- 支持依赖节点点击、详情查看、搜索过滤等功能
- 美化依赖冲突、版本差异等特殊情况的显示

### 2. 依赖分析功能扩展
- 支持多模块项目的依赖分析
- 添加依赖冲突检测和解决建议
- 实现依赖安全扫描和漏洞检测
- 支持 Profile、optional、exclusions 等高级特性

### 3. 性能优化
- 优化大型项目的依赖解析性能
- 添加依赖缓存机制
- 实现增量更新和实时刷新

### 4. 用户体验提升
- 添加依赖搜索和过滤功能
- 支持依赖图可视化
- 实现依赖升级建议和自动修复
- 添加团队协作和依赖分享功能

---

**最后更新**：2024年12月
**当前状态**：后端完整依赖树建立完成，前后端通信正常，具备完整的依赖分析能力

> 本文档持续更新，记录每次关键进展和后续计划，便于团队协作和问题追溯。

---

# 2024年12月最新进展（前端依赖树展示）

## 1. 前端依赖树递归渲染功能完成 ✅
- 前端 Dependency Analyzer 已支持递归渲染后端返回的完整依赖树结构。
- 兼容后端返回的根节点为 `{ children: [...] }` 的格式，聚焦展示项目下所有依赖。
- 每个依赖节点显示 groupId、artifactId、version、scope、USED/DROPPED 状态和 children 数量。
- 缩进、颜色、字体等细节优化，展示效果与后端控制台一致。
- 支持刷新，错误信息友好提示。

## 2. 用户体验提升 ✅
- 依赖树结构一目了然，便于分析依赖关系和冲突。
- 只聚焦项目依赖，避免根节点冗余，界面更简洁。
- 前后端数据结构和展示逻辑完全打通。

**当前状态**：后端依赖树分析与前端递归展示已全部打通，具备完整的依赖可视化能力。

---

# VSCode Maven Assistant 项目进展（2024年7月）

## 最新进展

- ✅ 已实现 VSCode 插件 Webview 与 Vue 前端的无缝集成，前端页面可正常显示 Vue 组件（HelloWorld 页面已在 Webview 中成功渲染）。
- ✅ 路径替换、静态资源加载、Vite 构建产物适配 VSCode Webview 全部打通。
- ✅ 本地开发与插件调试流程顺畅，前后端集成链路已验证。
- ✅ 依赖树已在 Vue 页面中递归渲染，支持多层嵌套、展开/收起、刷新等交互。
- ✅ 页面采用左右分栏布局，分割线可拖拽调整宽度，体验与主流IDE一致。
- ✅ 已彻底去除所有白边，依赖树区域完全填充，无多余留白。
- ✅ package.json 支持一键前后端构建（npm run build:all），开发效率提升。

## 下一步计划

- [ ] 右侧分栏集成依赖详情展示，支持点击依赖节点后显示详细信息。
- [ ] 优化依赖树交互体验（如节点高亮、悬停、动画等）。
- [ ] 丰富前后端消息通信，支持更多操作（如依赖搜索、过滤、详情等）。

---

> 2024-07-12 更新：
> - 已实现依赖树分栏拖拽、无白边、前后端一键构建等优化。
> - 下一步将专注于依赖详情、交互体验和功能扩展。
