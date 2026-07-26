import com.wasimaster.wmkeyboard.core.prediction.DictionaryLoader
import com.wasimaster.wmkeyboard.core.prediction.PackedTrie
import com.wasimaster.wmkeyboard.core.prediction.PackedTrieCodec
import java.io.File

/**
 * dictc — compiles plain-text wordlists into `.wmdict` binary tries.
 *
 * Usage: dictc <srcDir> <outDir>
 *
 * Every `<name>.txt` in srcDir (format: `word<space>frequency`, `#` comments)
 * becomes `<name>.wmdict` in outDir. Invoked by the app's
 * `compileBundledDictionaries` Gradle task; not shipped in the APK.
 */
fun main(args: Array<String>) {
    require(args.size == 2) { "usage: dictc <srcDir> <outDir>" }
    val srcDir = File(args[0])
    val outDir = File(args[1])
    val sources = srcDir.listFiles { file -> file.isFile && file.extension == "txt" }
        ?.sortedBy { it.name }
        .orEmpty()
    require(sources.isNotEmpty()) { "no .txt wordlists in $srcDir" }
    outDir.mkdirs()
    for (source in sources) {
        val entries = source.inputStream().use { DictionaryLoader.loadEntries(it) }
        val trie = PackedTrie.of(entries)
        val out = File(outDir, "${source.nameWithoutExtension}.wmdict")
        out.outputStream().use { PackedTrieCodec.write(trie, it) }
        println("dictc: ${source.name} (${entries.size} entries) -> ${out.name} (${out.length()} bytes)")
    }
}
