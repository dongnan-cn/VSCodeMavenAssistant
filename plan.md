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
