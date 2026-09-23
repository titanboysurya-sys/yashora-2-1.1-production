package com.ritvyom.yashoraReelgenerator.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ritvyom.yashoraReelgenerator.presentation.utils.localize

data class LanguageItem(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val category: String = "GLOBAL"
)

object LanguageData {
    val ALL_LANGUAGES = listOf(
        LanguageItem("ENGLISH", "ENGLISH", "English", "POPULAR"),
        LanguageItem("HINDI", "HINDI", "हिंदी", "POPULAR"),
        LanguageItem("HINGLISH", "HINGLISH", "Hinglish (Hindi + English)", "POPULAR"),
        LanguageItem("SPANISH", "SPANISH", "Español", "POPULAR"),
        LanguageItem("FRENCH", "FRENCH", "Français", "GLOBAL"),
        LanguageItem("GERMAN", "GERMAN", "Deutsch", "GLOBAL"),
        LanguageItem("ARABIC", "ARABIC", "العربية", "POPULAR"),
        LanguageItem("JAPANESE", "JAPANESE", "日本語", "GLOBAL"),
        LanguageItem("PORTUGUESE", "PORTUGUESE", "Português", "GLOBAL"),
        LanguageItem("BENGALI", "BENGALI", "বাংলা", "INDIAN"),
        LanguageItem("MARATHI", "MARATHI", "मराठी", "INDIAN"),
        LanguageItem("TELUGU", "TELUGU", "తెలుగు", "INDIAN"),
        LanguageItem("TAMIL", "TAMIL", "தமிழ்", "INDIAN"),
        LanguageItem("GUJARATI", "GUJARATI", "ગુજરાતી", "INDIAN"),
        LanguageItem("URDU", "URDU", "اردو", "INDIAN"),
        LanguageItem("KANNADA", "KANNADA", "ಕನ್ನಡ", "INDIAN"),
        LanguageItem("ODIA", "ODIA", "ଓଡ଼ିଆ", "INDIAN"),
        LanguageItem("MALAYALAM", "MALAYALAM", "മലയാളം", "INDIAN"),
        LanguageItem("PUNJABI", "PUNJABI", "ਪੰਜਾਬੀ", "INDIAN"),
        LanguageItem("ASSAMESE", "ASSAMESE", "অসমীয়া", "INDIAN"),
        LanguageItem("ITALIAN", "ITALIAN", "Italiano", "GLOBAL"),
        LanguageItem("RUSSIAN", "RUSSIAN", "Русский", "GLOBAL"),
        LanguageItem("CHINESE (SIMPLIFIED)", "CHINESE (SIMPLIFIED)", "简体中文", "GLOBAL"),
        LanguageItem("CHINESE (TRADITIONAL)", "CHINESE (TRADITIONAL)", "繁體中文", "GLOBAL"),
        LanguageItem("KOREAN", "KOREAN", "한국어", "GLOBAL"),
        LanguageItem("TURKISH", "TURKISH", "Türkçe", "GLOBAL"),
        LanguageItem("DUTCH", "DUTCH", "Nederlands", "GLOBAL"),
        LanguageItem("POLISH", "POLISH", "Polski", "GLOBAL"),
        LanguageItem("INDONESIAN", "INDONESIAN", "Bahasa Indonesia", "GLOBAL"),
        LanguageItem("VIETNAMESE", "VIETNAMESE", "Tiếng Việt", "GLOBAL"),
        LanguageItem("THAI", "THAI", "ไทย", "GLOBAL"),
        LanguageItem("FILIPINO", "FILIPINO", "Tagalog", "GLOBAL"),
        LanguageItem("MALAY", "MALAY", "Bahasa Melayu", "GLOBAL"),
        LanguageItem("PERSIAN", "PERSIAN", "فارسی", "GLOBAL"),
        LanguageItem("HEBREW", "HEBREW", "עברית", "GLOBAL"),
        LanguageItem("SWEDISH", "SWEDISH", "Svenska", "GLOBAL"),
        LanguageItem("NORWEGIAN", "NORWEGIAN", "Norsk", "GLOBAL"),
        LanguageItem("FINNISH", "FINNISH", "Suomi", "GLOBAL"),
        LanguageItem("GREEK", "GREEK", "Ελληνικά", "GLOBAL"),
        LanguageItem("ROMANIAN", "ROMANIAN", "Română", "GLOBAL"),
        LanguageItem("HUNGARIAN", "HUNGARIAN", "Magyar", "GLOBAL"),
        LanguageItem("CZECH", "CZECH", "Čeština", "GLOBAL"),
        LanguageItem("UKRAINIAN", "UKRAINIAN", "Українська", "GLOBAL"),
        LanguageItem("NEPALI", "NEPALI", "नेपाली", "GLOBAL"),
        LanguageItem("SINHALA", "SINHALA", "සිංහල", "GLOBAL"),
        LanguageItem("SWAHILI", "SWAHILI", "Kiswahili", "GLOBAL"),
        LanguageItem("HAUSA", "HAUSA", "Hausa", "GLOBAL"),
        LanguageItem("AMHARIC", "AMHARIC", "አማርኛ", "GLOBAL")
    )

