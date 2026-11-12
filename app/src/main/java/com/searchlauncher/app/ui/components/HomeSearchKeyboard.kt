package com.searchlauncher.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
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
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.Job
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

// With the number row on, the top letters hint symbols that otherwise need the second symbol page.
private val numberRowTopSymbols = "qwertyuiop".zip("%^~|[]<>{}").toMap()

// Long-press extras for the optional number row.
private val digitAlternatives =
  mapOf('1' to "¹½⅓¼", '2' to "²⅔", '3' to "³¾", '4' to "⁴", '0' to "°⁰")

/**
 * Height of the home keyboard. Without the number row it has four 55dp rows in 243dp. The number
 * row is 0.8 of a letter row, so letters keep their size and the keyboard grows to 292dp.
 */
internal fun builtInHomeKeyboardHeight(screenHeightDp: Int, numberRow: Boolean): Dp {
  val base = minOf(243f, screenHeightDp * 0.45f)
  return (if (numberRow) base * 292f / 243f else base).dp
}

/** Weight of the number row relative to a letter row. */
private const val NUMBER_ROW_WEIGHT = 0.8f

/** Visible space between keys. Each key's touch area covers half of it on every side. */
private val KEY_GAP = 4.dp
private val ROW_GAP = 5.dp

/** Drag distance before a keyboard touch becomes a cursor, scroll or home swipe. */
private val KEYBOARD_GESTURE_SLOP = 16.dp

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
  numberRow: Boolean = false,
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
  val typeText by rememberUpdatedState(onText)
  fun type(text: String) {
    typeText(if (shift || capsLock) text.uppercase() else text)
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
  // Fast typing drifts a little more than the platform slop. Claiming those touches for a
  // swipe swallowed the letter, so keyboard gestures need a clearly deliberate drag.
  val gestureSlop = with(density) { KEYBOARD_GESTURE_SLOP.toPx() }
  val homeSwipe by rememberUpdatedState(onHomeSwipe)
  val homeSwipeInput =
    Modifier.pointerInput(density, gestureSlop) {
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
            if (distance.getDistance() <= maxOf(viewConfiguration.touchSlop, gestureSlop)) continue
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
    Modifier.pointerInput(cursorStep, gestureSlop) {
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
            if (distance.getDistance() <= maxOf(viewConfiguration.touchSlop, gestureSlop)) continue
            vertical = abs(distance.y) > abs(distance.x)
            val amount = if (vertical) distance.y else distance.x
            remainder = 0f
            if (vertical) {
              startResultSwipe()
              scrollResults(
                amount.sign *
                  (abs(amount) - maxOf(viewConfiguration.touchSlop, gestureSlop)).coerceAtLeast(0f)
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
  val colors = MaterialTheme.colorScheme
  val dark = colors.surface.luminance() < colors.onSurface.luminance()
  // Every key shares the palette. Do not create four animation controllers per key.
  val keyColors =
    KeyboardKeyColors(
      animatedKeyboardColor(
        if (dark) colors.surfaceColorAtElevation(3.dp) else colors.surfaceVariant
      ),
      animatedKeyboardColor(if (dark) colors.onSurface else colors.onSurfaceVariant),
      animatedKeyboardColor(
        if (dark) colors.surfaceColorAtElevation(6.dp) else colors.secondaryContainer
      ),
      animatedKeyboardColor(if (dark) colors.onSurface else colors.onSecondaryContainer),
    )
  val rollover = remember { KeyRollover() }
  CompositionLocalProvider(
    LocalViewConfiguration provides keyboardConfiguration,
    LocalKeyboardKeyColors provides keyColors,
    LocalKeyRollover provides rollover,
  ) {
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
        // Keys own the gaps around them (see KEY_GAP), so the outer padding shrinks by half a gap.
        Column(Modifier.padding(horizontal = 4.dp - KEY_GAP / 2, vertical = 4.dp - ROW_GAP / 2)) {
          if (numberRow) {
            Row(
              Modifier.fillMaxWidth().weight(NUMBER_ROW_WEIGHT),
              horizontalArrangement = Arrangement.spacedBy(KEY_GAP / 2),
            ) {
              val digits = "1234567890"
              val groups = if (split) listOf(digits.take(5), digits.drop(5)) else listOf(digits)
              groups.forEachIndexed { half, keys ->
                if (half == 1) Spacer(Modifier.width(splitGap))
                Row(Modifier.weight(1f).fillMaxHeight()) {
                  keys.forEach { digit ->
                    KeyboardKey(
                      label = digit.toString(),
                      onClick = { type(digit.toString()) },
                      modifier = Modifier.weight(1f).fillMaxHeight(),
                      alternatives = digitAlternatives[digit].orEmpty().map { it.toString() },
                      onAlternative = { type(it) },
                    )
                  }
                }
              }
            }
          }
          val rows =
            if (!symbols) listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
            else if (!extraSymbols) listOf("1234567890", "@#€%&-+()", "*\"':;!?/")
            else listOf("~`|•√π÷×§∆", "£¢$^°={}\\", "_[]<>:,.")
          rows.forEachIndexed { index, letters ->
            Row(
              Modifier.fillMaxWidth().weight(1f),
              horizontalArrangement = Arrangement.spacedBy(KEY_GAP / 2),
            ) {
              val splitAt = if (index == 2) 4 else 5
              val groups =
                if (split) listOf(letters.take(splitAt), letters.drop(splitAt)) else listOf(letters)
              groups.forEachIndexed { half, keys ->
                if (half == 1) Spacer(Modifier.width(splitGap))
                Row(Modifier.weight(1f).fillMaxHeight()) {
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
                  keys.forEachIndexed { position, letter ->
                    // The middle row's outer keys also own the indent beside them.
                    val indented = index == 1 && !split
                    val first = indented && position == 0
                    val last = indented && position == keys.lastIndex
                    val symbolHint =
                      if (symbols) null
                      else if (numberRow) numberRowTopSymbols[letter] ?: keySymbols[letter]
                      else keySymbols[letter]
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
                    Box(Modifier.weight(if (first || last) 1.5f else 1f).fillMaxHeight()) {
                      KeyboardKey(
                        label = if (shift || capsLock) letter.uppercase() else letter.toString(),
                        modifier = Modifier.fillMaxSize(),
                        visualModifier =
                          if (first) Modifier.wrapContentWidth(Alignment.End).fillMaxWidth(2f / 3f)
                          else if (last)
                            Modifier.wrapContentWidth(Alignment.Start).fillMaxWidth(2f / 3f)
                          else Modifier,
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
            horizontalArrangement = Arrangement.spacedBy(KEY_GAP / 2),
          ) {
            repeat(if (split) 2 else 1) { half ->
              if (half == 1) Spacer(Modifier.width(splitGap))
              Row(Modifier.weight(1f).fillMaxHeight()) {
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
    strictDrag = true,
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
  visualModifier: Modifier = Modifier,
  /** Actions such as Go cancel on any drag past the touch slop; typing keys tolerate drift. */
  strictDrag: Boolean = false,
) {
  val colors = LocalKeyboardKeyColors.current
  val restingColor = colors.resting
  val restingContentColor = colors.restingContent
  val selectedColor = colors.selected
  val selectedContentColor = colors.selectedContent
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
  val currentClick by rememberUpdatedState(onClick)
  val currentLongClick by rememberUpdatedState(onLongClick)
  val rollover = LocalKeyRollover.current
  val hasLongPress = onLongClick != null
  val keyInput =
    Modifier.semantics {
        role = Role.Button
        onClick {
          currentClick()
          true
        }
        if (hasLongPress) {
          onLongClick {
            currentLongClick?.invoke()
            true
          }
        }
        if (alternatives.isNotEmpty()) {
          customActions =
            alternatives.map { value ->
              CustomAccessibilityAction("Insert $value") {
                currentAlternative(value)
                true
              }
            }
        }
      }
      .pointerInput(alternatives, reverse, repeat, hasLongPress, strictDrag) {
        // Every touch on a key is its own press, so fast overlapping taps never cancel each other.
        awaitEachGesture {
          var next: PointerInputChange? = awaitFirstDown()
          gesturePressed = true
          try {
            while (next != null) {
              val down: PointerInputChange = next
              next = null
              down.consume()
              val press = KeyPress { currentClick() }
              val maxDrift =
                if (strictDrag) viewConfiguration.touchSlop else KEYBOARD_GESTURE_SLOP.toPx()
              rollover.begin(press)
              val holdMillis =
                when {
                  repeat -> 400L
                  hasLongPress || alternatives.isNotEmpty() ->
                    viewConfiguration.longPressTimeoutMillis
                  else -> null
                }
              var outcome =
                if (holdMillis == null) awaitKeyRelease(down, maxDrift)
                else withTimeoutOrNull(holdMillis) { awaitKeyRelease(down, maxDrift) }
              if (outcome == null && press.committed) {
                // A later key already typed this one; the hold must not add anything.
                outcome = awaitKeyRelease(down, maxDrift)
              }
              when {
                outcome is KeyRelease.Released -> press.commit()
                outcome is KeyRelease.Handover -> {
                  press.commit()
                  next = outcome.down
                }
                outcome == null && repeat -> {
                  rollover.hold(press)
                  do {
                    currentClick()
                    outcome = withTimeoutOrNull(65L) { awaitKeyRelease(down, maxDrift) }
                  } while (outcome == null)
                  if (outcome is KeyRelease.Handover) next = outcome.down
                }
                outcome == null && alternatives.isEmpty() -> {
                  rollover.hold(press)
                  currentLongClick?.invoke()
                  val release = awaitKeyRelease(down, maxDrift)
                  if (release is KeyRelease.Handover) next = release.down
                }
                outcome == null -> {
                  rollover.hold(press)
                  heldSelection = 0
                  val origin = currentEvent.changes.firstOrNull { it.id == down.id }?.position?.x
                  val direction = if (reverse) -1 else 1
                  while (origin != null) {
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
                  heldSelection = null
                }
              }
              rollover.end(press)
            }
          } finally {
            heldSelection = null
            gesturePressed = false
          }
        }
      }
  Box(
    modifier =
      modifier
        .focusProperties { canFocus = false }
        .onGloballyPositioned { keyCenterX = it.positionInWindow().x + it.size.width / 2f }
        .semantics { contentDescription = description }
        // Keys (especially Go) never turn a drag into a click: awaitKeyRelease cancels a press
        // once it drifts past the keyboard gesture slop, even when no keyboard gesture claims it.
        .then(keyInput)
  ) {
    Surface(
      // The touch area includes the gap around the key, so taps between keys still land.
      modifier =
        visualModifier.fillMaxSize().padding(horizontal = KEY_GAP / 2, vertical = ROW_GAP / 2),
      shape = RoundedCornerShape(6.dp),
      color =
        when {
          gesturePressed -> MaterialTheme.colorScheme.primaryContainer
          selected -> selectedColor
          else -> restingColor
        },
      contentColor =
        when {
          gesturePressed -> MaterialTheme.colorScheme.onPrimaryContainer
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
            Surface(
              shape = RoundedCornerShape(8.dp),
              shadowElevation = 6.dp,
              color = restingColor,
            ) {
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

private data class KeyboardKeyColors(
  val resting: Color,
  val restingContent: Color,
  val selected: Color,
  val selectedContent: Color,
)

private val LocalKeyboardKeyColors =
  compositionLocalOf<KeyboardKeyColors> { error("Keyboard keys must be inside HomeSearchKeyboard") }

/** One touch on one key. It types at most once, either on release or when a later key goes down. */
private class KeyPress(private val onCommit: () -> Unit) {
  var committed = false
    private set

  fun commit() {
    if (committed) return
    committed = true
    onCommit()
  }

  /** A hold (repeat, long click or accent picker) took over, so release must not type. */
  fun disarm() {
    committed = true
  }
}

/**
 * Keeps overlapping taps in touch order. When typing fast, the next finger often lands before the
 * previous one lifts. The earlier key then types at once instead of waiting for its release, so
 * letters never swap and no press is lost while another is down.
 */
private class KeyRollover {
  private var pending: KeyPress? = null

  fun begin(press: KeyPress) {
    pending?.commit()
    pending = press
  }

  fun hold(press: KeyPress) {
    press.disarm()
    end(press)
  }

  fun end(press: KeyPress) {
    if (pending === press) pending = null
  }
}

private val LocalKeyRollover = staticCompositionLocalOf { KeyRollover() }

private sealed interface KeyRelease {
  data object Released : KeyRelease

  data object Cancelled : KeyRelease

  /** Another finger landed on the same key: it starts a new press. */
  class Handover(val down: PointerInputChange) : KeyRelease
}

/**
 * Waits for [down] to lift. Unlike a regular tap detector it tolerates a little drift, even past
 * the key's edge, because fast thumbs roll off the key they hit. A longer drag ([maxDrift]), a
 * consumed change (a keyboard swipe took over) or a cancelled touch types nothing.
 */
private suspend fun AwaitPointerEventScope.awaitKeyRelease(
  down: PointerInputChange,
  maxDrift: Float,
): KeyRelease {
  while (true) {
    val event = awaitPointerEvent()
    event.changes
      .firstOrNull { it.id != down.id && it.pressed && !it.previousPressed }
      ?.let {
        return KeyRelease.Handover(it)
      }
    val change = event.changes.firstOrNull { it.id == down.id } ?: return KeyRelease.Cancelled
    if (change.isConsumed) return KeyRelease.Cancelled
    if ((change.position - down.position).getDistance() > maxDrift) return KeyRelease.Cancelled
    if (!change.pressed) {
      change.consume()
      return KeyRelease.Released
    }
  }
}
