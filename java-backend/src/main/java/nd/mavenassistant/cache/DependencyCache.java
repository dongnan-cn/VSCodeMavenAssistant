package nd.mavenassistant.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 依赖分析结果缓存管理器
 * 负责缓存Maven依赖分析结果，避免重复计算
 */
public class DependencyCache {
    
    // 依赖分析结果缓存
    private final Map<CacheKey, CacheEntry> dependencyCache = new HashMap<>();
    
    // 文件大小缓存，避免重复的文件I/O操作（线程安全）
    private final Map<String, Long> fileSizeCache = new ConcurrentHashMap<>();
    
    // 缓存过期时间：5分钟
    private static final long CACHE_EXPIRY_MS = 5 * 60 * 1000;
    
    /**
     * 缓存键类
     */
    public static class CacheKey {
        private final String pomPath;
        private final long pomLastModified;
        
        public CacheKey(String pomPath, long pomLastModified) {
            this.pomPath = pomPath;
            this.pomLastModified = pomLastModified;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            CacheKey cacheKey = (CacheKey) obj;
            return pomLastModified == cacheKey.pomLastModified && Objects.equals(pomPath, cacheKey.pomPath);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(pomPath, pomLastModified);
        }
    }
    
    /**
     * 缓存条目类
     */
    public static class CacheEntry {
        private final String result;
        private final long timestamp;
        
        public CacheEntry(String result, long timestamp) {
            this.result = result;
            this.timestamp = timestamp;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS;
        }
        
        public String getResult() {
            return result;
        }
    }
    
    /**
     * 获取缓存的依赖分析结果
     */
    public CacheEntry getDependencyResult(CacheKey key) {
        return dependencyCache.get(key);
    }
    
    /**
     * 缓存依赖分析结果
     */
    public void putDependencyResult(CacheKey key, String result) {
        dependencyCache.put(key, new CacheEntry(result, System.currentTimeMillis()));
    }
    
    /**
     * 获取文件大小缓存
     */
    public Long getFileSize(String filePath) {
        return fileSizeCache.get(filePath);
    }
    
    /**
     * 缓存文件大小
     */
    public void putFileSize(String filePath, long size) {
        fileSizeCache.put(filePath, size);
    }
    
    /**
     * 清理所有缓存
     */
    public void clearCaches() {
        dependencyCache.clear();
        fileSizeCache.clear();
    }
    
    /**
     * 清理过期的依赖缓存
     */
    public void cleanupExpiredCaches() {
        dependencyCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    /**
     * 获取依赖缓存大小
     */
    public int getDependencyCacheSize() {
        return dependencyCache.size();
    }
    
    /**
     * 获取文件大小缓存大小
     */
    public int getFileSizeCacheSize() {
        return fileSizeCache.size();
    }
}