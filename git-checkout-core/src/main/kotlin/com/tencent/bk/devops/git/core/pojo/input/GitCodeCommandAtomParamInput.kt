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

package com.tencent.bk.devops.git.core.pojo.input

import com.tencent.bk.devops.git.core.enums.AuthType
import com.tencent.bk.devops.git.core.enums.PullStrategy
import com.tencent.bk.devops.git.core.enums.PullType
import com.tencent.bk.devops.git.core.enums.ScmType

@SuppressWarnings("ALL")
data class GitCodeCommandAtomParamInput(
    // 系统参数
    val bkWorkspace: String = "",
    val pipelineId: String = "",
    val pipelineTaskId: String = "",
    val pipelineBuildId: String = "",
    val pipelineStartUserName: String = "",
    val postEntryParam: String? = "false",

    var repositoryUrl: String = "",

    val scmType: ScmType = ScmType.CODE_GIT,
    var authType: AuthType? = null,
    var ticketId: String? = null,
    var accessToken: String? = null,
    var username: String? = null,
    var password: String? = null,
    var personalAccessToken: String? = null,
    var authUserId: String? = null,

    var localPath: String? = null,
    var strategy: String = PullStrategy.REVERT_UPDATE.name,

    var enableSubmodule: Boolean = true,
    var submodulePath: String? = "",
    var enableVirtualMergeBranch: Boolean = true,
    var enableSubmoduleRemote: Boolean = false,
    var enableSubmoduleRecursive: Boolean? = true,
    /**
     * submodule depth
     */
    var submoduleDepth: Int? = null,
    /**
     * submodule并发拉取数量
     */
    val submoduleJobs: Int? = 0,
    var autoCrlf: String? = "",
    var pullType: String = PullType.BRANCH.name,
    var refName: String = "master",

    var fetchDepth: Int? = null,
    val enableFetchRefSpec: Boolean? = false,
    val fetchRefSpec: String? = null,
    var enableGitLfs: Boolean = false,
    /**
     * lfs并发上传下载的数量
     */
    val lfsConcurrentTransfers: Int? = 0,
    /**
     * 是否开启Git Lfs清理
     */
    val enableGitLfsClean: Boolean = false,
    var includePath: String? = "",
    var excludePath: String? = "",

    var enableGitClean: Boolean = true,
    var enableGitCleanIgnore: Boolean = true,
    var enableGitCleanNested: Boolean = false,

    // 非前端传递的参数
    val pipelineStartType: String? = null,
    val hookEventType: String? = null,
    val hookSourceBranch: String? = null,
    val hookTargetBranch: String? = null,
    val hookSourceUrl: String? = null,
    val hookTargetUrl: String? = null,

    // 重试时检出的commitId
    var retryStartPoint: String? = "",
    var persistCredentials: Boolean,
    var hostNameList: List<String>? = null,
    val enableTrace: Boolean? = false,

    var usernameConfig: String? = "",
    var userEmailConfig: String? = "",
    val enablePartialClone: Boolean? = false,
    /**
     * 缓存路径:自定义的制品库路径,保存仓库的.git压缩文件
     */
    val cachePath: String? = "",
    /**
     * 是否开启全局insteadOf
     */
    val enableGlobalInsteadOf: Boolean = false,
    /**
     * 是否使用自定义凭证
     *
     * 只要是http[s]，都是用自定义的checkout凭证,不管有没有配置全局的凭证
     */
    val useCustomCredential: Boolean = true,
    /**
     * 是否使用工蜂边缘节点加速
     */
    val enableTGitCache: Boolean? = false,
    /**
     * 工蜂边缘节点url
     */
    val tGitCacheUrl: String? = null,
    /**
     * 工蜂边缘节点 proxy url,给http.proxy使用
     */
    val tGitCacheProxyUrl: String? = null,
    /**
     * 是否设置安全目录
     */
    val setSafeDirectory: Boolean? = true,
    /**
     * 是否为源材料主仓库
     */
    val mainRepo: Boolean? = false,
    /**
     * 工蜂cache灰度项目
     */
    val tGitCacheGrayProject: String?,
    /**
     * 工蜂cache灰度白名单项目
     */
    val tGitCacheGrayWhiteProject: String?,
    /**
     * 工蜂cache灰度权重
     */
    val tGitCacheGrayWeight: String?
)
