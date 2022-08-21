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
import com.tencent.bk.devops.git.core.constant.GitConstants.SUPPORT_EMPTY_CRED_HELPER_GIT_VERSION
import com.tencent.bk.devops.git.core.enums.AuthHelperType
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
) : AbGitAuthHelper(git = git, settings = settings) {

    companion object {
        private val logger = LoggerFactory.getLogger(AskPassGitAuthHelper::class.java)
    }

    private var askpass: File? = null

    override fun configureHttp() {
        if (!serverInfo.httpProtocol ||
            authInfo.username.isNullOrBlank() ||
            authInfo.password.isNullOrBlank()
        ) {
            return
        }
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
        EnvHelper.putContext(GitConstants.GIT_CREDENTIAL_AUTH_HELPER, AuthHelperType.ASK_PASS.name)
        /**
         * 1. 调用全局凭证管理,将用户名密码保存到凭证管理中使凭证能够向下游插件传递,同时覆盖构建机上错误的凭证
         * 2. 保存全局凭证必须在禁用凭证之前,否则调用全局凭证无用
         * 3. 保存的全局凭证在下游插件可能不生效，因为在同一个私有构建机，
         *    如果同时执行多条流水线,每条流水线拉代码的账号oauth不同就可能被覆盖
         */
        storeCredential()
        if (git.isAtLeastVersion(SUPPORT_EMPTY_CRED_HELPER_GIT_VERSION)) {
            git.tryDisableOtherGitHelpers()
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
            git.tryConfigUnset(configKey = GitConstants.GIT_CREDENTIAL_HELPER)
        }
        git.tryConfigUnset(configKey = GitConstants.GIT_CREDENTIAL_AUTH_HELPER)
    }

    override fun configGlobalAuth(copyGlobalConfig: Boolean) {
        super.configGlobalAuth(true)
        // 临时卸载全局凭证,保证插件的core.askpass一定会生效
        git.tryConfigUnset(configKey = GitConstants.GIT_CREDENTIAL_HELPER, configScope = GitConfigScope.GLOBAL)
    }

    /**
     * 配置全局凭证,保证凭证能够向下游插件传递,兼容http和https
     */
    private fun storeCredential() {
        logger.info("store global credential for other plugins")
        val credentialHosts = getHostList()
        // 同一服务多个域名时，需要保存不同域名的凭证
        credentialHosts.forEach { cHost ->
            listOf("https", "http").forEach { cProtocol ->
                git.credentialStore(
                    CredentialArguments(
                        protocol = cProtocol,
                        host = cHost,
                        username = authInfo.username,
                        password = authInfo.password
                    ).convertInputStream()
                )
            }
        }
    }

    override fun configureSubmoduleAuth() {
        super.configureSubmoduleAuth()
        if (askpass != null) {
            git.submoduleForeach(
                "git config core.askpass '${askpass!!.absolutePath}'",
                settings.nestedSubmodules
            )
        }
    }

    override fun removeSubmoduleAuth() {
        super.removeSubmoduleAuth()
        git.submoduleForeach(
            "git config --unset core.askpass || true",
            settings.nestedSubmodules
        )
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
