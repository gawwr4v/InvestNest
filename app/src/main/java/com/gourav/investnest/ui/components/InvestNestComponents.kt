package com.gourav.investnest.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gourav.investnest.model.FundSummary
import com.gourav.investnest.ui.theme.InvestNestTheme

@Composable
fun InvestNestSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search funds",
    enabled: Boolean = true,
    onDisabledClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .then(
                if (!enabled && onDisabledClick != null) {
                    Modifier.clickable(onClick = onDisabledClick)
                } else {
                    Modifier
                }
            ),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder) },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                )
            },
            shape = RoundedCornerShape(18.dp),
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    actionLabel: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = actionLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onActionClick),
        )
    }
}

@Composable
fun FundCard(
    fund: FundSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(188.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InitialBadge(label = fund.schemeName)
                Text(
                    text = fund.schemeName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (fund.isMetadataLoading) "Fetching NAV" else "NAV",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (fund.latestNav.isBlank()) "..." else "₹${fund.latestNav}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun FundListItem(
    fund: FundSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InitialBadge(
                label = fund.schemeName,
                modifier = Modifier.size(44.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = fund.schemeName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val supportingText = when {
                    fund.schemeCategory.isNotBlank() && fund.latestNav.isNotBlank() ->
                        "${fund.schemeCategory}, NAV ₹${fund.latestNav}"

                    fund.schemeCategory.isNotBlank() -> fund.schemeCategory
                    fund.isMetadataLoading -> "Fetching latest NAV"
                    else -> "Scheme details"
                }
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun EmptyPortfolioState(
    title: String,
    message: String,
    actionLabel: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        EmptyIllustration()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Card(
            modifier = Modifier.clickable(onClick = onActionClick),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(
                text = actionLabel,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun InitialBadge(
    label: String,
    modifier: Modifier = Modifier,
) {
    val initials = label.split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials.ifBlank { "MF" },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun EmptyIllustration(
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    Box(
        modifier = modifier.size(150.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val outline = Stroke(width = 5.dp.toPx())
            drawRoundRect(
                color = primaryColor,
                topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.34f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.6f, size.height * 0.34f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f, 24f),
                style = outline,
            )
            drawCircle(
                color = secondaryColor,
                radius = size.minDimension * 0.09f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.57f),
                style = outline,
            )
            drawLine(
                color = secondaryColor,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.38f, size.height * 0.7f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.62f, size.height * 0.7f),
                strokeWidth = 5.dp.toPx(),
            )
            drawRoundRect(
                color = primaryColor,
                topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.08f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.15f, size.height * 0.18f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f),
                style = outline,
            )
            drawRoundRect(
                color = primaryColor,
                topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.66f, size.height * 0.1f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.15f, size.height * 0.18f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f),
                style = outline,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FundCardPreview() {
    InvestNestTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FundCard(
                fund = FundSummary(
                    schemeCode = 1,
                    schemeName = "UTI Nifty 50 Index Fund",
                    amcName = "UTI Mutual Fund",
                    latestNav = "210.15",
                ),
                onClick = {},
            )
            FundListItem(
                fund = FundSummary(
                    schemeCode = 1,
                    schemeName = "SBI Bluechip Fund",
                    schemeCategory = "Equity Scheme",
                    latestNav = "145.20",
                ),
                onClick = {},
            )
            Spacer(modifier = Modifier.height(8.dp))
            EmptyPortfolioState(
                title = "No funds added yet",
                message = "Explore the market and save funds into this watchlist.",
                actionLabel = "Explore Funds",
                onActionClick = {},
            )
        }
    }
}
