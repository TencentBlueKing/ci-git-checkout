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

package com.tencent.bk.devops.git.core.util

import com.tencent.bk.devops.atom.api.Header
import com.tencent.bk.devops.atom.api.SdkEnv
import com.tencent.bk.devops.git.core.enums.OSType
import java.util.Locale

object AgentEnv {

    private var os: OSType? = null

    fun getOS(): OSType {
        if (os == null) {
            synchronized(this) {
                if (os == null) {
                    val OS = System.getProperty("os.name", "generic").lowercase(Locale.ENGLISH)
                    os = if (OS.indexOf(string = "mac") >= 0 || OS.indexOf("darwin") >= 0) {
                        OSType.MAC_OS
                    } else if (OS.indexOf("win") >= 0) {
                        OSType.WINDOWS
                    } else if (OS.indexOf("nux") >= 0) {
                        OSType.LINUX
                    } else {
                        OSType.OTHER
                    }
                }
            }
        }
        return os!!
    }

    @JvmStatic
    fun isThirdParty() = SdkEnv.getSdkHeader()[Header.AUTH_HEADER_DEVOPS_BUILD_TYPE] == "AGENT"

    /**
     * 常驻docker,构建完并不会被清理
     */
    fun isThirdDocker() = System.getenv("DEVOPS_SLAVE_ENVIRONMENT") == "pcg-devcloud"

    fun isDocker() = isThirdDocker() || !isThirdParty()
}
