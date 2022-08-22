package com.tencent.bk.devops.git.core.util

import java.util.regex.Pattern

object SensitiveLineParser {
    private val urlPasswordPattern = Pattern.compile("(.*)(http[s]?://)(.*):(.*@)([-.a-z0-9A-Z]+)(:[0-9]+)?/(.*)")
    private val patternCredentialPassword = Pattern.compile("password=.*")

    @SuppressWarnings("ReturnCount")
    fun onParseLine(line: String): String {
        val matcher = urlPasswordPattern.matcher(line)
        return when {
            matcher.find() ->
                matcher.replaceFirst("$1$2$3:***@$5$6/$7")
            line.contains("password=") -> {
                patternCredentialPassword.matcher(line).replaceAll("password=***")
            }
            else ->
                line
        }
    }
}
