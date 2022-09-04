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
import com.tencent.bk.devops.git.core.enums.CredentialActionEnum
import com.tencent.bk.devops.git.core.enums.GitConfigScope
import com.tencent.bk.devops.git.core.enums.GitProtocolEnum
import com.tencent.bk.devops.git.core.enums.OSType
import com.tencent.bk.devops.git.core.pojo.CredentialArguments
import com.tencent.bk.devops.git.core.pojo.GitSourceSettings
import com.tencent.bk.devops.git.core.service.GitCommandManager
import com.tencent.bk.devops.git.core.util.AgentEnv
import com.tencent.bk.devops.git.core.util.EnvHelper
import org.slf4j.LoggerFactory
import java.io.File

/**
 * 通过GIT_ASKPASS访问git凭证
 */
class AskPassGitAuthHelper(
    private val git: GitCommandManager,
    private val settings: GitSourceSettings
) : HttpGitAuthHelper(git = git, settings = settings) {

    companion object {
        private val logger = LoggerFactory.getLogger(AskPassGitAuthHelper::class.java)
    }

    private var askpass: File? = null

    override fun configureAuth() {
        logger.info("using core.askpass to set credentials ${authInfo.username}/******")
        EnvHelper.putContext(ContextConstants.CONTEXT_GIT_PROTOCOL, GitProtocolEnum.HTTP.name)
        askpass = if (AgentEnv.getOS() == OSType.WINDOWS) {
            createWindowsAskpass()
        } else {
            createUnixAskpass()
        }
        git.setEnvironmentVariable(GitConstants.GIT_ASKPASS, askpass!!.absolutePath)
        git.config(configKey = GitConstants.CORE_ASKPASS, configValue = askpass!!.absolutePath)
        git.config(configKey = GitConstants.GIT_CREDENTIAL_AUTH_HELPER, configValue = AuthHelperType.ASK_PASS.name)
        /**
         * 当流水线编排是: 拉仓库1 -> 拉仓库2 -> bash: git push 仓库1,
         * 拉仓库1和拉仓库2使用不同的oauth凭证,那么执行bash的时候工蜂会报repository not found.
         * 这是因为在拉仓库2时存储了全局凭证，bash插件git push时读到仓库2的全局凭证，导致仓库1中的ask_pass没有生效,
         * 所以设置仓库按照路径进行隔离
         */
        git.config(configKey = GitConstants.GIT_CREDENTIAL_USEHTTPPATH, configValue = "true")
        EnvHelper.putContext(GitConstants.GIT_CREDENTIAL_AUTH_HELPER, AuthHelperType.ASK_PASS.name)
        eraseOauth2Credential()
        storeCredential()
    }

    /**
     * 存储全局凭证,保证凭证能够向下游插件传递,兼容http和https
     *
     * 1. 调用全局凭证管理,将用户名密码保存到凭证管理中使凭证能够向下游插件传递,同时覆盖构建机上错误的凭证
     * 2. 保存全局凭证必须在禁用凭证之前,否则调用全局凭证无用
     * 3. 保存的全局凭证在下游插件可能不生效，因为在同一个私有构建机，
     *    如果同时执行多条流水线,每条流水线拉代码的账号oauth不同就可能被覆盖
     */
    private fun storeCredential() {
        if (settings.persistCredentials && !AgentEnv.isThirdParty()) {
            logger.info("store and overriding global credential for other plugins")
            println("##[command]$ git credential approve")
            val credentialHosts = getHostList()
            // 同一服务多个域名时，需要保存不同域名的凭证
            credentialHosts.forEach { cHost ->
                listOf("https", "http").forEach { cProtocol ->
                    git.credential(
                        action = CredentialActionEnum.APPROVE,
                        inputStream = CredentialArguments(
                            protocol = cProtocol,
                            host = cHost,
                            username = authInfo.username,
                            password = authInfo.password
                        ).convertInputStream()
                    )
                }
            }
        }
    }

    override fun removeAuth() {
        val askPass = git.tryConfigGet(
            configKey = GitConstants.CORE_ASKPASS
        )
        if (askPass.isNotBlank()) {
            if (File(askPass).exists()) {
                logger.info("Deleting askpass file $askPass")
                File(askPass).delete()
            }
            git.tryConfigUnset(configKey = GitConstants.CORE_ASKPASS)
            git.tryConfigUnset(configKey = GitConstants.GIT_CREDENTIAL_USEHTTPPATH)
        }
    }

    override fun configGlobalAuth(copyGlobalConfig: Boolean) {
        super.configGlobalAuth(true)
        git.config(
            configKey = GitConstants.CORE_ASKPASS,
            configValue = askpass!!.absolutePath,
            configScope = GitConfigScope.GLOBAL
        )
    }

    override fun configureSubmoduleAuth() {
        super.configureSubmoduleAuth()
        val commands = mutableListOf<String>()
        if (askpass != null) {
            commands.add("git config core.askpass '${askpass!!.absolutePath}'")
        }
        getHostList().forEach { host ->
            listOf("https", "http").forEach { protocol ->
                commands.add("git config credential.$protocol://$host/.useHttpPath true")
            }
        }
        if (commands.isNotEmpty()) {
            git.submoduleForeach("${commands.joinToString(";")} || true", settings.nestedSubmodules)
        }
    }

    override fun removeSubmoduleAuth() {
        super.removeSubmoduleAuth()
        val commands = mutableListOf<String>()
        commands.add("git config --unset core.askpass")
        getHostList().forEach { host ->
            listOf("https", "http").forEach { protocol ->
                commands.add("git config --unset credential.$protocol://$host/.useHttpPath")
            }
        }
        if (commands.isNotEmpty()) {
            git.submoduleForeach("${commands.joinToString(";")} || true", settings.nestedSubmodules)
        }
    }

    private fun createUnixAskpass(): File {
        val askpass = File.createTempFile("pass_", ".sh")
        askpass.writeText(
            "#!/bin/sh\n" +
                "case \"\$1\" in\n" +
                "Username*) echo ${authInfo.username} ;;\n" +
                "Password*) echo ${authInfo.password} ;;\n" +
                "esac\n"
        )
        askpass.setExecutable(true, true)
        return askpass
    }

    private fun createWindowsAskpass(): File {
        val askpass = File.createTempFile("pass_", ".bat")
        askpass.writeText(
            "@set arg=%~1\r\n" +
                "@if (%arg:~0,8%)==(Username) ECHO ${authInfo.username}\r\n" +
                "@if (%arg:~0,8%)==(Password) ECHO ${authInfo.password}\r\n"
        )
        askpass.setExecutable(true, true)
        return askpass
    }
}
