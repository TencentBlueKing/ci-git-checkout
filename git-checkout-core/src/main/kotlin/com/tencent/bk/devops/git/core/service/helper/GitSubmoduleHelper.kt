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

import com.tencent.bk.devops.git.core.pojo.GitSubmodule
import com.tencent.bk.devops.git.core.util.CommandUtil
import java.io.File
import java.util.regex.Pattern

class GitSubmoduleHelper {

    companion object {
        private const val SUBMODULE_REMOTE_PATTERN_CONFIG_KEY = "^submodule\\.(.+)\\.url"
        private const val SUBMODULE_REMOTE_PATTERN_STRING = SUBMODULE_REMOTE_PATTERN_CONFIG_KEY + "\\s+[^\\s]+$"
        private val submodulePattern = Pattern.compile(SUBMODULE_REMOTE_PATTERN_STRING)
    }

    fun getSubmodules(repositoryDir: File, recursive: Boolean): List<GitSubmodule> {
        val submoduleCfg = CommandUtil.execute(
            workingDirectory = repositoryDir,
            executable = "git",
            args = listOf("config", "-f", ".gitmodules", "--get-regexp", SUBMODULE_REMOTE_PATTERN_CONFIG_KEY),
            allowAllExitCodes = true
        ).stdOuts

        val submodules = mutableListOf<GitSubmodule>()
        submoduleCfg.forEach { cfg ->
            val matcher = submodulePattern.matcher(cfg)
            if (matcher.find()) {
                val submoduleName = matcher.group(1)
                val path = getSubmodulePath(repositoryDir = repositoryDir, name = submoduleName)
                submodules.add(
                    GitSubmodule(
                        name = submoduleName,
                        path = path,
                        relativePath = File(repositoryDir, path).path,
                        url = getSubmoduleUrl(repositoryDir = repositoryDir, name = submoduleName)
                    )
                )
                if (recursive) {
                    submodules.addAll(getSubmodules(File(repositoryDir, path), recursive))
                }
            }
        }
        return submodules
    }

    private fun getSubmodulePath(repositoryDir: File, name: String): String {
        return CommandUtil.execute(
            workingDirectory = repositoryDir,
            executable = "git",
            args = listOf("config", "-f", ".gitmodules", "--get", "submodule.$name.path"),
            allowAllExitCodes = true
        ).stdOut
    }

    private fun getSubmoduleUrl(repositoryDir: File, name: String): String {
        return CommandUtil.execute(
            workingDirectory = repositoryDir,
            executable = "git",
            args = listOf("config", "-f", ".gitmodules", "--get", "submodule.$name.url"),
            allowAllExitCodes = true
        ).stdOut
    }
}
