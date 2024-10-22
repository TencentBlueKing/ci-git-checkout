package com.tencent.bk.devops.git.core.pojo

data class GitSubmoduleStatus(
    // submodule 版本信息
    val commitId: String,
    // submodule 路径
    val path: String,
    // submodule 依赖信息(分支名/tag名)
    val ref: String? = ""
)
