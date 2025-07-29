package nd.mavenassistant.utils;

import org.apache.maven.model.Model;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;

/**
 * POM XML 文件处理工具类
 * 提供 DOM 解析、XML 元素操作、缩进处理等功能
 */
public class PomXmlUtils {
    
    /**
     * 缩进记录，包含依赖缩进和缩进单位
     */
    public static record IndentRecord(String dependencyIndent, String indentUnit) {
    }
    
    /**
     * 获取元素的文本内容
     * 
     * @param parent 父元素
     * @param tagName 标签名
     * @return 元素的文本内容，如果不存在则返回null
     */
    public static String getElementTextContent(Element parent, String tagName) {
        NodeList elements = parent.getElementsByTagName(tagName);
        if (elements.getLength() > 0) {
            return elements.item(0).getTextContent();
        }
        return null;
    }
    
    /**
     * 获取目标依赖元素的缩进信息
     * 
     * @param targetDependencyElement 目标依赖元素
     * @return 缩进记录
     */
    public static IndentRecord getIndent(Element targetDependencyElement) {
        String dependencyIndent = "\n";
        String parentIndent = "\n";
        if (targetDependencyElement != null) {
            // 获取<dependency>标签的缩进
            Node depPrev = targetDependencyElement.getPreviousSibling();
            if (depPrev != null && depPrev.getNodeType() == Node.TEXT_NODE) {
                String text = depPrev.getTextContent();
                int lastNewline = text.lastIndexOf('\n');
                if (lastNewline != -1) {
                    dependencyIndent = text.substring(lastNewline);
                } else {
                    dependencyIndent = text;
                }
            }
            // 获取父节点的缩进
            Node parent = targetDependencyElement.getParentNode();
            if (parent != null) {
                Node parentPrev = parent.getPreviousSibling();
                if (parentPrev != null && parentPrev.getNodeType() == Node.TEXT_NODE) {
                    String text = parentPrev.getTextContent();
                    int lastNewline = text.lastIndexOf('\n');
                    if (lastNewline != -1) {
                        parentIndent = text.substring(lastNewline);
                    } else {
                        parentIndent = text;
                    }
                }
            }
        }
        // 计算单位缩进（父子标签缩进差值）
        String indentUnit;
        if (dependencyIndent.length() > parentIndent.length()) {
            indentUnit = dependencyIndent.substring(parentIndent.length());
        } else {
            indentUnit = "  "; // 默认2空格
        }
        return new IndentRecord(dependencyIndent, indentUnit);
    }
    
    /**
     * 获取指定层级的缩进字符串
     * 
     * @param dependencyIndent 依赖缩进记录
     * @param level 层级
     * @return 缩进字符串
     */
    public static String getLeveledIndent(IndentRecord dependencyIndent, int level) {
        return dependencyIndent.dependencyIndent() + dependencyIndent.indentUnit().repeat(level);
    }
    
