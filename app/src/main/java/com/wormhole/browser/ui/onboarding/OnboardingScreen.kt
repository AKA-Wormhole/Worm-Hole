package com.wormhole.browser.ui.onboarding

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.wormhole.browser.core.settings.SearchEngine
import com.wormhole.browser.ui.theme.bouncyClickable

@Composable
fun OnboardingScreen(
    currentEngine: SearchEngine,
    onEngineSelected: (SearchEngine) -> Unit,
    onFinished: () -> Unit,
    onPrivacyPolicyClick: () -> Unit = {},
    onTermsClick: () -> Unit = {},
) {
    var page by remember { mutableIntStateOf(0) }
    val pageCount = 4
    val colors = MaterialTheme.colorScheme
    val titleColor = colors.onBackground
    val bodyColor = colors.onBackground.copy(alpha = 0.72f)
    val lineColor = colors.onBackground.copy(alpha = 0.16f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            if (page < pageCount - 1) {
                Text(
                    text = "Skip",
                    color = bodyColor,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .bouncyClickable(onClick = { page = pageCount - 1 })
                        .padding(8.dp),
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (page) {
                0 -> WelcomePage(titleColor, bodyColor)
                1 -> SearchEnginePage(currentEngine, onEngineSelected, titleColor, bodyColor, lineColor)
                2 -> PermissionsPage(titleColor, bodyColor, lineColor)
                else -> DefaultBrowserPage(titleColor, bodyColor, lineColor)
            }
        }

        PageIndicator(pageCount, page, titleColor, bodyColor.copy(alpha = 0.35f))

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = titleColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 8.dp)
                .height(54.dp)
                .bouncyClickable(onClick = { if (page < pageCount - 1) page++ else onFinished() }),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (page == pageCount - 1) "Start browsing" else "Next",
                    color = colors.background,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (page == pageCount - 1) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("By continuing you agree to the", style = MaterialTheme.typography.bodySmall, color = bodyColor)
                Row {
                    Text(
                        "Terms",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = titleColor,
                        modifier = Modifier.bouncyClickable(onClick = onTermsClick),
                    )
                    Text(" and ", style = MaterialTheme.typography.bodySmall, color = bodyColor)
                    Text(
                        "Privacy Policy",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = titleColor,
                        modifier = Modifier.bouncyClickable(onClick = onPrivacyPolicyClick),
                    )
                }
            }
        } else {
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WelcomePage(titleColor: Color, bodyColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .background(titleColor.copy(alpha = 0.08f), CircleShape)
                .border(1.dp, titleColor.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Public, contentDescription = null, tint = titleColor, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("WormHole", color = titleColor, fontSize = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "Everything you browse, right where it belongs.",
            color = titleColor,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = 28.sp,
        )
    }
}

@Composable
private fun SearchEnginePage(
    currentEngine: SearchEngine,
    onEngineSelected: (SearchEngine) -> Unit,
    titleColor: Color,
    bodyColor: Color,
    lineColor: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = titleColor, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(12.dp))
        Text("Choose search", color = titleColor, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "Used when you type words instead of a website. Change later in Settings.",
            color = bodyColor,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
        )
        SearchEngine.entries.forEach { engine ->
            val selected = engine == currentEngine
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) titleColor else lineColor,
                        shape = RoundedCornerShape(18.dp),
                    )
                    .bouncyClickable(onClick = { onEngineSelected(engine) })
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    engine.displayName,
                    color = titleColor,
                    fontSize = 17.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (selected) {
                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = titleColor)
                }
            }
        }
    }
}

