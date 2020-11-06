package uk.gov.justice.digital.hmpps.keyworker.config

import net.sf.ehcache.CacheManager
import net.sf.ehcache.config.CacheConfiguration
import net.sf.ehcache.store.MemoryStoreEvictionPolicy
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.CachingConfigurer
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.ehcache.EhCacheCacheManager
import org.springframework.cache.interceptor.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableCaching
class CacheConfig(@Value("\${cache.timeout.seconds.staff:86400}")
                  private val staffInformationTimeoutSeconds: Int = 0) : CachingConfigurer {

    @Bean(destroyMethod = "shutdown")
    fun ehCacheManager(): CacheManager {
        val config = net.sf.ehcache.config.Configuration()
        config.addCache(config("getBasicKeyworkerDtoForStaffId", 10000, staffInformationTimeoutSeconds, MemoryStoreEvictionPolicy.LRU))
        config.addCache(config("getStaffDetailByUserId", 10000, staffInformationTimeoutSeconds, MemoryStoreEvictionPolicy.LRU))
        return CacheManager.newInstance(config)
    }

    @Bean
    override fun cacheManager(): org.springframework.cache.CacheManager {
        return EhCacheCacheManager(ehCacheManager())
    }

    @Bean
    override fun keyGenerator(): KeyGenerator {
        return SimpleKeyGenerator()
    }

    @Bean
    override fun cacheResolver(): CacheResolver {
        return SimpleCacheResolver(cacheManager())
    }

    @Bean
    override fun errorHandler(): CacheErrorHandler {
        return SimpleCacheErrorHandler()
    }

    companion object {
        fun config(name: String?, maxElements: Int, timeoutSeconds: Int, policy: MemoryStoreEvictionPolicy?): CacheConfiguration {
            return CacheConfiguration().name(name)
                    .memoryStoreEvictionPolicy(policy)
                    .eternal(false)
                    .overflowToOffHeap(false)
                    .maxEntriesLocalHeap(maxElements)
                    .timeToLiveSeconds(timeoutSeconds.toLong())
                    .timeToIdleSeconds(timeoutSeconds.toLong())
        }
    }
}