package nd.mavenassistant.lsp;

import nd.mavenassistant.model.ArtifactGav;
import java.io.*;
import java.util.*;

public class MavenClasspathFetcher {
    // GAV列表缓存
    private static final Map<String, CacheEntry> gavListCache = new HashMap<>();
    private static final long CACHE_EXPIRY_MS = 3 * 60 * 1000; // 3分钟缓存过期时间
    
    // 缓存条目类
    private static class CacheEntry {
        private final List<ArtifactGav> gavList;
        private final long timestamp;
        private final long pomLastModified;
        
        public CacheEntry(List<ArtifactGav> gavList, long timestamp, long pomLastModified) {
            this.gavList = gavList;
            this.timestamp = timestamp;
            this.pomLastModified = pomLastModified;
        }
        
        public boolean isExpired(long currentPomLastModified) {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS || 
                   pomLastModified != currentPomLastModified;
        }
        
        public List<ArtifactGav> getGavList() {
            return gavList;
        }
    }
    /**
     * 执行 mvn dependency:list 并返回所有 GAV 信息的列表（支持 moduleName）
     */
    public static List<ArtifactGav> fetchGavList() throws Exception {
        return fetchGavList("pom.xml");
    }
    
    /**
     * 执行 mvn dependency:list 并返回所有 GAV 信息的列表（支持 moduleName 和缓存）
     */
    public static List<ArtifactGav> fetchGavList(String pomPath) throws Exception {
        // 获取POM文件的最后修改时间
        File pomFile = new File(pomPath);
        long pomLastModified = pomFile.exists() ? pomFile.lastModified() : 0;
        
        // 检查缓存
        CacheEntry cachedEntry = gavListCache.get(pomPath);
        if (cachedEntry != null && !cachedEntry.isExpired(pomLastModified)) {
            return new ArrayList<>(cachedEntry.getGavList()); // 返回副本避免外部修改
        }
        
        // 清理过期缓存
        gavListCache.entrySet().removeIf(entry -> entry.getValue().isExpired(pomLastModified));
        
        // 执行Maven命令获取依赖列表
        List<ArtifactGav> gavList = executeMavenDependencyList();
        
        // 缓存结果
        gavListCache.put(pomPath, new CacheEntry(gavList, System.currentTimeMillis(), pomLastModified));
        
        return gavList;
    }
    
    /**
     * 执行Maven dependency:list命令的核心逻辑
     */
    private static List<ArtifactGav> executeMavenDependencyList() throws Exception {
        // 判断操作系统，选择合适的mvn命令
        String mvnCmd = System.getProperty("os.name").toLowerCase().contains("win") ? "mvn.cmd" : "mvn";
        // 执行mvn dependency:list命令
        ProcessBuilder pb = new ProcessBuilder(mvnCmd, "dependency:list", "-DoutputAbsoluteArtifactFilename=false");
        pb.redirectErrorStream(true);
        pb.directory(new File("."));
        Process process = pb.start();

        List<ArtifactGav> gavList = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // 只处理以[INFO]开头且包含:的依赖行
                if (line.startsWith("[INFO]") && line.contains(":")) {
                    String dep = line.substring(6).trim(); // 去掉[INFO]
                    String moduleName = null;
                    // 检查是否有-- module
                    if (dep.contains("-- module")) {
                        String[] arr = dep.split("-- module");
                        dep = arr[0].trim();
                        moduleName = arr[1].trim();
                        // 去掉括号及其内容，如 (auto)
                        int idx = moduleName.indexOf('(');
                        if (idx != -1) {
                            moduleName = moduleName.substring(0, idx).trim();
                        }
                    }
                    // 解析GAV部分
                    String[] parts = dep.split(":");
                    // groupId:artifactId:packaging:version:scope
                    if (parts.length >= 5) {
                        String groupId = parts[0];
                        String artifactId = parts[1];
                        // String packaging = parts[2]; // 如需可加
                        String version = parts[3];
                        String scope = parts[4];
                        // 构造ArtifactGav对象
                        gavList.add(new ArtifactGav(groupId, artifactId, version, moduleName, scope));
                    }
                }
            }
        }
        process.waitFor();
        return gavList;
    }

    // 演示用主方法
    public static void main(String[] args) throws Exception {
        List<ArtifactGav> gavs = fetchGavList();
        for (ArtifactGav gav : gavs) {
            System.out.println(gav);
        }
    }
}