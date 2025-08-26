package app.brightnesspulse

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private fun hasOverlayPermission() = Settings.canDrawOverlays(this)
    private fun hasWriteSettingsPermission() = Settings.System.canWrite(this)

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun requestWriteSettingsPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
    }
        
        setContent {
            MaterialTheme {
                var overlayGranted by remember { mutableStateOf(hasOverlayPermission()) }
                var writeGranted by remember { mutableStateOf(hasWriteSettingsPermission()) }
                var systemBoostEnabled by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    overlayGranted = hasOverlayPermission()
                    writeGranted = hasWriteSettingsPermission()
                }

                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Brightness Pulse", style = MaterialTheme.typography.headlineSmall)
                        Text("자동밝기 기준으로 약간 밝음↔어둠을 반복")

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = systemBoostEnabled,
                                onCheckedChange = {
                                    systemBoostEnabled = it
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("시스템 밝기 살짝 올리기(옵션, WRITE_SETTINGS 필요)")
                        }

                        Button(onClick = {
    if (!hasOverlayPermission()) {
        requestOverlayPermission()
    } else {
        val i = Intent(this@MainActivity, PulseService::class.java).apply {
            putExtra(PulseService.EXTRA_USE_SYSTEM_BOOST, systemBoostEnabled && hasWriteSettingsPermission())
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(i)
            } else {
                startService(i)
            }
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException 등 발생 시 일반 서비스로 폴백
            startService(i)
        }
    }
}) { Text("시작") }

                        Button(onClick = {
                            stopService(Intent(this@MainActivity, PulseService::class.java))
                        }) { Text("정지") }

                        Divider()
                        Text("권한 상태")
                        Text("오버레이(필수): ${if (overlayGranted) "허용" else "미허용"}")
                        Text("WRITE_SETTINGS(선택): ${if (writeGranted) "허용" else "미허용"}")
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { requestOverlayPermission() }) { Text("오버레이 허용") }
                            OutlinedButton(onClick = { requestWriteSettingsPermission() }) { Text("WRITE_SETTINGS 허용") }
                        }
                    }
                }
            }
        }
    }
}
