package online.youcd.heartrate.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import online.youcd.heartrate.BuildConfig
import online.youcd.heartrate.data.model.Gender
import online.youcd.heartrate.data.model.MaxHrMode
import online.youcd.heartrate.data.model.UserProfile
import online.youcd.heartrate.data.update.UpdateChecker
import online.youcd.heartrate.data.update.UpdateDownloader
import online.youcd.heartrate.data.update.UpdateInfo
import online.youcd.heartrate.ui.theme.SuccessGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val profile by viewModel.profile.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val bytes = viewModel.buildBackupZip()
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                }.onSuccess {
                    Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val success = context.contentResolver.openInputStream(uri)?.use { input ->
                    viewModel.restoreFromZip(input)
                } ?: false
                Toast.makeText(
                    context,
                    if (success) "导入成功" else "导入失败：文件格式不正确",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    var nickname by remember { mutableStateOf(profile.nickname) }
    var gender by remember { mutableStateOf(profile.gender) }
    var birthYear by remember { mutableStateOf(profile.birthYear) }
    var birthMonth by remember { mutableStateOf(profile.birthMonth) }
    var height by remember { mutableStateOf(profile.heightCm.toString()) }
    var weight by remember { mutableStateOf(profile.weightKg.toString()) }
    var maxHrMode by remember { mutableStateOf(profile.maxHrMode) }
    var manualMaxHr by remember { mutableStateOf(profile.manualMaxHr.toString()) }
    var saveState by remember { mutableStateOf<Boolean?>(null) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var showNumberInputDialog by remember { mutableStateOf(false) }
    var editingField by remember { mutableStateOf("") }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateError by remember { mutableStateOf(false) }

    LaunchedEffect(profile) {
        nickname = profile.nickname
        gender = profile.gender
        birthYear = profile.birthYear
        birthMonth = profile.birthMonth
        height = profile.heightCm.toString()
        weight = profile.weightKg.toString()
        maxHrMode = profile.maxHrMode
        manualMaxHr = profile.manualMaxHr.toString()
    }

    val computedAge = UserProfile.computeAge(birthYear, birthMonth)
    val manualMaxHrValue = manualMaxHr.toIntOrNull() ?: 0
    val computedMaxHr = if (maxHrMode == MaxHrMode.AUTO) {
        (208 - 0.7 * computedAge).toInt().coerceAtLeast(80)
    } else {
        manualMaxHrValue
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .padding(horizontal = 20.dp)
    ) {
        // 固定 header
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(top = 16.dp, bottom = 20.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {

        SettingsCard(title = "个人资料") {
            SettingsRow(
                label = "昵称",
                content = {
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it },
                        placeholder = { Text("请输入昵称", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            disabledBorderColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )
            SettingsDivider()
            SettingsRow(
                label = "性别",
                content = {
                    AnimatedSegmentedControl(
                        options = listOf("男", "女"),
                        selectedIndex = if (gender == Gender.MALE) 0 else 1,
                        onSelect = { index ->
                            gender = if (index == 0) Gender.MALE else Gender.FEMALE
                        },
                        height = 40.dp
                    )
                }
            )
            SettingsDivider()
            BirthDateSettingRow(
                birthYear = birthYear,
                birthMonth = birthMonth,
                onYearChange = { birthYear = it },
                onMonthChange = { birthMonth = it }
            )
            SettingsDivider()
            ListItemSettingRow(
                label = "身高",
                value = if (height.toIntOrNull() ?: 0 > 0) height else "--",
                unit = "cm",
                onEdit = {
                    showNumberInputDialog = true
                    editingField = "身高"
                }
            )
            SettingsDivider()
            val heightCm = height.toIntOrNull() ?: 0
            val weightKg = weight.toIntOrNull() ?: 0
            val idealWeight = if (heightCm > 0) {
                (22.0 * (heightCm / 100.0) * (heightCm / 100.0)).roundToInt()
            } else 0
            if (weightKg > 0 && idealWeight > 0) {
                ListItemSettingRow(
                    label = "体重",
                    value = weight.toString(),
                    unit = "kg",
                    extra = "标准 ${idealWeight}kg",
                    onEdit = {
                        showNumberInputDialog = true
                        editingField = "体重"
                    }
                )
            } else {
                ListItemSettingRow(
                    label = "体重",
                    value = if (weightKg > 0) weight else "--",
                    unit = "kg",
                    onEdit = {
                        showNumberInputDialog = true
                        editingField = "体重"
                    }
                )
            }
            if (heightCm > 0 && weightKg > 0) {
                val bmi = weightKg / ((heightCm / 100.0) * (heightCm / 100.0))
                val bmiText = String.format(Locale.US, "%.1f", bmi)
                val bmiDesc = when {
                    bmi < 18.5 -> "偏瘦"
                    bmi < 24 -> "正常"
                    bmi < 28 -> "超重"
                    else -> "肥胖"
                }
                Text(
                    text = "BMI $bmiText · $bmiDesc · 标准 18.5-24",
                    fontSize = 12.sp,
                    color = Color(0xFF636366),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        SettingsCard(title = "心率设置") {
            AnimatedSegmentedControl(
                options = listOf("自动", "手动"),
                selectedIndex = if (maxHrMode == MaxHrMode.AUTO) 0 else 1,
                onSelect = { index ->
                    maxHrMode = if (index == 0) MaxHrMode.AUTO else MaxHrMode.MANUAL
                },
                height = 44.dp
            )

            Spacer(Modifier.height(20.dp))

            // 手动模式展开区（动画）
            AnimatedVisibility(
                visible = maxHrMode == MaxHrMode.MANUAL,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    SettingsRow(
                        label = "最大心率",
                        content = {
                            OutlinedTextField(
                                value = manualMaxHr,
                                onValueChange = {
                                    if (it.length <= 3) manualMaxHr = it.filter(Char::isDigit)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = MaterialTheme.typography.titleMedium,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF2C2C2E),
                                    unfocusedContainerColor = Color(0xFF2C2C2E),
                                    focusedBorderColor = Color(0xFFFF2D55),
                                    unfocusedBorderColor = Color(0xFF2C2C2E),
                                    cursorColor = Color(0xFFFF2D55)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "根据公式 220 - 年龄，建议值为 ${computedMaxHr} BPM",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF636366),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 结果展示（心形动态脉动）
            val pulseTransition = rememberInfiniteTransition(label = "heartPulse")
            val pulseScale by pulseTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseScale"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2C2C2E), RoundedCornerShape(12.dp))
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer {
                                scaleX = pulseScale
                                scaleY = pulseScale
                            }
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "$computedMaxHr",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = SuccessGreen
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "bpm",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFA1A1A6)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (maxHrMode == MaxHrMode.AUTO) {
                Text(
                    text = "根据您的年龄（$computedAge 岁），系统自动计算最大心率为 $computedMaxHr BPM",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF636366),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "自定义最大心率值",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF636366),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        SettingsCard(title = "数据备份") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val fileName =
                            "heartRate_" + SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
                                .format(Date()) + ".zip"
                        exportLauncher.launch(fileName)
                    },
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF2C2C2E)),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF2C2C2E),
                        contentColor = Color.White
                    )
                ) {
                    Text("导出备份", fontWeight = FontWeight.Medium)
                }
                OutlinedButton(
                    onClick = { showImportConfirm = true },
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF2C2C2E)),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF2C2C2E),
                        contentColor = Color.White
                    )
                ) {
                    Text("导入备份", fontWeight = FontWeight.Medium)
                }
            }
            Text(
                text = "导出文件包含训练历史与个人资料，导入将覆盖当前数据。",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = Color(0xFF636366),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        SettingsCard(title = "关于") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "版本号",
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF8E8E93)
                )
            }
            SettingsDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !checkingUpdate) {
                        checkingUpdate = true
                        updateError = false
                        updateInfo = null
                        scope.launch {
                            val info = UpdateChecker.checkForUpdate()
                            checkingUpdate = false
                            if (info != null) {
                                if (UpdateChecker.isNewer(info.versionName, BuildConfig.VERSION_NAME)) {
                                    updateInfo = info
                                } else {
                                    Toast.makeText(context, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                updateError = true
                            }
                        }
                    }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "检查更新",
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                if (checkingUpdate) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFFFF2D55)
                    )
                } else {
                    Text(
                        text = if (updateError) "检查失败，点击重试" else "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.sp,
                        color = if (updateError) Color(0xFFFF3B30) else Color(0xFF8E8E93)
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                viewModel.saveProfile(
                    UserProfile(
                        nickname = nickname,
                        gender = gender,
                        birthYear = birthYear.coerceIn(
                            UserProfile.MIN_BIRTH_YEAR,
                            java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                        ),
                        birthMonth = birthMonth.coerceIn(1, 12),
                        heightCm = height.toIntOrNull()
                            ?.coerceIn(UserProfile.MIN_HEIGHT, UserProfile.MAX_HEIGHT) ?: 0,
                        weightKg = weight.toIntOrNull()
                            ?.coerceIn(UserProfile.MIN_WEIGHT, UserProfile.MAX_WEIGHT) ?: 0,
                        maxHrMode = maxHrMode,
                        manualMaxHr = manualMaxHrValue.coerceIn(80, 250)
                    )
                )
                saveState = true
                Toast.makeText(context, "设置已更新", Toast.LENGTH_SHORT).show()
                scope.launch {
                    delay(2000)
                    saveState = null
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF2D55),
                contentColor = Color.White
            )
        ) {
            when {
                saveState == true -> {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFF34C759))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "已保存",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                else -> {
                    Text(
                        text = "保存设置",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        }
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text("确认导入备份？") },
            text = { Text("导入将覆盖当前所有训练数据和个人资料，且无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    importLauncher.launch(arrayOf("application/zip"))
                }) {
                    Text("继续导入", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showNumberInputDialog) {
        var inputValue by remember {
            mutableStateOf(
                if (editingField == "身高") height else weight
            )
        }
        AlertDialog(
            onDismissRequest = { showNumberInputDialog = false },
            title = { Text("编辑${editingField}") },
            text = {
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = { if (it.length <= 3) inputValue = it.filter(Char::isDigit) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.titleMedium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF2C2C2E),
                        unfocusedContainerColor = Color(0xFF2C2C2E),
                        focusedBorderColor = Color(0xFFFF2D55),
                        unfocusedBorderColor = Color(0xFF2C2C2E),
                        cursorColor = Color(0xFFFF2D55)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editingField == "身高") height = inputValue else weight = inputValue
                    showNumberInputDialog = false
                }) {
                    Text("确定", color = Color(0xFFFF2D55))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNumberInputDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = { Text("发现新版本 v${info.versionName}") },
            text = {
                Column {
                    Text(
                        text = info.releaseNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "当前版本 v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8E8E93)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    updateInfo = null
                    UpdateDownloader.download(context, info.downloadUrl)
                }) {
                    Text("下载更新", color = Color(0xFFFF2D55))
                }
            },
            dismissButton = {
                TextButton(onClick = { updateInfo = null }) {
                    Text("稍后再说")
                }
            }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 14.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontSize = 12.sp,
            color = Color(0xFF8E8E93),
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1C1C1E),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(96.dp)
        )
        content()
    }
}

