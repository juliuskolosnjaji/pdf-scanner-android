package com.julius.pdfscanner.processing

data class ContactInfo(
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val company: String? = null,
    val website: String? = null
) {
    val isEmpty get() = name == null && phone == null && email == null
}

object ContactExtractor {

    private val phoneRegex = Regex("""[\+]?[\d][\d\s\-\(\)\.]{6,18}[\d]""")
    private val emailRegex = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}""")
    private val websiteRegex = Regex("""(https?://|www\.)[^\s]{4,}""")

    fun extract(rawText: String): ContactInfo {
        val phone = phoneRegex.find(rawText)?.value?.trim()
        val email = emailRegex.find(rawText)?.value?.trim()
        val website = websiteRegex.find(rawText)?.value?.trim()

        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.length > 1 }

        // Name: first line that isn't a phone/email/url and looks like a name
        val name = lines.firstOrNull { line ->
            !phoneRegex.containsMatchIn(line) &&
            !emailRegex.containsMatchIn(line) &&
            !websiteRegex.containsMatchIn(line) &&
            line.split(" ").size in 1..5 &&
            line.all { it.isLetter() || it.isWhitespace() || it == '-' || it == '.' }
        }

        // Company: second candidate name-like line (all caps or follows name)
        val company = lines.drop(1).firstOrNull { line ->
            !phoneRegex.containsMatchIn(line) &&
            !emailRegex.containsMatchIn(line) &&
            !websiteRegex.containsMatchIn(line) &&
            line != name &&
            line.length > 2
        }

        return ContactInfo(name = name, phone = phone, email = email, company = company, website = website)
    }
}
