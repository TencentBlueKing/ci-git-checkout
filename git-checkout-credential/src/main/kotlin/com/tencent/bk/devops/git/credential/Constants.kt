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

package com.tencent.bk.devops.git.credential

object Constants {
    const val BK_CI_BUILD_ID = "BK_CI_BUILD_ID"
    const val BK_CI_PIPELINE_ID = "BK_CI_PIPELINE_ID"
    const val BK_CI_BUILD_JOB_ID = "BK_CI_BUILD_JOB_ID"
    const val BK_CI_BUILD_TASK_ID = "BK_CI_BUILD_TASK_ID"
    const val GIT_CREDENTIAL_HELPER = "credential.helper"
    const val GIT_CREDENTIAL_HELPER_VALUEREGEX = "git-checkout-credential"
    const val GIT_CREDENTIAL_TASKID = "credential.taskId"
    const val XDG_CONFIG_HOME = "XDG_CONFIG_HOME"
    const val GIT_CREDENTIAL_USEHTTPPATH = "credential.useHttpPath"
    const val GIT_REPO_PATH = "GIT_REPO_PATH"
    const val CREDENTIAL_COMPATIBLE_HOST = "credential_compatible_host"
}
