package fyi.goodbye.fridgy.utils

/**
 * A thread-safe LRU (Least Recently Used) cache implementation.
 *
 * This cache evicts the least recently accessed entries when it reaches
 * its maximum size, preventing unbounded memory growth.
 *
 * @param K The type of keys maintained by this cache
 * @param V The type of mapped values
 * @param maxSize The maximum number of entries to hold before evicting
 */
class LruCache<K, V>(private val maxSize: Int) {
    
    init {
        require(maxSize > 0) { "maxSize must be > 0" }
    }
    
    // LinkedHashMap with accessOrder=true automatically maintains LRU order
    private val cache = object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxSize
        }
    }
    
    /**
     * Returns the value for [key] if it exists in the cache, or null.
     * Accessing a key moves it to the "most recently used" position.
     */
    @Synchronized
    operator fun get(key: K): V? = cache[key]
    
    /**
     * Caches [value] for [key].
     * If the cache is at capacity, the least recently used entry is evicted.
     */
    @Synchronized
    operator fun set(key: K, value: V) {
        cache[key] = value
    }
    
    /**
     * Caches [value] for [key]. Alias for set operator.
     */
    @Synchronized
    fun put(key: K, value: V): V? = cache.put(key, value)
    
    /**
     * Removes the entry for [key] if it exists.
     * @return The previous value associated with [key], or null.
     */
    @Synchronized
    fun remove(key: K): V? = cache.remove(key)
    
    /**
     * Clears all entries from the cache.
     */
    @Synchronized
    fun clear() = cache.clear()
    
    /**
     * Returns the current number of entries in the cache.
     */
    @Synchronized
    fun size(): Int = cache.size
    
    /**
     * Returns true if the cache contains the specified [key].
     */
    @Synchronized
    fun containsKey(key: K): Boolean = cache.containsKey(key)
    
    /**
     * Returns a snapshot of all keys currently in the cache.
     * The returned set is a copy and modifications do not affect the cache.
     */
    @Synchronized
    fun keys(): Set<K> = cache.keys.toSet()
    
    /**
     * Returns a snapshot of all values currently in the cache.
     * The returned list is a copy and modifications do not affect the cache.
     */
    @Synchronized
    fun values(): List<V> = cache.values.toList()
    
    /**
     * Performs the given [action] for each entry in the cache.
     */
    @Synchronized
    fun forEach(action: (K, V) -> Unit) {
        cache.forEach { (k, v) -> action(k, v) }
    }
}
