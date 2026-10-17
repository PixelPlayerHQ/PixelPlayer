package com.theveloper.pixelplay.presentation.telegram.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.telegram.channel.TelegramChannelSearchSheet
import com.theveloper.pixelplay.presentation.telegram.dashboard.TelegramDashboardScreen
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.PixelPlayTheme
import com.theveloper.pixelplay.utils.AppLocaleManager
import dagger.hilt.android.AndroidEntryPoint
import org.drinkless.tdlib.TdApi
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import java.util.Locale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow

@AndroidEntryPoint
class TelegramLoginActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PixelPlayTheme {
                TelegramLoginScreen(onFinish = { finish() })
            }
        }
    }
}

private enum class TelegramVisualStep {
    Phone,
    Code,
    Password,
    Status
}

@kotlin.OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun TelegramLoginScreen(
    viewModel: TelegramLoginViewModel = hiltViewModel(),
    onFinish: () -> Unit
) {
    val authState by viewModel.authorizationState.collectAsStateWithLifecycle(initialValue = null)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSearchSheet by remember { mutableStateOf(false) }

    if (showSearchSheet) {
        TelegramChannelSearchSheet(
            onDismissRequest = { showSearchSheet = false },
            onSongSelected = { song -> viewModel.downloadAndPlay(song) }
        )
    }

    val context = LocalContext.current
    val defaultWorkingMessage = stringResource(R.string.telegram_working)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.playbackRequest.collect { song ->
            val intent = Intent(context, MainActivity::class.java).apply {
                action = "com.theveloper.pixelplay.ACTION_PLAY_SONG"
                putExtra("song", song as android.os.Parcelable)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
            onFinish()
        }
    }

    if (authState is TdApi.AuthorizationStateReady && !uiState.isLoading) {
        TelegramDashboardScreen(
            onAddChannel = { showSearchSheet = true },
            onBack = onFinish
        )
        return
    }

    val visualStep = remember(authState, uiState.phoneEditMode) {
        resolveTelegramVisualStep(authState, uiState.phoneEditMode)
    }

    BackHandler(enabled = true) {
        if (!viewModel.handleBackNavigation(authState)) {
            onFinish()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.telegram_login_title),
                        fontFamily = GoogleSansRounded,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    FilledIconButton(
                        onClick = {
                            if (!viewModel.handleBackNavigation(authState)) {
                                onFinish()
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TelegramBrandingHeader()
            Spacer(modifier = Modifier.height(20.dp))

            val currentStep = when (visualStep) {
                TelegramVisualStep.Phone -> 0
                TelegramVisualStep.Code -> 1
                TelegramVisualStep.Password -> 2
                TelegramVisualStep.Status -> -1
            }
            if (currentStep >= 0) {
                StepIndicator(currentStep = currentStep, totalSteps = 3)
                Spacer(modifier = Modifier.height(20.dp))
            }

            if (uiState.phoneEditMode && (authState is TdApi.AuthorizationStateWaitCode || authState is TdApi.AuthorizationStateWaitPassword)) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = {
                        Text(
                            text = stringResource(R.string.telegram_edit_number_chip),
                            fontFamily = GoogleSansRounded
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (uiState.isLoading) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = uiState.loadingMessage.ifBlank { defaultWorkingMessage },
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = GoogleSansRounded,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            uiState.inlineError?.let { errorText ->
                InlineErrorCard(
                    message = errorText,
                    onDismiss = viewModel::clearInlineError
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            AnimatedContent(
                targetState = visualStep,
                transitionSpec = {
                    (slideInHorizontally { width -> width / 3 } + fadeIn())
                        .togetherWith(slideOutHorizontally { width -> -width / 3 } + fadeOut())
                },
                label = "telegramLoginStepTransition"
            ) { step ->
                when (step) {
                    TelegramVisualStep.Phone -> {
                        ExpressivePhoneNumberInput(
                            phoneNumber = uiState.phoneNumber,
                            isLoading = uiState.isLoading,
                            onPhoneNumberChanged = viewModel::onPhoneNumberChanged,
                            onSend = viewModel::sendPhoneNumber
                        )
                    }

                    TelegramVisualStep.Code -> {
                        ExpressiveCodeInput(
                            code = uiState.code,
                            isLoading = uiState.isLoading,
                            onCodeChanged = viewModel::onCodeChanged,
                            onCheck = viewModel::checkCode,
                            onEditPhone = viewModel::enablePhoneEditMode,
                            onResendCode = viewModel::sendPhoneNumber
                        )
                    }

                    TelegramVisualStep.Password -> {
                        ExpressivePasswordInput(
                            password = uiState.password,
                            isLoading = uiState.isLoading,
                            onPasswordChanged = viewModel::onPasswordChanged,
                            onCheck = viewModel::checkPassword,
                            onEditPhone = viewModel::enablePhoneEditMode
                        )
                    }

                    TelegramVisualStep.Status -> {
                        StatusMessage(message = authStateStatusMessage(authState))
                    }
                }
            }
        }
    }
}

private fun resolveTelegramVisualStep(
    authState: TdApi.AuthorizationState?,
    isPhoneEditMode: Boolean
): TelegramVisualStep {
    if (isPhoneEditMode) return TelegramVisualStep.Phone

    return when (authState) {
        is TdApi.AuthorizationStateWaitPhoneNumber -> TelegramVisualStep.Phone
        is TdApi.AuthorizationStateWaitCode -> TelegramVisualStep.Code
        is TdApi.AuthorizationStateWaitPassword -> TelegramVisualStep.Password
        else -> TelegramVisualStep.Status
    }
}

@Composable
private fun authStateStatusMessage(state: TdApi.AuthorizationState?): String =
    stringResource(
        when (state) {
            null -> R.string.telegram_status_initializing
            is TdApi.AuthorizationStateLoggingOut -> R.string.telegram_status_logging_out
            is TdApi.AuthorizationStateClosing -> R.string.telegram_status_closing
            is TdApi.AuthorizationStateClosed -> R.string.telegram_status_closed
            is TdApi.AuthorizationStateWaitTdlibParameters -> R.string.telegram_status_preparing
            else -> R.string.telegram_status_waiting
        }
    )

@Composable
private fun TelegramBrandingHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.telegram),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = stringResource(R.string.telegram_connect),
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.telegram_connect_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = GoogleSansRounded,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StepIndicator(
    currentStep: Int,
    totalSteps: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            val isActive = index <= currentStep
            val isCurrent = index == currentStep

            val scale by animateFloatAsState(
                targetValue = if (isCurrent) 1.2f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "telegramStepScale"
            )

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .size(if (isCurrent) 12.dp else 10.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

@Composable
private fun InlineErrorCard(
    message: String,
    onDismiss: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            FilledIconButton(
                onClick = onDismiss,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.22f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(imageVector = Icons.Rounded.Clear, contentDescription = stringResource(R.string.common_dismiss))
            }
        }
    }
}

@Composable
private fun StatusMessage(message: String) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = GoogleSansRounded,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressivePhoneNumberInput(
    phoneNumber: String,
    isLoading: Boolean,
    onPhoneNumberChanged: (String) -> Unit,
    onSend: () -> Unit
) {
    var countryCode by remember { mutableStateOf("") }
    var localNumber by remember { mutableStateOf("") }
    var codeFieldFocused by remember { mutableStateOf(false) }
    var numFieldFocused by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }

    val numFocusRequester = remember { FocusRequester() }
    val codeFocusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val isActive = codeFieldFocused || numFieldFocused

    LaunchedEffect(Unit) {
        val (parsedCode, parsedLocal) = splitPhoneNumberForInput(phoneNumber)
        if (parsedCode.isNotEmpty() || parsedLocal.isNotEmpty()) {
            countryCode = parsedCode
            localNumber = parsedLocal
            isExpanded = true
            return@LaunchedEffect
        }

        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
        val iso = tm?.networkCountryIso?.uppercase()?.takeIf { it.isNotEmpty() }
            ?: tm?.simCountryIso?.uppercase()?.takeIf { it.isNotEmpty() }
            ?: Locale.getDefault().country
        getDialCodeForCountry(iso).takeIf { it.isNotEmpty() }?.let { countryCode = it }
    }

    LaunchedEffect(countryCode, localNumber) {
        onPhoneNumberChanged(
            if (countryCode.isNotEmpty()) "+$countryCode$localNumber" else localNumber
        )
    }

    LaunchedEffect(isExpanded) {
        if (!isExpanded || isLoading) return@LaunchedEffect
        if (countryCode.isEmpty()) codeFocusRequester.requestFocus() else numFocusRequester.requestFocus()
    }

    val inputShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 16.dp,
        cornerRadiusTL = 16.dp,
        cornerRadiusBR = 16.dp,
        cornerRadiusBL = 16.dp,
        smoothnessAsPercentTR = 60,
        smoothnessAsPercentTL = 60,
        smoothnessAsPercentBR = 60,
        smoothnessAsPercentBL = 60
    )

    val borderColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        label = "phoneBorderColor"
    )

    val borderWidth = if (isActive) 2.dp else 1.dp
    val containerColor = if (isActive) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AuthStepHeader(
            icon = Icons.Rounded.Phone,
            title = stringResource(R.string.telegram_phone_number_title),
            subtitle = stringResource(R.string.telegram_phone_number_subtitle)
        )

        Spacer(Modifier.height(24.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(inputShape)
                    .background(containerColor)
                    .border(BorderStroke(borderWidth, borderColor), inputShape)
                    .clickable(enabled = !isExpanded && !isLoading) { isExpanded = true }
            ) {
                AnimatedContent(
                    targetState = isExpanded,
                    transitionSpec = {
                        (fadeIn() + slideInHorizontally { it / 4 })
                            .togetherWith(fadeOut())
                    },
                    label = "phoneExpandedTransition"
                ) { expanded ->
                    if (!expanded) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Phone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.telegram_phone_number_hint),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = GoogleSansRounded,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                )
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.auth_plus_label),
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            BasicTextField(
                                value = countryCode,
                                onValueChange = { raw ->
                                    if (isLoading) return@BasicTextField
                                    val digits = raw.filter(Char::isDigit).take(4)
                                    countryCode = digits
                                    val shouldJump = raw.endsWith(" ") ||
                                        digits.length >= 3 ||
                                        (digits.isNotEmpty() && isoForDialCode(digits).isNotEmpty())
                                    if (shouldJump) numFocusRequester.requestFocus()
                                },
                                modifier = Modifier
                                    .width(42.dp)
                                    .focusRequester(codeFocusRequester)
                                    .onFocusChanged { codeFieldFocused = it.isFocused },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { numFocusRequester.requestFocus() }
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (countryCode.isEmpty()) {
                                            Text(
                                                text = stringResource(R.string.telegram_phone_country_placeholder),
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontFamily = GoogleSansRounded,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                                )
                                            )
                                        }
                                        inner()
                                    }
                                }
                            )

                            Spacer(Modifier.width(8.dp))

                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(24.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )

                            Spacer(Modifier.width(12.dp))

                            BasicTextField(
                                value = localNumber,
                                onValueChange = { raw ->
                                    if (isLoading) return@BasicTextField
                                    localNumber = raw.filter(Char::isDigit).take(15)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(numFocusRequester)
                                    .onFocusChanged { numFieldFocused = it.isFocused },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = GoogleSansRounded,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    letterSpacing = 1.sp
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (!isLoading) onSend()
                                }),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (localNumber.isEmpty()) {
                                            Text(
                                                text = stringResource(R.string.telegram_phone_local_placeholder),
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontFamily = GoogleSansRounded,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                                    letterSpacing = 1.sp
                                                )
                                            )
                                        }
                                        inner()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (isExpanded) {
                Text(
                    text = stringResource(R.string.telegram_phone_number_hint),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = GoogleSansRounded,
                        color = if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp)
                        .offset(y = (-8).dp)
                        .background(containerColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        ExpressiveButton(
            text = stringResource(R.string.telegram_send_code),
            onClick = onSend,
            enabled = countryCode.isNotEmpty() && localNumber.isNotBlank() && !isLoading,
            loading = isLoading
        )
    }
}

