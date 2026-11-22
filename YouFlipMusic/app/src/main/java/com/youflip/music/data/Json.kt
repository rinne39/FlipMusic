package com.youflip.music.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Helpers for walking InnerTube's deeply nested, frequently changing JSON. */

/** Depth-first search for every value stored under [key], at any depth. */
fun JsonElement.findAll(key: String): List<JsonElement> {
    val out = mutableListOf<JsonElement>()
    fun walk(e: JsonElement) {
        when (e) {
            is JsonObject -> e.forEach { (k, v) -> if (k == key) out += v; walk(v) }
            is JsonArray -> e.forEach(::walk)
            else -> {}
        }
    }
    walk(this)
    return out
}

fun JsonElement.findFirst(key: String): JsonElement? = findAll(key).firstOrNull()

fun JsonElement.findStrings(key: String): List<String> =
    findAll(key).mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

/** Follows a path of object keys / array indices; returns null as soon as one step is missing. */
fun JsonElement?.at(vararg path: Any): JsonElement? {
    var cur: JsonElement? = this
    for (p in path) {
        cur = when {
            p is String && cur is JsonObject -> cur[p]
            p is Int && cur is JsonArray -> cur.getOrNull(p)
            else -> null
        } ?: return null
    }
    return cur
}

val JsonElement?.str: String? get() = (this as? JsonPrimitive)?.contentOrNull

/** Concatenates `{"runs":[{"text":…}]}` (or `simpleText`) into a plain string. */
fun JsonElement?.runsText(): String? {
    val o = this as? JsonObject ?: return null
    o["simpleText"].str?.let { return it }
    val runs = o["runs"] as? JsonArray ?: return null
    return runs.joinToString("") { it.at("text").str.orEmpty() }.takeIf { it.isNotBlank() }
}

/** Largest thumbnail URL found anywhere under this element. */
fun JsonElement?.bestThumbnail(): String? =
    this?.findAll("thumbnails")?.flatMap { (it as? JsonArray).orEmpty() }
        ?.maxByOrNull { (it.at("width").str?.toIntOrNull() ?: 0) }
        ?.at("url").str