@Composable
private fun PermissionsPage(titleColor: Color, bodyColor: Color, lineColor: Color) {
    val context = LocalContext.current
    var cameraGranted by remember { mutableStateOf(hasPermission(context, Manifest.permission.CAMERA)) }
    var micGranted by remember { mutableStateOf(hasPermission(context, Manifest.permission.RECORD_AUDIO)) }
    var locationGranted by remember { mutableStateOf(hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) }
    var notificationsGranted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 || hasPermission(context, Manifest.permission.POST_NOTIFICATIONS))
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraGranted = it || hasPermission(context, Manifest.permission.CAMERA)
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        micGranted = it || hasPermission(context, Manifest.permission.RECORD_AUDIO)
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        locationGranted = it || hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsGranted = it || Build.VERSION.SDK_INT < 33 ||
            hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Permissions", color = titleColor, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "Only used when a site asks. You can skip any of these.",
            color = bodyColor,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
        )
        if (Build.VERSION.SDK_INT >= 33) {
            PermissionRow(
                icon = Icons.Default.Notifications,
                title = "Notifications",
                subtitle = "Download progress and alerts.",
                granted = notificationsGranted,
                titleColor = titleColor,
                bodyColor = bodyColor,
                lineColor = lineColor,
                onAllow = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            )
        }
        PermissionRow(
            icon = Icons.Default.CameraAlt,
            title = "Camera",
            subtitle = "Video calls and site camera access.",
            granted = cameraGranted,
            titleColor = titleColor,
            bodyColor = bodyColor,
            lineColor = lineColor,
            onAllow = { cameraLauncher.launch(Manifest.permission.CAMERA) },
        )
        PermissionRow(
            icon = Icons.Default.Mic,
            title = "Microphone",
            subtitle = "Voice search and site audio.",
            granted = micGranted,
            titleColor = titleColor,
            bodyColor = bodyColor,
            lineColor = lineColor,
            onAllow = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        )
        PermissionRow(
            icon = Icons.Default.LocationOn,
            title = "Location",
            subtitle = "Maps and sites that ask where you are.",
            granted = locationGranted,
            titleColor = titleColor,
            bodyColor = bodyColor,
            lineColor = lineColor,
            onAllow = { locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
        )
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    titleColor: Color,
    bodyColor: Color,
    lineColor: Color,
    onAllow: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .border(1.dp, lineColor, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(titleColor.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = titleColor, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = titleColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = bodyColor, fontSize = 13.sp)
        }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (granted) titleColor.copy(alpha = 0.12f) else titleColor,
            modifier = Modifier
                .height(36.dp)
                .bouncyClickable(onClick = { if (!granted) onAllow() }),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
                Text(
                    text = if (granted) "Allowed" else "Allow",
                    color = if (granted) titleColor else MaterialTheme.colorScheme.background,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun DefaultBrowserPage(titleColor: Color, bodyColor: Color, lineColor: Color) {
    val context = LocalContext.current
    var isDefault by remember { mutableStateOf(isDefaultBrowser(context)) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = isDefaultBrowser(context)
        if (isDefault) {
            Toast.makeText(context, "WormHole is your default browser", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = titleColor, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(12.dp))
        Text("Open links here", color = titleColor, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "Set WormHole as the default browser so links from Messages, email, and other apps open here.",
            color = bodyColor,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .border(1.dp, if (isDefault) titleColor else lineColor, RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isDefault) "WormHole is the default" else "Default browser",
                    color = titleColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (isDefault) "Links will open in WormHole." else "Optional. You can do this later in Settings.",
                    color = bodyColor,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (isDefault) titleColor.copy(alpha = 0.12f) else titleColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .bouncyClickable(onClick = {
                    if (isDefault) {
                        Toast.makeText(context, "Already the default browser", Toast.LENGTH_SHORT).show()
                    } else {
                        openDefaultBrowserPrompt(context, roleLauncher::launch)
                    }
                }),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (isDefault) "Already set" else "Set as default browser",
                    color = if (isDefault) titleColor else MaterialTheme.colorScheme.background,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun PageIndicator(count: Int, current: Int, active: Color, idle: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { index ->
            val selected = index == current
            val width by animateFloatAsState(if (selected) 20f else 7f, label = "dotWidth")
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .height(7.dp)
                    .width(width.dp)
                    .background(if (selected) active else idle, RoundedCornerShape(50)),
            )
        }
    }
}

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun isDefaultBrowser(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
            return roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
        }
    }
    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com"))
    val resolve = intent.resolveActivity(context.packageManager) ?: return false
    return resolve.packageName == context.packageName
}

private fun openDefaultBrowserPrompt(context: Context, launch: (Intent) -> Unit) {
    val host = context.findActivity() ?: context
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = host.getSystemService(RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
            if (roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)) return
            launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
            return
        }
    }
    val fallbacks = listOf(
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
        Intent(Settings.ACTION_HOME_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (intent in fallbacks) {
        val ok = runCatching { launch(intent) }.isSuccess
        if (ok) return
    }
    Toast.makeText(context, "Open Settings > Default apps > Browser app", Toast.LENGTH_LONG).show()
}
