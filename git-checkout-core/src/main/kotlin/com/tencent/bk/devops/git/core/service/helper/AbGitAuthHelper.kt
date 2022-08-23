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

import com.tencent.bk.devops.git.core.constant.GitConstants.HOME
import com.tencent.bk.devops.git.core.enums.GitConfigScope
import com.tencent.bk.devops.git.core.pojo.GitSourceSettings
import com.tencent.bk.devops.git.core.service.GitCommandManager
import com.tencent.bk.devops.git.core.util.AgentEnv
import com.tencent.bk.devops.git.core.util.FileUtils
import com.tencent.bk.devops.git.core.util.GitUtil
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

abstract class AbGitAuthHelper(
    private val git: GitCommandManager,
    private val settings: GitSourceSettings
) : IGitAuthHelper {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }

    protected val serverInfo = GitUtil.getServerInfo(settings.repositoryUrl)
    protected val authInfo = settings.authInfo

    override fun configGlobalAuth(copyGlobalConfig: Boolean) {
        // 蓝盾默认镜像中有insteadOf,应该卸载,不然在凭证传递到下游插件时会导致凭证失效
        if (!AgentEnv.isThirdParty()) {
            unsetInsteadOf()
        }
        val tempHomePath = Files.createTempDirectory("checkout")
        val gitConfigPath = Paths.get(
            System.getenv(HOME) ?: System.getProperty("user.home"),
            ".gitconfig"
        )
        val newGitConfigPath = Paths.get(tempHomePath.toString(), ".gitconfig")
        if (copyGlobalConfig && Files.exists(gitConfigPath)) {
            logger.info("Copying $gitConfigPath to $newGitConfigPath")
            Files.copy(gitConfigPath, newGitConfigPath)
        } else {
            Files.createFile(newGitConfigPath)
        }
        logger.info("Temporarily overriding HOME='$tempHomePath' for fetching submodules")
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

    abstract fun insteadOf()

    /**
     * 删除全局的insteadOf
     */
    abstract fun unsetInsteadOf()

    protected fun getHostList(): MutableSet<String> {
        val insteadOfHosts = mutableSetOf(serverInfo.hostName)
        // 当一个git远程仓库有多个域名时，需要都兼容
        val compatibleHostList = settings.compatibleHostList
        if (!compatibleHostList.isNullOrEmpty() && compatibleHostList.contains(serverInfo.hostName)) {
            insteadOfHosts.addAll(compatibleHostList)
        }
        return insteadOfHosts
    }

    protected fun unsetGitInsteadOfHttp(host: String) {
        git.tryConfigUnset(
            configKey = "url.git@$host:.insteadof",
            configScope = GitConfigScope.GLOBAL
        )
    }

    protected fun unsetHttpInsteadOfGit(host: String) {
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
