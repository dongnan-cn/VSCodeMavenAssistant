package nd.mavenassistant.utils;

import org.apache.maven.model.Model;
import org.apache.maven.model.building.*;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

/**
 * Maven模型和依赖处理工具类
 * 负责POM文件解析、依赖转换、排除规则处理等功能
 */
public class MavenModelUtils {

    /**
     * 从POM文件路径获取Maven模型
     * 
     * @param pomPath POM文件路径，如果为空则使用当前目录的pom.xml
     * @return Maven模型对象
     * @throws Exception 如果POM文件不存在或解析失败
     */
    public static Model getModel(String pomPath) throws Exception {
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

    /**
     * 创建有效的依赖收集请求
     * 
     * @param artifact 根构件
     * @param directDependencies 直接依赖列表
     * @param managedDependencies 管理的依赖列表
     * @param repos 远程仓库列表
     * @return 依赖收集请求对象
     */
    public static CollectRequest getEffectiveCollectRequest(Artifact artifact, List<Dependency> directDependencies,
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
     * 获取模型的直接依赖列表
     * 
     * @param model Maven模型对象
     * @return 直接依赖列表
     */
    public static List<Dependency> getDirectDependencies(Model model) {
        List<Dependency> dependencies = new ArrayList<>();
        if (model.getDependencies() != null) {
            for (org.apache.maven.model.Dependency dep : model.getDependencies()) {
                dependencies.add(convertMavenToAetherDependency(dep));
            }
        }
        return dependencies;
    }

    /**
     * 获取模型的管理依赖列表
     * 
     * @param model Maven模型对象
     * @return 管理依赖列表
     */
    public static List<Dependency> getManagedDependencies(Model model) {
        List<Dependency> managedDependencies = new ArrayList<>();
        if (model.getDependencyManagement() != null && model.getDependencyManagement().getDependencies() != null) {
            for (org.apache.maven.model.Dependency dep : model.getDependencyManagement().getDependencies()) {
                managedDependencies.add(convertMavenToAetherDependency(dep));
            }
        }
        return managedDependencies;
    }

    /**
     * 将Maven依赖转换为Aether依赖
     * 
     * @param mavenDep Maven依赖对象
     * @return Aether依赖对象
     */
    public static Dependency convertMavenToAetherDependency(org.apache.maven.model.Dependency mavenDep) {
        String coords = String.format("%s:%s:%s",
                mavenDep.getGroupId(),
                mavenDep.getArtifactId(),
                mavenDep.getVersion() != null ? mavenDep.getVersion() : "[0,)");

        if (mavenDep.getType() != null && !"jar".equals(mavenDep.getType())) {
            coords += ":" + mavenDep.getType();
        }

        if (mavenDep.getClassifier() != null) {
            coords += ":" + mavenDep.getClassifier();
        }

        DefaultArtifact artifact = new DefaultArtifact(coords);
        String scope = mavenDep.getScope() != null ? mavenDep.getScope() : "compile";
        boolean optional = mavenDep.isOptional();

        return new Dependency(artifact, scope, optional);
    }

    /**
     * 构建排除规则映射表，保存原始的排除信息
     * 
     * @param model Maven模型对象
     * @return 排除规则映射表，key为groupId:artifactId，value为被排除的依赖集合
     */
    public static Map<String, Set<String>> buildExclusionMap(Model model) {
        Map<String, Set<String>> exclusionMap = new HashMap<>();
        
        // 处理直接依赖的排除规则
        if (model.getDependencies() != null) {
            for (org.apache.maven.model.Dependency dep : model.getDependencies()) {
                if (dep.getExclusions() != null && !dep.getExclusions().isEmpty()) {
                    String depKey = dep.getGroupId() + ":" + dep.getArtifactId();
                    Set<String> exclusions = new HashSet<>();
                    for (org.apache.maven.model.Exclusion exclusion : dep.getExclusions()) {
                        exclusions.add(exclusion.getGroupId() + ":" + exclusion.getArtifactId());
                    }
                    exclusionMap.put(depKey, exclusions);
                }
            }
        }
        
        // 处理dependencyManagement中的排除规则
        if (model.getDependencyManagement() != null && model.getDependencyManagement().getDependencies() != null) {
            for (org.apache.maven.model.Dependency dep : model.getDependencyManagement().getDependencies()) {
                if (dep.getExclusions() != null && !dep.getExclusions().isEmpty()) {
                    String depKey = dep.getGroupId() + ":" + dep.getArtifactId();
                    Set<String> exclusions = exclusionMap.getOrDefault(depKey, new HashSet<>());
                    for (org.apache.maven.model.Exclusion exclusion : dep.getExclusions()) {
                        exclusions.add(exclusion.getGroupId() + ":" + exclusion.getArtifactId());
                    }
                    exclusionMap.put(depKey, exclusions);
                }
            }
        }
        
        return exclusionMap;
    }

    /**
     * 从Maven模型创建构件对象
     * 
     * @param model Maven模型对象
     * @return 构件对象
     */
    public static Artifact getArtifactFromModel(Model model) {
        return new DefaultArtifact(model.getGroupId(), model.getArtifactId(), "jar", model.getVersion());
    }
}