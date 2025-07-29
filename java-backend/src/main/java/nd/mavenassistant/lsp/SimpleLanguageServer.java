package nd.mavenassistant.lsp;

import com.google.gson.Gson;
import nd.mavenassistant.cache.DependencyCache;
import nd.mavenassistant.model.ArtifactConflictInfo;
import nd.mavenassistant.model.ArtifactGav;
import nd.mavenassistant.utils.MavenModelUtils;
import nd.mavenassistant.utils.PomXmlUtils;
import org.apache.maven.model.Model;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession.CloseableSession;
import org.eclipse.aether.artifact.Artifact;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
    
    // Maven本地仓库路径常量
    private static final String USER_HOME = System.getProperty("user.home");
    private static final String MAVEN_LOCAL_REPO_PATH = USER_HOME + "/.m2/repository";
    private static final File MAVEN_LOCAL_REPO_DIR = new File(MAVEN_LOCAL_REPO_PATH);
    
    // 缓存管理器
    private final DependencyCache cache = new DependencyCache();
    
    // 线程池用于并行计算jar文件大小
    private final ExecutorService jarSizeExecutor = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
    




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
            client.logMessage(new MessageParams(MessageType.Info, "LSP Server initialized"));
        }
        // 返回空的初始化结果
        return CompletableFuture.completedFuture(new InitializeResult(new ServerCapabilities()));
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        // 关闭线程池
        jarSizeExecutor.shutdown();
        try {
            // 等待正在执行的任务完成，最多等待10秒
            if (!jarSizeExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                jarSizeExecutor.shutdownNow(); // 强制关闭
            }
        } catch (InterruptedException e) {
            jarSizeExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        // 进程退出时清理缓存
        cache.clearCaches();
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
            try {
                // 获取实际的POM文件路径
                String actualPomPath = (pomPath == null || pomPath.trim().isEmpty()) ? "pom.xml" : pomPath;
                File pomFile = new File(actualPomPath);
                if (!pomFile.exists()) {
                    return errorJson("POM file does not exist: " + actualPomPath);
                }
                
                // 检查缓存
                long pomLastModified = pomFile.lastModified();
                DependencyCache.CacheKey cacheKey = new DependencyCache.CacheKey(actualPomPath, pomLastModified);
                DependencyCache.CacheEntry cachedEntry = cache.getDependencyResult(cacheKey);
                if (cachedEntry != null && !cachedEntry.isExpired()) {
                    return cachedEntry.getResult();
                }
                
                // 清理过期缓存
                cache.cleanupExpiredCaches();
                
                try (RepositorySystem system = new RepositorySystemSupplier().get();
                     CloseableSession session = new SessionBuilderSupplier(system)
                             .get()
                             .withLocalRepositoryBaseDirectories(MAVEN_LOCAL_REPO_DIR.toPath())
                             .setDependencySelector(new CustomScopeDependencySelector())
                             .setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, ConflictResolver.Verbosity.STANDARD)
                             .build()) {
                    Model model = MavenModelUtils.getModel(actualPomPath);
                    List<Dependency> directDependencies = MavenModelUtils.getDirectDependencies(model);
                    List<Dependency> managedDependencies = MavenModelUtils.getManagedDependencies(model);
                    Artifact artifact = MavenModelUtils.getArtifactFromModel(model);
                    CollectRequest collectRequest = MavenModelUtils.getEffectiveCollectRequest(artifact, directDependencies,
                            managedDependencies, repos);
                    DependencyNode rootNode = system.collectDependencies(session, collectRequest).getRoot();

                    // 预加载文件大小以减少I/O操作
                    preloadFileSizes(rootNode);
                    
                    List<ArtifactGav> effectiveGavs = MavenClasspathFetcher.fetchGavList(actualPomPath);
                    Set<String> usedGAVSet = new HashSet<>();
                    Set<String> usedGASet = new HashSet<>();
                    Map<String, String> gavScopeMap = new HashMap<>();
                    fillEffectiveGavSets(effectiveGavs, usedGAVSet, usedGASet, gavScopeMap);
                    // 构建 exclusion 映射表，保存原始的 exclusion 信息
                    Map<String, Set<String>> exclusionMap = MavenModelUtils.buildExclusionMap(model);
                    // 初始化GAV层级映射，用于层级优先处理
                    Map<String, GavLevelTuple> gavLevelMap = new HashMap<>();
                    // 构建树形结构并返回JSON，传入 exclusion 信息
                    Map<String, Object> tree = buildDependencyTreeWithConflict(rootNode, usedGAVSet, usedGASet, gavScopeMap, exclusionMap, gavLevelMap, 0);
                    String result = new Gson().toJson(tree);
                    
                    // 缓存结果
                    cache.putDependencyResult(cacheKey, result);
                    
                    return result;
                }
            } catch (Exception e) {
                return errorJson("Dependency analysis exception: " + e.getMessage());
            }
        });
    }

    /**
     * 填充有效依赖GAV集合和scope映射
     */
    private void fillEffectiveGavSets(List<ArtifactGav> effectiveGavs, Set<String> usedGAVSet, Set<String> usedGASet, Map<String, String> gavScopeMap) {
        for (ArtifactGav gav : effectiveGavs) {
            usedGAVSet.add(gav.getGroupId() + ":" + gav.getArtifactId() + ":" + gav.getVersion());
            usedGASet.add(gav.getGroupId() + ":" + gav.getArtifactId());
            if (gav.getScope() != null) {
                gavScopeMap.put(gav.getGroupId() + ":" + gav.getArtifactId() + ":" + gav.getVersion(), gav.getScope());
            }
        }
    }

    /**
     * 返回标准错误JSON
     */
    private String errorJson(String msg) {
        return "{\"error\":\"" + msg + "\"}";
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
                Map<String, String> params = parseDependencyPathParams(request);
                String targetGroupId = params.get("groupId");
                String targetArtifactId = params.get("artifactId");
                String targetVersion = params.get("version");
                if (!validateDependencyPathParams(targetGroupId, targetArtifactId)) {
                    return "{\"success\":false,\"error\":\"Missing required parameters: groupId, artifactId\"}";
                }
                String pomPath = getPomPathFromParams(params);
                Model model = MavenModelUtils.getModel(pomPath);
                Artifact artifact = MavenModelUtils.getArtifactFromModel(model);
                try (RepositorySystem system = new RepositorySystemSupplier().get();
                     CloseableSession session = new SessionBuilderSupplier(system)
                             .get()
                             .withLocalRepositoryBaseDirectories(
                                     new File(System.getProperty("user.home") + "/.m2/repository").toPath())
                             .setDependencySelector(new CustomScopeDependencySelector())
                             .setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, ConflictResolver.Verbosity.STANDARD)
                             .build()) {
                    CollectRequest collectRequest = MavenModelUtils.getEffectiveCollectRequest(artifact,
                            MavenModelUtils.getDirectDependencies(model), MavenModelUtils.getManagedDependencies(model), repos);
                    DependencyNode rootNode = system.collectDependencies(session, collectRequest).getRoot();
                    DependencyPathInfo pathInfo = findDependencyPath(rootNode, targetGroupId, targetArtifactId, targetVersion);
                    if (pathInfo != null) {
                        return new Gson().toJson(pathInfo);
                    } else {
                        return "{\"success\":false,\"error\":\"Dependency path not found\"}";
                    }
                }
            } catch (Exception e) {
                return "{\"success\":false,\"error\":\"Failed to get dependency path: " + e.getMessage() + "\"}";
            }
        });
    }

    /**
     * 解析依赖路径请求参数
     */
    private Map<String, String> parseDependencyPathParams(String request) {
        return new Gson().fromJson(request, Map.class);
    }

    /**
     * 校验依赖路径请求参数
     */
    private boolean validateDependencyPathParams(String groupId, String artifactId) {
        return groupId != null && artifactId != null;
    }

    /**
     * 从参数中获取pom.xml路径
     */
    private String getPomPathFromParams(Map<String, String> params) {
        return (StringUtils.isBlank(params.get("pomPath")))
                ? new File("pom.xml").getAbsolutePath()
                : params.get("pomPath");
    }



    /**
     * 插入 exclusion 到指定的依赖中
     * 步骤 6：使用新的 Maven Model API 实现替换原有的字符串处理逻辑
     */
    @JsonRequest("maven/insertExclusion")
    public CompletableFuture<String> insertExclusion(String request) {
        if (client != null) {
            client.logMessage(new MessageParams(MessageType.Info, "Insert exclusion request received: " + request));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 解析参数
                Map<String, Object> params = new Gson().fromJson(request, Map.class);
                String pomPath = (String) params.getOrDefault("pomPath", "pom.xml");
                Map<String, String> rootDep = (Map<String, String>) params.get("rootDependency");
                Map<String, String> targetDep = (Map<String, String>) params.get("targetDependency");
                if (rootDep == null || targetDep == null) {
                    return "{\"success\":false,\"error\":\"Missing dependency parameters\"}";
                }

                // 调用新的 DOM 解析器实现（保留注释）
                return insertExclusionWithDOM(pomPath, rootDep, targetDep);
            } catch (Exception e) {
                return "{\"success\":false,\"error\":\"Failed to insert exclusion: " + e.getMessage() + "\"}";
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
            Map<String, String> resolvedDependencies = PomXmlUtils.resolveMavenVariables(pomPath);

            // 使用 DOM 解析器读取 pom.xml
            Document doc = PomXmlUtils.parseDocument(pomPath);

            // 查找目标依赖
            Element targetDependencyElement = PomXmlUtils.findTargetDependencyElement(
                    doc, targetGroupId, targetArtifactId, targetVersion, resolvedDependencies);
            
            if (client != null) {
                client.logMessage(new MessageParams(MessageType.Info, "Resolved " + resolvedDependencies.size() + " dependencies with variables"));
                if (targetDependencyElement != null) {
                    client.logMessage(new MessageParams(MessageType.Info, "Found target dependency!"));
                }
            }

            if (targetDependencyElement == null) {
                if (client != null) {
                    client.logMessage(new MessageParams(MessageType.Error, "Root dependency not found"));
                }
                return "{\"success\":false,\"error\":\"Root dependency not found: " + targetGroupId + ":" + targetArtifactId + "\"}";
            }

            // 将检查和插入逻辑全部交给fillExclude
            boolean inserted = PomXmlUtils.fillExclude(exclusionGroupId, exclusionArtifactId, doc, targetDependencyElement);
            if (!inserted) {
                if (client != null) {
                    client.logMessage(new MessageParams(MessageType.Info, "Exclusion already exists: " + exclusionGroupId + ":" + exclusionArtifactId));
                }
                return buildExclusionResponse(true, "Exclusion already exists");
            }

            // 写回文件
            PomXmlUtils.writeDocument(doc, pomPath);

            if (client != null) {
                client.logMessage(new MessageParams(MessageType.Info, "File written successfully"));
            }

            return buildExclusionResponse(true, "Exclusion added successfully");

        } catch (Exception e) {
            if (client != null) {
                client.logMessage(new MessageParams(MessageType.Error, "DOM parser failed: " + e.getMessage()));
            }
            return "{\"success\":false,\"error\":\"DOM parser failed: " + e.getMessage() + "\"}";
        }
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
                    pathInfo.error = "Failed to parse POM file location: " + e.getMessage();
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
        return MAVEN_LOCAL_REPO_PATH + "/" + groupId + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + ".pom";
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

        throw new Exception("Target dependency not found: " + groupId + ":" + artifactId);
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
     * 将Maven依赖转换为Aether依赖
     * @param mavenDep Maven依赖对象
     * @return Aether依赖对象
     */

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


    /**
     * GAV层级信息和依赖对象的元组类
     */
    private static class GavLevelTuple {
        private int level;
        private Map<String, Object> depInfo;
        
        public GavLevelTuple(int level, Map<String, Object> depInfo) {
            this.level = level;
            this.depInfo = depInfo;
        }
        
        public int getLevel() { return level; }
        public void setLevel(int level) { this.level = level; }
        public Map<String, Object> getDepInfo() { return depInfo; }
        public void setDepInfo(Map<String, Object> depInfo) { this.depInfo = depInfo; }
    }

    /**
     * 递归构建依赖树，包含冲突信息，采用深度优先且层级优先策略
     *
     * @param node        当前Aether依赖节点
     * @param usedGAVSet  有效依赖GAV集合
     * @param usedGASet   有效依赖groupId:artifactId集合
     * @param gavScopeMap GAV到scope的映射
     * @param exclusionMap exclusion 映射表
     * @param gavLevelMap GAV到层级信息和依赖对象的映射，用于层级优先处理和子依赖转移
     * @param currentLevel 当前层级深度
     * @return 树形依赖结构（Map表示）
     */
    private Map<String, Object> buildDependencyTreeWithConflict(DependencyNode node, Set<String> usedGAVSet, Set<String> usedGASet, Map<String, String> gavScopeMap, Map<String, Set<String>> exclusionMap, Map<String, GavLevelTuple> gavLevelMap, int currentLevel) {
        // 在根节点（currentLevel == 0）时并行预加载所有jar文件大小
        if (currentLevel == 0) {
            preloadJarSizesParallel(node, usedGASet);
        }
        
        Artifact artifact = node.getArtifact();
        
        // 如果artifact为null（通常是根节点），直接处理子依赖
        if (artifact == null) {
            List<Map<String, Object>> children = new ArrayList<>();
            for (DependencyNode child : node.getChildren()) {
                Map<String, Object> childNode = buildDependencyTreeWithConflict(child, usedGAVSet, usedGASet, gavScopeMap, exclusionMap, gavLevelMap, currentLevel);
                if (childNode != null) {
                    children.add(childNode);
                }
            }
            
            // 如果有子依赖，返回一个包含children的Map，否则返回null
            if (!children.isEmpty()) {
                Map<String, Object> rootInfo = new LinkedHashMap<>();
                rootInfo.put("children", children);
                return rootInfo;
            }
            return null;
        }
        
        String groupId = artifact.getGroupId();
        String artifactId = artifact.getArtifactId();
        String version = artifact.getVersion();
        String key = groupId + ":" + artifactId + ":" + version;
        String ga = groupId + ":" + artifactId;
        
        // 如果groupId:artifactId不在usedGASet中，直接跳过该节点
        if (!usedGASet.contains(ga)) {
            return null;
        }
        
        // 检查GAV的层级优先级，实现层级优先策略和子依赖转移
        if (gavLevelMap.containsKey(key)) {
            // GAV已存在，检查层级
            GavLevelTuple existingTuple = gavLevelMap.get(key);
            int existingLevel = existingTuple.getLevel();
            
            if (currentLevel > existingLevel) {
                // 当前层级更深，返回不包含children的depInfo
                return buildDepInfoContent(groupId, artifactId, version, key, gavScopeMap, node, usedGAVSet, exclusionMap);
            } else if (currentLevel < existingLevel) {
                // 当前层级更浅，需要转移旧的子依赖到新位置
                Map<String, Object> oldDepInfo = existingTuple.getDepInfo();
                // 如果旧的depInfo有children，准备转移
                Object oldChildren = oldDepInfo.get("children");
                
                // 构建新的depInfo
                Map<String, Object> depInfo = buildDepInfoContent(groupId, artifactId, version, key, gavScopeMap, node, usedGAVSet, exclusionMap);
                
                // 转移旧的children到新的depInfo
                if (oldChildren != null) {
                    depInfo.put("children", oldChildren);
                }
                
                // 更新gavLevelMap中的层级和depInfo
                existingTuple.setLevel(currentLevel);
                existingTuple.setDepInfo(depInfo);
                
                return depInfo;
            } else {
                // 相同层级，返回不包含children的depInfo
                return buildDepInfoContent(groupId, artifactId, version, key, gavScopeMap, node, usedGAVSet, exclusionMap);
            }
        } else {
            // GAV首次出现，构建depInfo并记录到gavLevelMap
            Map<String, Object> depInfo = buildDepInfoContent(groupId, artifactId, version, key, gavScopeMap, node, usedGAVSet, exclusionMap);
            gavLevelMap.put(key, new GavLevelTuple(currentLevel, depInfo));
            
            // 递归处理子依赖
            List<Map<String, Object>> children = new ArrayList<>();
            boolean dropped = (Boolean) depInfo.getOrDefault("droppedByConflict", false);
            
            // 只有在依赖未被丢弃的情况下才处理子依赖
            if (!dropped) {
                for (DependencyNode child : node.getChildren()) {
                    Map<String, Object> childNode = buildDependencyTreeWithConflict(child, usedGAVSet, usedGASet, gavScopeMap, exclusionMap, gavLevelMap, currentLevel + 1);
                    if (childNode != null) {
                        children.add(childNode);
                    }
                }
            }
            
            if (!children.isEmpty()) {
                depInfo.put("children", children);
            }
            
            return depInfo;
        }
    }
    
    /**
     * 构建依赖信息内容的辅助方法
     */
    private Map<String, Object> buildDepInfoContent(String groupId, String artifactId, String version, String key, Map<String, String> gavScopeMap, DependencyNode node, Set<String> usedGAVSet, Map<String, Set<String>> exclusionMap) {
        Map<String, Object> depInfo = new LinkedHashMap<>();
        
        depInfo.put("groupId", groupId);
        depInfo.put("artifactId", artifactId);
        depInfo.put("version", version);
        
        // 优先用gavScopeMap
        String scope = gavScopeMap.getOrDefault(key, node.getDependency() != null ? node.getDependency().getScope() : "compile");
        depInfo.put("scope", scope);
        
        boolean dropped = !usedGAVSet.contains(key);
        depInfo.put("droppedByConflict", dropped);
        
        // 依赖jar大小，单位字节
        depInfo.put("size", getJarFileSize(node.getArtifact()));
        
        // 添加 exclusion 信息
        String depKey = groupId + ":" + artifactId;
        if (exclusionMap.containsKey(depKey)) {
            Set<String> exclusions = exclusionMap.get(depKey);
            List<Map<String, String>> exclusionList = new ArrayList<>();
            for (String exclusion : exclusions) {
                String[] parts = exclusion.split(":");
                if (parts.length >= 2) {
                    Map<String, String> exclusionInfo = new HashMap<>();
                    exclusionInfo.put("groupId", parts[0]);
                    exclusionInfo.put("artifactId", parts[1]);
                    exclusionList.add(exclusionInfo);
                }
            }
            if (!exclusionList.isEmpty()) {
                depInfo.put("exclusions", exclusionList);
            }
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

    /**
     * 构建Exclusion相关的标准JSON响应
     */
    private String buildExclusionResponse(boolean success, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", message);
        response.put("highlightLine", 0);
        return new Gson().toJson(response);
    }

    /**
     * 构建JAR文件路径的工具方法
     * @param groupId Maven groupId
     * @param artifactId Maven artifactId  
     * @param version Maven version
     * @return JAR文件的完整路径
     */
    private String buildJarPath(String groupId, String artifactId, String version) {
        String groupIdPath = groupId.replace('.', '/');
        return MAVEN_LOCAL_REPO_PATH + "/" + groupIdPath + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + ".jar";
    }
    
    private long getJarFileSize(Artifact artifact) {
        String jarPath = buildJarPath(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
        
        // 检查缓存
        Long cachedSize = cache.getFileSize(jarPath);
        if (cachedSize != null) {
            return cachedSize;
        }
        
        // 获取文件大小并缓存
        File jarFile = new File(jarPath);
        long size = jarFile.exists() ? jarFile.length() : 0L;
        cache.putFileSize(jarPath, size);
        return size;
    }
    
    /**
     * 预加载文件大小以减少I/O操作
     */
    private void preloadFileSizes(DependencyNode rootNode) {
        Set<String> jarPaths = collectJarPaths(rootNode);
        
        for (String jarPath : jarPaths) {
            if (cache.getFileSize(jarPath) == null) {
                File jarFile = new File(jarPath);
                long size = jarFile.exists() ? jarFile.length() : 0L;
                cache.putFileSize(jarPath, size);
            }
        }
    }
    
    /**
     * 并行预加载指定节点及其所有子节点的jar文件大小
     * @param node 要处理的依赖节点
     * @param usedGASet 有效的GA集合，用于过滤不需要的依赖
     */
    private void preloadJarSizesParallel(DependencyNode node, Set<String> usedGASet) {
        // 收集所有需要计算大小的artifact
        List<Artifact> artifactsToProcess = new ArrayList<>();
        collectArtifactsForSizeCalculation(node, usedGASet, artifactsToProcess);
        
        // 过滤出尚未缓存的artifact
        List<Artifact> uncachedArtifacts = artifactsToProcess.stream()
                .filter(artifact -> {
                    String jarPath = buildJarPath(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
                    return cache.getFileSize(jarPath) == null;
                })
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        
        if (uncachedArtifacts.isEmpty()) {
            return; // 所有文件大小都已缓存
        }
        
        // 并行计算文件大小
        List<Future<Void>> futures = new ArrayList<>();
        for (Artifact artifact : uncachedArtifacts) {
            Future<Void> future = jarSizeExecutor.submit(() -> {
                String jarPath = buildJarPath(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
                File jarFile = new File(jarPath);
                long size = jarFile.exists() ? jarFile.length() : 0L;
                cache.putFileSize(jarPath, size);
                return null;
            });
            futures.add(future);
        }
        
        // 等待所有任务完成
        for (Future<Void> future : futures) {
            try {
                future.get(5, TimeUnit.SECONDS); // 设置超时避免无限等待
            } catch (Exception e) {
                // 记录错误但不中断处理流程
                System.err.println("Error occurred while calculating jar file sizes in parallel: " + e.getMessage());
            }
        }
    }
    
    /**
     * 收集需要计算文件大小的artifact列表
     */
    private void collectArtifactsForSizeCalculation(DependencyNode node, Set<String> usedGASet, List<Artifact> artifacts) {
        if (node.getArtifact() != null) {
            String ga = node.getArtifact().getGroupId() + ":" + node.getArtifact().getArtifactId();
            if (usedGASet.contains(ga)) {
                artifacts.add(node.getArtifact());
            }
        }
        
        // 递归处理子节点
        for (DependencyNode child : node.getChildren()) {
            collectArtifactsForSizeCalculation(child, usedGASet, artifacts);
        }
    }
    
    /**
     * 原有的预加载方法保持不变，用于向后兼容
     */
    private void preloadFileSizesLegacy(DependencyNode rootNode) {
        Set<String> jarPaths = collectJarPaths(rootNode);
        
        for (String jarPath : jarPaths) {
            if (cache.getFileSize(jarPath) == null) {
                File jarFile = new File(jarPath);
                long size = jarFile.exists() ? jarFile.length() : 0L;
                cache.putFileSize(jarPath, size);
            }
        }
    }
    
    /**
     * 递归收集所有JAR文件路径
     */
    private Set<String> collectJarPaths(DependencyNode node) {
        Set<String> jarPaths = new HashSet<>();
        collectJarPathsRecursive(node, jarPaths);
        return jarPaths;
    }
    
    /**
     * 递归收集JAR文件路径的辅助方法
     */
    private void collectJarPathsRecursive(DependencyNode node, Set<String> jarPaths) {
        if (node.getArtifact() != null) {
            Artifact artifact = node.getArtifact();
            String jarPath = buildJarPath(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
            jarPaths.add(jarPath);
        }
        
        for (DependencyNode child : node.getChildren()) {
            collectJarPathsRecursive(child, jarPaths);
        }
    }
    
    /**
     * 清理缓存
     */

}