@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveCodeInput(
    code: String,
    isLoading: Boolean,
    onCodeChanged: (String) -> Unit,
    onCheck: () -> Unit,
    onEditPhone: () -> Unit,
    onResendCode: () -> Unit
) {
    val inputShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 16.dp,
        cornerRadiusTL = 16.dp,
        cornerRadiusBR = 16.dp,
        cornerRadiusBL = 16.dp,
        smoothnessAsPercentTR = 60,
        smoothnessAsPercentTL = 60,
        smoothnessAsPercentBR = 60,
        smoothnessAsPercentBL = 60
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AuthStepHeader(
            icon = Icons.Rounded.Sms,
            title = stringResource(R.string.telegram_verification_code_title),
            subtitle = stringResource(R.string.telegram_verification_code_subtitle)
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = code,
            onValueChange = { onCodeChanged(it.filter(Char::isDigit).take(8)) },
            label = { Text(stringResource(R.string.telegram_code_field_label), fontFamily = GoogleSansRounded) },
            placeholder = { Text(stringResource(R.string.telegram_code_placeholder), fontFamily = GoogleSansRounded) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Sms,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                if (!isLoading) onCheck()
            }),
            singleLine = true,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
            shape = inputShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent
            )
        )

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onEditPhone, enabled = !isLoading) {
                Text(text = stringResource(R.string.telegram_edit_phone), fontFamily = GoogleSansRounded, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onResendCode, enabled = !isLoading) {
                Text(text = stringResource(R.string.telegram_resend_code), fontFamily = GoogleSansRounded, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Spacer(Modifier.height(18.dp))

        ExpressiveButton(
            text = stringResource(R.string.telegram_verify_code),
            onClick = onCheck,
            enabled = code.length >= 3 && !isLoading,
            loading = isLoading
        )
    }
}

@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressivePasswordInput(
    password: String,
    isLoading: Boolean,
    onPasswordChanged: (String) -> Unit,
    onCheck: () -> Unit,
    onEditPhone: () -> Unit
) {
    // Local state so keystrokes don't push through the VM uiState on every char,
    // which would recompose the whole login screen and cause input jank.
    var localPassword by remember { mutableStateOf(password) }
    var passwordVisible by remember { mutableStateOf(false) }

    // Re-sync from VM only when it actively resets the password (e.g. auth state change).
    LaunchedEffect(password) {
        if (password.isEmpty() && localPassword.isNotEmpty()) {
            localPassword = ""
        }
    }

    val inputShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTR = 16.dp,
            cornerRadiusTL = 16.dp,
            cornerRadiusBR = 16.dp,
            cornerRadiusBL = 16.dp,
            smoothnessAsPercentTR = 60,
            smoothnessAsPercentTL = 60,
            smoothnessAsPercentBR = 60,
            smoothnessAsPercentBL = 60
        )
    }

    val submitPassword = {
        if (!isLoading && localPassword.isNotBlank()) {
            onPasswordChanged(localPassword)
            onCheck()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AuthStepHeader(
            icon = Icons.Rounded.Lock,
            title = stringResource(R.string.telegram_two_step_password_title),
            subtitle = stringResource(R.string.telegram_two_step_password_subtitle)
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = localPassword,
            onValueChange = { localPassword = it },
            label = { Text(stringResource(R.string.telegram_password_label), fontFamily = GoogleSansRounded) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                val image = if (passwordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff
                val description = if (passwordVisible) "Hide password" else "Show password"

                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = description, tint = MaterialTheme.colorScheme.primary)
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { submitPassword() }),
            singleLine = true,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
            shape = inputShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent
            )
        )

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onEditPhone, enabled = !isLoading) {
                Text(text = stringResource(R.string.telegram_edit_phone), fontFamily = GoogleSansRounded, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Spacer(Modifier.height(18.dp))

        ExpressiveButton(
            text = stringResource(R.string.telegram_verify_password),
            onClick = submitPassword,
            enabled = localPassword.isNotBlank() && !isLoading,
            loading = isLoading
        )
    }
}

