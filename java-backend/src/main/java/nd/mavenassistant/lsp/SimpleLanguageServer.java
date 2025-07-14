package nd.mavenassistant.lsp;

import com.google.gson.Gson;
import nd.mavenassistant.model.ArtifactConflictInfo;
import nd.mavenassistant.model.ArtifactGav;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.*;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession.CloseableSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.supplier.SessionBuilderSupplier;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.eclipse.aether.graph.Dependency;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;

/**
 * 最基础的 LanguageServer 实现
 * 目前所有方法均为空实现，后续可逐步扩展具体功能。
 */
public class SimpleLanguageServer implements LanguageServer {
    // LanguageClient 用于与 VSCode 前端通信，推送日志等
    private LanguageClient client;

    public static record IndentRecord(String dependencyIndent, String indentUnit) {
    }

    private final List<RemoteRepository> repos = Collections.singletonList(
            new RemoteRepository.Builder(
                    "central", "default", "https://repo.maven.apache.org/maven2/").build());

    // 提供一个方法让主入口注入 LanguageClient
    public void connect(LanguageClient client) {
        this.client = client;
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        // 通过 LSP 协议向 VSCode 输出面板推送初始化日志
        if (client != null) {
            client.logMessage(new MessageParams(MessageType.Info, "LSP Server 已初始化"));
        }
        // 返回空的初始化结果
        return CompletableFuture.completedFuture(new InitializeResult(new ServerCapabilities()));
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        // 进程退出时的处理
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return null;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return null;
    }

