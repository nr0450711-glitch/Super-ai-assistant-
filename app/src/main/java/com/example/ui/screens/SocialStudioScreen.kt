package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.JarvisViewModel
import com.example.ui.components.HudCard
import com.example.ui.components.HudSectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.HudEmerald

@Composable
fun SocialStudioScreen(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    val selectedUri by viewModel.socialPhotoUri.collectAsStateWithLifecycle()
    val caption by viewModel.socialCaption.collectAsStateWithLifecycle()
    val isPolishing by viewModel.isPolishingCaption.collectAsStateWithLifecycle()
    var showConfirmShareDialog by remember { mutableStateOf(false) }

    // Play-Policy compliant zero-permission Android Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.setSocialPhoto(uri)
        }
    }

    if (showConfirmShareDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmShareDialog = false },
            title = { Text("Confirm Facebook Share") },
            text = {
                Text(
                    buildString {
                        append(if (selectedUri != null) "Photo + Caption ready to share.\n\n" else "Text post ready to share.\n\n")
                        append(caption.ifBlank { "(No caption text)" })
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmShareDialog = false
                        viewModel.confirmSocialShare()
                    },
                    modifier = Modifier.testTag("confirm_social_share_dialog_button")
                ) {
                    Text("Share Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmShareDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            HudSectionHeader(
                title = "Facebook Social Studio",
                subtitle = "Zero-permission Photo Picker + confirmed sharing",
                icon = Icons.Default.Share,
                trailing = {
                    StatusPill(label = "ZERO STORAGE PERM", color = HudEmerald)
                }
            )
        }

        item {
            HudCard {
                if (selectedUri != null) {
                    AsyncImage(
                        model = selectedUri,
                        contentDescription = stringResource(R.string.cd_social_preview),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(MaterialTheme.shapes.medium)
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.img_jarvis_social_banner),
                        contentDescription = stringResource(R.string.cd_social_preview),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(MaterialTheme.shapes.medium)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_photo_button")
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (selectedUri == null) "Pick Photo" else "Change Photo")
                    }

                    if (selectedUri != null) {
                        OutlinedButton(
                            onClick = { viewModel.setSocialPhoto(null) }
                        ) {
                            Text("Clear")
                        }
                    }
                }
            }
        }

        item {
            HudCard {
                Text(
                    text = "Bilingual Caption Composer",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        AssistChip(
                            onClick = { viewModel.applySocialPreset(hindi = false) },
                            label = { Text("English VIP Preset") }
                        )
                    }
                    item {
                        AssistChip(
                            onClick = { viewModel.applySocialPreset(hindi = true) },
                            label = { Text("हिन्दी VIP Preset") }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = caption,
                    onValueChange = { viewModel.setSocialCaption(it) },
                    placeholder = { Text("Write or polish your Facebook post caption...") },
                    minLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("social_caption_input")
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = { viewModel.polishSocialCaptionWithBackend() },
                        enabled = !isPolishing,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("polish_caption_button")
                    ) {
                        if (isPolishing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("AI Polish")
                    }

                    Button(
                        onClick = { showConfirmShareDialog = true },
                        enabled = selectedUri != null || caption.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("share_facebook_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Confirm & Share")
                    }
                }
            }
        }
    }
}
