package com.focusguard.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.domain.auth.AuthUiState
import com.focusguard.app.ui.components.GlassCard
import com.focusguard.app.ui.components.GradientButton
import com.focusguard.app.ui.theme.FrictionColors

@Composable
fun LoginScreen(
    state: AuthUiState,
    onGoogleSignIn: () -> Unit,
    onEmailSignIn: (email: String, password: String) -> Unit,
    onEmailSignUp: (name: String, email: String, password: String) -> Unit,
    onContinueWithoutAccount: () -> Unit,
    onClearError: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var isCreatingAccount by remember { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val scrollState = rememberScrollState()

    val isFormValid = when {
        state.isLoading -> false
        isCreatingAccount && name.trim().length < 2 -> false
        !email.contains("@") || !email.contains(".") -> false
        password.length < 8 -> false
        else -> true
    }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onClearError()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        FrictionColors.Background,
                        FrictionColors.Surface,
                        FrictionColors.Background
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding()
            .padding(24.dp)
    ) {
        GlassCard(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            cornerRadius = 28.dp,
            backgroundColor = FrictionColors.GlassBackground
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(FrictionColors.AccentSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = FrictionColors.Accent,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Friction Guard",
                    color = FrictionColors.TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Control your distractions",
                    color = FrictionColors.TextSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(22.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { isCreatingAccount = false },
                        enabled = !state.isLoading
                    ) {
                        Text(
                            text = "Sign in",
                            color = if (isCreatingAccount) FrictionColors.TextSecondary else FrictionColors.Accent,
                            fontWeight = if (isCreatingAccount) FontWeight.Normal else FontWeight.Bold
                        )
                    }
                    Text(
                        text = " / ",
                        color = FrictionColors.TextMuted
                    )
                    TextButton(
                        onClick = { isCreatingAccount = true },
                        enabled = !state.isLoading
                    ) {
                        Text(
                            text = "Sign up",
                            color = if (isCreatingAccount) FrictionColors.Accent else FrictionColors.TextSecondary,
                            fontWeight = if (isCreatingAccount) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                if (isCreatingAccount) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        enabled = !state.isLoading,
                        singleLine = true,
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    enabled = !state.isLoading,
                    singleLine = true,
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    enabled = !state.isLoading,
                    singleLine = true,
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (state.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(FrictionColors.AccentSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isCreatingAccount) "Creating account..." else "Signing in...",
                            color = FrictionColors.Accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    GradientButton(
                        text = if (isCreatingAccount) "Create account" else "Sign in",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isFormValid,
                        onClick = {
                            if (isCreatingAccount) {
                                onEmailSignUp(name.trim(), email.trim(), password)
                            } else {
                                onEmailSignIn(email.trim(), password)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(
                    onClick = onGoogleSignIn,
                    enabled = !state.isLoading
                ) {
                    Text(
                        text = "Continue with Google",
                        color = FrictionColors.TextSecondary,
                        fontSize = 13.sp
                    )
                }

                TextButton(
                    onClick = onContinueWithoutAccount,
                    enabled = !state.isLoading
                ) {
                    Text(
                        text = "Continue without account",
                        color = FrictionColors.TextSecondary,
                        fontSize = 13.sp
                    )
                }

                if (!state.isConfigured) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sync is not configured in this build. Local blocking still works.",
                        color = FrictionColors.TextMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }

                if (isCreatingAccount && name.isNotBlank() && name.trim().length < 2) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Name should be at least 2 characters.",
                        color = FrictionColors.Warning,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
