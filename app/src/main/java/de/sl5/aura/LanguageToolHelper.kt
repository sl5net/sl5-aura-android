package de.sl5.aura

import org.languagetool.JLanguageTool
import org.languagetool.language.German

object LanguageToolHelper {
    private val langTool = JLanguageTool(org.languagetool.language.German())

    fun correctText(text: String): String {
        val propertyName = "javax.xml.parsers.SAXParserFactory"
        val oldFactory = System.getProperty(propertyName)

        try {
            System.setProperty(propertyName, "org.apache.xerces.jaxp.SAXParserFactoryImpl")

            val matches = langTool.check(text)
            var correctedText = text
            var offsetCorrection = 0

            for (match in matches) {
                if (match.suggestedReplacements.isNotEmpty()) {
                    val replacement = match.suggestedReplacements.first()
                    val from = match.fromPos + offsetCorrection
                    val to = match.toPos + offsetCorrection

                    correctedText = correctedText.replaceRange(from, to, replacement)
                    offsetCorrection += replacement.length - (match.toPos - match.fromPos)
                }
            }
            return correctedText

        } finally {
            if (oldFactory != null) {
                System.setProperty(propertyName, oldFactory)
            } else {
                System.clearProperty(propertyName)
            }
        }
    }
}