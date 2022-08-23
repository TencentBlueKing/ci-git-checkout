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
import com.tencent.bk.devops.git.core.enums.GitConfigScope
import com.tencent.bk.devops.git.core.enums.GitProtocolEnum
import com.tencent.bk.devops.git.core.exception.ParamInvalidException
import com.tencent.bk.devops.git.core.pojo.GitSourceSettings
import com.tencent.bk.devops.git.core.service.GitCommandManager
import com.tencent.bk.devops.git.core.util.EnvHelper
import com.tencent.bk.devops.git.core.util.SSHAgentUtils

class SshGitAuthHelper(
    private val git: GitCommandManager,
    private val settings: GitSourceSettings
) : AbGitAuthHelper(git = git, settings = settings) {

    override fun configureAuth() {
        if (authInfo.privateKey.isNullOrBlank()) {
            throw ParamInvalidException(errorMsg = "private key must not be empty")
        }
        EnvHelper.putContext(ContextConstants.CONTEXT_GIT_PROTOCOL, GitProtocolEnum.SSH.name)
        SSHAgentUtils(privateKey = authInfo.privateKey, passPhrase = authInfo.passPhrase).addIdentity()
        git.setEnvironmentVariable(GitConstants.GIT_SSH_COMMAND, GitConstants.GIT_SSH_COMMAND_VALUE)
        git.config(
            configKey = GitConstants.GIT_CREDENTIAL_AUTH_HELPER,
            configValue = AuthHelperType.SSH.name
        )
    }

    override fun removeAuth() = Unit

    override fun configureSubmoduleAuth() {
        val insteadOfHosts = getHostList()
        // 卸载上一步可能没有清理干净的insteadOf
        // windows 执行一条git submodule foreach都需要很久时间,将insteadOf组装在一起节省执行时间
        val commands = mutableListOf<String>()
        val insteadOfKey = "url.git@${serverInfo.hostName}:.insteadof"
        commands.add("git config --unset-all $insteadOfKey")
        insteadOfHosts.forEach { host ->
            listOf("http", "https").forEach { protocol ->
                commands.add(" git config --add $insteadOfKey $protocol://$host/ ")
            }
        }
        git.submoduleForeach("${commands.joinToString(";")} || true", settings.nestedSubmodules)
    }

    override fun removeSubmoduleAuth() {
        val insteadOfKey = "url.git@${serverInfo.hostName}:.insteadof"
        // git低版本卸载insteadOf后,但是url.*并没有卸载,需要指定再卸载
        git.submoduleForeach(
            " git config --unset-all $insteadOfKey; " +
                "git config --remove-section url.git@${serverInfo.hostName}: || true",
            settings.nestedSubmodules
        )
    }

    override fun insteadOf() {
        val insteadOfHosts = getHostList()
        val insteadOfKey = "url.git@${serverInfo.hostName}:.insteadof"
        git.tryConfigUnset(
            configKey = insteadOfKey,
            configScope = GitConfigScope.GLOBAL
        )
        insteadOfHosts.forEach { host ->
            gitInsteadOfHttp(
                host = host,
                insteadOfKey = insteadOfKey
            )
        }
    }

    override fun unsetInsteadOf() {
        val insteadOfHosts = getHostList()
        insteadOfHosts.forEach { host ->
            unsetHttpInsteadOfGit(host = host)
        }
    }
}
