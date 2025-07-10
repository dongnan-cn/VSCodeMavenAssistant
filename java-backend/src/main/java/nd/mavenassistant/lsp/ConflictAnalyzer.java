package nd.mavenassistant.lsp;

import java.util.List;
import java.util.Stack;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;

public class ConflictAnalyzer implements DependencyVisitor {
    // 用于记录从根到当前节点的路径，方便打印冲突路径
    private final Stack<DependencyNode> path = new Stack<>();
    // 统计版本发生变化的次数 (包括冲突解决和依赖管理)
    private int versionChangeCount = 0;
    // 统计构件重定向的次数
    private int relocationCount = 0;

    /**
     * 当访问进入一个依赖节点时调用。
     * 在这里执行冲突和重定向的检测逻辑。
     *
     * @param node 当前访问的依赖节点
     * @return true 继续访问子节点，false 停止访问当前分支
     */
    @Override
    public boolean visitEnter(DependencyNode node) {
        path.push(node); // 将当前节点压入路径栈

        // 根节点可能没有 Dependency，需要判空，避免空指针异常
        if (node.getDependency() == null) {
            // 跳过根节点或特殊节点的冲突分析
            return true;
        }

        // 获取原始请求的构件和最终解析的构件
        // node.getDependency() 表示这个节点原始是作为什么依赖被请求的（可能带着原始版本、scope等）
        // node.getArtifact() 表示经过 Aether 仲裁和解析后，最终决定使用的那个构件（最终版本、实际文件等）
        Artifact requestedArtifact = node.getDependency().getArtifact();
        Artifact resolvedArtifact = node.getArtifact();

        // --- 1. 检测版本变化 (包括冲突解决和依赖管理覆盖) ---
        // 如果原始请求的版本和最终解析的版本不同，则说明发生了版本变化
        if (!requestedArtifact.getVersion().equals(resolvedArtifact.getVersion())) {
            System.out.println("--- 版本变化检测 (冲突或依赖管理) ---");
            System.out.println("  构件: " + requestedArtifact.getGroupId() + ":" + requestedArtifact.getArtifactId());
            System.out.println("  请求版本: " + requestedArtifact.getVersion());
            System.out.println("  解析版本: " + resolvedArtifact.getVersion());
            System.out.print("  变化路径: ");
            printPath(); // 打印导致此变化的依赖路径
            versionChangeCount++;
        }

        // --- 2. 检测构件重定向 (Relocation) ---
        // 重定向指的是构件的 GAV (GroupId, ArtifactId, Version) 坐标本身发生了变化。
        // 这通常发生在发布者将构件从一个坐标迁移到另一个坐标时。
        // Aether 在解析过程中会处理这些重定向，并最终返回重定向后的构件。
        // 检测方法是比较请求构件和解析构件的 GroupId 或 ArtifactId 是否发生变化。
        // 如果它们不同，则表明发生了重定向。
        if (!requestedArtifact.getGroupId().equals(resolvedArtifact.getGroupId()) ||
                !requestedArtifact.getArtifactId().equals(resolvedArtifact.getArtifactId())) {

            System.out.println("--- 构件重定向检测 ---");
            System.out.println("  原始请求 GAV: " + requestedArtifact.getGroupId() + ":" + requestedArtifact.getArtifactId()
                    + ":" + requestedArtifact.getVersion());
            System.out.println("  重定向至 GAV: " + resolvedArtifact.getGroupId() + ":" + resolvedArtifact.getArtifactId()
                    + ":" + resolvedArtifact.getVersion());
            System.out.print("  重定向路径: ");
            printPath(); // 打印导致此重定向的依赖路径
            relocationCount++;
        }

        // 返回 true 表示继续访问当前节点的子节点
        return true;
    }

    /**
     * 当访问离开一个依赖节点时调用。
     * 在这里将当前节点从路径栈中弹出。
     *
     * @param node 当前访问的依赖节点
     * @return true 继续处理，false 停止处理
     */
    @Override
    public boolean visitLeave(DependencyNode node) {
        path.pop(); // 将当前节点从路径栈中弹出
        return true;
    }

    /**
     * 辅助方法：打印当前依赖节点的完整路径，从根到当前节点。
     */
    private void printPath() {
        for (int i = 0; i < path.size(); i++) {
            DependencyNode node = path.get(i);
            // 打印每个节点的 artifactId 和 version，形成链条
            System.out.print((i == 0 ? "" : " -> ") + node.getArtifact().getArtifactId() + ":"
                    + node.getArtifact().getVersion());
        }
        System.out.println(); // 路径打印完成后换行
    }

    /**
     * 支持多根节点的冲突分析入口
     * @param roots 依赖树的所有根节点（通常为 directDependencies）
     */
    public void analyze(List<DependencyNode> roots) {
        for (DependencyNode root : roots) {
            path.clear(); // 每棵树单独分析，路径栈清空
            root.accept(this);
        }
    }

    /**
     * 获取检测到的版本变化（冲突或依赖管理覆盖）的总次数。
     *
     * @return 版本变化次数
     */
    public int getVersionChangeCount() {
        return versionChangeCount;
    }

    /**
     * 获取检测到的构件重定向的总次数。
     *
     * @return 重定向次数
     */
    public int getRelocationCount() {
        return relocationCount;
    }
}
