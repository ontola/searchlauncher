package com.searchlauncher.app.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun TutorialOverlay(
  currentStep: OnboardingStep?,
  bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
  // Manage visibility and delayed step update
  val visibleState = remember { MutableTransitionState(false) }
  var renderedStep by remember { mutableStateOf<OnboardingStep?>(null) }

  LaunchedEffect(currentStep) {
    if (currentStep == null) {
      visibleState.targetState = false
      // Wait for exit animation
      delay(500)
      renderedStep = null
    } else {
      if (renderedStep != null && renderedStep != currentStep) {
        // Transitioning from one step to another
        visibleState.targetState = false
        delay(550) // Slightly more than exit transition (500ms)
      }
      renderedStep = currentStep
      visibleState.targetState = true
    }
  }

  // Use renderedStep instead of currentStep for display
  if (renderedStep == null && !visibleState.currentState && !visibleState.targetState) return

  AnimatedVisibility(
    visibleState = visibleState,
    enter = fadeIn(animationSpec = tween(1000)),
    exit = fadeOut(animationSpec = tween(500)),
  ) {
    Box(modifier = Modifier.fillMaxSize().padding(bottom = bottomPadding)) {
      when (renderedStep) {
        OnboardingStep.SwipeBackground -> {
          SwipeGestureIndicator(
            text = "Swipe to switch backgrounds",
            direction = SwipeDirection.Horizontal,
            modifier = Modifier.align(Alignment.Center),
          )
        }

        OnboardingStep.SwipeNotifications -> {
          SwipeGestureIndicator(
            text = "Swipe down (left) for notifications",
            direction = SwipeDirection.Down,
            modifier =
              Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 120.dp),
            align = Alignment.Start,
          )
        }

        OnboardingStep.SwipeQuickSettings -> {
          SwipeGestureIndicator(
            text = "Swipe down (right) for settings",
            direction = SwipeDirection.Down,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 120.dp),
            align = Alignment.End,
          )
        }

        OnboardingStep.SwipeAppDrawer -> {
          SwipeGestureIndicator(
            text = "Swipe up for App Drawer",
            direction = SwipeDirection.Up,
            modifier = Modifier.align(Alignment.Center),
          )
        }

        OnboardingStep.LongPressBackground -> {
          HoldGestureIndicator(
            text = "Hold to change background or add Widgets",
            modifier = Modifier.align(Alignment.Center),
          )
        }

        OnboardingStep.SearchYoutube -> {
          TextHintIndicator(
            text = "Try typing 'y spacebar test' to search YouTube",
            modifier = Modifier.align(Alignment.Center),
          )
        }

        OnboardingStep.SearchGoogle -> {
          TextHintIndicator(
            text = "Try typing 'g spacebar test' to search Google\n(more in settings)",
            modifier = Modifier.align(Alignment.Center),
          )
        }

        OnboardingStep.AddFavorite -> {
          // Requires context of search results, handled in SearchScreen item renderer or generic
          // overlay
          // For now, let's put a subtle indicator near the top where results appear
          TextHintIndicator(
            text = "Long press an app to Favorite",
            modifier = Modifier.align(Alignment.Center).padding(bottom = 100.dp),
          )
        }

        OnboardingStep.ReorderFavorites -> {
          TextHintIndicator(
            text = "Hold app icons to change order",
            modifier = Modifier.align(Alignment.Center).padding(bottom = 150.dp),
          )
        }

        OnboardingStep.OpenSettings -> {
          TextHintIndicator(
            text = "Press the ⚙ to open Settings",
            modifier = Modifier.align(Alignment.Center).padding(bottom = 150.dp),
          )
        }

        OnboardingStep.SetDefaultLauncher -> {
          TextHintIndicator(
            text = "Type 'set launcher' to change\nyour default launcher",
            modifier = Modifier.align(Alignment.Center),
          )
        }

        else -> {}
      }
    }
  }
}

enum class SwipeDirection {
  Horizontal,
  Vertical,
  Down,
  Up,
}

