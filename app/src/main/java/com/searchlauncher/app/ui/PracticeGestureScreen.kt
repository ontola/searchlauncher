package com.searchlauncher.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun PracticeGestureScreen(onBack: () -> Unit) {
  var feedbackText by remember {
    mutableStateOf("Swipe from either blue edge,\nthen back to the edge")
  }
  var isSuccess by remember { mutableStateOf(false) }

  // Gesture tracking state
  var touchPosition by remember { mutableStateOf<Offset?>(null) }
  var pathPoints by remember { mutableStateOf(listOf<Offset>()) }
  var hasCrossedThreshold by remember { mutableStateOf(false) }
  var startFromLeft by remember { mutableStateOf(true) }

  val threshold = 250f // Slightly larger for visual feedback

  // Animation for the guide arrow
  val infiniteTransition = rememberInfiniteTransition(label = "guide")
  val guideProgress by
          infiniteTransition.animateFloat(
                  initialValue = 0f,
                  targetValue = 1f,
                  animationSpec =
                          infiniteRepeatable(
                                  animation = tween(2000, easing = FastOutSlowInEasing),
                                  repeatMode = RepeatMode.Restart,
                          ),
                  label = "progress",
          )

  LaunchedEffect(isSuccess) {
    if (isSuccess) {
      delay(2000)
      isSuccess = false
      pathPoints = emptyList()
      touchPosition = null
      hasCrossedThreshold = false
      feedbackText = "Swipe from either blue edge,\nthen back to the edge"
    }
  }

  Box(
          modifier =
                  Modifier.fillMaxSize()
                          .background(MaterialTheme.colorScheme.background)
                          .pointerInput(Unit) {
                            @Suppress("DEPRECATION")
                            forEachGesture {
                              awaitPointerEventScope {
                                val width = size.width
                                val down = awaitFirstDown()

                                val isLeft = down.position.x <= 120f
                                val isRight = down.position.x >= width - 120f

                                if (isLeft || isRight) {
                                  startFromLeft = isLeft
                                  touchPosition = down.position
                                  pathPoints = listOf(down.position)
                                  hasCrossedThreshold = false
                                  feedbackText = "Keep going..."

                                  do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull()
                                    if (change != null && change.pressed) {
                                      touchPosition = change.position
                                      pathPoints = pathPoints + change.position

                                      val deltaX = change.position.x - pathPoints.first().x

                                      if (isLeft) {
                                        if (!hasCrossedThreshold && deltaX > threshold) {
                                          hasCrossedThreshold = true
                                          feedbackText = "Now swipe back!"
                                        } else if (hasCrossedThreshold && deltaX < threshold / 3) {
                                          isSuccess = true
                                          feedbackText = "Great job!"
                                        }
                                      } else {
                                        // Right edge logic (delta is negative)
                                        if (!hasCrossedThreshold && deltaX < -threshold) {
                                          hasCrossedThreshold = true
                                          feedbackText = "Now swipe back!"
                                        } else if (hasCrossedThreshold && deltaX > -threshold / 3) {
                                          isSuccess = true
                                          feedbackText = "Great job!"
                                        }
                                      }
                                    }
                                  } while (event.changes.any { it.pressed } && !isSuccess)

                                  if (!isSuccess) {
                                    touchPosition = null
                                    pathPoints = emptyList()
                                    feedbackText = "Try again: Swipe out, then back."
                                  }
                                }
                              }
                            }
                          }
  ) {
    // Content
    Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Spacer(modifier = Modifier.weight(1f))
        Text(text = "Practice Gesture", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.size(48.dp)) // Balance
      }

      Spacer(modifier = Modifier.height(64.dp))

      if (isSuccess) {
        Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color.Green,
                modifier = Modifier.size(80.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
      }

      Text(
              text = feedbackText,
              style = MaterialTheme.typography.headlineSmall,
              textAlign = TextAlign.Center,
      )
    }

    // Left Edge Indicator
    Box(
            modifier =
                    Modifier.fillMaxHeight()
                            .width(15.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            .align(Alignment.CenterStart)
    )

    // Right Edge Indicator
    Box(
            modifier =
                    Modifier.fillMaxHeight()
                            .width(15.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            .align(Alignment.CenterEnd)
    )

    // Guide Animation
    if (touchPosition == null && !isSuccess) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val centerY = size.height / 2
        val maxDist = 300f

        // Left Guide
        val leftStartX = 20f
        val leftCurrentX =
                if (guideProgress < 0.5f) {
                  leftStartX + (maxDist * (guideProgress * 2))
                } else {
                  leftStartX + maxDist - (maxDist * ((guideProgress - 0.5f) * 2))
                }

        drawCircle(
                color = Color.Blue.copy(alpha = 0.3f),
                radius = 30f,
                center = Offset(leftCurrentX, centerY - 100f), // Slightly above center
        )

        // Right Guide
        val rightStartX = size.width - 20f
        val rightCurrentX =
                if (guideProgress < 0.5f) {
                  rightStartX - (maxDist * (guideProgress * 2))
                } else {
                  rightStartX - maxDist + (maxDist * ((guideProgress - 0.5f) * 2))
                }

        drawCircle(
                color = Color.Blue.copy(alpha = 0.3f),
                radius = 30f,
                center = Offset(rightCurrentX, centerY + 100f), // Slightly below center
        )
      }
    }

    // User Path visualization
    if (pathPoints.isNotEmpty()) {
      val primaryColor = MaterialTheme.colorScheme.primary

      Canvas(modifier = Modifier.fillMaxSize()) {
        val path = Path()
        if (pathPoints.isNotEmpty()) {
          path.moveTo(pathPoints.first().x, pathPoints.first().y)
          pathPoints.drop(1).forEach { path.lineTo(it.x, it.y) }
        }

        drawPath(
                path = path,
                color = if (hasCrossedThreshold) Color.Green else primaryColor,
                style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Draw threshold line hint
        val thresholdX = if (startFromLeft) threshold + 20f else size.width - threshold - 20f

        drawLine(
                color = Color.Gray.copy(alpha = 0.3f),
                start = Offset(thresholdX, 0f),
                end = Offset(thresholdX, size.height),
                strokeWidth = 2f,
        )
      }
    }
  }
}