    /**
     * 依赖分析请求，参数为 pom.xml 路径，返回所有依赖（含传递依赖、冲突）JSON 字符串
     *
     * @param pomPath pom.xml 文件路径（可为 null，默认取当前工作目录下 pom.xml）
     */
    @JsonRequest("maven/analyzeDependencies")
    public CompletableFuture<String> analyzeDependencies(String pomPath) throws Exception {
        return CompletableFuture.supplyAsync(() -> {
            try (RepositorySystem system = new RepositorySystemSupplier().get();
                 CloseableSession session = new SessionBuilderSupplier(system)
                         .get()
                         .withLocalRepositoryBaseDirectories(
                                 new File(System.getProperty("user.home") + "/.m2/repository").toPath())
                         .setDependencySelector(new CustomScopeDependencySelector())
                         .setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, ConflictResolver.Verbosity.STANDARD)
                         .build()) {
                Model model = getModel(pomPath);
                List<Dependency> directDependencies = new ArrayList<>();
                List<Dependency> managedDependencies = new ArrayList<>();
                model.getDependencies().forEach(dep -> {
                    directDependencies.add(new Dependency(
                            new DefaultArtifact(dep.getGroupId(), dep.getArtifactId(), dep.getType(),
                                    dep.getClassifier(), dep.getVersion()),
                            dep.getScope()));
                });
                if (model.getDependencyManagement() != null) {
                    model.getDependencyManagement().getDependencies().forEach(dep -> {
                        managedDependencies.add(new Dependency(
                                new DefaultArtifact(dep.getGroupId(), dep.getArtifactId(), dep.getType(),
                                        dep.getClassifier(), dep.getVersion()),
                                dep.getScope()
                        ));
                    });
                }
                String coords = model.getGroupId() + ":" + model.getArtifactId() + ":" + model.getVersion();
                Artifact artifact = new DefaultArtifact(coords);
                CollectRequest collectRequest = getEffectiveCollectRequest(artifact, directDependencies,
                        managedDependencies, repos);
                DependencyNode rootNode = system.collectDependencies(session, collectRequest).getRoot();


                List<ArtifactGav> effectiveGavs = MavenClasspathFetcher.fetchGavList();
                Set<String> usedGAVSet = new HashSet<>();
                Set<String> usedGASet = new HashSet<>();
                Map<String, String> gavScopeMap = new HashMap<>();
                for (ArtifactGav gav : effectiveGavs) {
                    usedGAVSet.add(gav.getGroupId() + ":" + gav.getArtifactId() + ":" + gav.getVersion());
                    usedGASet.add(gav.getGroupId() + ":" + gav.getArtifactId());
                    if (gav.getScope() != null) {
                        gavScopeMap.put(gav.getGroupId() + ":" + gav.getArtifactId() + ":" + gav.getVersion(), gav.getScope());
                    }
                }
                // 打印所有 rootNode 的依赖以及子依赖，带缩进，便于调试
//                printDependencyTree(rootNode, 0);

                // 构建树形结构并返回JSON
                Map<String, Object> tree = buildDependencyTreeWithConflict(rootNode, usedGAVSet, usedGASet, gavScopeMap);
                return new Gson().toJson(tree);
            } catch (Exception e) {
                return "{\"error\":\"依赖解析异常: " + e.getMessage() + "\"}";
            }
        });
    }

    /**
     * 获取依赖的完整路径信息，用于定位到上一级依赖的pom文件
     *
     * @param request 包含groupId、artifactId、version等依赖信息的JSON字符串
     */
    @JsonRequest("maven/getDependencyPath")
    public CompletableFuture<String> getDependencyPath(String request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 解析请求参数
                Map<String, String> params = new Gson().fromJson(request, Map.class);
                String targetGroupId = params.get("groupId");
                String targetArtifactId = params.get("artifactId");
                String targetVersion = params.get("version");

                // 验证必要参数
                if (targetGroupId == null || targetArtifactId == null) {
                    return "{\"success\":false,\"error\":\"缺少必要参数：groupId、artifactId\"}";
                }

                // 获取当前项目的pom.xml路径
                String pomPath = (StringUtils.isBlank(params.get("pomPath")))
                        ? new File("pom.xml").getAbsolutePath()
                        : params.get("pomPath");

                // 解析依赖树，找到目标依赖的完整路径
                Model model = getModel(pomPath);
                String coords = model.getGroupId() + ":" + model.getArtifactId() + ":" + model.getVersion();
                Artifact artifact = new DefaultArtifact(coords);

                try (RepositorySystem system = new RepositorySystemSupplier().get();
                     CloseableSession session = new SessionBuilderSupplier(system)
                             .get()
                             .withLocalRepositoryBaseDirectories(
                                     new File(System.getProperty("user.home") + "/.m2/repository").toPath())
                             .setDependencySelector(new CustomScopeDependencySelector())
                             .setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, ConflictResolver.Verbosity.STANDARD)
                             .build()) {

                    CollectRequest collectRequest = getEffectiveCollectRequest(artifact,
                            getDirectDependencies(model), getManagedDependencies(model), repos);
                    DependencyNode rootNode = system.collectDependencies(session, collectRequest).getRoot();

                    // 查找目标依赖的路径
                    DependencyPathInfo pathInfo = findDependencyPath(rootNode, targetGroupId, targetArtifactId, targetVersion);

                    if (pathInfo != null) {
                        return new Gson().toJson(pathInfo);
                    } else {
                        return "{\"success\":false,\"error\":\"未找到依赖路径\"}";
                    }
                }

            } catch (Exception e) {
                return "{\"success\":false,\"error\":\"获取依赖路径失败: " + e.getMessage() + "\"}";
            }
        });
    }

    /**
     * 插入 exclusion 到指定的依赖中
     * 步骤 6：使用新的 Maven Model API 实现替换原有的字符串处理逻辑
     */
    @JsonRequest("maven/insertExclusion")
    public CompletableFuture<String> insertExclusion(String request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 解析参数
                Map<String, Object> params = new Gson().fromJson(request, Map.class);
                String pomPath = (String) params.getOrDefault("pomPath", "pom.xml");
                Map<String, String> rootDep = (Map<String, String>) params.get("rootDependency");
                Map<String, String> targetDep = (Map<String, String>) params.get("targetDependency");
                if (rootDep == null || targetDep == null) {
                    return "{\"success\":false,\"error\":\"缺少依赖参数\"}";
                }

                // 调用新的 DOM 解析器实现（保留注释）
                return insertExclusionWithDOM(pomPath, rootDep, targetDep);
            } catch (Exception e) {
                return "{\"success\":false,\"error\":\"插入exclusion失败: " + e.getMessage() + "\"}";
            }
        });
    }


    /**
     * 使用 DOM 解析器插入 exclusion，保留注释（推荐方案）
     * 使用 DOM 解析器读取和修改 pom.xml，可以完全保留原始格式和注释
     */
    private String insertExclusionWithDOM(String pomPath, Map<String, String> rootDep, Map<String, String> targetDep) {
        try {
            if (client != null) {
                client.logMessage(new MessageParams(MessageType.Info, "Start processing exclusion with DOM parser"));
            }

            // 解析参数
            String targetGroupId = rootDep.get("groupId");
            String targetArtifactId = rootDep.get("artifactId");
            String targetVersion = rootDep.get("version");
            String exclusionGroupId = targetDep.get("groupId");
            String exclusionArtifactId = targetDep.get("artifactId");

            if (client != null) {
                client.logMessage(new MessageParams(MessageType.Info, "Target dependency: " + targetGroupId + ":" + targetArtifactId + ":" + targetVersion));
                client.logMessage(new MessageParams(MessageType.Info, "Exclusion to add: " + exclusionGroupId + ":" + exclusionArtifactId));
            }

            // 解析 Maven 变量，获取解析后的依赖版本映射
            Map<String, String> resolvedDependencies = resolveMavenVariables(pomPath);

            // 使用 DOM 解析器读取 pom.xml
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new File(pomPath));
            doc.getDocumentElement().normalize();

            // 查找目标依赖
            NodeList dependencies = doc.getElementsByTagName("dependency");
            Element targetDependencyElement = null;

            for (int i = 0; i < dependencies.getLength(); i++) {
                Element depElement = (Element) dependencies.item(i);
                String groupId = getElementTextContent(depElement, "groupId");
                String artifactId = getElementTextContent(depElement, "artifactId");
                String version = getElementTextContent(depElement, "version");

                // 如果 version 包含变量，使用解析后的值
                if (version != null && version.contains("${")) {
                    String key = groupId + ":" + artifactId;
                    String resolvedVersion = resolvedDependencies.get(key);
                    if (resolvedVersion != null) {
                        version = resolvedVersion;
                        if (client != null) {
                            client.logMessage(new MessageParams(MessageType.Info, "Resolved version for " + key + ": " + version));
                        }
                    }
                }

                if (client != null) {
                    client.logMessage(new MessageParams(MessageType.Info, "Checking dependency: " + groupId + ":" + artifactId + ":" + version));
                }

                boolean groupIdMatch = groupId.equals(targetGroupId);
                boolean artifactIdMatch = artifactId.equals(targetArtifactId);
                boolean versionMatch =
                        (version == null && (targetVersion == null || targetVersion.isEmpty())) ||
                                (version != null && version.equals(targetVersion));

                if (groupIdMatch && artifactIdMatch && versionMatch) {
                    targetDependencyElement = depElement;
                    if (client != null) {
                        client.logMessage(new MessageParams(MessageType.Info, "Found target dependency!"));
                    }
                    break;
                }
            }

            if (targetDependencyElement == null) {
                if (client != null) {
                    client.logMessage(new MessageParams(MessageType.Error, "Root dependency not found"));
                }
                return "{\"success\":false,\"error\":\"Root dependency not found: " + targetGroupId + ":" + targetArtifactId + "\"}";
            }

            fillExclude(exclusionGroupId, exclusionArtifactId, doc, targetDependencyElement);

            // 写回文件
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(pomPath));
            transformer.transform(source, result);

            if (client != null) {
                client.logMessage(new MessageParams(MessageType.Info, "File written successfully"));
            }

            // 返回成功信息，包含 highlightLine 字段以兼容旧接口
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Exclusion added successfully");
            response.put("highlightLine", 0); // 添加 highlightLine 字段以兼容旧接口
            return new Gson().toJson(response);

        } catch (Exception e) {
            if (client != null) {
                client.logMessage(new MessageParams(MessageType.Error, "DOM parser failed: " + e.getMessage()));
            }
            return "{\"success\":false,\"error\":\"DOM parser failed: " + e.getMessage() + "\"}";
        }
    }

    private void fillExclude(String exclusionGroupId, String exclusionArtifactId, Document doc,
                             Element targetDependencyElement) {
        // 检查是否已有 exclusions 元素
        NodeList exclusionsList = targetDependencyElement.getElementsByTagName("exclusions");
        IndentRecord dependencyIndent = getIndent(targetDependencyElement);
        Element exclusionsElement;
        boolean exclusionsExist = false;
        if (exclusionsList.getLength() > 0) {
            exclusionsExist = true;
            // 已有 exclusions 元素
            exclusionsElement = (Element) exclusionsList.item(0);
            if (client != null) {
                client.logMessage(new MessageParams(MessageType.Info, "Found existing <exclusions> element"));
            }
        } else {
            exclusionsExist = false;
            // 创建新的 exclusions 元素
            exclusionsElement = doc.createElement("exclusions");
            targetDependencyElement.appendChild(doc.createTextNode(dependencyIndent.indentUnit()));
            targetDependencyElement.appendChild(exclusionsElement);
            targetDependencyElement.appendChild(doc.createTextNode(dependencyIndent.dependencyIndent()));
            if (client != null) {
                client.logMessage(new MessageParams(MessageType.Info, "Created new <exclusions> element"));
            }
        }

        // 创建 exclusion 元素
        Element exclusionElement = doc.createElement("exclusion");

        Element groupIdElement = doc.createElement("groupId");
        groupIdElement.setTextContent(exclusionGroupId);
        exclusionElement.appendChild(doc.createTextNode(getLeveledIndent(dependencyIndent, 3)));
        exclusionElement.appendChild(groupIdElement);
        exclusionElement.appendChild(doc.createTextNode(getLeveledIndent(dependencyIndent, 3)));

        Element artifactIdElement = doc.createElement("artifactId");
        artifactIdElement.setTextContent(exclusionArtifactId);
        exclusionElement.appendChild(artifactIdElement);
        exclusionElement.appendChild(doc.createTextNode(getLeveledIndent(dependencyIndent, 2)));

        if(!exclusionsExist){
            exclusionsElement.appendChild(doc.createTextNode(getLeveledIndent(dependencyIndent, 2)));
        } else {
            exclusionsElement.appendChild(doc.createTextNode(dependencyIndent.indentUnit()));
        }
        exclusionsElement.appendChild(exclusionElement);
        exclusionsElement.appendChild(doc.createTextNode(getLeveledIndent(dependencyIndent, 1)));


        if (client != null) {
            client.logMessage(new MessageParams(MessageType.Info, "Exclusion element created successfully"));
        }
    }

    private static String getLeveledIndent(IndentRecord dependencyIndent, int level) {
        return dependencyIndent.dependencyIndent() + dependencyIndent.indentUnit().repeat(level);
    }

    /**
     * 解析 Maven 变量，获取解析后的依赖版本映射
     * 使用 Maven Model API 解析 pom.xml 中的变量，返回 groupId:artifactId -> resolvedVersion 的映射
     *
     * @param pomPath pom.xml 文件路径
     * @return 解析后的依赖版本映射，key 为 groupId:artifactId，value 为解析后的版本
     */
    private Map<String, String> resolveMavenVariables(String pomPath) {
        try {
            // 使用 Maven Model API 解析变量，获取解析后的依赖列表
            Model resolvedModel = getModel(pomPath);
            Map<String, String> resolvedDependencies = new HashMap<>();
            for (org.apache.maven.model.Dependency dep : resolvedModel.getDependencies()) {
                String key = dep.getGroupId() + ":" + dep.getArtifactId();
                resolvedDependencies.put(key, dep.getVersion());
            }
            if (client != null) {
                client.logMessage(new MessageParams(MessageType.Info, "Resolved " + resolvedDependencies.size() + " dependencies with variables"));
            }
            return resolvedDependencies;
        } catch (Exception e) {
            if (client != null) {
                client.logMessage(new MessageParams(MessageType.Error, "Failed to resolve Maven variables: " + e.getMessage()));
            }
            return new HashMap<>();
        }
    }

    /**
     * 获取元素的文本内容
     */
    private String getElementTextContent(Element parent, String tagName) {
        NodeList elements = parent.getElementsByTagName(tagName);
        if (elements.getLength() > 0) {
            return elements.item(0).getTextContent();
        }
        return null;
    }


    /**
     * 依赖路径信息
     */
    private static class DependencyPathInfo {
        public boolean success = true;
        public String parentPomPath; // 上一级依赖的pom文件路径
        public String parentGroupId; // 上一级依赖的groupId
        public String parentArtifactId; // 上一级依赖的artifactId
        public String parentVersion; // 上一级依赖的version
        public int lineNumber; // 依赖在pom文件中的行号
        public int artifactIdStart; // artifactId在行中的起始位置
        public int artifactIdEnd; // artifactId在行中的结束位置
        public String error;
    }

    /**
     * 查找依赖的完整路径
     */
    private DependencyPathInfo findDependencyPath(DependencyNode rootNode, String targetGroupId,
                                                  String targetArtifactId, String targetVersion) {
        List<DependencyNode> path = new ArrayList<>();
        if (findDependencyPathRecursive(rootNode, targetGroupId, targetArtifactId, targetVersion, path)) {
            // 找到路径，获取上一级依赖的信息
            if (path.size() >= 2) {
                DependencyNode parentNode = path.get(path.size() - 2); // 上一级依赖
                DependencyNode targetNode = path.get(path.size() - 1); // 目标依赖

                DependencyPathInfo pathInfo = new DependencyPathInfo();
                Artifact parentArtifact = parentNode.getArtifact();
                Artifact targetArtifact = targetNode.getArtifact();

                // 构建上一级依赖的pom文件路径
                String parentPomPath = buildPomPath(parentArtifact);
                pathInfo.parentPomPath = parentPomPath;
                pathInfo.parentGroupId = parentArtifact.getGroupId();
                pathInfo.parentArtifactId = parentArtifact.getArtifactId();
                pathInfo.parentVersion = parentArtifact.getVersion();

                // 解析pom文件，找到目标依赖的位置信息
                try {
                    Map<String, Object> positionInfo = parseDependencyPosition(parentPomPath,
                            targetArtifact.getGroupId(), targetArtifact.getArtifactId());
                    pathInfo.lineNumber = (Integer) positionInfo.get("lineNumber");
                    pathInfo.artifactIdStart = (Integer) positionInfo.get("artifactIdStart");
                    pathInfo.artifactIdEnd = (Integer) positionInfo.get("artifactIdEnd");
                } catch (Exception e) {
                    pathInfo.error = "解析pom文件位置失败: " + e.getMessage();
                }

                return pathInfo;
            }
        }
        return null;
    }

    /**
     * 递归查找依赖路径
     */
    private boolean findDependencyPathRecursive(DependencyNode node, String targetGroupId,
                                                String targetArtifactId, String targetVersion, List<DependencyNode> path) {
        if (node == null) return false;

        path.add(node);

        // 检查当前节点是否为目标依赖
        Artifact artifact = node.getArtifact();
        if (artifact != null &&
                artifact.getGroupId().equals(targetGroupId) &&
                artifact.getArtifactId().equals(targetArtifactId) &&
                (targetVersion == null || artifact.getVersion().equals(targetVersion))) {
            return true;
        }

        // 递归查找子节点
        for (DependencyNode child : node.getChildren()) {
            if (findDependencyPathRecursive(child, targetGroupId, targetArtifactId, targetVersion, path)) {
                return true;
            }
        }

        // 回溯
        path.remove(path.size() - 1);
        return false;
    }

    /**
     * 构建pom文件路径
     */
    private String buildPomPath(Artifact artifact) {
        String groupId = artifact.getGroupId().replace('.', '/');
        String artifactId = artifact.getArtifactId();
        String version = artifact.getVersion();
        String home = System.getProperty("user.home");
        return home + "/.m2/repository/" + groupId + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + ".pom";
    }

    /**
     * 解析pom文件中依赖的位置信息
     */
    private Map<String, Object> parseDependencyPosition(String pomPath, String groupId, String artifactId) throws Exception {
        Map<String, Object> result = new HashMap<>();

        // 读取pom文件内容
        List<String> lines = java.nio.file.Files.readAllLines(java.nio.file.Paths.get(pomPath));

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            // 查找dependency标签
            if (line.trim().startsWith("<dependency>")) {
                // 查找groupId和artifactId
                boolean foundGroupId = false;
                boolean foundArtifactId = false;
                String currentGroupId = null;
                String currentArtifactId = null;

                // 向前查找groupId和artifactId
                for (int j = i; j < lines.size(); j++) {
                    String currentLine = lines.get(j);
                    if (currentLine.trim().startsWith("</dependency>")) {
                        break;
                    }

                    if (currentLine.trim().startsWith("<groupId>")) {
                        currentGroupId = extractTagContent(currentLine);
                        foundGroupId = true;
                    } else if (currentLine.trim().startsWith("<artifactId>")) {
                        currentArtifactId = extractTagContent(currentLine);
                        foundArtifactId = true;

                        // 检查是否匹配目标依赖
                        if (foundGroupId && foundArtifactId &&
                                currentGroupId.equals(groupId) && currentArtifactId.equals(artifactId)) {
                            // 找到目标依赖，记录位置信息
                            result.put("lineNumber", j + 1); // 行号从1开始

                            // 计算artifactId在行中的位置
                            int start = currentLine.indexOf("<artifactId>") + "<artifactId>".length();
                            int end = currentLine.indexOf("</artifactId>");
                            result.put("artifactIdStart", start);
                            result.put("artifactIdEnd", end);

                            return result;
                        }
                    }
                }
            }
        }

        throw new Exception("未找到目标依赖: " + groupId + ":" + artifactId);
    }

    /**
     * 提取XML标签内容
     */
    private String extractTagContent(String line) {
        int start = line.indexOf('>') + 1;
        int end = line.indexOf("</");
        if (start > 0 && end > start) {
            return line.substring(start, end);
        }
        return "";
    }

    /**
     * 获取直接依赖列表
     */
    private List<Dependency> getDirectDependencies(Model model) {
        List<Dependency> dependencies = new ArrayList<>();
        model.getDependencies().forEach(dep -> {
            dependencies.add(new Dependency(
                    new DefaultArtifact(dep.getGroupId(), dep.getArtifactId(), dep.getType(),
                            dep.getClassifier(), dep.getVersion()),
                    dep.getScope()));
        });
        return dependencies;
    }

    /**
     * 获取管理依赖列表
     */
    private List<Dependency> getManagedDependencies(Model model) {
        List<Dependency> dependencies = new ArrayList<>();
        if (model.getDependencyManagement() != null) {
            model.getDependencyManagement().getDependencies().forEach(dep -> {
                dependencies.add(new Dependency(
                        new DefaultArtifact(dep.getGroupId(), dep.getArtifactId(), dep.getType(),
                                dep.getClassifier(), dep.getVersion()),
                        dep.getScope()));
            });
        }
        return dependencies;
    }

    public Set<String> collectAllArtifacts(DependencyNode rootNode) {
        Set<String> artifacts = new HashSet<>();
        collectArtifactsRecursive(rootNode, artifacts);
        return artifacts;
    }

    private void collectArtifactsRecursive(DependencyNode node, Set<String> artifacts) {
        Artifact artifact = node.getArtifact();
        if (artifact != null) {
            // 用 groupId:artifactId:version 作为唯一标识
            String key = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
            artifacts.add(key);
        }
        for (DependencyNode child : node.getChildren()) {
            collectArtifactsRecursive(child, artifacts);
        }
    }

    private Model getModel(String pomPath) throws Exception {
        String pomFilePath = (StringUtils.isBlank(pomPath))
                ? new File("pom.xml").getAbsolutePath()
                : pomPath;
        File pomFile = new File(pomFilePath);
        if (!pomFile.exists()) {
            throw new FileNotFoundException("{\"error\":\"pom.xml does not exist: " + pomFilePath + "\"}");
        }

        DefaultModelBuildingRequest request = new DefaultModelBuildingRequest();
        request.setPomFile(pomFile);
        request.setModelResolver(null);
        request.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
        request.setSystemProperties(System.getProperties());

        DefaultModelBuilder modelBuilder = new DefaultModelBuilderFactory().newInstance();
        ModelBuildingResult result = modelBuilder.build(request);

        return result.getEffectiveModel();
    }


    private static CollectRequest getEffectiveCollectRequest(Artifact artifact, List<Dependency> directDependencies,
                                                             List<Dependency> managedDependencies, List<RemoteRepository> repos) {
        CollectRequest collectRequest = new CollectRequest();
        // 直接将 effectiveModel 的 GAV 作为根 Artifact
        // collectRequest.setRoot(new Dependency(
        // artifact, null));
        collectRequest.setDependencies(directDependencies);
        collectRequest.setManagedDependencies(managedDependencies);
        collectRequest.setRepositories(repos);

        return collectRequest;
    }

    /**
     * 递归构建依赖树结构，并标记冲突（droppedByConflict）
     *
     * @param node        当前Aether依赖节点
     * @param usedGAVSet  有效依赖GAV集合
     * @param usedGASet   有效依赖groupId:artifactId集合
     * @param gavScopeMap GAV到scope的映射
     * @return 树形依赖结构（Map表示）
     */
    private Map<String, Object> buildDependencyTreeWithConflict(DependencyNode node, Set<String> usedGAVSet, Set<String> usedGASet, Map<String, String> gavScopeMap) {
        Map<String, Object> depInfo = new LinkedHashMap<>();
        Artifact artifact = node.getArtifact();
        if (artifact != null) {
            String groupId = artifact.getGroupId();
            String artifactId = artifact.getArtifactId();
            String version = artifact.getVersion();
            String key = groupId + ":" + artifactId + ":" + version;
            String ga = groupId + ":" + artifactId;
            // 如果groupId:artifactId不在usedGASet中，直接跳过该节点
            if (!usedGASet.contains(ga)) {
                return null;
            }
            depInfo.put("groupId", groupId);
            depInfo.put("artifactId", artifactId);
            depInfo.put("version", version);
            // 优先用gavScopeMap
            String scope = gavScopeMap.getOrDefault(key, node.getDependency() != null ? node.getDependency().getScope() : "compile");
            depInfo.put("scope", scope);
            boolean dropped = !usedGAVSet.contains(key);
            depInfo.put("droppedByConflict", dropped);
        }
        // 递归 children
        List<Map<String, Object>> children = new ArrayList<>();
        for (DependencyNode child : node.getChildren()) {
            Map<String, Object> childNode = buildDependencyTreeWithConflict(child, usedGAVSet, usedGASet, gavScopeMap);
            if (childNode != null) {
                children.add(childNode);
            }
        }
        if (!children.isEmpty()) {
            depInfo.put("children", children);
        }
        return depInfo;
    }

    /**
     * 根据所有artifact GAV字符串和实际用到的GAV对象列表，生成包含gav、scope、是否因冲突被放弃的json数组
     */
    public static String buildArtifactConflictJson(Set<String> allArtifacts, List<ArtifactGav> effectiveGavs,
                                                   Map<String, String> gavToScope) {
        Set<String> usedGASet = new HashSet<>();
        Set<String> usedGAVSet = new HashSet<>();
        // 建立GAV到scope的映射，便于后续查找
        Map<String, String> gavScopeMap = new HashMap<>();
        for (ArtifactGav gav : effectiveGavs) {
            usedGASet.add(gav.getGroupId() + ":" + gav.getArtifactId());
            usedGAVSet.add(gav.getGroupId() + ":" + gav.getArtifactId() + ":" + gav.getVersion());
            // 以 groupId:artifactId:version 作为key
            String key = gav.getGroupId() + ":" + gav.getArtifactId() + ":" + gav.getVersion();
            if (gav.getScope() != null) {
                gavScopeMap.put(key, gav.getScope());
            }
        }
        List<ArtifactConflictInfo> result = new ArrayList<>();
        for (String gav : allArtifacts) {
            String[] parts = gav.split(":");
            if (parts.length < 3)
                continue;
            String ga = parts[0] + ":" + parts[1];
            if (!usedGASet.contains(ga)) {
                // 这个 groupId:artifactId 根本没被采用，直接跳过
                continue;
            }

            // 优先使用effectiveGavs中的scope，否则用gavToScope，否则默认compile
            String key = parts[0] + ":" + parts[1] + ":" + parts[2];
            String scope = gavScopeMap.getOrDefault(key, gavToScope.getOrDefault(gav, "compile"));

            boolean dropped = !usedGAVSet.contains(gav);
            if (parts.length >= 3) {
                result.add(new ArtifactConflictInfo(parts[0], parts[1], parts[2], scope, dropped));
            }
        }
        return new Gson().toJson(result);
    }

    // 实现 setTrace 方法，防止 VSCode 发送 $/setTrace 时抛出异常
    @Override
    public void setTrace(SetTraceParams params) {
        // 这里可以根据 params.getValue() 设置日志级别，目前为空实现
    }

    /**
     * 获取<dependency>标签的缩进量和父子标签缩进量之差（单位缩进）
     *
     * @param targetDependencyElement 依赖元素
     * @return IndentRecord对象，包含<dependency>标签的缩进和单位缩进
     */
    private static IndentRecord getIndent(Element targetDependencyElement) {
        String dependencyIndent = "\n";
        String parentIndent = "\n";
        if (targetDependencyElement != null) {
            // 获取<dependency>标签的缩进
            Node depPrev = targetDependencyElement.getPreviousSibling();
            if (depPrev != null && depPrev.getNodeType() == Node.TEXT_NODE) {
                String text = depPrev.getTextContent();
                int lastNewline = text.lastIndexOf('\n');
                if (lastNewline != -1) {
                    dependencyIndent = text.substring(lastNewline);
                } else {
                    dependencyIndent = text;
                }
            }
            // 获取父节点的缩进
            Node parent = targetDependencyElement.getParentNode();
            if (parent != null) {
                Node parentPrev = parent.getPreviousSibling();
                if (parentPrev != null && parentPrev.getNodeType() == Node.TEXT_NODE) {
                    String text = parentPrev.getTextContent();
                    int lastNewline = text.lastIndexOf('\n');
                    if (lastNewline != -1) {
                        parentIndent = text.substring(lastNewline);
                    } else {
                        parentIndent = text;
                    }
                }
            }
        }
        // 计算单位缩进（父子标签缩进差值）
        String indentUnit;
        if (dependencyIndent.length() > parentIndent.length()) {
            indentUnit = dependencyIndent.substring(parentIndent.length());
        } else {
            indentUnit = "  "; // 默认2空格
        }
        return new IndentRecord(dependencyIndent, indentUnit);
    }

    // 打印所有 rootNode 的依赖以及子依赖，带缩进，便于调试
    private void printDependencyTree(DependencyNode node, int level) {
        // 构建缩进字符串
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < level; i++) {
            indent.append("  ");
        }
        Artifact artifact = node.getArtifact();
        if (artifact != null) {
            // 详细打印 groupId:artifactId:version:scope
            String scope = node.getDependency() != null ? node.getDependency().getScope() : "";
            System.out.println(indent + artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion() + (scope != null && !scope.isEmpty() ? " [" + scope + "]" : ""));
        } else {
            // 根节点可能没有artifact
            System.out.println(indent + "(no artifact)");
        }
        // 递归打印子依赖
        for (DependencyNode child : node.getChildren()) {
            printDependencyTree(child, level + 1);
        }
    }
}