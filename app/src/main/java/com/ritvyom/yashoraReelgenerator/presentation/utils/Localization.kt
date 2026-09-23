package com.ritvyom.yashoraReelgenerator.presentation.utils

/**
 * Universal High-Fidelity Localization Engine for Yashora Reel Generator.
 * Provides complete, dynamic translation across all supported languages (48+ languages & dialects)
 * ensuring every single UI label, dialog, button, and header updates instantly when the user selects a language.
 */
object Localization {

    /**
     * Complete map of normalized language keys to translation dictionaries.
     * All 48 languages supported by Yashora Reel Generator are registered here.
     */
    val TRAN_LANG_MAPS: Map<String, Map<String, String>> = mapOf(
        // Indian Languages
        "HINDI" to LocalizationIndian.HINDI_MAP,
        "HINGLISH" to LocalizationIndian.HINGLISH_MAP,
        "BENGALI" to LocalizationIndian.BENGALI_MAP,
        "MARATHI" to LocalizationIndian.MARATHI_MAP,
        "TELUGU" to LocalizationIndian.TELUGU_MAP,
        "TAMIL" to LocalizationIndian.TAMIL_MAP,
        "GUJARATI" to LocalizationIndian.GUJARATI_MAP,
        "URDU" to LocalizationIndian.URDU_MAP,
        "KANNADA" to LocalizationIndian.KANNADA_MAP,
        "ODIA" to LocalizationIndian.ODIA_MAP,
        "MALAYALAM" to LocalizationIndian.MALAYALAM_MAP,
        "PUNJABI" to LocalizationIndian.PUNJABI_MAP,
        "ASSAMESE" to LocalizationIndian.ASSAMESE_MAP,
        "NEPALI" to LocalizationIndian.NEPALI_MAP,
        "SINHALA" to LocalizationIndian.SINHALA_MAP,

        // European Languages
        "SPANISH" to LocalizationEuropean.SPANISH_MAP,
        "FRENCH" to LocalizationEuropean.FRENCH_MAP,
        "GERMAN" to LocalizationEuropean.GERMAN_MAP,
        "ITALIAN" to LocalizationEuropean.ITALIAN_MAP,
        "PORTUGUESE" to LocalizationEuropean.PORTUGUESE_MAP,
        "RUSSIAN" to LocalizationEuropean.RUSSIAN_MAP,
        "UKRAINIAN" to LocalizationEuropean.UKRAINIAN_MAP,
        "POLISH" to LocalizationEuropean.POLISH_MAP,
        "DUTCH" to LocalizationEuropean.DUTCH_MAP,
        "SWEDISH" to LocalizationEuropean.SWEDISH_MAP,
        "NORWEGIAN" to LocalizationEuropean.NORWEGIAN_MAP,
        "FINNISH" to LocalizationEuropean.FINNISH_MAP,
        "GREEK" to LocalizationEuropean.GREEK_MAP,
        "ROMANIAN" to LocalizationEuropean.ROMANIAN_MAP,
        "HUNGARIAN" to LocalizationEuropean.HUNGARIAN_MAP,
        "CZECH" to LocalizationEuropean.CZECH_MAP,

        // Global Languages (Asia, Middle East, Africa)
        "ARABIC" to LocalizationGlobal.ARABIC_MAP,
        "PERSIAN" to LocalizationGlobal.PERSIAN_MAP,
        "HEBREW" to LocalizationGlobal.HEBREW_MAP,
        "TURKISH" to LocalizationGlobal.TURKISH_MAP,
        "CHINESE" to LocalizationGlobal.CHINESE_SIMPLIFIED_MAP,
        "CHINESE (SIMPLIFIED)" to LocalizationGlobal.CHINESE_SIMPLIFIED_MAP,
        "CHINESE (TRADITIONAL)" to LocalizationGlobal.CHINESE_TRADITIONAL_MAP,
        "JAPANESE" to LocalizationGlobal.JAPANESE_MAP,
        "KOREAN" to LocalizationGlobal.KOREAN_MAP,
        "INDONESIAN" to LocalizationGlobal.INDONESIAN_MAP,
        "MALAY" to LocalizationGlobal.MALAY_MAP,
        "VIETNAMESE" to LocalizationGlobal.VIETNAMESE_MAP,
        "THAI" to LocalizationGlobal.THAI_MAP,
        "FILIPINO" to LocalizationGlobal.FILIPINO_MAP,
        "SWAHILI" to LocalizationGlobal.SWAHILI_MAP,
        "HAUSA" to LocalizationGlobal.HAUSA_MAP,
        "AMHARIC" to LocalizationGlobal.AMHARIC_MAP
    )

