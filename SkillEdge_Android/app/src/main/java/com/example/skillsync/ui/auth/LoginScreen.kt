package com.example.skillsync.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.R
import com.example.skillsync.theme.BrandCyan
import com.example.skillsync.theme.StatusBarIcons
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.Appear
import com.example.skillsync.ui.components.Motion
import com.example.skillsync.ui.components.SkillSyncLogo
import com.example.skillsync.ui.components.SkillSyncWordmark
import com.example.skillsync.ui.components.rememberShake

@Composable
fun LoginScreen(
    onLoginSuccess: (email: String) -> Unit,
    viewModel: LoginViewModel = viewModel(),
) {
    var email by remember { mutableStateOf("") }
    val loginState by viewModel.loginState.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current

    val isDark = MaterialTheme.colorScheme.background.luminanceIsDark()
    StatusBarIcons(lightIcons = isDark)

    LaunchedEffect(loginState) {
        if (loginState is LoginState.Success) {
            keyboard?.hide()
            onLoginSuccess((loginState as LoginState.Success).email)
        }
    }

    val errorMessage = (loginState as? LoginState.Error)?.message
    val shake by rememberShake(errorMessage)

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()

        // heightIn(min = maxHeight) inside the scroll is what makes Arrangement.Center
        // work: the column fills the viewport when content is short, and scrolls once
        // the keyboard is up or the content outgrows the screen.
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
        ) {
            val viewport = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = viewport)
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Appear(index = 0) {
                    SkillSyncLogo(size = 132.dp, floating = true)
                }
                Spacer(Modifier.height(18.dp))
                Appear(index = 1) { SkillSyncWordmark() }

                Spacer(Modifier.height(34.dp))

                Appear(index = 2) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 460.dp)
                            .graphicsLayer { translationX = shake },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.skill.cardBg),
                        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    ) {
                        Column(Modifier.padding(22.dp)) {
                            Text(
                                "Sign in",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Managers and Trainer Plus accounts only.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.skill.subText,
                            )

                            Spacer(Modifier.height(20.dp))

                            EmailField(
                                value = email,
                                onValueChange = { email = it },
                                isError = errorMessage != null,
                                onSubmit = { viewModel.login(email) },
                            )

                            AnimatedVisibility(
                                visible = errorMessage != null,
                                enter = fadeIn(tween(Motion.FAST)) + expandVertically(tween(Motion.NORMAL)),
                                exit = fadeOut(tween(Motion.FAST)) + shrinkVertically(tween(Motion.FAST)),
                            ) {
                                ErrorBanner(errorMessage.orEmpty())
                            }

                            Spacer(Modifier.height(18.dp))

                            SignInButton(
                                state = loginState,
                                enabled = email.isNotBlank(),
                                onClick = {
                                    keyboard?.hide()
                                    viewModel.login(email)
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(26.dp))

                Appear(index = 3) {
                    Text(
                        "Koenig Solutions · Delivery Intelligence",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.skill.subText,
                    )
                }
            }
        }
    }
}

/** Slow-drifting brand-tinted glows behind the form. */
@Composable
private fun AuroraBackground() {
    val t = rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = Motion.Standard), RepeatMode.Reverse),
        label = "a",
    )
    val b by t.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(12000, easing = Motion.Standard), RepeatMode.Reverse),
        label = "b",
    )
    val bg = MaterialTheme.colorScheme.background

    Box(
        Modifier
            .fillMaxSize()
            .background(bg)
            .background(
                Brush.radialGradient(
                    colors = listOf(BrandCyan.copy(alpha = 0.26f), Color.Transparent),
                    center = Offset(180f + a * 460f, 200f + a * 220f),
                    radius = 760f,
                )
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(860f - b * 520f, 1500f - b * 380f),
                    radius = 880f,
                )
            )
    )
}

@Composable
private fun EmailField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    onSubmit: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Work email") },
        placeholder = { Text("name@koenig-solutions.com") },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_mail),
                contentDescription = null,
                tint = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.skill.subText,
                modifier = Modifier.size(20.dp),
            )
        },
        singleLine = true,
        isError = isError,
        interactionSource = interaction,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.04f),
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ErrorBanner(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_alert),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun SignInButton(
    state: LoginState,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val loading = state is LoginState.Loading
    val success = state is LoginState.Success
    val scale by animateFloatAsState(
        targetValue = if (loading) 0.98f else 1f,
        animationSpec = Motion.springy(),
        label = "press",
    )

    Button(
        onClick = onClick,
        enabled = enabled && !loading && !success,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        AnimatedContent(
            targetState = when {
                success -> ButtonPhase.DONE
                loading -> ButtonPhase.LOADING
                else -> ButtonPhase.IDLE
            },
            transitionSpec = {
                (fadeIn(tween(Motion.FAST)) + scaleIn(tween(Motion.NORMAL), initialScale = 0.7f))
                    .togetherWith(fadeOut(tween(Motion.FAST)))
            },
            label = "button",
        ) { phase ->
            when (phase) {
                ButtonPhase.IDLE -> Text(
                    "Sign in",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
                ButtonPhase.LOADING -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Verifying access…", style = MaterialTheme.typography.labelLarge)
                }
                ButtonPhase.DONE -> Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

private enum class ButtonPhase { IDLE, LOADING, DONE }

private fun Color.luminanceIsDark(): Boolean =
    (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f
