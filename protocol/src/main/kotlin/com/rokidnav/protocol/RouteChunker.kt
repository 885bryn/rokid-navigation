package com.rokidnav.protocol

/** Splits a route polyline into [RouteChunk]s that fit under [MAX_FRAME_BYTES]. */
object RouteChunker {
    // Halved because '\' (valid in encoded polylines) becomes 2 bytes in JSON; 512 covers the envelope.
    private const val CHUNK_CHARS = (MAX_FRAME_BYTES - 512) / 2

    fun split(
        routeId: String,
        encodedPolyline: String,
        totalMeters: Int,
        nextSeq: () -> Long,
    ): List<RouteChunk> {
        val parts = encodedPolyline.chunked(CHUNK_CHARS).ifEmpty { listOf("") }
        return parts.mapIndexed { i, part ->
            RouteChunk(nextSeq(), routeId, i, parts.size, part, totalMeters)
        }
    }
}

/** Glasses-side reassembly. Feed every [RouteChunk]; returns the full polyline once complete. */
class RouteAssembler {
    private var routeId: String? = null
    private var parts: Array<String?> = emptyArray()

    fun accept(chunk: RouteChunk): String? {
        if (chunk.routeId != routeId || parts.size != chunk.chunkCount) {
            routeId = chunk.routeId
            parts = arrayOfNulls(chunk.chunkCount)
        }
        if (chunk.chunkIndex !in parts.indices) return null
        parts[chunk.chunkIndex] = chunk.encodedPolylinePart
        return if (parts.all { it != null }) parts.joinToString("") else null
    }

    /** Missing indices, for a [ResyncRequest] if a chunk never arrives. */
    fun missing(): List<Int> = parts.indices.filter { parts[it] == null }
}
