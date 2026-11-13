package com.muhabbet.designsystem.components

import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.crossfadeMillis
import coil3.size.Dimension
import coil3.size.Size
import com.muhabbet.designsystem.theme.MuhabbetTheme
import com.muhabbet.designsystem.theme.MuhabbetThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

/**
 * What [MuhabbetImage] actually asks Coil for. The pipeline is cut off by an interceptor that records
 * the request and the resolved size and fails it, so no network or decoder is involved.
 *
 * The first test is the regression it exists for: the painter path never measures its layout, so a
 * request without a size resolver decodes at `Size.ORIGINAL` — a full camera photo for an avatar.
 */
@OptIn(DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MuhabbetImageRequestTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private class Executed(val request: ImageRequest, val size: Size)

    @Volatile
    private var executed: Executed? = null

    @Before
    fun installCapturingLoader() {
        val capture = Interceptor { chain ->
            executed = Executed(chain.request, chain.size)
            val failed: ImageResult = ErrorResult(
                image = null,
                request = chain.request,
                throwable = IllegalStateException("captured")
            )
            failed
        }
        SingletonImageLoader.setUnsafe(ImageLoader.Builder(context).components { add(capture) }.build())
    }

    @After
    fun resetLoader() {
        SingletonImageLoader.reset()
    }

    private fun awaitExecuted(): Executed {
        rule.waitUntil(timeoutMillis = 5_000) { executed != null }
        return requireNotNull(executed)
    }

    private fun show(model: Any) {
        rule.setContent {
            MuhabbetTheme(mode = MuhabbetThemeMode.Light) {
                MuhabbetImage(model = model, contentDescription = null, modifier = Modifier.size(AvatarDp.dp))
            }
        }
    }

    private fun avatarSize(): Size {
        val px = with(rule.density) { AvatarDp.dp.toPx().roundToInt() }
        return Size(Dimension(px), Dimension(px))
    }

    @Test
    fun `should decode at the laid-out size when the image is shown in a bounded slot`() {
        show(Url)

        val size = awaitExecuted().size
        assertNotEquals("must not decode the original file", Size.ORIGINAL, size)
        assertEquals(avatarSize(), size)
    }

    @Test
    fun `should keep the caller's request options when the model is an ImageRequest`() {
        show(ImageRequest.Builder(context).data(Url).memoryCacheKey(Key).diskCacheKey(Key).build())

        val run = awaitExecuted()
        assertEquals("the caller's request is extended, not nested as data", Url, run.request.data)
        assertEquals(Key, run.request.memoryCacheKey)
        assertEquals(Key, run.request.diskCacheKey)
        assertEquals("the component owns the fade", 0, run.request.crossfadeMillis)
        assertEquals(avatarSize(), run.size)
    }

    @Test
    fun `should honour the caller's size when its ImageRequest sets one`() {
        show(ImageRequest.Builder(context).data(Url).size(Thumb, Thumb).build())

        assertEquals(Size(Dimension(Thumb), Dimension(Thumb)), awaitExecuted().size)
    }

    private companion object {
        const val Url = "https://media.invalid/photo.jpg"
        const val Key = "media-42"
        const val AvatarDp = 48
        const val Thumb = 320
    }
}
