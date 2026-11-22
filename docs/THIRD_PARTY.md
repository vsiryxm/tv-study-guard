# 第三方材料

- QR 生成器：Project Nayuki，MIT。源码 `core/src/main/java/io/nayuki/qrcodegen/` 保留许可头；上游提交与逐文件 SHA-256 见 `content/third-party-provenance.json`。本地编译，无扫码服务或外网依赖。
- 唐诗三百首数据：[chinese-poetry](https://github.com/chinese-poetry/chinese-poetry)，MIT。许可见 `content/licenses/chinese-poetry-MIT.txt`。仅选取适龄篇目，OpenCC t2s 转简体，去掉括号异文注；补充公版小学常见唐诗。实际入选文本与来源见 `content/poetry-sources.json`。
- 英语仅按湘少版新版已核实目录主题编写原创练习，未复制课文、插图或出版社原题。参考目录见 `content/english-reference-catalog.json`；第三方目录不代表出版社授权或完整逐页校对。
- 完整第三方许可也随 APK 包含在 assets/third-party-notices.txt。
- Android/Gradle/JUnit 依赖按其自身许可使用；开发和审计工具不随 APK 打包。
