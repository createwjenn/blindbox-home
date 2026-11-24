package com.jenn.blindboxworld.content

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jenn.blindboxworld.R
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect

// App-owned colors from the existing room library, shared across all three collections.
private val paper = Color(0xFFFFF1D5) // design-style: fixed-figma-color room library screenshot, cream text
private val brass = Color(0xFFECC78C) // design-style: fixed-figma-color room library screenshot, coin balance
private val controlGlass = Color(0xA5404848) // design-style: fixed-figma-color room library screenshot, tab fill
private val darkInk = Color(0xFF373834) // design-style: fixed-figma-color room library screenshot, selected tab
private val panelColors = listOf(Color(0xEE494C47), Color(0xF02A2F31)) // design-style: fixed-figma-color room library screenshot, panel gradient
private val itemBackground = Color(0xFFFFFFFF).copy(alpha = .1f) // design-style: fixed-figma-color user-approved 10% white item surface.
private val lockedBadge = Color(0xC7393C3C) // design-style: fixed-figma-color room library screenshot, locked caption
private val actionBadge = Color(0xF6222828) // design-style: fixed-figma-color room library screenshot, action caption

@Composable
internal fun RoomShopIcon(onClick: () -> Unit) {
    Image(painterResource(R.drawable.room_shop), "Open shop",
        modifier=Modifier.size(92.dp).spatialHoverEffect().clickable(role=Role.Button,onClick=onClick),
        contentScale=ContentScale.Fit)
}

@Composable
private fun ShopControl(label: String, selected: Boolean = false, enabled: Boolean = true, click: () -> Unit) {
    Button(onClick = click, enabled = enabled, modifier = Modifier.height(43.dp),
        shape = RoundedCornerShape(100.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) paper else controlGlass,
            contentColor = if (selected) darkInk else paper,
        )) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private enum class LibrarySection(val title: String) {
    FURNITURE("Furniture"), ART("Art"), ACCESSORIES("Accessories")
}

private const val LIBRARY_COLUMNS = 4
private const val LIBRARY_ROWS = 2
private const val LIBRARY_PAGE_SIZE = LIBRARY_COLUMNS * LIBRARY_ROWS

