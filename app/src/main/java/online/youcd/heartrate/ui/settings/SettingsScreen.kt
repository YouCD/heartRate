package online.youcd.heartrate.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import online.youcd.heartrate.BuildConfig
import online.youcd.heartrate.data.model.Gender
import online.youcd.heartrate.data.model.MaxHrMode
import online.youcd.heartrate.data.model.UserProfile
import online.youcd.heartrate.ui.theme.SuccessGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "设置",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))

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
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = gender == Gender.MALE,
                            onClick = { gender = Gender.MALE },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primary,
                                activeContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text("男", fontWeight = FontWeight.Medium)
                        }
                        SegmentedButton(
                            selected = gender == Gender.FEMALE,
                            onClick = { gender = Gender.FEMALE },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primary,
                                activeContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text("女", fontWeight = FontWeight.Medium)
                        }
                    }
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
            NumberSettingRow(
                label = "身高",
                value = height,
                onValueChange = { if (it.length <= 3) height = it.filter(Char::isDigit) },
                unit = "cm"
            )
            SettingsDivider()
            NumberSettingRow(
                label = "体重",
                value = weight,
                onValueChange = { if (it.length <= 3) weight = it.filter(Char::isDigit) },
                unit = "kg"
            )
        }

        Spacer(Modifier.height(14.dp))

        SettingsCard(title = "心率设置") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = maxHrMode == MaxHrMode.AUTO,
                    onClick = { maxHrMode = MaxHrMode.AUTO },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primary,
                        activeContentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("自动", fontWeight = FontWeight.Medium)
                }
                SegmentedButton(
                    selected = maxHrMode == MaxHrMode.MANUAL,
                    onClick = { maxHrMode = MaxHrMode.MANUAL },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primary,
                        activeContentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("手动", fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(20.dp))

            if (maxHrMode == MaxHrMode.MANUAL) {
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
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                )
                Spacer(Modifier.height(12.dp))
            }

            // 结果展示
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(28.dp)
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (maxHrMode == MaxHrMode.AUTO) {
                    "基于标准公式：208 - 0.7 × 年龄"
                } else {
                    "自定义最大心率值"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(14.dp))

        SettingsCard(title = "数据备份") {
            SettingsRow(
                label = "导出备份",
                content = {
                    Button(
                        onClick = {
                            val fileName =
                                "heartRate_" + SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
                                    .format(Date()) + ".zip"
                            exportLauncher.launch(fileName)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("导出")
                    }
                }
            )
            SettingsDivider()
            SettingsRow(
                label = "导入备份",
                content = {
                    Button(
                        onClick = { importLauncher.launch(arrayOf("application/zip")) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("导入")
                    }
                }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "导出为 heartRate_时间戳.zip，包含训练历史与个人资料。导入将覆盖当前数据。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(14.dp))

        SettingsCard(title = "关于") {
            SettingsRow(
                label = "版本",
                content = {
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
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
            shape = RoundedCornerShape(16.dp)
        ) {
            if (saveState == true) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "已保存",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = "保存设置",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
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
    var yearMenuOpen by remember { mutableStateOf(false) }
    var monthMenuOpen by remember { mutableStateOf(false) }
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
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(96.dp)
        )
        Box {
            OutlinedButton(
                onClick = { yearMenuOpen = true },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("$birthYear 年", style = MaterialTheme.typography.titleMedium)
            }
            DropdownMenu(
                expanded = yearMenuOpen,
                onDismissRequest = { yearMenuOpen = false },
                modifier = Modifier.height(300.dp)
            ) {
                (UserProfile.MIN_BIRTH_YEAR..currentYear).reversed().forEach { y ->
                    DropdownMenuItem(
                        text = { Text("$y 年") },
                        onClick = {
                            onYearChange(y)
                            yearMenuOpen = false
                        }
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box {
            OutlinedButton(
                onClick = { monthMenuOpen = true },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("$birthMonth 月", style = MaterialTheme.typography.titleMedium)
            }
            DropdownMenu(
                expanded = monthMenuOpen,
                onDismissRequest = { monthMenuOpen = false },
                modifier = Modifier.height(300.dp)
            ) {
                (1..12).forEach { m ->
                    DropdownMenuItem(
                        text = { Text("$m 月") },
                        onClick = {
                            onMonthChange(m)
                            monthMenuOpen = false
                        }
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "约 $ageText 岁",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NumberSettingRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String
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
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.titleMedium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.06f))
}
