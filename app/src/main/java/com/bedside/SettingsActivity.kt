package com.bedside

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.bedside.calendar.CalendarReader
import com.bedside.collect.CollectionScheduler
import com.bedside.data.CollectedEvent
import com.bedside.data.Db
import com.bedside.health.HealthAvailability
import com.bedside.health.HealthConnectReader
import com.bedside.health.SleepSummary
import com.bedside.health.WeightSample
import com.bedside.location.GeofenceManager
import com.bedside.location.GeofencePlaces
import com.bedside.media.MediaStorePhotoReader
import com.bedside.media.PhotoSummary
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 설정·수집 화면. 권한·지오펜스·수집 서비스·개발용 읽기/이벤트 확인이 모여 있다.
 * 홈(오늘 밤 대화·지난 일기)과 분리된 뒷단 화면이다.
 */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen()
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val health = remember { HealthConnectReader(context) }
    val photos = remember { MediaStorePhotoReader(context) }
    val geofences = remember { GeofenceManager(context) }
    val scope = rememberCoroutineScope()
    val fmt = remember {
        DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.systemDefault())
    }

    val availability = remember { health.availability() }
    var status by remember { mutableStateOf("준비됨") }
    var healthGranted by remember { mutableStateOf(false) }
    var sleep by remember { mutableStateOf<SleepSummary?>(null) }
    var steps by remember { mutableStateOf<Long?>(null) }
    var weight by remember { mutableStateOf<WeightSample?>(null) }

    val readImagesPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
    val mediaPermissions = remember {
        buildList {
            add(readImagesPermission)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACCESS_MEDIA_LOCATION)
            }
        }.toTypedArray()
    }
    var photoGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, readImagesPermission) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var locationGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_MEDIA_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var photoSummary by remember { mutableStateOf<PhotoSummary?>(null) }

    // 위치/지오펜스
    fun granted(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    var fineGranted by remember { mutableStateOf(granted(Manifest.permission.ACCESS_FINE_LOCATION)) }
    var bgGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        )
    }
    var geoStatus by remember { mutableStateOf("") }

    var eventCount by remember { mutableStateOf(0) }
    var events by remember { mutableStateOf<List<CollectedEvent>>(emptyList()) }

    val healthPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { grantedPerms ->
        healthGranted = grantedPerms.containsAll(health.permissions)
        status = if (healthGranted) "건강 권한 허용됨" else "건강 권한 거부됨(일부 포함)"
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        photoGranted = result[readImagesPermission] == true
        locationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            result[Manifest.permission.ACCESS_MEDIA_LOCATION] == true
        status = if (photoGranted) "사진 권한 허용됨" else "사진 권한 거부됨"
    }

    val fineLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        fineGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        status = if (fineGranted) "위치 권한 허용됨" else "위치 권한 거부됨"
    }
    val bgLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { ok ->
        bgGranted = ok
        status = if (ok) "백그라운드 위치 허용됨" else "백그라운드 위치는 설정에서 '항상 허용' 필요"
    }
    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { }

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
        Text("설정 · 수집", style = MaterialTheme.typography.titleLarge)

        // --- 앱 잠금 (민감 데이터 보호) ---
        var appLock by remember { mutableStateOf(AppLock.enabled(context)) }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("앱 잠금", style = MaterialTheme.typography.titleSmall)
                Text("열 때 기기 잠금(PIN·지문)으로 확인", style = MaterialTheme.typography.bodySmall)
            }
            androidx.compose.material3.Switch(
                checked = appLock,
                onCheckedChange = { on ->
                    val km = context.getSystemService(android.app.KeyguardManager::class.java)
                    if (on && (km == null || !km.isDeviceSecure)) {
                        status = "기기에 잠금(PIN·지문)이 없어요. 먼저 기기 잠금을 설정하세요."
                    } else {
                        appLock = on
                        AppLock.setEnabled(context, on)
                        if (!on) AppLock.unlocked = true
                    }
                },
            )
        }
        // --- 틈틈이 알림 ---
        var nudges by remember { mutableStateOf(com.bedside.nudge.NudgeScheduler.enabled(context)) }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("틈틈이 말 걸기", style = MaterialTheme.typography.titleSmall)
                Text("하루 중 상황에 맞춰 먼저 알림으로 물어봄", style = MaterialTheme.typography.bodySmall)
            }
            androidx.compose.material3.Switch(
                checked = nudges,
                onCheckedChange = { on ->
                    nudges = on
                    com.bedside.nudge.NudgeScheduler.setEnabled(context, on)
                    status = if (on) "틈틈이 알림 켬" else "틈틈이 알림 끔"
                },
            )
        }
        // --- 사진 보여주기 (비전) ---
        var photoVision by remember { mutableStateOf(com.bedside.media.PhotoVision.enabled(context)) }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("사진 보여주기", style = MaterialTheme.typography.titleSmall)
                Text("방금 찍은 사진을 AI가 보고 반응(사진이 API로 전송됨)", style = MaterialTheme.typography.bodySmall)
            }
            androidx.compose.material3.Switch(
                checked = photoVision,
                onCheckedChange = { on ->
                    photoVision = on
                    com.bedside.media.PhotoVision.setEnabled(context, on)
                    status = if (on) "사진 보여주기 켬" else "사진 보여주기 끔"
                },
            )
        }
        HorizontalDivider()

        // --- 캘린더 (오늘 일정) ---
        var calGranted by remember { mutableStateOf(CalendarReader.hasPermission(context)) }
        var calText by remember { mutableStateOf("") }
        val calLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            calGranted = granted
            status = if (granted) "캘린더 권한 허용됨" else "캘린더 권한 거부됨"
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("캘린더", style = MaterialTheme.typography.titleSmall)
                Text("오늘 일정을 질문 재료로 참고", style = MaterialTheme.typography.bodySmall)
            }
            if (!calGranted) {
                Button(onClick = { calLauncher.launch(Manifest.permission.READ_CALENDAR) }) {
                    Text("권한 요청")
                }
            } else {
                Button(onClick = {
                    scope.launch {
                        calText = CalendarReader.formatForContext(CalendarReader.today(context))
                            .ifBlank { "오늘 일정 없음" }
                    }
                }) { Text("오늘 일정") }
            }
        }
        if (calText.isNotEmpty()) {
            Text(calText, style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()

        // --- 화면 사용시간 ---
        var stText by remember { mutableStateOf("") }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("화면 사용시간", style = MaterialTheme.typography.titleSmall)
                Text("오늘 얼마나·뭘 봤는지 질문 재료로 (설정의 '사용 정보 접근' 허용)", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = {
                if (com.bedside.usage.ScreenTimeReader.hasPermission(context)) {
                    val s = com.bedside.usage.ScreenTimeReader.today(context)
                    stText = if (s == null) {
                        "오늘 기록 없음"
                    } else {
                        "총 ${s.totalMinutes / 60}시간 ${s.totalMinutes % 60}분 · " +
                            s.topApps.joinToString(", ") { "${it.first} ${it.second}분" }
                    }
                } else {
                    com.bedside.usage.ScreenTimeReader.openSettings(context)
                    status = "설정에서 bedside의 '사용 정보 접근'을 허용하세요"
                }
            }) { Text("오늘 보기") }
        }
        if (stText.isNotEmpty()) {
            Text(stText, style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()

        Text("Health Connect: ${availabilityText(availability)}")
        Text("상태: $status")

        // --- Health Connect: 수면·걸음·몸무게 ---
        Button(
            onClick = { healthPermissionLauncher.launch(health.permissions) },
            enabled = availability == HealthAvailability.AVAILABLE && !healthGranted,
        ) { Text("수면·걸음·몸무게 권한 요청") }

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

        Button(
            onClick = {
                scope.launch {
                    status = "몸무게 읽는 중..."
                    weight = try {
                        health.readLatestWeight(Instant.now())
                    } catch (t: Throwable) {
                        status = "몸무게 오류: ${t.message}"
                        return@launch
                    }
                    status = if (weight == null) "몸무게 기록 없음" else "몸무게 읽음"
                }
            },
            enabled = healthGranted,
        ) { Text("최근 몸무게 읽기") }

        // --- MediaStore: 사진 메타데이터 + GPS ---
        Button(
            onClick = { mediaPermissionLauncher.launch(mediaPermissions) },
            enabled = !(photoGranted && locationGranted),
        ) { Text("사진·위치 권한 요청") }

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

        // --- 위치 / 지오펜스 ---
        HorizontalDivider()
        Text("위치 / 지오펜스", style = MaterialTheme.typography.titleMedium)

        Button(
            onClick = {
                fineLocationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            },
            enabled = !fineGranted,
        ) { Text("위치 권한 요청") }

        Button(
            onClick = { bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) },
            enabled = fineGranted && !bgGranted,
        ) { Text("백그라운드 위치 허용(항상)") }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Button(
                onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            ) { Text("알림 권한 요청") }
        }

        Button(
            onClick = {
                scope.launch {
                    geoStatus = "현재 위치 잡는 중..."
                    geoStatus = geofences.captureCurrentAs("work", GeofencePlaces.labelFor("work")).fold(
                        onSuccess = { "회사 위치 저장됨: %.5f, %.5f".format(it.lat, it.lng) },
                        onFailure = { "회사 위치 실패: ${it.message}" },
                    )
                }
            },
            enabled = fineGranted,
        ) { Text("현재 위치를 회사로 설정") }

        Button(
            onClick = {
                scope.launch {
                    geoStatus = "현재 위치 잡는 중..."
                    geoStatus = geofences.captureCurrentAs("home", GeofencePlaces.labelFor("home")).fold(
                        onSuccess = { "집 위치 저장됨: %.5f, %.5f".format(it.lat, it.lng) },
                        onFailure = { "집 위치 실패: ${it.message}" },
                    )
                }
            },
            enabled = fineGranted,
        ) { Text("현재 위치를 집으로 설정") }

        Button(
            onClick = {
                scope.launch {
                    val places = GeofencePlaces.all
                    if (places.isEmpty()) {
                        geoStatus = "장소 설정 없음 (personal.properties 확인)"
                        return@launch
                    }
                    geoStatus = "주소 해석 중..."
                    val resolved = geofences.resolve(places)
                    if (resolved.isEmpty()) {
                        geoStatus = "주소 해석 실패 — personal.properties에 lat/lng 직접 입력 필요"
                        return@launch
                    }
                    val coords = resolved.joinToString("\n") {
                        "· ${it.label} %.5f, %.5f".format(it.lat, it.lng)
                    }
                    geoStatus = geofences.register(resolved).fold(
                        onSuccess = {
                            SetupStatus.markGeofencesRegistered(context)
                            "지오펜스 ${it}개 등록됨 (반경 ${GeofencePlaces.RADIUS_METERS.toInt()}m)\n$coords"
                        },
                        onFailure = { "등록 실패: ${it.message}\n$coords" },
                    )
                }
            },
            enabled = fineGranted,
        ) { Text("지오펜스 등록") }

        Button(
            onClick = {
                scope.launch {
                    geoStatus = geofences.clear().fold(
                        onSuccess = { "지오펜스 해제됨" },
                        onFailure = { "해제 실패: ${it.message}" },
                    )
                }
            },
            enabled = fineGranted,
        ) { Text("지오펜스 해제") }

        if (geoStatus.isNotEmpty()) Text(geoStatus)

        // --- 수집 서비스 ---
        HorizontalDivider()
        Text("수집 서비스", style = MaterialTheme.typography.titleMedium)
        Button(onClick = { CollectionScheduler.start(context) }) {
            Text("지금 수집 (서비스 시작)")
        }
        Button(
            onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            },
        ) { Text("배터리 최적화 예외 요청") }

        // --- 저장(암호화 DB) ---
        Button(
            onClick = {
                scope.launch {
                    val dao = Db.get(context).events()
                    eventCount = dao.count()
                    events = dao.recent(20)
                }
            },
        ) { Text("저장된 이벤트 보기") }

        if (eventCount > 0) {
            HorizontalDivider()
            Text("저장된 이벤트 ${eventCount}개 (암호화 DB)")
            events.forEach { e ->
                Text(
                    "· [${e.source}/${e.type}] ${e.label ?: ""} ${e.value ?: ""} — ${
                        fmt.format(Instant.ofEpochMilli(e.occurredAt))
                    }".replace("  ", " "),
                )
            }
        }

        // --- 읽은 값 ---
        if (sleep != null || steps != null || weight != null || photoSummary != null) {
            HorizontalDivider()
            steps?.let { Text("오늘 걸음 ${it}보") }
            weight?.let { Text("최근 몸무게 %.1fkg (%s)".format(it.kilograms, fmt.format(it.time))) }
            photoSummary?.let {
                Text("오늘 사진 ${it.count}장 (${fmt.format(it.first)} ~ ${fmt.format(it.last)})")
                val loc = it.firstLocation
                if (loc != null) {
                    Text("· 위치정보 ${it.locatedCount}장, 첫 위치 %.4f, %.4f".format(loc.lat, loc.lon))
                } else {
                    Text("· 위치정보 없음")
                }
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