    /**
     * 判断exclusionsElement下是否已存在指定GA的exclusion
     * 
     * @param exclusionsElement exclusions元素
     * @param groupId 组ID
     * @param artifactId 构件ID
     * @return 如果存在则返回true，否则返回false
     */
    public static boolean hasExclusion(Element exclusionsElement, String groupId, String artifactId) {
        NodeList exclusionNodes = exclusionsElement.getElementsByTagName("exclusion");
        for (int i = 0; i < exclusionNodes.getLength(); i++) {
            Element exclusion = (Element) exclusionNodes.item(i);
            String existGroupId = getElementTextContent(exclusion, "groupId");
            String existArtifactId = getElementTextContent(exclusion, "artifactId");
            if (groupId.equals(existGroupId) && artifactId.equals(existArtifactId)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 填充exclusion，如果已存在则不插入，返回false，否则插入并返回true
     * 
     * @param exclusionGroupId 排除的组ID
     * @param exclusionArtifactId 排除的构件ID
     * @param doc XML文档
     * @param targetDependencyElement 目标依赖元素
     * @return 如果插入成功返回true，如果已存在返回false
     */
    public static boolean fillExclude(String exclusionGroupId, String exclusionArtifactId, Document doc,
                                     Element targetDependencyElement) {
        // 检查是否已有 exclusions 元素
        NodeList exclusionsList = targetDependencyElement.getElementsByTagName("exclusions");
        IndentRecord dependencyIndent = getIndent(targetDependencyElement);
        Element exclusionsElement;
        boolean exclusionsExist = false;
        if (exclusionsList.getLength() > 0) {
            exclusionsExist = true;
            // 已有 exclusions 元素
            exclusionsElement = (Element) exclusionsList.item(0);
            // 使用独立方法判断是否已存在相同GA的exclusion
            if (hasExclusion(exclusionsElement, exclusionGroupId, exclusionArtifactId)) {
                return false; // 已存在
            }
        } else {
            exclusionsExist = false;
            // 创建新的 exclusions 元素
            exclusionsElement = doc.createElement("exclusions");
            targetDependencyElement.appendChild(doc.createTextNode(dependencyIndent.indentUnit()));
            targetDependencyElement.appendChild(exclusionsElement);
            targetDependencyElement.appendChild(doc.createTextNode(dependencyIndent.dependencyIndent()));
        }

        // 创建 exclusion 元素
        Element exclusionElement = doc.createElement("exclusion");

        Element groupIdElement = doc.createElement("groupId");
        groupIdElement.setTextContent(exclusionGroupId);
        exclusionElement.appendChild(doc.createTextNode(getLeveledIndent(dependencyIndent, 3)));
        exclusionElement.appendChild(groupIdElement);
        exclusionElement.appendChild(doc.createTextNode(getLeveledIndent(dependencyIndent, 3)));

        Element artifactIdElement = doc.createElement("artifactId");
        artifactIdElement.setTextContent(exclusionArtifactId);
        exclusionElement.appendChild(artifactIdElement);
        exclusionElement.appendChild(doc.createTextNode(getLeveledIndent(dependencyIndent, 2)));

        if(!exclusionsExist){
            exclusionsElement.appendChild(doc.createTextNode(getLeveledIndent(dependencyIndent, 2)));
        } else {
            exclusionsElement.appendChild(doc.createTextNode(dependencyIndent.indentUnit()));
        }
        exclusionsElement.appendChild(exclusionElement);
        exclusionsElement.appendChild(doc.createTextNode(getLeveledIndent(dependencyIndent, 1)));

        return true; // 插入成功
    }
    
    /**
     * 解析 Maven 变量，获取解析后的依赖版本映射
     * 使用 Maven Model API 解析 pom.xml 中的变量，返回 groupId:artifactId -> resolvedVersion 的映射
     *
     * @param pomPath pom.xml 文件路径
     * @return 解析后的依赖版本映射，key 为 groupId:artifactId，value 为解析后的版本
     */
    public static Map<String, String> resolveMavenVariables(String pomPath) {
        // 参数验证
        if (pomPath == null || pomPath.trim().isEmpty()) {
            return new HashMap<>();
        }
        
        try {
            // 使用 Maven Model API 解析变量，获取解析后的依赖列表
            Model resolvedModel = MavenModelUtils.getModel(pomPath);
            Map<String, String> resolvedDependencies = new HashMap<>();
            for (org.apache.maven.model.Dependency dep : resolvedModel.getDependencies()) {
                String key = dep.getGroupId() + ":" + dep.getArtifactId();
                resolvedDependencies.put(key, dep.getVersion());
            }
            return resolvedDependencies;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
    
    /**
     * 解析XML文档
     * 
     * @param pomPath POM文件路径
     * @return 解析后的XML文档
     * @throws Exception 解析异常
     */
    public static Document parseDocument(String pomPath) throws Exception {
        // 参数验证
        if (pomPath == null || pomPath.trim().isEmpty()) {
            throw new IllegalArgumentException("POM file path cannot be empty");
        }
        
        File pomFile = new File(pomPath);
        if (!pomFile.exists()) {
            throw new FileNotFoundException("POM file does not exist: " + pomPath);
        }
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(pomFile);
        doc.getDocumentElement().normalize();
        return doc;
    }
    
    /**
     * 将XML文档写回文件
     * 
     * @param doc XML文档
     * @param pomPath POM文件路径
     * @throws Exception 写入异常
     */
    public static void writeDocument(Document doc, String pomPath) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(pomPath));
        transformer.transform(source, result);
    }
    
    /**
     * 查找目标依赖元素
     * 
     * @param doc XML文档
     * @param targetGroupId 目标组ID
     * @param targetArtifactId 目标构件ID
     * @param targetVersion 目标版本
     * @param resolvedDependencies 解析后的依赖映射
     * @return 目标依赖元素，如果未找到则返回null
     */
    public static Element findTargetDependencyElement(Document doc, String targetGroupId, String targetArtifactId, 
                                                      String targetVersion, Map<String, String> resolvedDependencies) {
        // 参数验证
        if (doc == null || targetGroupId == null || targetArtifactId == null) {
            return null;
        }
        
        NodeList dependencies = doc.getElementsByTagName("dependency");
        
        for (int i = 0; i < dependencies.getLength(); i++) {
            Element depElement = (Element) dependencies.item(i);
            String groupId = getElementTextContent(depElement, "groupId");
            String artifactId = getElementTextContent(depElement, "artifactId");
            String version = getElementTextContent(depElement, "version");

            // 跳过缺少必要信息的依赖
            if (groupId == null || artifactId == null) {
                continue;
            }

            // 如果 version 包含变量，使用解析后的值
            if (version != null && version.contains("${")) {
                String key = groupId + ":" + artifactId;
                String resolvedVersion = resolvedDependencies.get(key);
                if (resolvedVersion != null) {
                    version = resolvedVersion;
                }
            }

            boolean groupIdMatch = groupId.equals(targetGroupId);
            boolean artifactIdMatch = artifactId.equals(targetArtifactId);
            boolean versionMatch = 
                    (targetVersion == null || targetVersion.isEmpty()) || // 如果没有指定目标版本，匹配任何版本
                    (version != null && version.equals(targetVersion)) || // 版本完全匹配
                    (version == null && (targetVersion == null || targetVersion.isEmpty())); // 都没有版本

            if (groupIdMatch && artifactIdMatch && versionMatch) {
                return depElement;
            }
        }
        
        return null;
    }
}