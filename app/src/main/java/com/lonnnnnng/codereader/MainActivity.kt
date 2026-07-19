package com.lonnnnnng.codereader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.lonnnnnng.codereader.ui.ReaderApp
import com.lonnnnnng.codereader.ui.ReaderViewModel

/**
 * 接收启动页和外部文件 Intent，并把来源统一交给 ReaderViewModel。
 *
 * @author long
 */
class MainActivity : ComponentActivity() {
    private lateinit var viewModel: ReaderViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[ReaderViewModel::class.java]
        setContent {
            ReaderApp(viewModel)
        }
        viewModel.handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleIntent(intent)
    }
}
