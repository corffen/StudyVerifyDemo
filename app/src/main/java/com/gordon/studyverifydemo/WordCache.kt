package com.gordon.studyverifydemo

import com.blankj.utilcode.util.CacheMemoryUtils
import kotlin.reflect.KProperty

object WordCache {
//
//    val CACHE by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
//        CacheMemoryUtils.getInstance(
//            "word-cache",
//            50
//        )
//    }
//
//    fun getAudioUrl(wordId: String) = CACHE.get<String>("${wordId}_audio")
//    fun putAudioUrl(wordId: String, audioUrl: String) = CACHE.put("${wordId}_audio", audioUrl)
//
//    fun getWordScore(wordId: String) = CACHE.get<Int>("${wordId}_score")
//    fun putWordScore(wordId: String, score: Int) = CACHE.put("${wordId}_score", score)

    var audioUrl by CacheManager<String>("audio_url")
    var wordScore by CacheManager<Int>("word_score")
}

class CacheManager<T>(
    private val cacheName: String,
    private val cacheSize: Int = 50
) {
    private val cache by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        CacheMemoryUtils.getInstance("word_cache", cacheSize)
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T? {
        return cache.get("${property.name}_$cacheName") as? T
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
        if (value != null) {
            cache.put("${property.name}_$cacheName", value)
        } else {
            cache.remove("${property.name}_$cacheName")
        }
    }
}