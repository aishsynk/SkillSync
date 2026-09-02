package com.example.skillsync.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.R
import com.example.skillsync.theme.AuroraBackground
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.StatusBarIcons
import com.example.skillsync.theme.glassSurface
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.Appear
import com.example.skillsync.ui.components.LocalNotify
import com.example.skillsync.ui.components.Motion
import com.example.skillsync.ui.components.SkillSyncLogo
import com.example.skillsync.ui.components.SkillSyncWordmark
import com.example.skillsync.ui.components.rememberShake

/**
 * Sign-in.
 *
 * Rebuilt on the shared scale and the shared ground. The previous version drew
 * its own animated aurora with hard pixel offsets (`180f + a * 460f`), which
 * meant the glow landed in a different place on every screen size and did not
 * match the ground every other screen sits on. It also mixed 10/12/22/26/34dp
 * spacing and put its error state on Material's `errorContainer` rather than on
 * the design system's status colours.
 *
 * Layout is now a single centred column on the 8pt scale: brand block, form
 * card capped at 420dp, footer. Failures surface twice — inline against the
 * field that caused them, and as a toast, so the reason survives the user
 * looking away from the form.
 */
@Composable
fun LoginScreen(
    onLoginSuccess: (email: String) -> Unit,
    viewModel: LoginViewModel = viewModel(),
) {
    var workId by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    val loginState by viewModel.loginState.collectAsState()
    val step by viewModel.step.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val notify = LocalNotify.current

    StatusBarIcons()

    val errorMessage = (loginState as? LoginState.Error)?.message
    val shake by rememberShake(errorMessage)

    LaunchedEffect(step) { secret = "" }

    LaunchedEffect(loginState) {
        when (val s = loginState) {
            is LoginState.Success -> {
                keyboard?.hide()
                notify.success("Signed in", "Loading your delivery intelligence…")
                onLoginSuccess(s.email)
                viewModel.reset()
            }
            is LoginState.Error -> notify.error("Sign-in failed", s.message)
            else -> Unit
        }
    }

    val onSubmit = {
        keyboard?.hide()
        viewModel.submit(workId, secret)
    }

    Box(Modifier.fillMaxSize()) {
        // The same ground every other screen stands on.
        AuroraBackground()

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
        ) {
            // heightIn(min = viewport) is what lets Arrangement.Center work
            // inside a scroller: the column fills the screen when the content is
            // short, and scrolls once the keyboard is up.
            val viewport = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = viewport)
                    .padding(horizontal = Space.xl, vertical = Space.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Appear(index = 0) { SkillSyncLogo(size = 116.dp, floating = true) }
                Spacer(Modifier.height(Space.lg))
                Appear(index = 1) { SkillSyncWordmark() }
                Spacer(Modifier.height(Space.sm))
                Appear(index = 2) {
                    Text(
                        "DELIVERY INTELLIGENCE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.skill.ice,
                    )
                }

                Spacer(Modifier.height(Space.xxl))

                Appear(index = 3) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = 420.dp)
                            .graphicsLayer { translationX = shake }
                            .glassSurface(RoundedCornerShape(Radii.hero))
                            .padding(Space.xl),
                    ) {
                        Text(
                            when (step) {
                                LoginStep.SET_PASSWORD -> "Set a password"
                                LoginStep.PASSWORD -> "Enter your password"
                                else -> "Sign in"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.skill.bodyText,
                        )
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            when (step) {
                                LoginStep.SET_PASSWORD ->
                                    "First sign-in: replace your employee code with a password (6+ characters)."
                                LoginStep.PASSWORD ->
                                    "First-time sign-in uses your employee code."
                                else -> "Enter your Koenig work ID."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.skill.subText,
                        )

                        Spacer(Modifier.height(Space.lg))

                        if (step == LoginStep.ID) {
                            EmailField(
                                value = workId,
                                onValueChange = { workId = it },
                                isError = errorMessage != null,
                                onSubmit = onSubmit,
                            )
                        } else {
                            SecretField(
                                value = secret,
                                onValueChange = { secret = it },
                                label = if (step == LoginStep.SET_PASSWORD) "New password" else "Password",
                                isError = errorMessage != null,
                                onSubmit = onSubmit,
                            )
                        }

                        AnimatedVisibility(
                            visible = errorMessage != null,
                            enter = fadeIn(tween(Motion.FAST)) + expandVertically(tween(Motion.NORMAL)),
                            exit = fadeOut(tween(Motion.FAST)) + shrinkVertically(tween(Motion.FAST)),
                        ) {
                            InlineError(errorMessage.orEmpty())
                        }

                        Spacer(Modifier.height(Space.lg))

                        SignInButton(
                            state = loginState,
                            enabled = if (step == LoginStep.ID) workId.isNotBlank() else secret.isNotBlank(),
                            onClick = onSubmit,
                        )
                    }
                }

                Spacer(Modifier.height(Space.xl))

                Appear(index = 4) {
                    Text(
                        "Koenig Solutions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.skill.labelText,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmailField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    onSubmit: () -> Unit,
) {
    val sk = MaterialTheme.skill
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filterNot { c -> c.isWhitespace() }) },
        label = { Text("Work ID") },
        placeholder = { Text("aishwar.c") },
        suffix = { Text("@koenig-solutions.com", color = sk.labelText) },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_mail),
                contentDescription = null,
                tint = when {
                    isError -> sk.crit
                    focused -> sk.brand
                    else -> sk.labelText
                },
                modifier = Modifier.size(20.dp),
            )
        },
        singleLine = true,
        isError = isError,
        interactionSource = interaction,
        shape = RoundedCornerShape(Radii.chip),
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = sk.brand,
            unfocusedBorderColor = sk.glassBorder,
            errorBorderColor = sk.crit,
            focusedLabelColor = sk.brand,
            unfocusedLabelColor = sk.labelText,
            focusedTextColor = sk.bodyText,
            unfocusedTextColor = sk.bodyText,
            cursorColor = sk.brand,
            focusedContainerColor = sk.brand.copy(alpha = 0.05f),
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    onSubmit: () -> Unit,
) {
    val sk = MaterialTheme.skill
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_alert),
                contentDescription = null,
                tint = when {
                    isError -> sk.crit
                    focused -> sk.brand
                    else -> sk.labelText
                },
                modifier = Modifier.size(20.dp),
            )
        },
        singleLine = true,
        isError = isError,
        interactionSource = interaction,
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        shape = RoundedCornerShape(Radii.chip),
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = sk.brand,
            unfocusedBorderColor = sk.glassBorder,
            errorBorderColor = sk.crit,
            focusedLabelColor = sk.brand,
            unfocusedLabelColor = sk.labelText,
            focusedTextColor = sk.bodyText,
            unfocusedTextColor = sk.bodyText,
            cursorColor = sk.brand,
            focusedContainerColor = sk.brand.copy(alpha = 0.05f),
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The failure, pinned to the field that caused it. The toast says the same
 * thing louder; this is what remains on screen while the user retypes.
 */
@Composable
private fun InlineError(message: String) {
    val sk = MaterialTheme.skill
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.md)
            .background(sk.crit.copy(alpha = 0.12f), RoundedCornerShape(Radii.chip))
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_alert),
            contentDescription = null,
            tint = sk.crit,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(Space.sm))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = sk.bodyText,
        )
    }
}

@Composable
private fun SignInButton(
    state: LoginState,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val sk = MaterialTheme.skill
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
        shape = RoundedCornerShape(Radii.chip),
        colors = ButtonDefaults.buttonColors(
            containerColor = sk.brand,
            contentColor = sk.frost,
            disabledContainerColor = sk.brand.copy(alpha = 0.30f),
            disabledContentColor = sk.frost.copy(alpha = 0.55f),
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
                ButtonPhase.IDLE -> Text("Sign in", style = MaterialTheme.typography.labelLarge)
                ButtonPhase.LOADING -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = sk.frost,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(Space.md))
                    Text("Verifying access…", style = MaterialTheme.typography.labelLarge)
                }
                ButtonPhase.DONE -> Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = sk.frost,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

private enum class ButtonPhase { IDLE, LOADING, DONE }