@Composable
fun SwipeGestureIndicator(
  text: String,
  direction: SwipeDirection,
  modifier: Modifier = Modifier,
  align: Alignment.Horizontal = Alignment.CenterHorizontally,
) {
  val infiniteTransition = rememberInfiniteTransition(label = "swipe")

  val duration = 2000
  val swipeDuration = 600
  val startDelay = 200
  val endDelay = 200

  // Animate offset: Hold 0, Move 0->1 (S-Curve), Hold 1
  val offsetVal by
  infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec =
      infiniteRepeatable(
        animation =
          keyframes {
            durationMillis = duration
            0f at 0 using LinearEasing // Start
            0f at
                    startDelay using
                    androidx.compose.animation.core.CubicBezierEasing(
                      0.42f,
                      0.0f,
                      0.58f,
                      1.0f,
                    ) // Start move
            1f at (startDelay + swipeDuration) using LinearEasing // End move
            1f at duration
          },
        repeatMode = RepeatMode.Restart,
      ),
    label = "offset",
  )

  // Animate fade: Fade In, Stay, Fade Out, Pause
  val fade by
  infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec =
      infiniteRepeatable(
        animation =
          keyframes {
            durationMillis = duration
            0f at 0 using LinearEasing
            1f at startDelay // Fully visible before move starts
            1f at (startDelay + swipeDuration) // Stay visible during move
            0f at (startDelay + swipeDuration + endDelay) // Fade out
            0f at duration // Pause
          },
        repeatMode = RepeatMode.Restart,
      ),
    label = "fade",
  )

  Column(modifier = modifier, horizontalAlignment = align) {
    Text(
      text = text,
      color = Color.White,
      style =
        MaterialTheme.typography.titleLarge.copy(
          fontSize = 22.sp,
          fontWeight = FontWeight.Bold,
          shadow =
            androidx.compose.ui.graphics.Shadow(
              color = Color.Black.copy(alpha = 0.9f),
              blurRadius = 8f,
            ),
        ),
      textAlign =
        when (align) {
          Alignment.Start -> TextAlign.Start
          Alignment.End -> TextAlign.End
          else -> TextAlign.Center
        },
    )
    Box(modifier = Modifier.size(120.dp).graphicsLayer { alpha = fade }) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val path = Path()
        val startX =
          if (direction == SwipeDirection.Horizontal) size.width * 0.2f else size.width / 2
        val startY =
          if (direction == SwipeDirection.Up) size.height * 0.8f
          else if (direction == SwipeDirection.Vertical || direction == SwipeDirection.Down)
            size.height * 0.2f
          else size.height / 2
        val endX = if (direction == SwipeDirection.Horizontal) size.width * 0.8f else size.width / 2
        val endY =
          if (direction == SwipeDirection.Up) size.height * 0.2f
          else if (direction == SwipeDirection.Vertical || direction == SwipeDirection.Down)
            size.height * 0.8f
          else size.height / 2

        val currentX = startX + (endX - startX) * offsetVal
        val currentY = startY + (endY - startY) * offsetVal

        // Draw trail
        path.moveTo(startX, startY)
        path.lineTo(currentX, currentY)

        drawPath(
          path = path,
          color = Color.White.copy(alpha = 0.5f),
          style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
        )

        // Draw circle head
        drawCircle(color = Color.White, radius = 8.dp.toPx(), center = Offset(currentX, currentY))
      }
    }
  }
}

@Composable
fun HoldGestureIndicator(text: String, modifier: Modifier = Modifier) {
  val infiniteTransition = rememberInfiniteTransition(label = "hold")
  val scale by
  infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = 1.2f,
    animationSpec =
      infiniteRepeatable(
        animation = tween(1000, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse,
      ),
    label = "scale",
  )

  Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
      modifier =
        Modifier.size(60.dp)
          .graphicsLayer {
            scaleX = scale
            scaleY = scale
          }
          .background(Color.White.copy(alpha = 0.3f), CircleShape)
          .padding(10.dp)
          .background(Color.White, CircleShape)
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
      text = text,
      color = Color.White,
      style =
        MaterialTheme.typography.titleLarge.copy(
          fontSize = 22.sp,
          fontWeight = FontWeight.Bold,
          shadow =
            androidx.compose.ui.graphics.Shadow(
              color = Color.Black.copy(alpha = 0.9f),
              blurRadius = 8f,
            ),
        ),
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
fun TextHintIndicator(text: String, modifier: Modifier = Modifier) {
  Text(
    text = text,
    color = Color.White,
    modifier = modifier,
    style =
      MaterialTheme.typography.titleLarge.copy(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        shadow =
          androidx.compose.ui.graphics.Shadow(
            color = Color.Black.copy(alpha = 0.9f),
            blurRadius = 8f,
          ),
      ),
    textAlign = TextAlign.Center,
  )
}
