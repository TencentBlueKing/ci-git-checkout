/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bk.devops.git.core.service.helper

import com.tencent.bk.devops.git.core.constant.ContextConstants
import com.tencent.bk.devops.git.core.constant.GitConstants.BK_CI_BUILD_JOB_ID
import com.tencent.bk.devops.git.core.constant.GitConstants.CREDENTIAL_JAR_PATH
import com.tencent.bk.devops.git.core.constant.GitConstants.CREDENTIAL_JAVA_PATH
import com.tencent.bk.devops.git.core.constant.GitConstants.GIT_CREDENTIAL_COMPATIBLEHOST
import com.tencent.bk.devops.git.core.constant.GitConstants.GIT_CREDENTIAL_HELPER
import com.tencent.bk.devops.git.core.constant.GitConstants.GIT_CREDENTIAL_HELPER_VALUE_REGEX
import com.tencent.bk.devops.git.core.constant.GitConstants.GIT_REPO_PATH
import com.tencent.bk.devops.git.core.enums.GitConfigScope
import com.tencent.bk.devops.git.core.enums.GitProtocolEnum
import com.tencent.bk.devops.git.core.pojo.CredentialArguments
import com.tencent.bk.devops.git.core.pojo.GitSourceSettings
import com.tencent.bk.devops.git.core.service.GitCommandManager
import com.tencent.bk.devops.git.core.util.CommandUtil
import com.tencent.bk.devops.git.core.util.EnvHelper
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URL

/**
 * 使用自定义git-checkout-credential凭证
 */
@Suppress("ALL")
class CredentialAuthHelper(
    private val git: GitCommandManager,
    private val settings: GitSourceSettings
) : AbGitAuthHelper(git = git, settings = settings) {

    companion object {
        private val logger = LoggerFactory.getLogger(CredentialAuthHelper::class.java)
    }

    private val credentialVersion = VersionHelper.getCredentialVersion()
    private val credentialJarFileName = if (credentialVersion.isNotBlank()) {
        "git-checkout-credential-$credentialVersion.jar"
    } else {
        "git-checkout-credential.jar"
    }
    private val credentialShellFileName = "git-checkout.sh"
    private val credentialHome = File(System.getProperty("user.home"), ".checkout").absolutePath
    private val credentialJarPath = File(credentialHome, credentialJarFileName).absolutePath
    private val credentialShellPath = File(credentialHome, credentialShellFileName).absolutePath

    override fun configureHttp() {
        if (!serverInfo.httpProtocol ||
            authInfo.username.isNullOrBlank() ||
            authInfo.password.isNullOrBlank()
        ) {
            return
        }

        logger.info("using custom credential helper to set credentials ${authInfo.username}/******")
        EnvHelper.putContext(ContextConstants.CONTEXT_GIT_PROTOCOL, GitProtocolEnum.HTTP.name)
        val compatibleHostList = settings.compatibleHostList
        if (!compatibleHostList.isNullOrEmpty() && compatibleHostList.contains(serverInfo.hostName)) {
            git.config(
                configKey = GIT_CREDENTIAL_COMPATIBLEHOST,
                configValue = compatibleHostList.joinToString(","),
                configScope = GitConfigScope.GLOBAL
            )
        }
        val jobId = System.getenv(BK_CI_BUILD_JOB_ID)
        EnvHelper.addEnvVariable("${CREDENTIAL_JAVA_PATH}_$jobId", getJavaFilePath())
        EnvHelper.addEnvVariable("${CREDENTIAL_JAR_PATH}_$jobId", credentialJarFileName)
        git.setEnvironmentVariable("${CREDENTIAL_JAVA_PATH}_$jobId", getJavaFilePath())
        git.setEnvironmentVariable("${CREDENTIAL_JAR_PATH}_$jobId", credentialJarFileName)
        install()
        store()
    }

    private fun install() {
        val credentialJarParentFile = File(credentialHome)
        if (!credentialJarParentFile.exists()) {
            credentialJarParentFile.mkdirs()
        }
        copyCredentialFile(
            sourceFilePath = "script/$credentialJarFileName",
            targetFile = File(credentialJarPath)
        )

        replaceCredentialFile(
            sourceFilePath = "script/$credentialShellFileName",
            targetFile = File(credentialShellPath)
        )
        // 凭证管理必须安装在全局,否则无法传递给其他插件
        if (!git.configExists(
                configKey = GIT_CREDENTIAL_HELPER,
                configValueRegex = GIT_CREDENTIAL_HELPER_VALUE_REGEX,
                configScope = GitConfigScope.GLOBAL
            )
        ) {
            git.configAdd(
                configKey = GIT_CREDENTIAL_HELPER,
                configValue = "!bash '$credentialShellPath'",
                configScope = GitConfigScope.GLOBAL
            )
        }
    }

    private fun store() {
        with(URL(settings.repositoryUrl).toURI()) {
            CommandUtil.execute(
                executable = getJavaFilePath(),
                args = listOf(
                    "-Dfile.encoding=utf-8",
                    "-Ddebug=${settings.enableTrace}",
                    "-jar",
                    credentialJarPath,
                    "devopsStore"
                ),
                runtimeEnv = mapOf(
                    GIT_REPO_PATH to settings.repositoryPath
                ),
                inputStream = CredentialArguments(
                    protocol = scheme,
                    host = host,
                    path = path.removePrefix("/"),
                    username = authInfo.username,
                    password = authInfo.password
                ).convertInputStream()
            )
        }
    }

    private fun copyCredentialFile(sourceFilePath: String, targetFile: File) {
        if (!targetFile.exists()) {
            javaClass.classLoader.getResourceAsStream(sourceFilePath)?.use { sourceInputStream ->
                FileUtils.copyToFile(sourceInputStream, targetFile)
            }
        }
    }

    private fun replaceCredentialFile(sourceFilePath: String, targetFile: File) {
        if (!targetFile.exists()) {
            javaClass.classLoader.getResourceAsStream(sourceFilePath)?.use { sourceInputStream ->
                FileUtils.copyToFile(sourceInputStream, targetFile)
            }
        } else {
            val newFileMd5 = javaClass.classLoader.getResourceAsStream(sourceFilePath)?.use { DigestUtils.md5Hex(it) }
            val oldFileMd5 = targetFile.inputStream().use { DigestUtils.md5Hex(it) }
            if (newFileMd5 != oldFileMd5) {
                targetFile.delete()
                javaClass.classLoader.getResourceAsStream(sourceFilePath)?.use { sourceInputStream ->
                    FileUtils.copyToFile(sourceInputStream, targetFile)
                }
            }
        }
    }

    private fun getJavaFilePath() = File(System.getProperty("java.home"), "/bin/java").absolutePath

    override fun removeAuth() {
        if (!serverInfo.httpProtocol) {
            return
        }
        // 清理构建机上凭证
        if (File(credentialJarPath).exists()) {
            with(URL(settings.repositoryUrl).toURI()) {
                CommandUtil.execute(
                    executable = getJavaFilePath(),
                    args = listOf(
                        "-Dfile.encoding=utf-8",
                        "-Ddebug=${settings.enableTrace}",
                        "-jar",
                        credentialJarPath,
                        "devopsErase"
                    ),
                    runtimeEnv = mapOf(
                        GIT_REPO_PATH to settings.repositoryPath
                    ),
                    inputStream = CredentialArguments(
                        protocol = scheme,
                        host = host,
                        path = path.removePrefix("/")
                    ).convertInputStream()
                )
            }
        }
    }
}
