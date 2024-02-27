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

import com.tencent.bk.devops.git.core.pojo.GitSubmoduleStatus
import com.tencent.bk.devops.git.core.pojo.ServerInfo
import org.junit.Assert
import org.junit.Test

class GitUtilTest {

    @SuppressWarnings("LongMethod")
    @Test
    fun getServerUrl() {
        var expected = GitUtil.getServerInfo("https://git.exaple.com/my-proj/my-repo.git")
        var actual = ServerInfo(
            scheme = "https://",
            origin = "https://git.exaple.com",
            hostName = "git.exaple.com",
            repositoryName = "my-proj/my-repo",
            httpProtocol = true
        )
        Assert.assertEquals(expected, actual)

        expected = GitUtil.getServerInfo("https://git.exaple.com/my-proj/my-repo")
        actual = ServerInfo(
            scheme = "https://",
            origin = "https://git.exaple.com",
            hostName = "git.exaple.com",
            repositoryName = "my-proj/my-repo",
            httpProtocol = true
        )
        Assert.assertEquals(expected, actual)

        expected = GitUtil.getServerInfo("https://git.exaple.com:8080/my-proj/my-repo.git")
        actual = ServerInfo(
            scheme = "https://",
            origin = "https://git.exaple.com:8080",
            hostName = "git.exaple.com:8080",
            repositoryName = "my-proj/my-repo",
            httpProtocol = true
        )
        Assert.assertEquals(expected, actual)

        expected = GitUtil.getServerInfo("https://oauth2:xxx@git.exaple.com:8080/my-proj/my-repo.git")
        actual = ServerInfo(
            scheme = "https://",
            origin = "https://git.exaple.com:8080",
            hostName = "git.exaple.com:8080",
            repositoryName = "my-proj/my-repo",
            httpProtocol = true
        )
        Assert.assertEquals(expected, actual)

        expected = GitUtil.getServerInfo("git@git.exaple.com:my-proj/my-repo.git")
        actual = ServerInfo(
            scheme = "git@",
            origin = "git@git.exaple.com",
            hostName = "git.exaple.com",
            repositoryName = "my-proj/my-repo",
            httpProtocol = false
        )
        Assert.assertEquals(expected, actual)

        expected = GitUtil.getServerInfo("git.exaple.com:my-proj/my-repo.git")
        actual = ServerInfo(
            scheme = "git@",
            origin = "git@git.exaple.com",
            hostName = "git.exaple.com",
            repositoryName = "my-proj/my-repo",
            httpProtocol = false
        )
        Assert.assertEquals(expected, actual)

        expected = GitUtil.getServerInfo("http://111.222.333.444:36000/my-group/my-repo.git")
        actual = ServerInfo(
            scheme = "http://",
            origin = "http://111.222.333.444:36000",
            hostName = "111.222.333.444:36000",
            repositoryName = "my-group/my-repo",
            httpProtocol = true
        )
        Assert.assertEquals(expected, actual)

        expected = GitUtil.getServerInfo("http://111.222.333.444/my-group/my-repo.git")
        actual = ServerInfo(
            scheme = "http://",
            origin = "http://111.222.333.444",
            hostName = "111.222.333.444",
            repositoryName = "my-group/my-repo",
            httpProtocol = true
        )
        Assert.assertEquals(expected, actual)

        expected = GitUtil.getServerInfo("ssh://git@111.222.333.444:36000/my-group/my-repo.git")
        actual = ServerInfo(
            scheme = "git@",
            origin = "git@111.222.333.444:36000",
            hostName = "111.222.333.444:36000",
            repositoryName = "my-group/my-repo",
            httpProtocol = false
        )
        Assert.assertEquals(expected, actual)

        expected = GitUtil.getServerInfo("ssh://git@111.222.333.444/my-group/my-repo.git")
        actual = ServerInfo(
            scheme = "git@",
            origin = "git@111.222.333.444",
            hostName = "111.222.333.444",
            repositoryName = "my-group/my-repo",
            httpProtocol = false
        )
        Assert.assertEquals(expected, actual)
    }

    @Test
    fun isSameRepository() {
        Assert.assertTrue(
            GitUtil.isSameRepository(
                repositoryUrl = "https://git.exaple.com/my-proj/my-repo.git",
                otherRepositoryUrl = "http://git.exaple.com/my-proj/my-repo.git",
                hostNameList = null
            )
        )

        Assert.assertTrue(
            GitUtil.isSameRepository(
                repositoryUrl = "https://git.exaple.com/my-proj/my-repo.git",
                otherRepositoryUrl = "git@git.exaple.com:my-proj/my-repo.git",
                hostNameList = null
            )
        )

        Assert.assertTrue(
            GitUtil.isSameRepository(
                repositoryUrl = "https://git.exaple.com/my-proj/my-repo.git",
                otherRepositoryUrl = "https://oauth2:xxx@git.exaple.com/my-proj/my-repo.git",
                hostNameList = null
            )
        )

        Assert.assertTrue(
            GitUtil.isSameRepository(
                repositoryUrl = "https://git.exaple.com/my-proj/my-repo.git",
                otherRepositoryUrl = "http://git.exaple2.com/my-proj/my-repo.git",
                hostNameList = listOf("git.exaple.com", "git.exaple2.com")
            )
        )
    }