@Composable
private fun AuthStepHeader(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start
            )
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    loading: Boolean
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "telegramButtonScale"
    )

    MediumExtendedFloatingActionButton(
        text = {
            Text(
                text = if (loading) stringResource(R.string.telegram_please_wait) else text,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.SemiBold
            )
        },
        icon = {
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.width(28.dp))
            } else {
                Icon(imageVector = Icons.Rounded.Check, contentDescription = null)
            }
        },
        onClick = {
            if (enabled && !loading) onClick()
        },
        expanded = true,
        shape = CircleShape,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        containerColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        interactionSource = interactionSource
    )
}

private fun splitPhoneNumberForInput(phoneNumber: String): Pair<String, String> {
    val normalized = phoneNumber.trim()
    if (normalized.isBlank()) return "" to ""

    if (!normalized.startsWith("+")) {
        return "" to normalized.filter(Char::isDigit)
    }

    val digits = normalized.drop(1).filter(Char::isDigit)
    if (digits.isBlank()) return "" to ""

    val dialCode = guessDialCodePrefix(digits).orEmpty()
    val localNumber = if (dialCode.isEmpty()) digits else digits.removePrefix(dialCode)
    return dialCode to localNumber
}

private fun guessDialCodePrefix(numberWithoutPlus: String): String? {
    return allKnownDialCodes().firstOrNull { numberWithoutPlus.startsWith(it) }
}

