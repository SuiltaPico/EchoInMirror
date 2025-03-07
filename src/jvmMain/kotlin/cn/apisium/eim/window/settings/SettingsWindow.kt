package cn.apisium.eim.window.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cn.apisium.eim.EchoInMirror
import cn.apisium.eim.api.window.SettingsTab
import cn.apisium.eim.components.Filled
import java.awt.Dimension

private class AudioSettings: SettingsTab {
    @Composable
    override fun label() {
        Text("音频")
    }

    @Composable
    override fun icon() {
        Icon(
            Icons.Filled.SettingsInputComponent,
            contentDescription = "Audio Settings",
        )
    }

    @Composable
    override fun content() {
        BasicText("音频设置")
    }
}

val settingsTabs = mutableStateListOf(NativeAudioPluginSettings(), AudioSettings())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun settingsWindow() {
    Dialog({ EchoInMirror.windowManager.settingsDialogOpen = false }, title = "设置") {
        window.minimumSize = Dimension(860, 700)
        window.isModal = false
        Surface(Modifier.fillMaxSize(), tonalElevation = 2.dp) {
            Row {
                var selected by remember { mutableStateOf(settingsTabs.getOrNull(0)?.run { this::class.java.name } ?: "") }
                val selectedTab = settingsTabs.find { it::class.java.name == selected }
                Column(Modifier.width(240.dp).padding(12.dp)) {
                    settingsTabs.forEach {
                        key(it::class.java.name) {
                            NavigationDrawerItem(
                                modifier = Modifier.height(46.dp),
                                icon = { it.icon() },
                                label = { it.label() },
                                selected = selected == it::class.java.name,
                                onClick = { selected = it::class.java.name }
                            )
                        }
                    }
                }
                Column(Modifier.fillMaxSize()) {
                    val stateVertical = rememberScrollState(0)
                    Box(Modifier.weight(1F)) {
                        Box(Modifier.fillMaxSize().verticalScroll(stateVertical)) {
                            Box(Modifier.padding(14.dp)) { selectedTab?.content() }
                        }
                        VerticalScrollbar(
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            adapter = rememberScrollbarAdapter(stateVertical)
                        )
                    }
                    Row(Modifier.padding(14.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Filled()
                        selectedTab?.buttons()
                        Button({ EchoInMirror.windowManager.settingsDialogOpen = false }) { Text("确认") }
                    }
                }
            }
        }
    }
}