    @Test
    fun parseSubmoduleStatus(){
        var submoduleStatusStr = "-e742bf800a18bdb61037357a1074e63057679913 bk_ci_process_test_java"
        var expected = GitUtil.parseSubmoduleStatus(submoduleStatusStr)[0]
        var actual = GitSubmoduleStatus(
            commitId = "e742bf800a18bdb61037357a1074e63057679913",
            path="bk_ci_process_test_java",
            ref = ""
        )
        Assert.assertEquals(expected,actual)

        submoduleStatusStr = " 45f22503588f74fea7e54f2462e8f95d827fcaaf ../bk_ci_process_test_java_3/HelloeWorlds_HJ" +
                " (v1.100.1.0-55-g45f2250)"
        expected = GitUtil.parseSubmoduleStatus(submoduleStatusStr)[0]
        actual = GitSubmoduleStatus(
            commitId = "45f22503588f74fea7e54f2462e8f95d827fcaaf",
            path="../bk_ci_process_test_java_3/HelloeWorlds_HJ",
            ref = "v1.100.1.0-55-g45f2250"
        )
        Assert.assertEquals(expected,actual)

        submoduleStatusStr = " 32651f57c27039e143a128f2b05403436f86ce74 git-checkout "
        expected = GitUtil.parseSubmoduleStatus(submoduleStatusStr)[0]
        actual = GitSubmoduleStatus(
            commitId = "32651f57c27039e143a128f2b05403436f86ce74",
            path="git-checkout",
            ref = ""
        )
        Assert.assertEquals(expected,actual)

        submoduleStatusStr = " 32651f57c27039e143a128f2b05403436f86ce74 git-checkout ()"
        expected = GitUtil.parseSubmoduleStatus(submoduleStatusStr)[0]
        actual = GitSubmoduleStatus(
            commitId = "32651f57c27039e143a128f2b05403436f86ce74",
            path="git-checkout",
            ref = ""
        )
        Assert.assertEquals(expected,actual)

        submoduleStatusStr = " 32651f57c27039e143a128f2b05403436f86ce74 bk_ci_process_test_java space" +
                " dir/git-checkout (1.1.27-2024-01-12)"
        expected = GitUtil.parseSubmoduleStatus(submoduleStatusStr)[0]
        actual = GitSubmoduleStatus(
            commitId = "32651f57c27039e143a128f2b05403436f86ce74",
            path="bk_ci_process_test_java space dir/git-checkout",
            ref = "1.1.27-2024-01-12"
        )
        Assert.assertEquals(expected,actual)

        submoduleStatusStr = " 32651f57c27039e143a128f2b05403436f86ce74 bk_ci_process_test_java space" +
                " dir/git-checkout (1.1.27-2024-01-12)"
        expected = GitUtil.parseSubmoduleStatus(submoduleStatusStr)[0]
        actual = GitSubmoduleStatus(
            commitId = "32651f57c27039e143a128f2b05403436f86ce74",
            path="bk_ci_process_test_java space dir/git-checkout",
            ref = "1.1.27-2024-01-12"
        )
        Assert.assertEquals(expected,actual)

        submoduleStatusStr = " 32651f57c27039e143a128f2b05403436f86ce74 bk_ci_process_test_java 代码" +
                " dir/git-checkout (1.1.27-2024-01-12)"
        expected = GitUtil.parseSubmoduleStatus(submoduleStatusStr)[0]
        actual = GitSubmoduleStatus(
            commitId = "32651f57c27039e143a128f2b05403436f86ce74",
            path="bk_ci_process_test_java 代码 dir/git-checkout",
            ref = "1.1.27-2024-01-12"
        )
        Assert.assertEquals(expected,actual)

        submoduleStatusStr = " 32651f57c27039e143a128f2b05403436f86ce74 bk_ci_process_test_java 代码 dir/git-checkout ()"
        expected = GitUtil.parseSubmoduleStatus(submoduleStatusStr)[0]
        actual = GitSubmoduleStatus(
            commitId = "32651f57c27039e143a128f2b05403436f86ce74",
            path="bk_ci_process_test_java 代码 dir/git-checkout",
            ref = ""
        )
        Assert.assertEquals(expected,actual)
    }
}
