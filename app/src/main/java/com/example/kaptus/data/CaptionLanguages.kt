package com.example.kaptus.data

/** Offline selection seed only. The provider catalog remains authoritative when available. */
object CaptionLanguages {
    val fallback: List<CaptionLanguage> = listOf(
        "af" to "Afrikaans", "sq" to "Albanian", "ar" to "Arabic", "hy" to "Armenian",
        "az" to "Azerbaijani", "eu" to "Basque", "be" to "Belarusian", "bn" to "Bengali",
        "bs" to "Bosnian", "bg" to "Bulgarian", "ca" to "Catalan",
        "zh-cn" to "Chinese (simplified)", "zh-tw" to "Chinese (traditional)",
        "hr" to "Croatian", "cs" to "Czech", "da" to "Danish", "nl" to "Dutch",
        "en" to "English", "eo" to "Esperanto", "et" to "Estonian", "fi" to "Finnish",
        "fr" to "French", "gl" to "Galician", "ka" to "Georgian", "de" to "German",
        "el" to "Greek", "gu" to "Gujarati", "he" to "Hebrew", "hi" to "Hindi",
        "hu" to "Hungarian", "is" to "Icelandic", "id" to "Indonesian", "it" to "Italian",
        "ja" to "Japanese", "kn" to "Kannada", "kk" to "Kazakh", "km" to "Khmer",
        "ko" to "Korean", "lv" to "Latvian", "lt" to "Lithuanian", "mk" to "Macedonian",
        "ms" to "Malay", "ml" to "Malayalam", "mr" to "Marathi", "mn" to "Mongolian",
        "ne" to "Nepali", "no" to "Norwegian", "fa" to "Persian", "pl" to "Polish",
        "pt-br" to "Portuguese (Brazil)", "pt-pt" to "Portuguese (Portugal)",
        "pa" to "Punjabi", "ro" to "Romanian", "ru" to "Russian", "sr" to "Serbian",
        "si" to "Sinhala", "sk" to "Slovak", "sl" to "Slovenian", "es" to "Spanish",
        "sw" to "Swahili", "sv" to "Swedish", "ta" to "Tamil", "te" to "Telugu",
        "th" to "Thai", "tr" to "Turkish", "uk" to "Ukrainian", "ur" to "Urdu",
        "vi" to "Vietnamese", "cy" to "Welsh"
    ).map { (code, name) -> CaptionLanguage(code, name) }
}
