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
import com.tencent.bk.devops.git.core.constant.GitConstants
import com.tencent.bk.devops.git.core.constant.GitConstants.HOME
import com.tencent.bk.devops.git.core.enums.GitConfigScope
import com.tencent.bk.devops.git.core.enums.GitProtocolEnum
import com.tencent.bk.devops.git.core.exception.ParamInvalidException
import com.tencent.bk.devops.git.core.pojo.GitSourceSettings
import com.tencent.bk.devops.git.core.service.GitCommandManager
import com.tencent.bk.devops.git.core.util.AgentEnv
import com.tencent.bk.devops.git.core.util.EnvHelper
import com.tencent.bk.devops.git.core.util.FileUtils
import com.tencent.bk.devops.git.core.util.GitUtil
import com.tencent.bk.devops.git.core.util.SSHAgentUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

@SuppressWarnings("TooManyFunctions")
abstract class AbGitAuthHelper(
    private val git: GitCommandManager,
    private val settings: GitSourceSettings
) : IGitAuthHelper {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }

    protected val serverInfo = GitUtil.getServerInfo(settings.repositoryUrl)
    protected val authInfo = settings.authInfo
    protected val credentialUserName = "${settings.pipelineId}_${settings.pipelineTaskId}"

    override fun configureAuth() {
        configureHttp()
        configureSsh()
    }

    override fun configGlobalAuth() {
        // 蓝盾默认镜像中有insteadOf,应该卸载,不然在凭证传递到下游插件时会导致凭证失效
        if (!AgentEnv.isThirdParty()) {
            unsetInsteadOf()
        }
        val tempHomePath = Files.createTempDirectory("checkout")
        val gitConfigPath = Paths.get(System.getenv(HOME), ".gitconfig")
        val newGitConfigPath = Paths.get(tempHomePath.toString(), ".gitconfig")
        if (Files.exists(gitConfigPath)) {
            logger.info("Copying $gitConfigPath to $newGitConfigPath")
            Files.copy(gitConfigPath, newGitConfigPath)
        } else {
            Files.createFile(newGitConfigPath)
        }
        logger.info("Temporarily overriding HOME='$tempHomePath' before making global git config changes")
        git.setEnvironmentVariable(HOME, tempHomePath.toString())
        if (AgentEnv.isThirdParty()) {
            unsetInsteadOf()
        }
        insteadOf()
    }

    override fun removeGlobalAuth() {
        val tempHome = git.removeEnvironmentVariable(HOME)
        if (!tempHome.isNullOrBlank()) {
            logger.info("Deleting Temporarily HOME='$tempHome'")
            FileUtils.deleteDirectory(File(tempHome))
        }
    }

    override fun configureSubmoduleAuth() {
        val insteadOfKey = "url.${serverInfo.origin}/.insteadOf"
        val insteadOfHosts = getHostList()
        insteadOfHosts.forEach { host ->
            git.submoduleForeach("git config --add $insteadOfKey git@$host:", settings.nestedSubmodules)
        }
    }

    override fun removeSubmoduleAuth() {
        val insteadOfKey = "url.${serverInfo.origin}/.insteadOf"
        git.submoduleForeach("git config --unset-all $insteadOfKey", settings.nestedSubmodules)
    }

    abstract fun configureHttp()

    private fun configureSsh() {
        if (serverInfo.httpProtocol) {
            return
        }
        if (authInfo.privateKey.isNullOrBlank()) {
            throw ParamInvalidException(errorMsg = "private key must not be empty")
        }
        EnvHelper.putContext(ContextConstants.CONTEXT_GIT_PROTOCOL, GitProtocolEnum.SSH.name)
        SSHAgentUtils(privateKey = authInfo.privateKey, passPhrase = authInfo.passPhrase).addIdentity()
        git.setEnvironmentVariable(GitConstants.GIT_SSH_COMMAND, GitConstants.GIT_SSH_COMMAND_VALUE)
    }

    protected open fun insteadOf() {
        val insteadOfHosts = getHostList()
        val insteadOfKey = "url.${serverInfo.origin}/.insteadOf"
        git.tryConfigUnset(
            configKey = insteadOfKey,
            configScope = GitConfigScope.GLOBAL
        )
        if (serverInfo.httpProtocol) {
            insteadOfHosts.forEach { host ->
                httpInsteadOfGit(
                    host = host,
                    insteadOfKey = insteadOfKey
                )
            }
        } else {
            insteadOfHosts.forEach { host ->
                gitInsteadOfHttp(
                    host = host,
                    insteadOfKey = insteadOfKey
                )
            }
        }
    }

    protected fun getHostList(): MutableSet<String> {
        val insteadOfHosts = mutableSetOf(serverInfo.hostName)
        // 当一个git远程仓库有多个域名时，需要都兼容
        val compatibleHostList = settings.compatibleHostList
        if (!compatibleHostList.isNullOrEmpty() && compatibleHostList.contains(serverInfo.hostName)) {
            insteadOfHosts.addAll(compatibleHostList)
        }
        return insteadOfHosts
    }

    /**
     * 删除全局的insteadOf
     */
    private fun unsetInsteadOf() {
        val insteadOfHosts = getHostList()
        if (serverInfo.httpProtocol) {
            insteadOfHosts.forEach { host ->
                unsetGitInsteadOfHttp(host = host)
            }
        } else {
            insteadOfHosts.forEach { host ->
                unsetHttpInsteadOfGit(host = host)
            }
        }
    }

    private fun unsetGitInsteadOfHttp(host: String) {
        git.tryConfigUnset(
            configKey = "url.git@$host:.insteadof",
            configScope = GitConfigScope.GLOBAL
        )
    }

    private fun unsetHttpInsteadOfGit(host: String) {
        listOf("http", "https").forEach { protocol ->
            git.tryConfigUnset(
                configKey = "url.$protocol://$host/.insteadof",
                configScope = GitConfigScope.GLOBAL
            )
        }
    }

    protected fun httpInsteadOfGit(host: String, insteadOfKey: String) {
        git.configAdd(
            configKey = insteadOfKey,
            configValue = "git@$host:",
            configScope = GitConfigScope.GLOBAL
        )
    }

    protected fun gitInsteadOfHttp(host: String, insteadOfKey: String) {
        listOf("http", "https").forEach { protocol ->
            git.configAdd(
                configKey = insteadOfKey,
                configValue = "$protocol://$host/",
                configScope = GitConfigScope.GLOBAL
            )
        }
    }
}