@Composable
private fun BirthDateSettingRow(
    birthYear: Int,
    birthMonth: Int,
    onYearChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit
) {
    var dateMenuOpen by remember { mutableStateOf(false) }
    var selectingMonth by remember { mutableStateOf(false) }
    val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    val ageText = UserProfile.computeAge(birthYear, birthMonth)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "出生年月",
            style = MaterialTheme.typography.titleMedium,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.width(96.dp)
        )
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { dateMenuOpen = true }
                    .background(Color(0xFF2C2C2E))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$birthYear 年 $birthMonth 月",
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 15.sp,
                    color = Color.White
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color(0xFF8E8E93),
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = dateMenuOpen,
                onDismissRequest = { dateMenuOpen = false },
                modifier = Modifier.height(320.dp)
            ) {
                if (selectingMonth) {
                    // 月份选择面板
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回",
                                    tint = Color(0xFF8E8E93),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "$birthYear 年 · 选择月份",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        },
                        onClick = { selectingMonth = false }
                    )
                    HorizontalDivider(color = Color(0xFF2C2C2E))
                    (1..12).forEach { m ->
                        DropdownMenuItem(
                            text = { Text("$m 月") },
                            onClick = {
                                onMonthChange(m)
                                dateMenuOpen = false
                                selectingMonth = false
                            }
                        )
                    }
                } else {
                    // 年份选择面板
                    Text(
                        text = "选择年份",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                    HorizontalDivider(color = Color(0xFF2C2C2E))
                    (UserProfile.MIN_BIRTH_YEAR..currentYear).reversed().forEach { y ->
                        DropdownMenuItem(
                            text = { Text("$y 年") },
                            onClick = {
                                onYearChange(y)
                                selectingMonth = true
                            }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "年龄 $ageText",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = SuccessGreen
        )
    }
}

@Composable
private fun ListItemSettingRow(
    label: String,
    value: String,
    onEdit: () -> Unit,
    unit: String,
    extra: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onEdit)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.width(96.dp)
        )
        Spacer(Modifier.weight(1f))
        if (extra != null) {
            Text(
                text = extra,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                color = Color(0xFF34C759)
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = unit,
            style = MaterialTheme.typography.titleMedium,
            fontSize = 14.sp,
            color = Color(0xFF8E8E93)
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFF636366),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.06f))
}

@Composable
private fun AnimatedSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp
) {
    val containerColor = Color(0xFF2C2C2E)
    val selectedColor = Color(0xFFFF2D55)
    val unselectedText = Color(0xFFA1A1A6)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
    ) {
        val itemWidth = maxWidth / options.size
        val indicatorOffset by animateDpAsState(
            targetValue = itemWidth * selectedIndex,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "indicatorOffset"
        )

        // 弹性滑动选中块
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(itemWidth)
                .fillMaxHeight()
                .padding(3.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(selectedColor)
        )

        // 选项文本
        Row(modifier = Modifier.fillMaxSize()) {
            options.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 15.sp,
                        fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Medium,
                        color = if (index == selectedIndex) Color.White else unselectedText
                    )
                }
            }
        }
    }
}
