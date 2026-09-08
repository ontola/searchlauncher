package com.searchlauncher.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

enum class KeyboardHomeSwipe {
  Up,
  DownLeft,
  DownRight,
  Left,
  Right
}

data class KeyboardGoTarget(val icon: ImageBitmap?, val description: String)

/**
 * Whether the embedded home keyboard (and the search-bar padding it occupies) should stay drawn.
 *
 * Settings and the app list are overlays on a still-composed
 * [com.searchlauncher.app.ui.SearchScreen], so this must not depend on that screen being "active".
 * Tying visibility to activity made the keys and bar drop for the frames of the overlay fade.
 */
internal fun builtInHomeKeyboardVisible(
  useBuiltInKeyboard: Boolean,
  openingOverviewTab: Boolean,
  inPip: Boolean,
): Boolean = useBuiltInKeyboard && !openingOverviewTab && !inPip

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
  goTarget: (@Composable () -> KeyboardGoTarget)? = null,
  spaceShortcutLabel: String? = null,
  spaceShortcutIcon: ImageBitmap? = null,
  spaceShortcutContent: (@Composable (Int) -> Unit)? = null,
  onSpaceShortcutPressed: (Int) -> Unit = {},
  gesturesEnabled: Boolean = true,
  cancelMomentumKey: Int = 0,
  onMoveCursor: (Int) -> Unit = {},
  onScrollResults: (Float) -> Unit = {},
  onResultSwipeStart: () -> Unit = {},
  onKeyboardTouch: () -> Unit = {},
  onResultFling: (suspend (Float) -> Unit)? = null,
  onHomeSwipe: ((KeyboardHomeSwipe) -> Unit)? = null,
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
  val moveCursor by rememberUpdatedState(onMoveCursor)
  val scrollResults by rememberUpdatedState(onScrollResults)
  val startResultSwipe by rememberUpdatedState(onResultSwipeStart)
  val keyboardTouch by rememberUpdatedState(onKeyboardTouch)
  val resultFling by rememberUpdatedState(onResultFling)
  val gestureScope = rememberCoroutineScope()
  val flingDecay = rememberSplineBasedDecay<Float>()
  var flingJob by remember { mutableStateOf<Job?>(null) }
  LaunchedEffect(cancelMomentumKey) { flingJob?.cancel() }
  DisposableEffect(gesturesEnabled) { onDispose { flingJob?.cancel() } }
  val density = LocalDensity.current
  val cursorStep = with(density) { 16.dp.toPx() }
  val homeSwipe by rememberUpdatedState(onHomeSwipe)
  val homeSwipeInput =
    Modifier.pointerInput(density) {
      awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var claimed = false
        var fired = false
        var vertical = false
        while (true) {
          val event = awaitPointerEvent(PointerEventPass.Initial)
          val change = event.changes.firstOrNull { it.id == down.id } ?: break
          if (change.isConsumed || event.changes.count { it.pressed } > 1) break
          val distance = change.position - down.position
          if (!claimed) {
            // Preserve immediate taps and the existing accent/symbol long press.
            if (!change.pressed || change.uptimeMillis - down.uptimeMillis >= 250L) break
            if (distance.getDistance() <= viewConfiguration.touchSlop) continue
            claimed = true
            vertical = abs(distance.y) > abs(distance.x)
          }
          change.consume()
          val amount = if (vertical) distance.y else distance.x
          if (!fired && abs(amount) >= with(density) { 32.dp.toPx() }) {
            fired = true
            homeSwipe?.invoke(
              if (!vertical) {
                if (amount < 0) KeyboardHomeSwipe.Left else KeyboardHomeSwipe.Right
              } else if (amount < 0) KeyboardHomeSwipe.Up
              else if (down.position.x < size.width / 2) KeyboardHomeSwipe.DownLeft
              else KeyboardHomeSwipe.DownRight
            )
          }
          if (!change.pressed) break
        }
      }
    }
  val swipeInput =
    Modifier.pointerInput(cursorStep) {
      awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        flingJob?.cancel()
        keyboardTouch()
        val velocityTracker = VelocityTracker()
        velocityTracker.addPosition(down.uptimeMillis, down.position)
        var vertical: Boolean? = null
        var remainder = 0f
        while (true) {
          val event = awaitPointerEvent(PointerEventPass.Initial)
          val change = event.changes.firstOrNull { it.id == down.id } ?: break
          if (event.changes.count { it.pressed } > 1 || change.isConsumed) break
          velocityTracker.addPosition(change.uptimeMillis, change.position)
          if (vertical == null) {
            // Once a hold starts, leave the gesture to the key's symbol/accent picker.
            if (change.uptimeMillis - down.uptimeMillis >= 250L || !change.pressed) break
            val distance = change.position - down.position
            if (distance.getDistance() <= viewConfiguration.touchSlop) continue
            vertical = abs(distance.y) > abs(distance.x)
            val amount = if (vertical) distance.y else distance.x
            remainder = 0f
            if (vertical) {
              startResultSwipe()
              scrollResults(
                amount.sign * (abs(amount) - viewConfiguration.touchSlop).coerceAtLeast(0f)
              )
            } else moveCursor(amount.sign.toInt())
          } else {
            val delta = change.position - change.previousPosition
            if (vertical) {
              scrollResults(delta.y)
            } else {
              remainder += delta.x
              val steps = (remainder / cursorStep).toInt()
              if (steps != 0) {
                moveCursor(steps)
                remainder -= steps * cursorStep
              }
            }
          }
          change.consume()
          if (!change.pressed) {
            if (vertical == true) {
              val velocity = velocityTracker.calculateVelocity().y.coerceIn(-20000f, 20000f)
              flingJob =
                gestureScope.launch {
                  val nativeFling = resultFling
                  if (nativeFling != null) {
                    nativeFling(velocity)
                    return@launch
                  }
                  var previousPosition = 0f
                  AnimationState(initialValue = 0f, initialVelocity = velocity).animateDecay(
                    flingDecay
                  ) {
                    scrollResults(value - previousPosition)
                    previousPosition = value
                  }
                }
            }
            break
          }
        }
      }
    }
  CompositionLocalProvider(LocalViewConfiguration provides keyboardConfiguration) {
    Surface(
      modifier.then(
        if (onHomeSwipe != null) homeSwipeInput else if (gesturesEnabled) swipeInput else Modifier
      ),
      color = animatedKeyboardColor(MaterialTheme.colorScheme.surface),
      contentColor = animatedKeyboardColor(MaterialTheme.colorScheme.onSurface),
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
                  {
                    onSpaceShortcutPressed(half)
                    type(" ")
                  },
                  Modifier.weight(if (split) 3f else 4f).fillMaxHeight(),
                  description = spaceShortcutLabel?.let { "Space: activate $it search" } ?: "Space",
                  selected = spaceShortcutLabel != null,
                  icon = spaceShortcutIcon,
                  showLabelWithIcon = true,
                  content = spaceShortcutContent?.let { content -> { content(half) } },
                )
                if (!split || half == 1) {
                  KeyboardKey(".", { type(".") }, Modifier.weight(1f).fillMaxHeight())
                  KeyboardGoKey(
                    onGo,
                    Modifier.weight(1.5f).fillMaxHeight(),
                    goIcon,
                    goDescription,
                    goTarget,
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

/** Read selection only here: scrolling must not recompose the whole search screen or keyboard. */
@Composable
private fun KeyboardGoKey(
  onGo: () -> Unit,
  modifier: Modifier,
  icon: ImageBitmap?,
  description: String,
  target: (@Composable () -> KeyboardGoTarget)?,
) {
  val resolved = target?.invoke()
  KeyboardKey(
    "Go",
    onGo,
    modifier,
    selected = true,
    description = resolved?.description ?: description,
    icon = if (resolved != null) resolved.icon else icon,
  )
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
  content: (@Composable () -> Unit)? = null,
  alternatives: List<String> = emptyList(),
  onAlternative: (String) -> Unit = {},
) {
  val colors = MaterialTheme.colorScheme
  val dark = colors.surface.luminance() < colors.onSurface.luminance()
  // Match SearchChromeBar's 3.dp tonal surface; the keyboard's base surface is a shade darker.
  val restingColor =
    animatedKeyboardColor(if (dark) colors.surfaceColorAtElevation(3.dp) else colors.surfaceVariant)
  val restingContentColor =
    animatedKeyboardColor(if (dark) colors.onSurface else colors.onSurfaceVariant)
  val selectedColor =
    animatedKeyboardColor(
      if (dark) colors.surfaceColorAtElevation(6.dp) else colors.secondaryContainer
    )
  val selectedContentColor =
    animatedKeyboardColor(if (dark) colors.onSurface else colors.onSecondaryContainer)
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
        // Clickable keys (especially Go) must never turn a drag into a click, even when
        // the keyboard-level recognizer yields to a hold or gestures are switched off.
        .then(
          if (alternatives.isEmpty())
            Modifier.pointerInput(Unit) {
              awaitEachGesture {
                val down =
                  awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var dragged = false
                while (true) {
                  val event = awaitPointerEvent(PointerEventPass.Initial)
                  val change = event.changes.firstOrNull { it.id == down.id } ?: break
                  if (change.isConsumed) break
                  if (
                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                  ) {
                    dragged = true
                  }
                  if (dragged) change.consume()
                  if (!change.pressed) break
                }
              }
            }
          else Modifier
        )
        .then(keyInput),
    shape = RoundedCornerShape(6.dp),
    color =
      when {
        (pressed || gesturePressed) -> MaterialTheme.colorScheme.primaryContainer
        selected -> selectedColor
        else -> restingColor
      },
    contentColor =
      when {
        (pressed || gesturePressed) -> MaterialTheme.colorScheme.onPrimaryContainer
        selected -> selectedContentColor
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

      if (content != null) {
        content()
      } else if (icon != null && showLabelWithIcon) {
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

// Blend wallpaper palette changes while keeping key press feedback immediate.
@Composable
private fun animatedKeyboardColor(target: Color): Color {
  val color by
    animateColorAsState(
      targetValue = target,
      animationSpec = tween(300),
      label = "Keyboard palette",
    )
  return color
}