private fun allKnownDialCodes(): List<String> {
    return ALL_COUNTRY_ISOS
        .map(::getDialCodeForCountry)
        .filter { it.isNotBlank() }
        .distinct()
        .sortedByDescending { it.length }
}

private val ALL_COUNTRY_ISOS = listOf(
    "AF", "AL", "DZ", "AD", "AO", "AR", "AM", "AU", "AT", "AZ", "BS", "BH", "BD", "BY", "BE",
    "BZ", "BJ", "BT", "BO", "BA", "BW", "BR", "BN", "BG", "BF", "BI", "KH", "CM", "CA", "CV",
    "CF", "TD", "CL", "CN", "CO", "KM", "CG", "CR", "HR", "CU", "CY", "CZ", "DK", "DJ", "DO",
    "EC", "EG", "SV", "GQ", "ER", "EE", "ET", "FJ", "FI", "FR", "GA", "GM", "GE", "DE", "GH",
    "GR", "GT", "GN", "GW", "GY", "HT", "HN", "HK", "HU", "IS", "IN", "ID", "IR", "IQ", "IE",
    "IL", "IT", "JM", "JP", "JO", "KZ", "KE", "KP", "KR", "KW", "KG", "LA", "LV", "LB", "LS",
    "LR", "LY", "LI", "LT", "LU", "MO", "MK", "MG", "MW", "MY", "MV", "ML", "MT", "MR", "MU",
    "MX", "MD", "MC", "MN", "ME", "MA", "MZ", "MM", "NA", "NP", "NL", "NZ", "NI", "NE", "NG",
    "NO", "OM", "PK", "PW", "PA", "PG", "PY", "PE", "PH", "PL", "PT", "QA", "RO", "RU", "RW",
    "SA", "SN", "RS", "SL", "SG", "SK", "SI", "SO", "ZA", "SS", "ES", "LK", "SD", "SR", "SZ",
    "SE", "CH", "SY", "TW", "TJ", "TZ", "TH", "TL", "TG", "TO", "TT", "TN", "TR", "TM", "UG",
    "UA", "AE", "GB", "US", "UY", "UZ", "VU", "VE", "VN", "YE", "ZM", "ZW"
)

