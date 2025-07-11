package nd.mavenassistant.lsp;

import nd.mavenassistant.model.ArtifactGav;
import java.io.*;
import java.util.*;

public class MavenClasspathFetcher {
    /**
     * 执行 mvn dependency:list 并返回所有 GAV 信息的列表（支持 moduleName）
     */
    public static List<ArtifactGav> fetchGavList() throws Exception {
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