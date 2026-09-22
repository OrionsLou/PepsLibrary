package app.pepslibrary.reader

import android.content.Context
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File

sealed interface OpenResult {
    /** The caller owns [publication] and must close it. */
    data class Opened(val publication: Publication) : OpenResult
    data class Failed(val message: String) : OpenResult
}

/** Turns a downloaded EPUB file into a Readium [Publication]. Local files only; nothing here touches the network. */
class EpubOpener private constructor(context: Context) {
    // Readium's parser wants an HTTP client even though we only ever open local files.
    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
    private val publicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            context,
            httpClient = httpClient,
            assetRetriever = assetRetriever,
            pdfFactory = null, // EPUB only
        ),
    )

    suspend fun open(file: File): OpenResult {
        val retrieved = assetRetriever.retrieve(file)
        val asset = retrieved.getOrNull()
            ?: return OpenResult.Failed(damaged(retrieved.failureOrNull()?.message))

        val opened = publicationOpener.open(asset, allowUserInteraction = false)
        val publication = opened.getOrNull()
        if (publication == null) {
            asset.close()
            return OpenResult.Failed(damaged(opened.failureOrNull()?.message))
        }
        return OpenResult.Opened(publication)
    }

    private fun damaged(detail: String?) =
        "This book couldn't be opened. The file may be damaged, so try downloading the work again." +
            (detail?.let { "\n\n($it)" } ?: "")

    companion object {
        @Volatile private var instance: EpubOpener? = null

        /** Building Readium's parsers is not free, so one instance serves every reader. */
        fun get(context: Context): EpubOpener = instance ?: synchronized(this) {
            instance ?: EpubOpener(context.applicationContext).also { instance = it }
        }
    }
}
