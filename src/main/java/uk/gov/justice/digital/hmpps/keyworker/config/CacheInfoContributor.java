package uk.gov.justice.digital.hmpps.keyworker.config;

import net.sf.ehcache.CacheManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class CacheInfoContributor implements InfoContributor {

    private final CacheManager cacheManager;

    @Autowired
    public CacheInfoContributor(final CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public void contribute(final Info.Builder builder) {
        final Map<String, String> results = new HashMap<>();
        long memory = 0;

        for (final var name : cacheManager.getCacheNames()) {
            final var cache = cacheManager.getCache(name);
            final var statistics = cache.getStatistics();
            results.put(name, String.format("%d / %d hits:%d misses:%d bytes:%d",
                    cache.getKeysNoDuplicateCheck().size(),
                    cache.getCacheConfiguration().getMaxEntriesLocalHeap(),
                    statistics.cacheHitCount(),
                    statistics.cacheMissCount(),
                    statistics.getLocalHeapSizeInBytes()
            ));
            memory += statistics.getLocalHeapSizeInBytes();

        }
        builder.withDetail("caches", results);
        builder.withDetail("cacheTotalMemoryMB", String.format("%.2f", memory / 1048576.0));
    }
}
