package it.palsoftware.pastiera

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import it.palsoftware.pastiera.gif.StickerPackRepository

class StickerPackImportActivity : Activity() {
    companion object {
        const val ACTION_PACK_IMPORTED = "it.palsoftware.pastiera.STICKER_PACK_IMPORTED"
        private const val REQUEST_OPEN_TREE = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        startActivityForResult(intent, REQUEST_OPEN_TREE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OPEN_TREE && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                persistUri(uri, data)
                StickerPackRepository(this).addPack(uri)
                sendBroadcast(Intent(ACTION_PACK_IMPORTED).apply {
                    setPackage(packageName)
                })
            }
        }
        finish()
    }

    private fun persistUri(uri: Uri, data: Intent?) {
        val flags = (data?.flags ?: 0) and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        contentResolver.takePersistableUriPermission(uri, flags or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
