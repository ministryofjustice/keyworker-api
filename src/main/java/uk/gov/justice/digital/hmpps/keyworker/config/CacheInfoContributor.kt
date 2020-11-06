package uk.gov.justice.digital.hmpps.keyworker.config

import net.sf.ehcache.CacheManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.stereotype.Component
import java.util.*

@Component
class CacheInfoContributor(@Autowired private val cacheManager: CacheManager) : InfoContributor {

    override fun contribute(builder: Info.Builder) {
        val results: MutableMap<String, String> = HashMap()
        var memory: Long = 0
        for (name in cacheManager.cacheNames) {
            val cache = cacheManager.getCache(name)
            val statistics = cache.statistics
            results[name] = String.format("%d / %d hits:%d misses:%d bytes:%d",
                    cache.keysNoDuplicateCheck.size,
                    cache.cacheConfiguration.maxEntriesLocalHeap,
                    statistics.cacheHitCount(),
                    statistics.cacheMissCount(),
                    statistics.localHeapSizeInBytes
            )
            memory += statistics.localHeapSizeInBytes
        }
        builder.withDetail("caches", results)
        builder.withDetail("cacheTotalMemoryMB", String.format("%.2f", memory / 1048576.0))
    }
}