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
import com.tencent.bk.devops.git.core.enums.AuthHelperType
import com.tencent.bk.devops.git.core.enums.GitProtocolEnum
import com.tencent.bk.devops.git.core.pojo.GitSourceSettings
import com.tencent.bk.devops.git.core.service.GitCommandManager
import com.tencent.bk.devops.git.core.util.EnvHelper
import com.tencent.bk.devops.git.core.util.GitUtil.urlEncode
import org.slf4j.LoggerFactory
import java.net.URI

/**
 * 用户名密码的方式授权,这种方式用户名密码是明文存储在构建机上
 */
class UsernamePwdGitAuthHelper(
    private val git: GitCommandManager,
    private val settings: GitSourceSettings
) : AbGitAuthHelper(git = git, settings = settings) {

    companion object {
        private val logger = LoggerFactory.getLogger(UsernamePwdGitAuthHelper::class.java)
        private const val INSTEADOF_URL_CONFIG = "core.insteadOfUrl"
    }

    override fun configureHttp() {
        if (!serverInfo.httpProtocol ||
            authInfo.username.isNullOrBlank() ||
            authInfo.password.isNullOrBlank()
        ) {
            return
        }
        logger.info("using username and password to set credentials ${authInfo.username}/******")
        EnvHelper.putContext(ContextConstants.CONTEXT_GIT_PROTOCOL, GitProtocolEnum.HTTP.name)
        with(settings) {
            replaceUrl(repositoryUrl, GitConstants.ORIGIN_REMOTE_NAME)
            if (preMerge && !sourceRepoUrlEqualsRepoUrl) {
                replaceUrl(sourceRepositoryUrl, GitConstants.DEVOPS_VIRTUAL_REMOTE_NAME)
            }
        }
        git.config(
            configKey = GitConstants.GIT_CREDENTIAL_AUTH_HELPER,
            configValue = AuthHelperType.USERNAME_PASSWORD.name
        )
        EnvHelper.putContext(GitConstants.GIT_CREDENTIAL_AUTH_HELPER, AuthHelperType.USERNAME_PASSWORD.name)
    }

    private fun replaceUrl(url: String, remoteName: String) {
        val uri = URI(url)
        val authUrl = "${uri.scheme}://${authInfo.username}:${urlEncode(authInfo.password!!)}@${uri.host}/${uri.path}"
        git.remoteSetUrl(remoteName = remoteName, remoteUrl = authUrl)
    }

    override fun insteadOf() {
        val insteadOfKey = getInsteadofUrl()
        val insteadOfHosts = getHostList()
        insteadOfHosts.forEach { host ->
            httpInsteadOfGit(
                host = host,
                insteadOfKey = insteadOfKey
            )
        }
        insteadOfHosts.forEach { host ->
            gitInsteadOfHttp(
                host = host,
                insteadOfKey = insteadOfKey
            )
        }
    }

    override fun removeAuth() {
        with(settings) {
            git.remoteSetUrl(remoteName = GitConstants.ORIGIN_REMOTE_NAME, remoteUrl = repositoryUrl)
            if (preMerge && !sourceRepoUrlEqualsRepoUrl) {
                git.remoteSetUrl(remoteName = GitConstants.DEVOPS_VIRTUAL_REMOTE_NAME, remoteUrl = sourceRepositoryUrl)
            }
        }
    }

    override fun configureSubmoduleAuth() {
        val insteadOfUrl = getInsteadofUrl()
        val insteadOfHosts = getHostList()
        val gitInsteadOfCommand = insteadOfHosts.joinToString(";") { host ->
            " git config --add $insteadOfUrl git@$host: "
        }
        val httpInsteadOfCommandBuilder = StringBuilder()
        insteadOfHosts.forEach { host ->
            listOf("http", "https").forEach { protocol ->
                httpInsteadOfCommandBuilder.append(" git config --add $insteadOfUrl $protocol://$host/ ").append(";")
            }
        }
        // 卸载子模块时,不知道子模块的insteadOf配置key，先暂存卸载时直接获取
        git.config(
            configKey = INSTEADOF_URL_CONFIG,
            configValue = insteadOfUrl
        )
        git.submoduleForeach(
            command = "$gitInsteadOfCommand;${httpInsteadOfCommandBuilder.removeSuffix(";")} || true",
            recursive = settings.nestedSubmodules
        )
    }

    override fun removeSubmoduleAuth() {
        val insteadOfUrl = git.tryConfigGet(configKey = INSTEADOF_URL_CONFIG)
        if (insteadOfUrl.isNotBlank()) {
            git.tryConfigUnset(configKey = INSTEADOF_URL_CONFIG)
            git.submoduleForeach(
                command = "git config --unset-all $insteadOfUrl; " +
                    "git config --remove-section ${insteadOfUrl.removeSuffix(".insteadOf")} || true",
                recursive = settings.nestedSubmodules
            )
        }
    }

    private fun getInsteadofUrl(): String {
        val uri = URI(settings.repositoryUrl)
        return "url.${uri.scheme}://${authInfo.username}:${urlEncode(authInfo.password!!)}@${uri.host}/.insteadOf"
    }
}
