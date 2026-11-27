package com.focusguard.app.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.antibypass.PermissionMonitor
import com.focusguard.app.ui.components.GlassCard
import com.focusguard.app.ui.theme.FrictionColors

@Composable
fun AccessibilityDisclosureScreen(onAccept: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FrictionColors.Background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 34.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Focus Guard",
                    color = FrictionColors.TextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Block distractions during focus time.",
                    color = FrictionColors.TextSecondary,
                    fontSize = 15.sp,
                    lineHeight = 21.sp
                )
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Accessibility disclosure",
                            color = FrictionColors.TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        DisclosurePoint(
                            title = "Foreground app detection",
                            body = "Detects blocked apps during focus time."
                        )
                        DisclosurePoint(
                            title = "Visible screen structure",
                            body = "For Reels/Shorts rules, visible screen text and structure are checked on device."
                        )
                        DisclosurePoint(
                            title = "No personal data collection",
                            body = "Screen content, messages, and passwords are not uploaded."
                        )
                        DisclosurePoint(
                            title = "Your control",
                            body = "Disable Accessibility to stop the service."
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = onAccept,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FrictionColors.Accent,
                        contentColor = FrictionColors.TextOnAccent
                    )
                ) {
                    Text(
                        text = "I understand and agree",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ProtectionSetupScreen(onArmProtection: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionMonitor = remember { PermissionMonitor(context) }

    var accessibilityReady by remember { mutableStateOf(permissionMonitor.isAccessibilityEnabled()) }
    var overlayReady by remember { mutableStateOf(permissionMonitor.isOverlayPermitted()) }
    var usageReady by remember { mutableStateOf(permissionMonitor.isUsageStatsPermitted()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityReady = permissionMonitor.isAccessibilityEnabled()
                overlayReady = permissionMonitor.isOverlayPermitted()
                usageReady = permissionMonitor.isUsageStatsPermitted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val canArm = accessibilityReady
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FrictionColors.Background)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 28.dp,
            backgroundColor = FrictionColors.GlassBackground
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = FrictionColors.Accent,
                    modifier = Modifier.size(38.dp)
                )
                Text(
                    text = "Finish protection setup",
                    color = FrictionColors.TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Accessibility is required for core blocking. Display over other apps enables PYQ challenges, and Usage access enables backup detection and analytics.",
                    color = FrictionColors.TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                SetupPermissionButton(
                    label = "Accessibility (Required)",
                    ready = accessibilityReady,
                    required = true,
                    onClick = {
                        FocusGuardApp.instance.prefs.allowPermissionSetupWindow()
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
                SetupPermissionButton(
                    label = "Display over other apps (PYQ overlay)",
                    ready = overlayReady,
                    required = false,
                    onClick = {
                        FocusGuardApp.instance.prefs.allowPermissionSetupWindow()
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                )
                SetupPermissionButton(
                    label = "Usage access (Analytics & backup)",
                    ready = usageReady,
                    required = false,
                    onClick = {
                        FocusGuardApp.instance.prefs.allowPermissionSetupWindow()
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                )
                Button(
                    onClick = onArmProtection,
                    enabled = canArm,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FrictionColors.Accent,
                        disabledContainerColor = FrictionColors.SurfaceElevated
                    )
                ) {
                    Text(if (canArm) "Arm Focus Guard" else "Enable Accessibility to continue")
                }
            }
        }
    }
}

@Composable
private fun SetupPermissionButton(
    label: String,
    ready: Boolean,
    required: Boolean,
    onClick: () -> Unit
) {
    val statusText = when {
        ready -> "Ready"
        required -> "Required"
        else -> "Optional"
    }
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label · $statusText",
            color = if (ready) FrictionColors.Success else FrictionColors.TextPrimary
        )
    }
}

@Composable
private fun DisclosurePoint(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            color = FrictionColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = body,
            color = FrictionColors.TextSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}
