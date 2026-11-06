package com.eddyizm.tempus.ui

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eddyizm.tempus.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import java.util.Locale
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class TranslatedSectionHeaderLayoutTest {
    @Test fun translatedTitlesAndActionsHaveSeparateUnclippedRows() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val musicSectors = listOf(
                R.id.home_discover_sector, R.id.starred_tracks_sector,
                R.id.starred_albums_sector, R.id.starred_artists_sector,
                R.id.home_most_played_albums_sector, R.id.home_recently_played_albums_sector,
                R.id.home_recently_added_albums_sector, R.id.pinned_playlists_sector
            )
            val librarySectors = listOf(
                R.id.library_album_sector, R.id.library_artist_sector,
                R.id.library_genres_sector, R.id.library_playlist_sector
            )
            for ((layout, sectors) in listOf(
                R.layout.fragment_home_tab_music to musicSectors,
                R.layout.fragment_library to librarySectors
            )) {
                for (sector in sectors) {
                    checkHeader(instrumentation.targetContext, layout, sector, "ca", 393, 1f)
                }
            }
            // There is no bundled RTL translation; Arabic exercises RTL with fallback strings.
            for (locale in listOf("ca", "en", "ar")) {
                for (width in listOf(320, 600)) {
                    for (scale in listOf(1.3f, 2f)) {
                        checkHeader(instrumentation.targetContext, R.layout.fragment_home_tab_music,
                            R.id.starred_artists_sector, locale, width, scale)
                        checkHeader(instrumentation.targetContext, R.layout.fragment_library,
                            R.id.library_playlist_sector, locale, width, scale)
                    }
                }
            }
        }
    }

    private fun checkHeader(base: Context, layout: Int, sector: Int, language: String,
                            widthDp: Int, fontScale: Float) {
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
            this.fontScale = fontScale
            screenWidthDp = widthDp
            smallestScreenWidthDp = widthDp
        }
        val context = ContextThemeWrapper(base.createConfigurationContext(configuration), R.style.AppTheme)
        val header = inflateHeader(context, layout, sector)
        header.layoutDirection = configuration.layoutDirection
        val width = (widthDp * context.resources.displayMetrics.density).roundToInt()
        header.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        header.layout(0, 0, width, header.measuredHeight)
        val titleGroup = header.getChildAt(0)
        val action = header.getChildAt(header.childCount - 1) as TextView
        val title = if (titleGroup is TextView) titleGroup else (titleGroup as ViewGroup).getChildAt(0) as TextView
        val label = "${context.resources.getResourceEntryName(sector)} $language ${widthDp}dp fontScale=$fontScale"
        val lines = (0 until title.lineCount).map {
            title.text.subSequence(title.layout.getLineStart(it), title.layout.getLineEnd(it)).toString().trim()
        }
        Log.i("TranslatedSectionHeader", "$label: $lines")
        assertTrue("$label: action must be below title/refresh group", action.top >= titleGroup.bottom)
        for (child in listOf(titleGroup, action)) {
            assertTrue("$label: left bound", child.left >= header.paddingLeft)
            assertTrue("$label: right bound", child.right <= header.width - header.paddingRight)
            assertTrue("$label: top bound", child.top >= header.paddingTop)
            assertTrue("$label: bottom bound", child.bottom <= header.height - header.paddingBottom)
        }
        assertEquals("$label: title has full row width",
            header.width - header.paddingLeft - header.paddingRight, titleGroup.width)
        if (header.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            assertEquals("$label: action aligned to end", header.paddingLeft, action.left)
        } else {
            assertEquals("$label: action aligned to end", header.width - header.paddingRight, action.right)
        }
        for (text in listOf(title, action)) {
            val textLayout = text.layout
            assertNotNull("$label: text layout", textLayout)
            assertEquals("$label: complete text", text.text.length, textLayout.getLineEnd(textLayout.lineCount - 1))
            assertTrue("$label: text height", textLayout.height <= text.height - text.compoundPaddingTop - text.compoundPaddingBottom)
            for (line in 0 until textLayout.lineCount) {
                assertEquals("$label: no ellipsis", 0, textLayout.getEllipsisCount(line))
                assertTrue("$label: text width", textLayout.getLineWidth(line) <= text.width - text.compoundPaddingLeft - text.compoundPaddingRight + 1)
            }
        }
        if (titleGroup is ViewGroup) {
            val icon = titleGroup.getChildAt(1)
            assertTrue("$label: icon inside row", icon.left >= 0 && icon.right <= titleGroup.width)
            assertTrue("$label: icon inside height", icon.top >= 0 && icon.bottom <= titleGroup.height)
            assertTrue("$label: title and icon do not overlap",
                title.right <= icon.left || icon.right <= title.left)
        }
        if (language == "ca" && widthDp == 393 && fontScale == 1f) {
            assertTrue("$label: no isolated trailing letter: $lines", lines.last().length > 1)
        }
    }

    private fun inflateHeader(context: Context, layout: Int, sector: Int): ViewGroup {
        // Inflate only the header subtree, skipping Library's embedded ToolbarFragment.
        // inflate() always consumes parser.next() before the root start tag. Stay on the
        // sector start tag so that step lands on the paired header, not its first child.
        context.resources.getLayout(layout).use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG &&
                    parser.getAttributeResourceValue("http://schemas.android.com/apk/res/android", "id", 0) == sector) {
                    return LayoutInflater.from(context).inflate(parser, FrameLayout(context), false) as ViewGroup
                }
            }
        }
        error("Header not found for sector $sector")
    }
}