    /**
     * Normalizes any input language code or name into a canonical uppercase lookup key.
     * Matches codes and display names across all 48 languages in LanguageData.
     */
    fun normalizeLanguageKey(language: String): String {
        val clean = language.trim().uppercase()
        return when {
            clean.contains("HINGLISH") -> "HINGLISH"
            clean.contains("HINDI") || clean == "HI" -> "HINDI"
            clean.contains("SPANISH") || clean.contains("ESPAÑOL") || clean == "ES" -> "SPANISH"
            clean.contains("FRENCH") || clean.contains("FRANÇAIS") || clean == "FR" -> "FRENCH"
            clean.contains("GERMAN") || clean.contains("DEUTSCH") || clean == "DE" -> "GERMAN"
            clean.contains("ARABIC") || clean.contains("العربية") || clean == "AR" -> "ARABIC"
            clean.contains("TRADITIONAL") || clean.contains("繁體") -> "CHINESE (TRADITIONAL)"
            clean.contains("SIMPLIFIED") || clean.contains("简体") || clean.contains("CHINESE") || clean.contains("中文") || clean == "ZH" -> "CHINESE (SIMPLIFIED)"
            clean.contains("JAPANESE") || clean.contains("日本語") || clean == "JA" -> "JAPANESE"
            clean.contains("PORTUGUESE") || clean.contains("PORTUGUÊS") || clean == "PT" -> "PORTUGUESE"
            clean.contains("RUSSIAN") || clean.contains("РУССКИЙ") || clean == "RU" -> "RUSSIAN"
            clean.contains("TURKISH") || clean.contains("TÜRKÇE") || clean == "TR" -> "TURKISH"
            clean.contains("URDU") || clean.contains("اردو") || clean == "UR" -> "URDU"
            clean.contains("BENGALI") || clean.contains("বাংলা") || clean == "BN" -> "BENGALI"
            clean.contains("MARATHI") || clean.contains("मराठी") || clean == "MR" -> "MARATHI"
            clean.contains("TELUGU") || clean.contains("తెలుగు") || clean == "TE" -> "TELUGU"
            clean.contains("TAMIL") || clean.contains("தமிழ்") || clean == "TA" -> "TAMIL"
            clean.contains("GUJARATI") || clean.contains("ગુજરાતી") || clean == "GU" -> "GUJARATI"
            clean.contains("KANNADA") || clean.contains("ಕನ್ನಡ") || clean == "KN" -> "KANNADA"
            clean.contains("ODIA") || clean.contains("ଓଡ଼ିଆ") || clean == "OR" -> "ODIA"
            clean.contains("MALAYALAM") || clean.contains("മലയാളം") || clean == "ML" -> "MALAYALAM"
            clean.contains("PUNJABI") || clean.contains("ਪੰਜਾਬੀ") || clean == "PA" -> "PUNJABI"
            clean.contains("ASSAMESE") || clean.contains("অসমীয়া") || clean == "AS" -> "ASSAMESE"
            clean.contains("KOREAN") || clean.contains("한국어") || clean == "KO" -> "KOREAN"
            clean.contains("ITALIAN") || clean.contains("ITALIANO") || clean == "IT" -> "ITALIAN"
            clean.contains("DUTCH") || clean.contains("NEDERLANDS") || clean == "NL" -> "DUTCH"
            clean.contains("POLISH") || clean.contains("POLSKI") || clean == "PL" -> "POLISH"
            clean.contains("INDONESIAN") || clean.contains("INDONESIA") || clean == "ID" -> "INDONESIAN"
            clean.contains("MALAY") || clean == "MS" -> "MALAY"
            clean.contains("VIETNAMESE") || clean.contains("VIỆT") || clean == "VI" -> "VIETNAMESE"
            clean.contains("THAI") || clean.contains("ไทย") || clean == "TH" -> "THAI"
            clean.contains("FILIPINO") || clean.contains("TAGALOG") || clean == "TL" -> "FILIPINO"
            clean.contains("NEPALI") || clean.contains("नेपाली") || clean == "NE" -> "NEPALI"
            clean.contains("SINHALA") || clean.contains("සිංහල") || clean == "SI" -> "SINHALA"
            clean.contains("UKRAINIAN") || clean.contains("УКРАЇНСЬКА") || clean == "UK" -> "UKRAINIAN"
            clean.contains("GREEK") || clean.contains("ΕΛΛΗΝΙΚΆ") || clean == "EL" -> "GREEK"
            clean.contains("ROMANIAN") || clean.contains("ROMÂNĂ") || clean == "RO" -> "ROMANIAN"
            clean.contains("HUNGARIAN") || clean.contains("MAGYAR") || clean == "HU" -> "HUNGARIAN"
            clean.contains("CZECH") || clean.contains("ČEŠTINA") || clean == "CS" -> "CZECH"
            clean.contains("SWEDISH") || clean.contains("SVENSKA") || clean == "SV" -> "SWEDISH"
            clean.contains("NORWEGIAN") || clean.contains("NORSK") || clean == "NO" -> "NORWEGIAN"
            clean.contains("FINNISH") || clean.contains("SUOMI") || clean == "FI" -> "FINNISH"
            clean.contains("PERSIAN") || clean.contains("FARSI") || clean.contains("فارسی") || clean == "FA" -> "PERSIAN"
            clean.contains("HEBREW") || clean.contains("עברית") || clean == "HE" || clean == "IW" -> "HEBREW"
            clean.contains("SWAHILI") || clean.contains("KISWAHILI") || clean == "SW" -> "SWAHILI"
            clean.contains("HAUSA") || clean == "HA" -> "HAUSA"
            clean.contains("AMHARIC") || clean.contains("አማርኛ") || clean == "AM" -> "AMHARIC"
            else -> clean
        }
    }

