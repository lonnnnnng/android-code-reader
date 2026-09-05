package com.lonnnnnng.codereader

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

/**
 * Android 16 起，instrumentation 的测试 APK 不再并入应用的 PathClassLoader，而是改由子加载器加载。
 * kotlinx-coroutines-test 的 ExceptionCollectorAsService 因此对应用侧 ServiceLoader 不可见，
 * 所有 Compose 用例会在 TestScopeImpl.enter 统一抛出
 * "Exception handler was not found via a ServiceLoader"。
 *
 * 本 runner 在测试进程启动时，把测试加载器里的 ExceptionCollectorAsService 实例补进
 * 应用加载器的 CoroutineExceptionHandler ServiceLoader 结果集，恢复旧布局下的可见性。
 * 集合中已存在该服务（旧平台或上游未来修复）时跳过；桥接失败时仅打印日志，不影响测试执行。
 *
 * 仅存在于 androidTest 源集，不进入任何正式产物。 @author long
 */
class ServiceLoaderBridgeRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle?) {
        bridgeCoroutineExceptionHandler()
        super.onCreate(arguments)
    }

    private fun bridgeCoroutineExceptionHandler() {
        try {
            val implKt = Class.forName("kotlinx.coroutines.internal.CoroutineExceptionHandlerImplKt")
            val field = implKt.getDeclaredField("platformExceptionHandlers")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val original = field.get(null) as Collection<Any>
            if (original.any { it.javaClass.name == COLLECTOR_SERVICE }) {
                System.setProperty(BRIDGE_KEY, "skipped:already-present")
                return
            }
            // ServiceLoader 结果可能来自 Kotlin toList 的单元素优化（Collections$SingletonList，不可变），
            // 因此整体替换字段为可变列表，而不是原地 add。 @author long
            val bridged = ArrayList<Any>(original)
            bridged.add(Class.forName(COLLECTOR_SERVICE).getDeclaredConstructor().newInstance())
            field.set(null, bridged)
            System.setProperty(BRIDGE_KEY, "added:size=" + bridged.size)
        } catch (t: Throwable) {
            System.setProperty(BRIDGE_KEY, "failed:" + t)
            println("ServiceLoaderBridgeRunner: coroutine handler bridge failed: $t")
        }
    }

    private companion object {
        const val COLLECTOR_SERVICE = "kotlinx.coroutines.test.internal.ExceptionCollectorAsService"

        /** 诊断用：测试侧通过该属性检查桥接执行结果。 @author long */
        const val BRIDGE_KEY = "acrr.coroutine.bridge"
    }
}
