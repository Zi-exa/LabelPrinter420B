package com.raphael.labelprinter

object PageRangeParser {
    fun parse(input: String?, totalPages: Int): List<Int> {
        require(totalPages > 0) { "PDF tidak memiliki halaman" }
        if (input.isNullOrBlank()) return (1..totalPages).toList()

        val result = mutableListOf<Int>()
        val seen = mutableSetOf<Int>()
        val tokens = input.split(",")

        for (raw in tokens) {
            val token = raw.trim()
            if (token.isEmpty()) continue
            if ("-" in token) {
                val parts = token.split("-", limit = 2)
                if (parts.size != 2) throw IllegalArgumentException("Rentang tidak valid: $token")
                val start = parts[0].trim().toIntOrNull()
                    ?: throw IllegalArgumentException("Halaman tidak valid: ${parts[0].trim()}")
                val end = parts[1].trim().toIntOrNull()
                    ?: throw IllegalArgumentException("Halaman tidak valid: ${parts[1].trim()}")
                if (start < 1 || end < 1) throw IllegalArgumentException("Halaman harus >= 1: $token")
                if (start > totalPages || end > totalPages) {
                    throw IllegalArgumentException("Halaman melebihi total $totalPages: $token")
                }
                if (start > end) throw IllegalArgumentException("Rentang terbalik: $token")
                for (p in start..end) {
                    if (seen.add(p)) result.add(p)
                }
            } else {
                val p = token.toIntOrNull()
                    ?: throw IllegalArgumentException("Halaman tidak valid: $token")
                if (p < 1 || p > totalPages) {
                    throw IllegalArgumentException("Halaman melebihi total $totalPages: $token")
                }
                if (seen.add(p)) result.add(p)
            }
        }

        if (result.isEmpty()) throw IllegalArgumentException("Rentang halaman kosong")

        // Keep user order but stable; if user typed "3,1" we preserve 3 then 1.
        // No sorting to respect explicit order. De-duplicated via seen.
        return result
    }
}
