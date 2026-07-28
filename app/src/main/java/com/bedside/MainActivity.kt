package com.bedside

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import com.bedside.health.HealthAvailability
import com.bedside.health.HealthConnectReader
import com.bedside.health.SleepSummary
import com.bedside.media.MediaStorePhotoReader
import com.bedside.media.PhotoSummary
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 수집 리더 확인용 스켈레톤 화면.
 *
 * 목적은 UI가 아니라 파이프라인 확인이다 — 각 수집 소스(수면·걸음·사진)를
 * 읽어오는 경로가 실제로 도는지 폰에서 눈으로 본다.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CollectScreen()
                }
            }
        }
    }
}

@Composable
private fun CollectScreen() {
    val context = LocalContext.current
    val health = remember { HealthConnectReader(context) }
    val photos = remember { MediaStorePhotoReader(context) }
    val scope = rememberCoroutineScope()
    val fmt = remember {
        DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.systemDefault())
    }

    val availability = remember { health.availability() }
    var status by remember { mutableStateOf("준비됨") }
    var healthGranted by remember { mutableStateOf(false) }
    var sleep by remember { mutableStateOf<SleepSummary?>(null) }
    var steps by remember { mutableStateOf<Long?>(null) }

    val mediaPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
    var photoGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, mediaPermission) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var photoSummary by remember { mutableStateOf<PhotoSummary?>(null) }

    val healthPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { grantedPerms ->
        healthGranted = grantedPerms.containsAll(health.permissions)
        status = if (healthGranted) "건강 권한 허용됨" else "건강 권한 거부됨(일부 포함)"
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { ok ->
        photoGranted = ok
        status = if (ok) "사진 권한 허용됨" else "사진 권한 거부됨"
    }

    LaunchedEffect(Unit) {
        if (availability == HealthAvailability.AVAILABLE) {
            healthGranted = health.hasReadPermission()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("bedside — 수집 리더 스켈레톤", style = MaterialTheme.typography.titleLarge)
        Text("Health Connect: ${availabilityText(availability)}")
        Text("상태: $status")

        // --- Health Connect: 수면·걸음 ---
        Button(
            onClick = { healthPermissionLauncher.launch(health.permissions) },
            enabled = availability == HealthAvailability.AVAILABLE && !healthGranted,
        ) { Text("수면·걸음 읽기 권한 요청") }

        Button(
            onClick = {
                scope.launch {
                    status = "수면 읽는 중..."
                    sleep = try {
                        health.readLastNight(Instant.now())
                    } catch (t: Throwable) {
                        status = "수면 오류: ${t.message}"
                        return@launch
                    }
                    status = if (sleep == null) "어젯밤 수면 기록 없음" else "수면 읽음"
                }
            },
            enabled = healthGranted,
        ) { Text("어젯밤 수면 읽기") }

        Button(
            onClick = {
                scope.launch {
                    status = "걸음 읽는 중..."
                    steps = try {
                        health.readTodaySteps(Instant.now())
                    } catch (t: Throwable) {
                        status = "걸음 오류: ${t.message}"
                        return@launch
                    }
                    status = if (steps == null) "오늘 걸음 기록 없음" else "걸음 읽음"
                }
            },
            enabled = healthGranted,
        ) { Text("오늘 걸음 읽기") }

        // --- MediaStore: 사진 메타데이터 ---
        Button(
            onClick = { mediaPermissionLauncher.launch(mediaPermission) },
            enabled = !photoGranted,
        ) { Text("사진 권한 요청") }

        Button(
            onClick = {
                scope.launch {
                    status = "사진 읽는 중..."
                    photoSummary = try {
                        photos.readTodayPhotos(Instant.now())
                    } catch (t: Throwable) {
                        status = "사진 오류: ${t.message}"
                        return@launch
                    }
                    status = if (photoSummary == null) "오늘 사진 없음" else "사진 읽음"
                }
            },
            enabled = photoGranted,
        ) { Text("오늘 사진 읽기") }

        if (sleep != null || steps != null || photoSummary != null) {
            HorizontalDivider()
            steps?.let { Text("오늘 걸음 ${it}보") }
            photoSummary?.let {
                Text("오늘 사진 ${it.count}장 (${fmt.format(it.first)} ~ ${fmt.format(it.last)})")
            }
            sleep?.let { SleepView(it, fmt) }
        }
    }
}

@Composable
private fun SleepView(s: SleepSummary, fmt: DateTimeFormatter) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("취침 ${fmt.format(s.start)} → 기상 ${fmt.format(s.end)}")
        Text("총 수면 ${s.totalMinutes / 60}시간 ${s.totalMinutes % 60}분")
        s.stageMinutes.forEach { (name, minutes) ->
            Text("· $name ${minutes}분")
        }
    }
}

private fun availabilityText(a: HealthAvailability): String = when (a) {
    HealthAvailability.AVAILABLE -> "사용 가능"
    HealthAvailability.UPDATE_REQUIRED -> "Health Connect 업데이트 필요"
    HealthAvailability.NOT_SUPPORTED -> "이 기기에서 지원 안 됨"
}
