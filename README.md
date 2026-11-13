# 沟通板

给能看懂简体中文、听懂普通话的老人使用。支持 Android 8.0 及以上。

## 用手点字说话

1. 首页固定显示 48 个常用开头。第一排是「我、你、我们、那个」，第二排是「什么、哪里、谁、为什么」。往下滑还有「我需要、我想找、请把、能不能、有没有」等开头。每个开头都有对应的离线接词。
2. 候选字词直接铺在同一页。可以点「喝水」，也可以分开点「喝」和「水」。每次点选都会加入句子框，并念出刚选的字词。
3. 句子框内可「读整句」「撤销」或「重来」。点句子文字也可朗读整句。
4. 底部可切换「沟通」「常用」「历史」「设置」。「常用」提供六类预置短语和疼痛部位图。

在「设置」里可调字号、按钮尺寸、对比度、语速、音量和逐字播报。播报使用手机已有的普通话离线语音。没有可用语音时，应用会提示去系统设置安装并重新检测。

## 历史和常用字词学习

「历史」按最近使用排序，最多保留 60 条不同的表达。点句子可以重播，点旁边的爱心会收到「常用 → 我的常用句」。清空历史后，收藏仍然保留。

在「常用」可添加整句，点句子即可朗读。在「沟通」可添加词语，再用「我的词语」把它加入正在拼的句子。

点「读整句」后，应用会在手机上记住已经用过的接词。同一接词在两次表达中使用后，会在相同上下文里排到前面。首页开头词位置固定。学习适用于联网候选和离线候选，学习记录保留在本机。「设置 → 常用字词学习」可以关闭或清除记录。

## 联网预测

点沟通页右上角的「AI 联想」随时开关。模型还没配置时，点按会进入模型设置，填写 OpenAI 兼容的 HTTPS Base URL、模型名，以及服务要求的 API Key。支持形如 `https://api.example.com/v1` 的基础地址，也支持完整的 `/chat/completions` 地址。保存后可点「测试连接」，应用会发送示例「我想」。[Chat Completions 接口文档](https://developers.openai.com/api/reference/resources/chat)

AI 联想开启后，当前拼出的文字会发送给你配置的服务。内置词和个人常用词始终保留，AI 生成的词追加在后面。往下滑到候选区末尾，会继续加载新一批联网词并去重。断网或服务失败时，内置词仍可使用。API Key 通过 Android Keystore 加密后保存在本机，改接口地址时会清除旧地址的密钥。

联网请求最多等待 30 秒，等待时可以继续选择内置词。加载失败时，页面会显示授权、超时或接口响应等具体原因。

## 离线联想

应用内置从 [Fcitx 5 拼音输入法的 LibIME 简体中文模型](https://github.com/fcitx/libime/blob/master/data/CMakeLists.txt) 提取的约 5.4 万组上下文联想。离线时，护理场景的接词优先显示，通用联想按当前文字补充。像「我想喝水」这样有现成补充短语的完整表达，优先给出自然接续和句末标点。

候选较多时，向下滑会接着显示更多。常用和相关性较高的字词排在前面。

模型源文件和转换脚本随源码包提供。模型及生成的联想表采用 LGPL-2.1-or-later，详见 `app/src/main/assets/third-party/libime-notice.txt`。修改或更新联想表可用 Python 3.14 及以上运行：

```powershell
python tools/build_libime_predictions.py third-party/lm_sc.arpa-20250113.tar.zst app/src/main/assets/third-party/libime-next-words.tsv
```

## 应用更新

打开应用时会自动检查更新。发现新版后，「设置」菜单会显示提示，进入「设置 → 应用更新」即可下载。下载前会比较 GitHub 直连和国内加速线路。若当前线路失败，会改试其他线路。校验文件大小、SHA-256 和应用签名后，才会交给 Android 确认安装。

发布时递增 `app/build.gradle.kts` 中的 `versionCode` 和 `versionName`，用 `:app:assembleRelease` 构建，再使用项目的发布密钥签名。将签名后的 APK 作为 `goutongban.apk` 上传至 GitHub Release，标签使用 `v版本号`。同步更新主分支的 `update.json`，供 GitHub API 受限时读取。后续版本应继续使用同一个签名密钥，才能覆盖安装并保留使用记录。

## 构建与测试

使用 JDK 17、Android SDK Platform 35 和 Build Tools 35.0.0。用 Android Studio 打开工程根目录，或设置 `JAVA_HOME` 和 `ANDROID_HOME` 后运行：

```powershell
.\gradlew.bat :app:clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。Gradle Wrapper 固定为 8.9，首次构建需要下载 Gradle 和依赖。Linux 和 macOS 用 `./gradlew`。Windows 中文路径下若出现构建或测试路径异常，可以在英文路径解压源码包再运行。

连接测试手机或模拟器后，运行 `.\gradlew.bat :app:connectedDebugAndroidTest`。源词库位于 `app/src/main/assets/phrases.json`，离线接词序列位于 `app/src/main/assets/word_sequences.txt`，用竖线分隔每次点选的内容。
