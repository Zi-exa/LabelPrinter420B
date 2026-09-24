package com.raphael.labelprinter

data class PrinterCandidate(
    val name: String?,
    val address: String,
)

object PrinterCandidateSelector {
    fun select(
        candidates: Collection<PrinterCandidate>,
        savedAddress: String?,
        excludedAddress: String? = null,
    ): PrinterCandidate? {
        val automaticCandidates = candidates.filterNot {
            !excludedAddress.isNullOrBlank() &&
                it.address.equals(excludedAddress, ignoreCase = true)
        }
        if (!savedAddress.isNullOrBlank() &&
            !savedAddress.equals(excludedAddress, ignoreCase = true)
        ) {
            automaticCandidates.firstOrNull {
                it.address.equals(savedAddress, ignoreCase = true)
            }?.let {
                return it
            }
        }

        return automaticCandidates.maxByOrNull(::xprinterScore)
            ?.takeIf { xprinterScore(it) > 0 }
    }

    private fun xprinterScore(candidate: PrinterCandidate): Int {
        val name = candidate.name.orEmpty().uppercase().replace(" ", "")
        return when {
            "XP-420B" in name || "XP420B" in name -> 3
            "XPRINTER" in name -> 2
            name.startsWith("XP-") -> 1
            else -> 0
        }
    }
}
