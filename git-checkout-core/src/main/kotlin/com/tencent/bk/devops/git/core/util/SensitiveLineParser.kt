package com.tencent.bk.devops.git.core.util

import java.util.regex.Pattern

object SensitiveLineParser {
    private val urlPasswordPattern = Pattern.compile("(.*)(http[s]?://)(.*):(.*?)@(.*)")
    private val patternCredentialPassword = Pattern.compile("password=.*")

    @SuppressWarnings("ReturnCount")
    fun onParseLine(line: String): String {
        val matcher = urlPasswordPattern.matcher(line)
        return when {
            matcher.find() ->
                matcher.replaceFirst("$1$2$3:***@$5")
            line.contains("password=") -> {
                patternCredentialPassword.matcher(line).replaceAll("password=***")
            }
            else ->
                line
        }
    }
}
