package com.searchlauncher.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val accents =
  mapOf(
    'a' to "àáâäæãå",
    'c' to "çćč",
    'e' to "èéêë",
    'i' to "ìíîï",
    'n' to "ñń",
    'o' to "òóôöœõø",
    's' to "ßśš",
    'u' to "ùúûü",
    'y' to "ýÿ",
    'z' to "žźż",
  )

// Fixed QWERTY hints: numbers on the top row, punctuation beneath it.
private val keySymbols =
  ("qwertyuiop".zip("1234567890") + "asdfghjkl".zip("@#$%&-+()") + "zxcvbnm".zip("*\"':;!?"))
    .toMap()

data class KeyboardShortcutHint(val label: String, val icon: ImageBitmap?)

/** Home-only keyboard. Its parent owns the height so the search bar and keys land together. */
@Composable
fun HomeSearchKeyboard(
  onText: (String) -> Unit,
  onBackspace: () -> Unit,
  onGo: () -> Unit,
  modifier: Modifier = Modifier,
  shortcutHints: Map<Char, KeyboardShortcutHint> = emptyMap(),
  goIcon: ImageBitmap? = null,
  goDescription: String = "Go: open search result",
  spaceShortcutLabel: String? = null,
  spaceShortcutIcon: ImageBitmap? = null,
) {
  var shift by remember { mutableStateOf(false) }
  var capsLock by remember { mutableStateOf(false) }
  var symbols by remember { mutableStateOf(false) }
  var extraSymbols by remember { mutableStateOf(false) }
  fun type(text: String) {
    onText(if (shift || capsLock) text.uppercase() else text)
    if (!capsLock) shift = false
  }
  val platformConfiguration = LocalViewConfiguration.current
  val keyboardConfiguration =
    remember(platformConfiguration) {
      object : ViewConfiguration by platformConfiguration {
        override val longPressTimeoutMillis: Long = 250L
      }
    }
  CompositionLocalProvider(LocalViewConfiguration provides keyboardConfiguration) {
    Surface(
      modifier,
      color = MaterialTheme.colorScheme.surface,
      contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
      BoxWithConstraints {
        val split = maxWidth >= 600.dp
        val splitGap = maxOf(48.dp, maxWidth - 640.dp)
        Column(
          Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
          verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          val rows =
            if (!symbols) listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
            else if (!extraSymbols) listOf("1234567890", "@#€%&-+()", "*\"':;!?/")
            else listOf("~`|•√π÷×§∆", "£¢$^°={}\\", "_[]<>:,.")
          rows.forEachIndexed { index, letters ->
            Row(
              Modifier.fillMaxWidth().weight(1f),
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              val splitAt = if (index == 2) 4 else 5
              val groups =
                if (split) listOf(letters.take(splitAt), letters.drop(splitAt)) else listOf(letters)
              groups.forEachIndexed { half, keys ->
                if (half == 1) Spacer(Modifier.width(splitGap))
                Row(
                  Modifier.weight(1f).fillMaxHeight(),
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  if (index == 1 && !split) Spacer(Modifier.weight(0.5f))
                  if (index == 2 && half == 0) {
                    KeyboardKey(
                      label = if (symbols) "=\\<" else if (capsLock) "⇪" else "⇧",
                      description =
                        if (symbols) "More symbols" else if (capsLock) "Caps lock on" else "Shift",
                      modifier = Modifier.weight(1.4f).fillMaxHeight(),
                      selected = shift || capsLock,
                      onClick = {
                        if (symbols) extraSymbols = !extraSymbols
                        else {
                          shift = !shift
                          capsLock = false
                        }
                      },
                      onLongClick =
                        if (symbols) null
                        else
                          ({
                            capsLock = !capsLock
                            shift = capsLock
                          }),
                    )
                  }
                  keys.forEach { letter ->
                    val symbolHint = if (symbols) null else keySymbols[letter]
                    val alternatives =
                      if (symbols) emptyList()
                      else
                        buildList {
                          symbolHint?.let { add(it) }
                          // Related bracket styles stay together without needing another keyboard
                          // page.
                          if (letter == 'k') addAll("[{".toList())
                          if (letter == 'l') addAll("]}".toList())
                          addAll(accents[letter].orEmpty().toList())
                        }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                      KeyboardKey(
                        label = if (shift || capsLock) letter.uppercase() else letter.toString(),
                        modifier = Modifier.fillMaxSize(),
                        symbolHint = symbolHint?.toString(),
                        description =
                          shortcutHints[letter]
                            ?.takeIf { !symbols }
                            ?.let { "$letter, ${it.label} shortcut" }
                            ?: if (shift || capsLock) letter.uppercase() else letter.toString(),
                        onClick = { type(letter.toString()) },
                        alternatives =
                          alternatives.map {
                            if (shift || capsLock) it.uppercase() else it.toString()
                          },
                        onAlternative = { type(it) },
                      )
                    }
                  }
                  if (index == 1 && !split) Spacer(Modifier.weight(0.5f))
                  if (index == 2 && (!split || half == 1))
                    KeyboardKey(
                      "⌫",
                      onBackspace,
                      Modifier.weight(1.4f).fillMaxHeight(),
                      description = "Backspace",
                      repeat = true,
                    )
                }
              }
            }
          }
          Row(
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            repeat(if (split) 2 else 1) { half ->
              if (half == 1) Spacer(Modifier.width(splitGap))
              Row(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                if (half == 0) {
                  KeyboardKey(
                    if (symbols) "ABC" else "?123",
                    { symbols = !symbols },
                    Modifier.weight(1.5f).fillMaxHeight(),
                    description = if (symbols) "Letters" else "Numbers and symbols",
                  )
                  KeyboardKey(",", { type(",") }, Modifier.weight(1f).fillMaxHeight())
                }
                KeyboardKey(
                  spaceShortcutLabel?.let { "Search $it" } ?: "space",
                  { type(" ") },
                  Modifier.weight(if (split) 3f else 4f).fillMaxHeight(),
                  description = spaceShortcutLabel?.let { "Space: activate $it search" } ?: "Space",
                  selected = spaceShortcutLabel != null,
                  icon = spaceShortcutIcon,
                  showLabelWithIcon = true,
                )
                if (!split || half == 1) {
                  KeyboardKey(".", { type(".") }, Modifier.weight(1f).fillMaxHeight())
                  KeyboardKey(
                    "Go",
                    onGo,
                    Modifier.weight(1.5f).fillMaxHeight(),
                    selected = true,
                    description = goDescription,
                    icon = goIcon,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeyboardKey(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  description: String = label,
  selected: Boolean = false,
  repeat: Boolean = false,
  onLongClick: (() -> Unit)? = null,
  symbolHint: String? = null,
  icon: ImageBitmap? = null,
  showLabelWithIcon: Boolean = false,
  alternatives: List<String> = emptyList(),
  onAlternative: (String) -> Unit = {},
) {
  val colors = MaterialTheme.colorScheme
  val dark = colors.surface.luminance() < colors.onSurface.luminance()
  // Match SearchChromeBar's 3.dp tonal surface; the keyboard's base surface is a shade darker.
  val restingColor = if (dark) colors.surfaceColorAtElevation(3.dp) else colors.surfaceVariant
  val restingContentColor = if (dark) colors.onSurface else colors.onSurfaceVariant
  var heldSelection by remember { mutableStateOf<Int?>(null) }
  var gesturePressed by remember { mutableStateOf(false) }
  var keyCenterX by remember { mutableFloatStateOf(0f) }
  val density = LocalDensity.current
  val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
  val reverse = keyCenterX > screenWidthPx / 2
  val cellWidth = 36.dp
  val cellWidthPx = with(density) { cellWidth.toPx() }
  val popupOffset = with(density) { 52.dp.roundToPx() }
  val currentAlternative by rememberUpdatedState(onAlternative)
  val interactions = remember { MutableInteractionSource() }
  val pressed by interactions.collectIsPressedAsState()
  val currentClick by rememberUpdatedState(onClick)
  var repeated by remember { mutableStateOf(false) }
  LaunchedEffect(pressed, repeat) {
    if (pressed && repeat) {
      repeated = false
      delay(400)
      while (true) {
        repeated = true
        currentClick()
        delay(65)
      }
    }
  }
  val keyInput =
    if (alternatives.isEmpty()) {
      Modifier.combinedClickable(
        interactionSource = interactions,
        indication = null,
        role = Role.Button,
        onClick = { if (!repeat || !repeated) onClick() },
        onLongClick = onLongClick,
      )
    } else {
      Modifier.semantics {
          role = Role.Button
          onClick {
            currentClick()
            true
          }
          customActions =
            alternatives.map { value ->
              CustomAccessibilityAction("Insert $value") {
                currentAlternative(value)
                true
              }
            }
        }
        .pointerInput(alternatives, reverse) {
          awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            gesturePressed = true
            try {
              val longPress = awaitLongPressOrCancellation(down.id)
              if (longPress == null) {
                // A normal release types the letter; consumed/cancelled gestures do not.
                val release = currentEvent.changes.firstOrNull { it.id == down.id }
                if (release != null && !release.pressed && !release.isConsumed) {
                  release.consume()
                  currentClick()
                }
              } else {
                heldSelection = 0
                val origin = longPress.position.x
                val direction = if (reverse) -1 else 1
                while (true) {
                  val event = awaitPointerEvent()
                  val change = event.changes.firstOrNull { it.id == down.id } ?: break
                  if (change.isConsumed) break
                  val distance = (change.position.x - origin) * direction
                  heldSelection =
                    (distance / cellWidthPx).roundToInt().coerceIn(0, alternatives.lastIndex)
                  change.consume()
                  if (!change.pressed) {
                    currentAlternative(alternatives[heldSelection ?: 0])
                    break
                  }
                }
              }
            } finally {
              heldSelection = null
              gesturePressed = false
            }
          }
        }
    }
  Surface(
    modifier =
      modifier
        .focusProperties { canFocus = false }
        .onGloballyPositioned { keyCenterX = it.positionInWindow().x + it.size.width / 2f }
        .semantics { contentDescription = description }
        .then(keyInput),
    shape = RoundedCornerShape(6.dp),
    color =
      when {
        (pressed || gesturePressed) -> MaterialTheme.colorScheme.primaryContainer
        selected -> if (dark) colors.surfaceColorAtElevation(6.dp) else colors.secondaryContainer
        else -> restingColor
      },
    contentColor =
      when {
        (pressed || gesturePressed) -> MaterialTheme.colorScheme.onPrimaryContainer
        selected -> if (dark) colors.onSurface else colors.onSecondaryContainer
        else -> restingContentColor
      },
  ) {
    Box(contentAlignment = Alignment.Center) {
      heldSelection?.let { selection ->
        Popup(
          alignment = if (reverse) Alignment.TopEnd else Alignment.TopStart,
          offset = IntOffset(0, -popupOffset),
          properties = PopupProperties(focusable = false),
        ) {
          Surface(shape = RoundedCornerShape(8.dp), shadowElevation = 6.dp, color = restingColor) {
            Row(Modifier.padding(4.dp)) {
              val indices =
                if (reverse) alternatives.indices.reversed() else alternatives.indices.toList()
              indices.forEach { index ->
                Surface(
                  modifier =
                    Modifier.size(width = cellWidth, height = 40.dp).semantics {
                      contentDescription = "Alternative ${alternatives[index]}"
                      this.selected = index == selection
                    },
                  shape = RoundedCornerShape(4.dp),
                  color =
                    if (index == selection) MaterialTheme.colorScheme.primary else restingColor,
                  contentColor =
                    if (index == selection) MaterialTheme.colorScheme.onPrimary
                    else restingContentColor,
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Text(alternatives[index], fontSize = 20.sp)
                  }
                }
              }
            }
          }
        }
      }

      if (icon != null && showLabelWithIcon) {
        Row(
          Modifier.padding(horizontal = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(24.dp))
          Text(label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      } else if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(28.dp))
      } else {
        Text(label, fontSize = if (label.length > 2) 14.sp else 20.sp, maxLines = 1)
      }
      if (symbolHint != null) {
        Text(
          symbolHint,
          modifier = Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 5.dp),
          fontSize = 9.sp,
          lineHeight = 10.sp,
          color = LocalContentColor.current.copy(alpha = 0.65f),
        )
      }
    }
  }
}
