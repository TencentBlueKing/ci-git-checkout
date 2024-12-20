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

import com.tencent.bk.devops.git.core.pojo.GitSourceSettings
import com.tencent.bk.devops.git.core.service.GitCommandManager

/**
 * git代理,加速代码下载速度,优先从代理服务下载
 */
interface IGitCacheHelper {

    /**
     * 是否支持代理类型
     */
    fun support(
        settings: GitSourceSettings,
        git: GitCommandManager
    ): Boolean

    /**
     * 读取顺序
     */
    fun getOrder(): Int

    /**
     * 代理名称
     */
    fun getName(): String

    /**
     * 从缓存服务下载代码
     *
     * @return 拉取是否成功
     */
    fun download(
        settings: GitSourceSettings,
        git: GitCommandManager
    ): Boolean

    /**
     * 添加缓存配置
     *
     */
    fun config(
        settings: GitSourceSettings,
        git: GitCommandManager
    )  = Unit

    fun unsetConfig(
        settings: GitSourceSettings,
        git: GitCommandManager
    ) = Unit
}
