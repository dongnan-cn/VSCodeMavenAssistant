package nd.mavenassistant.lsp;

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;

public class CustomScopeDependencySelector implements DependencySelector {
    private final int depth;

    public CustomScopeDependencySelector() {
        this(0);
    }

    private CustomScopeDependencySelector(int depth) {
        this.depth = depth;
    }

    @Override
    public boolean selectDependency(Dependency dependency) {
        if (depth == 0) {
            // 根节点，不过滤任何 scope
            return true;
        } else if (depth == 1) {
            // 根节点下的依赖，保留 test
            return true;
        } else {
            // 依赖的依赖，过滤 test
            return "compile".equals(dependency.getScope()) || "runtime".equals(dependency.getScope());
        }
    }

    @Override
    public DependencySelector deriveChildSelector(DependencyCollectionContext context) {
        return new CustomScopeDependencySelector(depth + 1);
    }
}
