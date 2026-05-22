package com.yin.cpufreq2

import android.annotation.SuppressLint
import android.os.Bundle
import android.provider.Settings.Global.getString
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.yin.cpufreq2.core.locale.ConfigManager
import com.yin.cpufreq2.core.locale.CpuFloatManager
import com.yin.cpufreq2.feature.home.HomeScreen
import com.yin.cpufreq2.ui.theme.Cpufreq2Theme

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ConfigManager.init(applicationContext)
        CpuFloatManager.init(applicationContext)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Cpufreq2Theme {
                Scaffold( modifier = Modifier.fillMaxSize() ) { innerPadding ->
                    HomeScreen()
                }
            }
        }
    }
}
