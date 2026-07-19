-keep class org.eclipse.jgit.** { *; }
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
