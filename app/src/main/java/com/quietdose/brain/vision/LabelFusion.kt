package com.quietdose.brain.vision

import com.quietdose.data.model.DoseUnit
import com.quietdose.data.model.ItemType

/**
 * Fuses the OCR/barcode results of several photos of one product (front + back,
 * maybe a third) into a single richer read. The front usually carries the name;
 * the back carries ingredients, dose and directions — so the union of all lines
 * plus a structured dose/form guess gives the model (or the heuristic) much more
 * to work with than a single shot. Pure, on-device, dependency-free.
 */
object LabelFusion {

    data class FusedScan(
        val lines: List<String>,
        val barcode: String?,
        val prominentLine: String?,
        val combinedText: String,
        /** Best dose read off the labels, if any. */
        val dose: Pair<Double, DoseUnit>?,
    ) {
        val isEmpty: Boolean get() = lines.isEmpty() && barcode.isNullOrBlank()
    }

    fun fuse(results: List<LabelScanner.ScanResult>): FusedScan {
        val lines = results.flatMap { it.lines }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val barcode = results.firstNotNullOfOrNull { it.barcode?.ifBlank { null } }
        // Product name tends to be the longest mostly-alphabetic line (front of pack).
        val prominent = results.firstNotNullOfOrNull { it.prominentLine }
            ?: lines.filter { it.any(Char::isLetter) }.maxByOrNull { it.count(Char::isLetter) }
        val combined = buildString {
            lines.forEach { appendLine(it) }
            if (!barcode.isNullOrBlank()) append("Barcode: ").append(barcode)
        }.trim()
        return FusedScan(lines, barcode, prominent, combined, detectDose(combined))
    }

    /** Guess the form from any label text — defaults to capsule. */
    fun guessType(scan: FusedScan): ItemType {
        val t = scan.combinedText.lowercase()
        return when {
            t.contains("softgel") -> ItemType.SOFTGEL
            t.contains("tablet") -> ItemType.TABLET
            t.contains("gummies") || t.contains("gummy") -> ItemType.GUMMY
            t.contains("powder") -> ItemType.POWDER
            t.contains("spray") -> ItemType.SPRAY
            t.contains("sublingual") -> ItemType.SUBLINGUAL
            t.contains("liquid") || t.contains("syrup") || t.contains("drops") -> ItemType.LIQUID
            t.contains("capsule") || t.contains("caps") -> ItemType.CAPSULE
            else -> ItemType.CAPSULE
        }
    }

    private fun detectDose(text: String): Pair<Double, DoseUnit>? {
        val m = Regex("(\\d+(?:\\.\\d+)?)\\s*(mcg|µg|mg|iu|g|ml)", RegexOption.IGNORE_CASE).find(text) ?: return null
        val amt = m.groupValues[1].toDoubleOrNull() ?: return null
        val unit = when (m.groupValues[2].lowercase()) {
            "mg" -> DoseUnit.MG
            "mcg", "µg" -> DoseUnit.MCG
            "g" -> DoseUnit.G
            "iu" -> DoseUnit.IU
            "ml" -> DoseUnit.ML
            else -> return null
        }
        return amt to unit
    }
}
