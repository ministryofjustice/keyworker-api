package uk.gov.justice.digital.hmpps.keyworker.services;

import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.ehcache.EhCacheCacheManager;
import org.springframework.cache.interceptor.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    @Value("${cache.timeout.seconds.staff:86400}")
    private int staffInformationTimeoutSeconds;


    @Bean(destroyMethod="shutdown")
    public net.sf.ehcache.CacheManager ehCacheManager() {
        net.sf.ehcache.config.Configuration config = new net.sf.ehcache.config.Configuration();

        config.addCache(config("getBasicKeyworkerDtoForStaffId", 10000, staffInformationTimeoutSeconds, MemoryStoreEvictionPolicy.LRU));
        config.addCache(config("getStaffDetailByUserId", 10000, staffInformationTimeoutSeconds, MemoryStoreEvictionPolicy.LRU));

        return net.sf.ehcache.CacheManager.newInstance(config);
    }

    public static CacheConfiguration config(String name, int maxElements, int timeoutSeconds, MemoryStoreEvictionPolicy policy){
        return new CacheConfiguration().name(name)
                .memoryStoreEvictionPolicy(policy)
                .eternal(false)
                .overflowToOffHeap(false)
                .maxEntriesLocalHeap(maxElements)
                .timeToLiveSeconds(timeoutSeconds)
                .timeToIdleSeconds(timeoutSeconds);
    }

    @Bean
    @Override
    public CacheManager cacheManager() {
        return new EhCacheCacheManager(ehCacheManager());
    }

    @Bean
    @Override
    public KeyGenerator keyGenerator() {
        return new SimpleKeyGenerator();
    }

    @Bean
    @Override
    public CacheResolver cacheResolver()    {
        return new SimpleCacheResolver(cacheManager());
    }

    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler();
    }
}
