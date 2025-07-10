package nd.mavenassistant.lsp;

import nd.mavenassistant.model.ArtifactGav;
import java.io.*;
import java.util.*;

public class MavenClasspathFetcher {
    /**
     * 执行 mvn dependency:list 并返回所有 GAV 信息的列表（支持 moduleName）
     */
    public static List<ArtifactGav> fetchGavList() throws Exception {
        String mvnCmd = System.getProperty("os.name").toLowerCase().contains("win") ? "mvn.cmd" : "mvn";
        ProcessBuilder pb = new ProcessBuilder(mvnCmd, "dependency:list", "-DoutputAbsoluteArtifactFilename=false");
        pb.redirectErrorStream(true);
        pb.directory(new File("."));
        Process process = pb.start();

        List<ArtifactGav> gavList = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.contains("-- module ")) {
                    String dep = line.substring(6).trim();
                    if (dep.contains(":") && !dep.startsWith("---")) {
                        // 解析 GAV 和 moduleName
                        String[] mainAndModule = dep.split("-- module ");
                        String[] parts = mainAndModule[0].split(":");
                        String moduleName = mainAndModule.length > 1 ? mainAndModule[1].split(" ")[0] : null;
                        if (parts.length >= 4) {
                            gavList.add(new ArtifactGav(parts[0], parts[1], parts[3], moduleName));
                        }
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