fun isoForDialCode(dialCode: String): String {
    return ALL_COUNTRY_ISOS.firstOrNull { getDialCodeForCountry(it) == dialCode } ?: ""
}

fun getDialCodeForCountry(isoCode: String): String = when (isoCode.uppercase()) {
    "AF" -> "93"; "AL" -> "355"; "DZ" -> "213"; "AD" -> "376"; "AO" -> "244"
    "AR" -> "54"; "AM" -> "374"; "AU" -> "61"; "AT" -> "43"; "AZ" -> "994"
    "BS" -> "1"; "BH" -> "973"; "BD" -> "880"; "BY" -> "375"; "BE" -> "32"
    "BZ" -> "501"; "BJ" -> "229"; "BT" -> "975"; "BO" -> "591"; "BA" -> "387"
    "BW" -> "267"; "BR" -> "55"; "BN" -> "673"; "BG" -> "359"; "BF" -> "226"
    "BI" -> "257"; "KH" -> "855"; "CM" -> "237"; "CA" -> "1"; "CV" -> "238"
    "CF" -> "236"; "TD" -> "235"; "CL" -> "56"; "CN" -> "86"; "CO" -> "57"
    "KM" -> "269"; "CG" -> "242"; "CR" -> "506"; "HR" -> "385"; "CU" -> "53"
    "CY" -> "357"; "CZ" -> "420"; "DK" -> "45"; "DJ" -> "253"; "DO" -> "1"
    "EC" -> "593"; "EG" -> "20"; "SV" -> "503"; "GQ" -> "240"; "ER" -> "291"
    "EE" -> "372"; "ET" -> "251"; "FJ" -> "679"; "FI" -> "358"; "FR" -> "33"
    "GA" -> "241"; "GM" -> "220"; "GE" -> "995"; "DE" -> "49"; "GH" -> "233"
    "GR" -> "30"; "GT" -> "502"; "GN" -> "224"; "GW" -> "245"; "GY" -> "592"
    "HT" -> "509"; "HN" -> "504"; "HK" -> "852"; "HU" -> "36"; "IS" -> "354"
    "IN" -> "91"; "ID" -> "62"; "IR" -> "98"; "IQ" -> "964"; "IE" -> "353"
    "IL" -> "972"; "IT" -> "39"; "JM" -> "1"; "JP" -> "81"; "JO" -> "962"
    "KZ" -> "7"; "KE" -> "254"; "KP" -> "850"; "KR" -> "82"; "KW" -> "965"
    "KG" -> "996"; "LA" -> "856"; "LV" -> "371"; "LB" -> "961"; "LS" -> "266"
    "LR" -> "231"; "LY" -> "218"; "LI" -> "423"; "LT" -> "370"; "LU" -> "352"
    "MO" -> "853"; "MK" -> "389"; "MG" -> "261"; "MW" -> "265"; "MY" -> "60"
    "MV" -> "960"; "ML" -> "223"; "MT" -> "356"; "MR" -> "222"; "MU" -> "230"
    "MX" -> "52"; "MD" -> "373"; "MC" -> "377"; "MN" -> "976"; "ME" -> "382"
    "MA" -> "212"; "MZ" -> "258"; "MM" -> "95"; "NA" -> "264"; "NP" -> "977"
    "NL" -> "31"; "NZ" -> "64"; "NI" -> "505"; "NE" -> "227"; "NG" -> "234"
    "NO" -> "47"; "OM" -> "968"; "PK" -> "92"; "PW" -> "680"; "PA" -> "507"
    "PG" -> "675"; "PY" -> "595"; "PE" -> "51"; "PH" -> "63"; "PL" -> "48"
    "PT" -> "351"; "QA" -> "974"; "RO" -> "40"; "RU" -> "7"; "RW" -> "250"
    "SA" -> "966"; "SN" -> "221"; "RS" -> "381"; "SL" -> "232"; "SG" -> "65"
    "SK" -> "421"; "SI" -> "386"; "SO" -> "252"; "ZA" -> "27"; "SS" -> "211"
    "ES" -> "34"; "LK" -> "94"; "SD" -> "249"; "SR" -> "597"; "SZ" -> "268"
    "SE" -> "46"; "CH" -> "41"; "SY" -> "963"; "TW" -> "886"; "TJ" -> "992"
    "TZ" -> "255"; "TH" -> "66"; "TL" -> "670"; "TG" -> "228"; "TO" -> "676"
    "TT" -> "1"; "TN" -> "216"; "TR" -> "90"; "TM" -> "993"; "UG" -> "256"
    "UA" -> "380"; "AE" -> "971"; "GB" -> "44"; "US" -> "1"; "UY" -> "598"
    "UZ" -> "998"; "VU" -> "678"; "VE" -> "58"; "VN" -> "84"; "YE" -> "967"
    "ZM" -> "260"; "ZW" -> "263"
    else -> ""
}
