package nd.mavenassistant.lsp;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.OptionalDependencySelector;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.io.FileNotFoundException;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.SetTraceParams;
import com.google.gson.Gson;

import nd.mavenassistant.model.ArtifactGav;
import nd.mavenassistant.model.ArtifactConflictInfo;

import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuilder;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingResult;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession.CloseableSession;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.supplier.SessionBuilderSupplier;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.impl.scope.ScopeDependencySelector;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.artifact.DefaultArtifact;
import java.io.File;
import java.util.*;

/**
 * 最基础的 LanguageServer 实现
 * 目前所有方法均为空实现，后续可逐步扩展具体功能。
 */
public class SimpleLanguageServer implements LanguageServer {
    // LanguageClient 用于与 VSCode 前端通信，推送日志等
    private LanguageClient client;

    private List<RemoteRepository> repos = Collections.singletonList(
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
                            .build()) {
                Model model = getModel(pomPath);
                List<Dependency> directDependencies = new ArrayList<>();
                List<Dependency> managedDependencies = new ArrayList<>();
                model.getDependencies().forEach(dep -> {
                    directDependencies.add(new org.eclipse.aether.graph.Dependency(
                            new DefaultArtifact(dep.getGroupId(), dep.getArtifactId(), dep.getType(),
                                    dep.getClassifier(), dep.getVersion()),
                            dep.getScope()));
                });
                if (model.getDependencyManagement() != null) {
                    model.getDependencyManagement().getDependencies().forEach(dep -> {
                        managedDependencies.add(new org.eclipse.aether.graph.Dependency(
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
                Map<String, String> gavScopeMap = new HashMap<>();
                for (ArtifactGav gav : effectiveGavs) {
                    usedGAVSet.add(gav.getGroupId() + ":" + gav.getArtifactId() + ":" + gav.getVersion());
                    if (gav.getScope() != null) {
                        gavScopeMap.put(gav.getGroupId() + ":" + gav.getArtifactId() + ":" + gav.getVersion(), gav.getScope());
                    }
                }
                // 构建树形结构并返回JSON
                Map<String, Object> tree = buildDependencyTreeWithConflict(rootNode, usedGAVSet, gavScopeMap);
                return new Gson().toJson(tree);
            } catch (Exception e) {
                return "{\"error\":\"依赖解析异常: " + e.getMessage() + "\"}";
            }
        });
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
     * @param node 当前Aether依赖节点
     * @param usedGAVSet 有效依赖GAV集合
     * @param gavScopeMap GAV到scope的映射
     * @return 树形依赖结构（Map表示）
     */
    private Map<String, Object> buildDependencyTreeWithConflict(DependencyNode node, Set<String> usedGAVSet, Map<String, String> gavScopeMap) {
        Map<String, Object> depInfo = new LinkedHashMap<>();
        Artifact artifact = node.getArtifact();
        if (artifact != null) {
            depInfo.put("groupId", artifact.getGroupId());
            depInfo.put("artifactId", artifact.getArtifactId());
            depInfo.put("version", artifact.getVersion());
            String key = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
            // 优先用gavScopeMap
            String scope = gavScopeMap.getOrDefault(key, node.getDependency() != null ? node.getDependency().getScope() : "compile");
            depInfo.put("scope", scope);
            boolean dropped = !usedGAVSet.contains(key);
            depInfo.put("droppedByConflict", dropped);
        }
        // 递归 children
        List<Map<String, Object>> children = new ArrayList<>();
        for (DependencyNode child : node.getChildren()) {
            children.add(buildDependencyTreeWithConflict(child, usedGAVSet, gavScopeMap));
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
}