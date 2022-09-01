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

import com.tencent.bk.devops.git.core.constant.GitConstants
import com.tencent.bk.devops.git.core.constant.GitConstants.GIT_CREDENTIAL_AUTH_HELPER
import com.tencent.bk.devops.git.core.constant.GitConstants.GIT_CREDENTIAL_HELPER_VALUE_REGEX
import com.tencent.bk.devops.git.core.constant.GitConstants.HOME
import com.tencent.bk.devops.git.core.enums.AuthHelperType
import com.tencent.bk.devops.git.core.pojo.GitSourceSettings
import com.tencent.bk.devops.git.core.service.GitCommandManager
import com.tencent.bk.devops.git.core.util.GitUtil

object GitAuthHelperFactory {

    private var gitAuthHelper: IGitAuthHelper? = null

    fun getAuthHelper(git: GitCommandManager, settings: GitSourceSettings): IGitAuthHelper {
        if (gitAuthHelper != null) {
            return gitAuthHelper!!
        }
        val serverInfo = GitUtil.getServerInfo(settings.repositoryUrl)
        gitAuthHelper = if (serverInfo.httpProtocol) {
            getHttpAuthHelper(git, settings)
        } else {
            SshGitAuthHelper(git, settings)
        }
        return gitAuthHelper!!
    }

    private fun getHttpAuthHelper(git: GitCommandManager, settings: GitSourceSettings): IGitAuthHelper {
        /**
         * 1. git < 1.7.10没有凭证管理,使用https://username:password@xxx这样的方式授权
         * 2. 如果git版本支持了凭证管理，需要判断用户是否配置了全局凭证并且全局凭证不是git-checkout-credential产生的,
         *    使用ask pass方式获取用户名密码,不然如果用户在拉代码后使用git config --global credential.helper 会报错
         */
        return if (git.isAtLeastVersion(GitConstants.SUPPORT_CRED_HELPER_GIT_VERSION)) {
            if (isUseCustomCredential(git)) {
                CredentialAuthHelper(git, settings)
            } else {
                AskPassGitAuthHelper(git, settings)
            }
        } else {
            UsernamePwdGitAuthHelper(git, settings)
        }
    }

    private fun isUseCustomCredential(git: GitCommandManager): Boolean {
        val credentialHelperConfig = git.tryConfigGetAll(
            configKey = GitConstants.GIT_CREDENTIAL_HELPER
        )
        return (
            credentialHelperConfig.isEmpty() ||
                credentialHelperConfig.any { it.contains(GIT_CREDENTIAL_HELPER_VALUE_REGEX) }
            ) &&
            // 当第三方构建机重启后,如果不重启agent,会导致找不到HOME,使用自定义凭证会出现$HOME not set
            System.getenv(HOME) != null
    }

    fun getCleanUpAuthHelper(git: GitCommandManager, settings: GitSourceSettings): IGitAuthHelper {
        return when (git.tryConfigGet(configKey = GIT_CREDENTIAL_AUTH_HELPER)) {
            AuthHelperType.CUSTOM_CREDENTIAL.name ->
                CredentialAuthHelper(git, settings)
            AuthHelperType.ASK_PASS.name ->
                AskPassGitAuthHelper(git, settings)
            AuthHelperType.USERNAME_PASSWORD.name ->
                UsernamePwdGitAuthHelper(git, settings)
            AuthHelperType.SSH.name ->
                SshGitAuthHelper(git, settings)
            else -> getAuthHelper(git, settings)
        }
    }
}
