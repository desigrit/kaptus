package com.example.kaptus.ui.composables

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.math.abs

// A composable that remembers our custom fling behavior
@Composable
fun rememberSnapFlingBehavior(
    lazyListState: LazyListState,
    onSnap: (index: Int) -> Unit
): FlingBehavior {
    return remember(lazyListState) {
        SnapFlingBehavior(
            lazyListState = lazyListState,
            onSnap = onSnap
        )
    }
}

private class SnapFlingBehavior(
    private val lazyListState: LazyListState,
    private val snapAnimationSpec: AnimationSpec<Float> = spring(stiffness = 400f),
    private val onSnap: (index: Int) -> Unit
) : FlingBehavior {

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val layoutInfo = lazyListState.layoutInfo
        if (layoutInfo.visibleItemsInfo.isEmpty()) return initialVelocity

        // Find the item closest to the center to snap to
        val center = layoutInfo.viewportSize.height / 2
        val closestItem = layoutInfo.visibleItemsInfo.minByOrNull {
            abs(it.offset + it.size / 2 - center)
        } ?: return initialVelocity

        // Calculate the offset needed to center it
        val targetOffset = center - (closestItem.offset + closestItem.size / 2)

        // Animate the scroll to the final snapped position using scrollBy.
        // We can't use lazyListState.animateScrollBy here, but scrollBy within
        // the ScrollScope achieves the same smooth animation.
        scrollBy(targetOffset.toFloat())

        // After the snap animation is complete, report the new centered index
        onSnap(closestItem.index)

        // Consume the rest of the velocity
        return 0f
    }
}