@Composable
internal fun RoomLibrary(
    furnishing: RoomFurnishing,
    balance: Int,
    unlockedAccessories: Set<String>,
    onTryAccessory: ((Accessory) -> Unit)?,
    onClose: () -> Unit,
    onBuy: (String) -> Unit,
) {
    val swapping = furnishing.swapSource != null
    var section by remember(swapping) { mutableStateOf(if (swapping) LibrarySection.ART else LibrarySection.FURNITURE) }
    var swapError by remember(furnishing.swapSource) { mutableStateOf(false) }
    var itemNotice by remember { mutableStateOf<String?>(null) }
    var page by remember(section) { mutableIntStateOf(0) }
    val decor = remember(furnishing, section, swapping, furnishing.storedArtwork) {
        when (section) {
            LibrarySection.FURNITURE -> furnishing.catalogue.filter { it.category != "frame" }
            LibrarySection.ART -> if (swapping) {
                (furnishing.artworks + furnishing.storedArtwork + furnishing.catalogue.filter { it.category == "frame" && furnishing.own(it.id) })
                    .filterNot { it.id == furnishing.swapSource }.sortedBy { !furnishing.own(it.id) }
            } else furnishing.catalogue.filter { it.category == "frame" } + furnishing.artworks + furnishing.storedArtwork
            LibrarySection.ACCESSORIES -> emptyList()
        }
    }
    val accessories = remember(unlockedAccessories) { ACCESSORIES.sortedBy { it.id !in unlockedAccessories } }
    val isWardrobe = section == LibrarySection.ACCESSORIES
    val count = if (isWardrobe) accessories.size else decor.size
    val pages = (count + LIBRARY_PAGE_SIZE - 1) / LIBRARY_PAGE_SIZE
    val shape = RoundedCornerShape(30.dp)

    Column(Modifier.width(PICKER_WIDTH_DP.dp).clip(shape)
        .background(Brush.verticalGradient(panelColors)).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (swapping) "Swap artwork" else "Shop", color = paper, fontWeight = FontWeight.Bold, fontSize = 28.sp, modifier = Modifier.weight(1f))
            Row(Modifier.padding(end = 15.dp).semantics(mergeDescendants = true) {
                contentDescription = "$balance ${if (balance == 1) "coin" else "coins"}"
            }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("$balance", color = paper, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Image(painterResource(R.drawable.shop_coin), null, modifier = Modifier.size(30.dp))
            }
            ShopControl("Close", click = onClose)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (swapping) Text(if (swapError) "This artwork needs more wall space." else "Choose artwork for this spot.", color = paper, fontSize = 18.sp)
            else for (tab in LibrarySection.values()) {
                ShopControl(tab.title, selected = section == tab) { section = tab }
            }
            Spacer(Modifier.weight(1f))
            ShopControl("Previous", enabled = page > 0) { page-- }
            Text("${page + 1} / $pages", fontSize = 16.sp, color = paper)
            ShopControl("Next", enabled = page + 1 < pages) { page++ }
        }
        itemNotice?.let { Text(it, color = paper, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(LIBRARY_ROWS) { row ->
                Row(Modifier.fillMaxWidth().height(158.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(LIBRARY_COLUMNS) { column ->
                        val index = page * LIBRARY_PAGE_SIZE + row * LIBRARY_COLUMNS + column
                        val cell = Modifier.weight(1f).fillMaxHeight()
                        if (index >= count) {
                            Spacer(cell)
                        } else if (isWardrobe) {
                            val accessory = accessories[index]
                            val owned = accessory.id in unlockedAccessories
                            val action = if (owned) onTryAccessory else null
                            val label = when {
                                !owned -> "Locked · Gumball"
                                action != null -> "Owned · Try on"
                                else -> "Owned"
                            }
                            LibraryTile(accessory.name, accessory.thumbnail, label, !owned, cell,
                                onClick = action?.let { open -> { open(accessory) } })
                        } else {
                            val item = decor[index]
                            val owned = furnishing.own(item.id)
                            val painting = item.category == "art"
                            val locked = !owned && (painting || balance < item.price)
                            val label = when {
                                locked && painting -> "Locked · Win world"
                                locked -> "Locked · ${item.price} coins"
                                swapping -> "Swap here"
                                item.id in furnishing.saved -> "Move item"
                                owned && painting -> "Hang painting"
                                owned && item.category == "frame" -> "Hang frame"
                                owned -> "Place item"
                                else -> "Buy · ${item.price} coins"
                            }
                            LibraryTile(item.title, item.thumbnail, label, locked, cell,
                                onClick = {
                                    if (locked) itemNotice = if (painting) "Win this world to unlock its painting."
                                        else "${item.title}: needs ${item.price} coins. You have $balance (${item.price - balance} more needed)."
                                    else { itemNotice = null; if (swapping) swapError = !furnishing.swapArt(item.id) else onBuy(item.id) }
                                })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryTile(
    title: String,
    thumbnail: Int,
    label: String,
    locked: Boolean,
    modifier: Modifier,
    onClick: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.clip(shape).background(itemBackground)
        .then(if (onClick != null) Modifier.spatialHoverEffect() else Modifier)
        .clickable(enabled = onClick != null, role = Role.Button) { onClick?.invoke() }
        .semantics(mergeDescendants = true) { contentDescription = "$title, $label" }
        .padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (thumbnail == 0) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Text("Loft original", color = paper, fontSize = 18.sp)
        } else Image(painterResource(thumbnail), null, modifier = Modifier.fillMaxWidth().weight(1f)
            .alpha(if (locked) .70f else 1f), contentScale = ContentScale.Fit)
        Box(Modifier.height(34.dp), contentAlignment = Alignment.Center) {
            Text(title, fontSize = 15.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold,
                color = paper, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.fillMaxWidth().height(28.dp).background(if (locked) lockedBadge else actionBadge,
            RoundedCornerShape(100.dp)), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (locked) paper else brass, maxLines = 1)
        }
    }
}
