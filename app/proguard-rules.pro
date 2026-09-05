# JGit 只保留反射触达的面，其余交给 R8 裁剪与混淆。 @author long
# - nls：GlobalBundleCache 按类名 getDeclaredConstructor().newInstance() 实例化文案类，
#   TranslationBundle.load 再按字段名 Field.set 回填翻译，因此文案类必须全量保留；
# - GSSManagerFactory：按系统属性反射实例化，Android 上不会走到 Kerberos 分支，留入口防意外；
# - 其余反射点（Transport 协议注册、Config 枚举 values()）均为直接引用或默认规则覆盖，无需 keep。
-keep class org.eclipse.jgit.nls.** { *; }
-keep class org.eclipse.jgit.internal.JGitText { *; }
-keep class org.eclipse.jgit.gitrepo.internal.RepoText { *; }
-keep class org.eclipse.jgit.internal.storage.dfs.DfsText { *; }
-keep class org.eclipse.jgit.util.GSSManagerFactory** { *; }

-dontwarn java.lang.management.**
-dontwarn javax.management.**

# Android 端只使用公开 HTTPS 浅克隆，不会进入 JGit 的桌面进程管理和 Kerberos 协商分支。
# author: long
-dontwarn java.lang.ProcessHandle
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid
