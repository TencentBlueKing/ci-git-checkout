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

import com.tencent.bk.devops.git.core.enums.CredentialActionEnum
import com.tencent.bk.devops.git.core.enums.GitConfigScope
import com.tencent.bk.devops.git.core.pojo.CredentialArguments
import com.tencent.bk.devops.git.core.pojo.GitSourceSettings
import com.tencent.bk.devops.git.core.service.GitCommandManager
import org.slf4j.LoggerFactory

abstract class HttpGitAuthHelper(
    private val git: GitCommandManager,
    private val settings: GitSourceSettings
) : AbGitAuthHelper(git = git, settings = settings) {

    companion object {
        private const val OAUTH2 = "oauth2"
        private val logger = LoggerFactory.getLogger(HttpGitAuthHelper::class.java)
    }

    override fun configureSubmoduleAuth() {
        val insteadOfHosts = getHostList()
        val commands = mutableListOf<String>()
        // 卸载上一步可能没有清理干净的insteadOf
        // windows 执行一条git submodule foreach都需要很久时间,将insteadOf组装在一起节省执行时间
        val insteadOfKey = "url.${serverInfo.origin}/.insteadOf"
        commands.add("git config --unset-all $insteadOfKey")
        insteadOfHosts.forEach { host ->
            commands.add("git config --add $insteadOfKey git@$host: ")
        }
        git.submoduleForeach("${commands.joinToString(";")} || true", settings.nestedSubmodules)
    }

    override fun removeSubmoduleAuth() {
        val insteadOfKey = "url.${serverInfo.origin}/.insteadOf"
        // git低版本卸载insteadOf后,但是url.*并没有卸载,需要指定再卸载
        git.submoduleForeach(
            " git config --unset-all $insteadOfKey; " +
                "git config --remove-section url.${serverInfo.origin}/ || true",
            settings.nestedSubmodules
        )
    }

    override fun insteadOf() {
        val insteadOfHosts = getHostList()
        val insteadOfKey = "url.${serverInfo.origin}/.insteadOf"
        git.tryConfigUnset(
            configKey = insteadOfKey,
            configScope = GitConfigScope.GLOBAL
        )
        insteadOfHosts.forEach { host ->
            httpInsteadOfGit(
                host = host,
                insteadOfKey = insteadOfKey
            )
        }
    }

    override fun unsetInsteadOf() {
        val insteadOfHosts = getHostList()
        insteadOfHosts.forEach { host ->
            unsetGitInsteadOfHttp(host = host)
        }
    }

    // 工蜂如果oauth2方式授权，如果token有效但是没有仓库的权限,返回状态码是200，但是会抛出repository not found异常,
    // 导致凭证不会自动清理,所以如果是oauth2授权，先移除全局oauth2的凭证
    fun eraseOauth2Credential() {
        if (authInfo.username == OAUTH2) {
            logger.info("removing global credential for `oauth2` username")
            println("##[command]$ git credential erase")
            // 同一服务多个域名时，需要保存不同域名的凭证
            getHostList().forEach { cHost ->
                listOf("https", "http").forEach { cProtocol ->
                    git.credential(
                        action = CredentialActionEnum.ERASE,
                        inputStream = CredentialArguments(
                            protocol = cProtocol,
                            host = cHost,
                            username = OAUTH2
                        ).convertInputStream()
                    )
                }
            }
        }
    }
}
