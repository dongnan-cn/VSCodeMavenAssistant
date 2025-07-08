package nd.mavenassistant.lsp;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.SetTraceParams;
import com.google.gson.Gson;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.LocalRepository;
import java.io.File;
import java.io.FileReader;
import java.util.*;

/**
 * 最基础的 LanguageServer 实现
 * 目前所有方法均为空实现，后续可逐步扩展具体功能。
 */
public class SimpleLanguageServer implements LanguageServer {
    // LanguageClient 用于与 VSCode 前端通信，推送日志等
    private LanguageClient client;

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
    public CompletableFuture<String> analyzeDependencies(String pomPath) {
        // supplyAsync 泛型指定为 String，避免类型不匹配
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. 解析 pom.xml 路径
                String pomFilePath = (pomPath == null || pomPath.isEmpty())
                        ? new File("pom.xml").getAbsolutePath()
                        : pomPath;
                File pomFile = new File(pomFilePath);
                if (!pomFile.exists()) {
                    return "{\"error\":\"pom.xml 文件不存在: " + pomFilePath + "\"}";
                }

                // 2. 初始化 Maven Resolver（org.eclipse.aether 体系）
                RepositorySystem system = createRepositorySystem();
                RepositorySystemSession session = createSession(system);

                // 3. 配置远程仓库
                List<RemoteRepository> repos = Collections.singletonList(
                        new RemoteRepository.Builder(
                                "central", "default", "https://repo.maven.apache.org/maven2/").build());

                // 4. 读取 pom.xml 获取 GAV 坐标
                Model model = new MavenXpp3Reader().read(new FileReader(pomFile));
                String coords = model.getGroupId() + ":" + model.getArtifactId() + ":" + model.getVersion();

                // 5. 构建依赖收集请求
                CollectRequest collectRequest = new CollectRequest();
                collectRequest.setRoot(new Dependency(
                        new DefaultArtifact(coords), "compile"));
                collectRequest.setRepositories(repos);

                // 6. 解析依赖树
                DependencyNode rootNode = system.collectDependencies(session, collectRequest).getRoot();

                // 7. 遍历依赖树生成JSON
                List<Map<String, Object>> dependencies = new ArrayList<>();
                collectDependencies(rootNode, dependencies, 0);

                return new Gson().toJson(dependencies);
            } catch (Exception e) {
                return "{\"error\":\"依赖解析异常: " + e.getMessage() + "\"}";
            }
        });
    }


    // 创建 RepositorySystem（org.eclipse.aether 体系）
    private RepositorySystem createRepositorySystem() {
        // 使用 org.eclipse.aether.impl.DefaultServiceLocator
        // DefaultServiceLocator locator = new DefaultServiceLocator();
        // locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        // locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        // locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        // RepositorySystem system = locator.getService(RepositorySystem.class);

        RepositorySystemSupplier systemSupplier = new RepositorySystemSupplier();
        RepositorySystem system = systemSupplier.get();
        if (system == null) {
            throw new IllegalStateException("RepositorySystem 实例获取失败，依赖包可能缺失或SPI加载失败");
        }
        return system;
    }

    // 创建 RepositorySystemSession
    private RepositorySystemSession createSession(RepositorySystem system) {
        // 使用 MavenRepositorySystemUtils 创建 session
        org.eclipse.aether.DefaultRepositorySystemSession session = org.apache.maven.repository.internal.MavenRepositorySystemUtils
                .newSession();
        LocalRepository localRepo = new LocalRepository(System.getProperty("user.home") + "/.m2/repository");
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        return session;
    }

    // 递归收集依赖信息
    private void collectDependencies(DependencyNode node, List<Map<String, Object>> result, int depth) {
        Map<String, Object> depInfo = new LinkedHashMap<>();
        if (node.getArtifact() != null) {
            depInfo.put("groupId", node.getArtifact().getGroupId());
            depInfo.put("artifactId", node.getArtifact().getArtifactId());
            depInfo.put("version", node.getArtifact().getVersion());
        }
        if (node.getDependency() != null) {
            depInfo.put("scope", node.getDependency().getScope());
        }
        depInfo.put("depth", depth);
        result.add(depInfo);
        for (DependencyNode child : node.getChildren()) {
            collectDependencies(child, result, depth + 1);
        }
    }

    // 实现 setTrace 方法，防止 VSCode 发送 $/setTrace 时抛出异常
    @Override
    public void setTrace(SetTraceParams params) {
        // 这里可以根据 params.getValue() 设置日志级别，目前为空实现
    }
}