    fun getLanguageCodes(): List<String> {
        return ALL_LANGUAGES.map { it.code }
    }
}

@Composable
fun SearchableLanguageSelector(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    appLanguage: String = "ENGLISH",
    availableLanguages: List<String> = LanguageData.getLanguageCodes()
) {
    var showDialog by remember { mutableStateOf(false) }

    Surface(
        onClick = { showDialog = true },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = "Language",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                
                val currentLangItem = LanguageData.ALL_LANGUAGES.find { 
                    it.code.equals(selectedLanguage, ignoreCase = true) 
                }
                
                Column {
                    Text(
                        text = selectedLanguage.uppercase(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (currentLangItem != null && currentLangItem.nativeName.isNotBlank() && !currentLangItem.nativeName.equals(selectedLanguage, ignoreCase = true)) {
                        Text(
                            text = currentLangItem.nativeName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        text = "Search 🔍",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showDialog) {
        SearchableLanguageDialog(
            selectedLanguage = selectedLanguage,
            onLanguageSelected = { lang ->
                onLanguageSelected(lang)
                showDialog = false
            },
            onDismissRequest = { showDialog = false },
            appLanguage = appLanguage,
            availableLanguages = availableLanguages
        )
    }
}

@Composable
fun SearchableLanguageDialog(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismissRequest: () -> Unit,
    appLanguage: String = "ENGLISH",
    availableLanguages: List<String> = LanguageData.getLanguageCodes()
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }

    val configuration = LocalConfiguration.current
    val dialogMaxHeight = (configuration.screenHeightDp * 0.85).dp

    val allItems = remember(availableLanguages) {
        val mappedList = mutableListOf<LanguageItem>()
        availableLanguages.forEach { code ->
            val existing = LanguageData.ALL_LANGUAGES.find { it.code.equals(code, ignoreCase = true) }
            if (existing != null) {
                mappedList.add(existing)
            } else {
                mappedList.add(LanguageItem(code, code, code, "GLOBAL"))
            }
        }
        mappedList
    }

    val filteredLanguages = remember(searchQuery, selectedCategory, allItems) {
        allItems.filter { item ->
            val matchesCategory = when (selectedCategory) {
                "POPULAR" -> item.category == "POPULAR"
                "INDIAN" -> item.category == "INDIAN"
                "GLOBAL" -> item.category == "GLOBAL"
                else -> true
            }

            val query = searchQuery.trim().lowercase()
            val matchesSearch = query.isEmpty() ||
                    item.displayName.lowercase().contains(query) ||
                    item.code.lowercase().contains(query) ||
                    item.nativeName.lowercase().contains(query)

            matchesCategory && matchesSearch
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = dialogMaxHeight),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Select Language".localize(appLanguage),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${filteredLanguages.size} " + "languages available".localize(appLanguage),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close".localize(appLanguage),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search Bar Tab
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "Search language (e.g. Hindi, Spanish)...".localize(appLanguage),
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search".localize(appLanguage),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear".localize(appLanguage),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Category Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val categories = listOf(
                        "ALL" to "All Languages",
                        "POPULAR" to "Popular ⭐",
                        "INDIAN" to "Indian Languages 🇮🇳",
                        "GLOBAL" to "Global 🌐"
                    )

                    items(categories) { (catKey, catLabel) ->
                        val isSelected = selectedCategory == catKey
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = catKey },
                            label = {
                                Text(
                                    text = catLabel.localize(appLanguage),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Language Item List
                if (filteredLanguages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "No languages match".localize(appLanguage) + " '$searchQuery'",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredLanguages, key = { it.code }) { item ->
                            val isSelected = item.code.equals(selectedLanguage, ignoreCase = true)

                            Card(
                                onClick = { onLanguageSelected(item.code) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                border = if (isSelected)
                                    androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                else null
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surfaceVariant
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = item.displayName.take(1).uppercase(),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Column {
                                            Text(
                                                text = item.displayName,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (item.nativeName.isNotBlank() && !item.nativeName.equals(item.displayName, ignoreCase = true)) {
                                                Text(
                                                    text = item.nativeName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