    /**
     * Translates the given English text to the requested target language.
     * Guaranteed to never throw and returns the localized string dynamically.
     */
    fun translate(englishText: String, targetLanguage: String): String {
        if (englishText.isBlank()) return englishText
        val normKey = normalizeLanguageKey(targetLanguage)
        if (normKey == "ENGLISH" || normKey == "EN") return englishText

        val selectedMap = TRAN_LANG_MAPS[normKey] ?: return englishText

        // 1. Direct exact match
        selectedMap[englishText]?.let { return it }

        // 2. Trimmed match
        val trimmed = englishText.trim()
        selectedMap[trimmed]?.let { return it }

        // 3. Dynamic pattern: Loading scene asset X of Y...
        if (englishText.startsWith("Loading scene asset ")) {
            val pattern = Regex("""Loading scene asset (\d+) of (\d+)\.\.\.""")
            val match = pattern.find(englishText)
            if (match != null) {
                val (current, total) = match.destructured
                return when (normKey) {
                    "SPANISH" -> "Cargando recurso de escena $current de $total..."
                    "HINDI" -> "सीन एसेट $current / $total लोड हो रहा है..."
                    "HINGLISH" -> "Scene asset $current of $total load ho raha hai..."
                    "PORTUGUESE" -> "Carregando recurso de cena $current de $total..."
                    "FRENCH" -> "Chargement de la scène $current sur $total..."
                    "GERMAN" -> "Lade Szenen-Asset $current von $total..."
                    "RUSSIAN" -> "Загрузка ресурса сцены $current из $total..."
                    "ARABIC" -> "جارٍ تحميل عنصر المشهد $current من $total..."
                    "CHINESE", "CHINESE (SIMPLIFIED)" -> "正在加载场景资源 $current / $total..."
                    "CHINESE (TRADITIONAL)" -> "正在載入場景資源 $current / $total..."
                    "JAPANESE" -> "シーンアセット $current / $total を読み込み中..."
                    "ITALIAN" -> "Caricamento risorsa scena $current di $total..."
                    else -> "$current / $total..."
                }
            }
        }

        // 4. Pattern: Text ending with question mark (e.g. "Delete Scene?")
        if (trimmed.endsWith("?")) {
            val base = trimmed.substring(0, trimmed.length - 1).trim()
            val translatedBase = selectedMap[base]
            if (translatedBase != null) {
                return "$translatedBase?"
            }
        }

        // 5. Pattern: Text ending with parenthesis count (e.g. "Projects (3)", "Saved Scripts (5)")
        val countSuffixPattern = Regex("""^(.*)\s*\((\d+)\)$""")
        val countMatch = countSuffixPattern.find(trimmed)
        if (countMatch != null) {
            val (baseText, count) = countMatch.destructured
            val translatedBase = selectedMap[baseText.trim()]
            if (translatedBase != null) {
                return "$translatedBase ($count)"
            }
        }

        // 6. Pattern: Label with colon (e.g. "Selected: Male", "Speech Speed: 1.0x")
        if (trimmed.contains(": ")) {
            val label = trimmed.substringBefore(": ").trim()
            val value = trimmed.substringAfter(": ").trim()
            val translatedLabel = selectedMap[label] ?: selectedMap["$label: "]
            if (translatedLabel != null) {
                val cleanLabel = if (translatedLabel.endsWith(": ") || translatedLabel.endsWith(":")) translatedLabel.trimEnd(' ', ':') else translatedLabel
                val translatedValue = selectedMap[value] ?: value
                return "$cleanLabel: $translatedValue"
            }
        }

        // 7. Pattern: Leading/trailing emoji stripping and preservation (e.g. "Popular ⭐", "Indian Languages 🇮🇳")
        val emojiRegex = Regex("""^([\uD83C-\uDBFF\uDC00-\uDFFF\u2600-\u27BF\s]+)?(.*?)([\uD83C-\uDBFF\uDC00-\uDFFF\u2600-\u27BF\s]+)?$""")
        val emojiMatch = emojiRegex.find(trimmed)
        if (emojiMatch != null) {
            val (prefix, core, suffix) = emojiMatch.destructured
            if (core.isNotBlank() && (prefix.isNotBlank() || suffix.isNotBlank())) {
                val translatedCore = selectedMap[core.trim()]
                if (translatedCore != null) {
                    return "${prefix.trim()}${if (prefix.isNotBlank()) " " else ""}$translatedCore${if (suffix.isNotBlank()) " " else ""}${suffix.trim()}"
                }
            }
        }

        // 8. Dynamic Prefix match for compound UI strings
        for ((key, value) in selectedMap) {
            if (trimmed.startsWith(key) && key.length >= 4) {
                val remaining = trimmed.substring(key.length)
                val remainingTranslated = when (remaining.trim()) {
                    "Male" -> selectedMap["Male"] ?: "Male"
                    "Female" -> selectedMap["Female"] ?: "Female"
                    "Child" -> selectedMap["Child"] ?: "Child"
                    else -> remaining
                }
                return value + remainingTranslated
            }
        }

        // 9. Case-insensitive lookup as fallback
        val matchedEntry = selectedMap.entries.firstOrNull { it.key.equals(trimmed, ignoreCase = true) }
        if (matchedEntry != null) {
            return matchedEntry.value
        }

        return englishText
    }
}

/**
 * Extension function on String to translate/localize UI texts inline.
 */
fun String.localize(languageState: String): String {
    return Localization.translate(this, languageState)
}
