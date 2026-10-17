package com.theveloper.pixelplay.presentation.s3.auth

import android.widget.Toast
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.PixelPlayTheme
import dagger.hilt.android.AndroidEntryPoint
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@AndroidEntryPoint
class S3LoginActivity : ComponentActivity() {

    companion object {
        const val EXTRA_EDIT_MODE = "edit_mode"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val editMode = intent.getBooleanExtra(EXTRA_EDIT_MODE, false)
        setContent {
            PixelPlayTheme {
                S3LoginScreen(editMode = editMode, onClose = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun S3LoginScreen(
    viewModel: S3LoginViewModel = hiltViewModel(),
    editMode: Boolean = false,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val loginState by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    var endpoint by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var bucket by remember { mutableStateOf("") }
    var accessKey by remember { mutableStateOf("") }
    var secretKey by remember { mutableStateOf("") }
    var prefix by remember { mutableStateOf("") }
    var secretVisible by remember { mutableStateOf(false) }

    LaunchedEffect(editMode) {
        if (editMode) {
            viewModel.getExistingConnection()?.let { c ->
                endpoint = c.endpoint
                region = c.region
                bucket = c.bucket
                accessKey = c.accessKey
                secretKey = c.secretKey
                prefix = c.prefix.trimEnd('/')
            }
        }
    }

    LaunchedEffect(loginState) {
        when (val state = loginState) {
            is S3LoginState.Success -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.auth_s3_connected_toast),
                    Toast.LENGTH_SHORT
                ).show()
                onClose()
            }
            is S3LoginState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearError()
            }
            else -> Unit
        }
    }

    val isLoading = loginState is S3LoginState.Connecting
    val isFolderSetup = loginState is S3LoginState.FolderSetup
    val inputShape = AbsoluteSmoothCornerShape(18.dp, 60)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.auth_s3_title),
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    FilledIconButton(
                        modifier = Modifier.padding(start = 6.dp),
                        onClick = onClose,
                        enabled = !isLoading,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
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
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
        }
    ) { paddingValues ->
        if (isFolderSetup) {
            val setup = loginState as S3LoginState.FolderSetup
            S3FolderPickerContent(
                setup = setup,
                paddingValues = paddingValues,
                isLoading = isLoading,
                onOpenFolder = { viewModel.browse(it) },
                onSelectFolder = { viewModel.selectFolder(it) },
                onSelectCurrent = { viewModel.selectCurrentFolder() }
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.CloudQueue,
                        contentDescription = stringResource(R.string.auth_s3_title),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.auth_s3_title),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = stringResource(R.string.auth_s3_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                shape = AbsoluteSmoothCornerShape(20.dp, 60),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.auth_s3_info_msg),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = GoogleSansRounded
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = AbsoluteSmoothCornerShape(28.dp, 60),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    Text(
                        text = stringResource(R.string.auth_details_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    S3LoginField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                        label = stringResource(R.string.auth_s3_endpoint_label),
                        placeholder = stringResource(R.string.auth_s3_endpoint_placeholder),
                        supportingText = stringResource(R.string.auth_s3_endpoint_hint),
                        leadingIcon = Icons.Rounded.CloudQueue,
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        shape = inputShape
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    S3LoginField(
                        value = region,
                        onValueChange = { region = it },
                        label = stringResource(R.string.auth_s3_region_label),
                        placeholder = stringResource(R.string.auth_s3_region_placeholder),
                        supportingText = stringResource(R.string.auth_s3_region_hint),
                        leadingIcon = Icons.Rounded.CloudQueue,
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        shape = inputShape
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    S3LoginField(
                        value = bucket,
                        onValueChange = { bucket = it },
                        label = stringResource(R.string.auth_s3_bucket_label),
                        placeholder = stringResource(R.string.auth_s3_bucket_placeholder),
                        supportingText = stringResource(R.string.auth_s3_bucket_hint),
                        leadingIcon = Icons.Rounded.Folder,
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        shape = inputShape
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    S3LoginField(
                        value = accessKey,
                        onValueChange = { accessKey = it },
                        label = stringResource(R.string.auth_s3_access_key_label),
                        placeholder = stringResource(R.string.auth_s3_access_key_placeholder),
                        supportingText = stringResource(R.string.auth_s3_access_key_hint),
                        leadingIcon = Icons.Rounded.Person,
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        shape = inputShape
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    S3LoginField(
                        value = secretKey,
                        onValueChange = { secretKey = it },
                        label = stringResource(R.string.auth_s3_secret_key_label),
                        placeholder = stringResource(R.string.auth_s3_secret_key_placeholder),
                        supportingText = stringResource(R.string.auth_s3_secret_key_hint),
                        leadingIcon = Icons.Rounded.Lock,
                        enabled = !isLoading,
                        visualTransformation = if (secretVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        trailingContent = {
                            IconButton(
                                onClick = { secretVisible = !secretVisible },
                                enabled = !isLoading
                            ) {
                                Icon(
                                    imageVector = if (secretVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = stringResource(
                                        if (secretVisible) R.string.auth_hide_pwd_action else R.string.auth_show_pwd_action
                                    )
                                )
                            }
                        },
                        shape = inputShape
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    S3LoginField(
                        value = prefix,
                        onValueChange = { prefix = it },
                        label = stringResource(R.string.auth_s3_prefix_label),
                        placeholder = stringResource(R.string.auth_s3_prefix_placeholder),
                        supportingText = stringResource(R.string.auth_s3_prefix_hint),
                        leadingIcon = Icons.Rounded.Folder,
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        ),
                        shape = inputShape
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val canConnect = endpoint.isNotBlank() && bucket.isNotBlank() &&
                accessKey.isNotBlank() && secretKey.isNotBlank()

            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.connect(
                        endpoint = endpoint,
                        region = region,
                        bucket = bucket,
                        accessKey = accessKey,
                        secretKey = secretKey,
                        prefix = prefix
                    )
                },
                enabled = !isLoading && canConnect,
                shape = AbsoluteSmoothCornerShape(18.dp, 60),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.auth_connecting_msg), fontFamily = GoogleSansRounded)
                } else {
                    Text(stringResource(R.string.auth_connect_action), fontFamily = GoogleSansRounded, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.auth_s3_footer_msg),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = GoogleSansRounded
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun S3FolderPickerContent(
    setup: S3LoginState.FolderSetup,
    paddingValues: PaddingValues,
    isLoading: Boolean,
    onOpenFolder: (String) -> Unit,
    onSelectFolder: (String) -> Unit,
    onSelectCurrent: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.auth_s3_pick_folder_title),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (!isLoading) {
                TextButton(onClick = onSelectCurrent) {
                    Text(
                        text = stringResource(R.string.auth_s3_add_current_action),
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
            if (setup.folders.isEmpty() && !isLoading) {
                Text(
                    text = stringResource(R.string.auth_s3_pick_folder_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = GoogleSansRounded
                )
            }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            }
        } else {
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(items = setup.folders, key = { it.prefix }) { folder ->
                    Card(
                        onClick = { onOpenFolder(folder.prefix) },
                        shape = AbsoluteSmoothCornerShape(20.dp, 60),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(44.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(
                                onClick = { onSelectFolder(folder.prefix) },
                                enabled = true,
                                shape = AbsoluteSmoothCornerShape(14.dp, 60),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.auth_s3_add_action),
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun S3LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    supportingText: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingContent: @Composable (() -> Unit)? = null,
    shape: AbsoluteSmoothCornerShape
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        shape = shape,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = GoogleSansRounded
        ),
        label = {
            Text(
                text = label,
                fontFamily = GoogleSansRounded
            )
        },
        placeholder = {
            Text(
                text = placeholder,
                fontFamily = GoogleSansRounded
            )
        },
        supportingText = {
            Text(
                text = supportingText,
                fontFamily = GoogleSansRounded
            )
        },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingIcon = trailingContent,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent
        )
    )
}
