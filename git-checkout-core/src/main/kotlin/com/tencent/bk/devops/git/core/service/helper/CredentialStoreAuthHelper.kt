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
import com.tencent.bk.devops.git.core.pojo.CredentialArguments
import com.tencent.bk.devops.git.core.pojo.GitSourceSettings
import com.tencent.bk.devops.git.core.service.GitCommandManager
import com.tencent.bk.devops.git.core.util.EnvHelper
import com.tencent.bk.devops.git.core.util.GitUtil
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class CredentialStoreAuthHelper(
    private val git: GitCommandManager,
    private val settings: GitSourceSettings
) : HttpGitAuthHelper(git = git, settings = settings) {

    companion object {
        private val logger = LoggerFactory.getLogger(CredentialStoreAuthHelper::class.java)
    }
    private val storeFile = File.createTempFile("git_", "_credentials")

    override fun configureAuth() {
        logger.info("using store credential to set credentials ${authInfo.username}/******")
        EnvHelper.putContext(ContextConstants.CONTEXT_GIT_PROTOCOL, GitProtocolEnum.HTTP.name)
        git.config(
            configKey = GitConstants.GIT_CREDENTIAL_AUTH_HELPER,
            configValue = AuthHelperType.STORE_CREDENTIAL.name
        )
        eraseOauth2Credential()
        storeGlobalCredential()
        writeStoreFile()
        if (git.isAtLeastVersion(GitConstants.SUPPORT_EMPTY_CRED_HELPER_GIT_VERSION)) {
            git.tryDisableOtherGitHelpers(configScope = GitConfigScope.LOCAL)
        }
        git.config(
            configKey = GitConstants.GIT_CREDENTIAL_HELPER,
            configValue = "store --file=${storeFile.absolutePath}"
        )
    }

    /**
     * 存储全局凭证,保证凭证能够向下游插件传递,兼容http和https
     *
     * 1. 调用全局凭证管理,将用户名密码保存到凭证管理中使凭证能够向下游插件传递,同时覆盖构建机上错误的凭证
     * 2. 保存全局凭证必须在禁用凭证之前,否则调用全局凭证无用
     * 3. 保存的全局凭证在下游插件可能不生效，因为在同一个私有构建机，
     *    如果同时执行多条流水线,每条流水线拉代码的账号oauth不同就可能被覆盖
     */
    private fun storeGlobalCredential() {
        logger.info("store and overriding global credential for other plugins")
        println("##[command]$ git credential approve")
        // 同一服务多个域名时，需要保存不同域名的凭证
        combinableHost { protocol, host ->
            git.credential(
                action = CredentialActionEnum.APPROVE,
                inputStream = CredentialArguments(
                    protocol = protocol,
                    host = host,
                    username = authInfo.username,
                    password = authInfo.password
                ).convertInputStream()
            )
        }
    }

    override fun removeAuth() {
        val storeCredentialValue = git.tryConfigGet(configKey = GitConstants.GIT_CREDENTIAL_HELPER)
        val credentialFilePath = storeCredentialValue.substringAfter("--file=")
        Files.deleteIfExists(Paths.get(credentialFilePath))
        git.tryConfigUnset(configKey = GitConstants.GIT_CREDENTIAL_HELPER)
    }

    override fun configGlobalAuth(copyGlobalConfig: Boolean) {
        super.configGlobalAuth(copyGlobalConfig)
        git.configAdd(
            configKey = GitConstants.GIT_CREDENTIAL_HELPER,
            configValue = "store --file=${storeFile.absolutePath}",
            configScope = GitConfigScope.GLOBAL
        )
    }

    override fun configureSubmoduleAuth() {
        super.configureSubmoduleAuth()
        val commands = mutableListOf<String>()
        if (git.isAtLeastVersion(GitConstants.SUPPORT_EMPTY_CRED_HELPER_GIT_VERSION)) {
            combinableHost { protocol, host ->
                commands.add("git config credential.$protocol://$host/.helper '' ")
            }
        }
        commands.add("git config credential.helper 'store --file=${storeFile.absolutePath}'")
        git.submoduleForeach("${commands.joinToString(";")} || true", settings.nestedSubmodules)
    }

    override fun removeSubmoduleAuth() {
        super.removeSubmoduleAuth()
        val commands = mutableListOf<String>()
        if (git.isAtLeastVersion(GitConstants.SUPPORT_EMPTY_CRED_HELPER_GIT_VERSION)) {
            combinableHost { protocol, host ->
                commands.add("git config --unset credential.$protocol://$host/.helper '' ")
            }
            combinableHost { protocol, host ->
                commands.add("git config --remove-section credential.$protocol://$host/.helper '' ")
            }
        }
        commands.add("git config --unset credential.helper")
        commands.add("git config --remove-section credential.helper")
        git.submoduleForeach("${commands.joinToString(";")} || true", settings.nestedSubmodules)
    }

    private fun writeStoreFile() {
        combinableHost { protocol, host ->
            storeFile.appendText(
                "$protocol://" +
                    "${GitUtil.urlEncode(authInfo.username!!)}:${GitUtil.urlEncode(authInfo.password!!)}@$host\n"
            )
        }
    }
}
