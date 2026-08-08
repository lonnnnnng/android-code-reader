package com.lonnnnnng.codereader.share

import androidx.core.content.FileProvider

/** 只为用户主动选择的项目文件签发临时读取 URI，不复用更新安装包的授权边界。 @author long */
class ReaderFileProvider : FileProvider()
