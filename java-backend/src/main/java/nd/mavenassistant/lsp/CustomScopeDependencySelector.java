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
        if (depth == 0 || depth == 1) {
            return true;
        } else if (depth <= 4) {
            return "compile".equals(dependency.getScope()) || "runtime".equals(dependency.getScope());
        }
        return false;
    }

    @Override
    public DependencySelector deriveChildSelector(DependencyCollectionContext context) {
        return new CustomScopeDependencySelector(depth + 1);
    }
}
