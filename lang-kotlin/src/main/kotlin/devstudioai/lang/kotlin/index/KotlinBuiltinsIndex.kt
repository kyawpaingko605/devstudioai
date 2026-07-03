package devstudioai.lang.kotlin.index

import devstudioai.index.IndexExtension
import devstudioai.index.IndexId
import devstudioai.index.IndexInput
import devstudioai.index.IndexOrigin
import devstudioai.index.InputFilter
import devstudioai.index.KeyDescriptor
import devstudioai.index.MatchingMode
import devstudioai.index.StringKeyDescriptor
import devstudioai.lang.kotlin.symbols.BuiltinsReader
import devstudioai.lang.kotlin.symbols.TypeShape

/**
 * `kotlin.builtins` — the owner-keyed shape index for Kotlin's INTRINSIC types (`List`, `Int`, `String`,
 * `Collection`, `Map`, …). These live in `kotlin-stdlib.jar` as `.kotlin_builtins` protobuf fragments, NOT as
 * `.class` files, so [KotlinTypeShapeIndex] (which filters on `.class`) can't carry them. This index decodes
 * each fragment via [BuiltinsReader.shapesFrom] (companion members merged in as statics) and persists one
 * [TypeShape] per type, keyed by its Kotlin FQN (`kotlin.collections.List`) — so the Kotlin backend reads the
 * real Kotlin shape (a read-only `List` has no `add`/`remove`; `Int.` shows `MAX_VALUE`) from the index, with
 * no live jar read, exactly like every other classpath shape. Reuses [TypeShapeExternalizer].
 *
 * Kept SEPARATE from `kotlin.typeShape` because the keys differ: a built-in is queried by its Kotlin FQN,
 * while `kotlin.typeShape` routes a Kotlin type through its JVM mapping (`kotlin.collections.List` →
 * `java.util.List`) — the whole point of built-ins is to NOT use that java.* approximation.
 */
object KotlinBuiltinsIndex : IndexExtension<String, TypeShape> {
    override val id = IndexId("kotlin.builtins")
    override val version = 4 // v4: shared TypeShapeExternalizer gained TypeShape.sealedSubclasses
    override val keyDescriptor: KeyDescriptor<String> = StringKeyDescriptor
    override val valueExternalizer = TypeShapeExternalizer
    override val matching = MatchingMode.PREFIX_ONLY // queried only by exact Kotlin FQN
    override val inputFilter = InputFilter {
        (it.origin == IndexOrigin.SDK || it.origin == IndexOrigin.LIBRARY) && it.unitName?.endsWith(".kotlin_builtins") == true
    }

    override fun index(input: IndexInput): Map<String, Collection<TypeShape>> {
        val bytes = runCatching { input.bytes() }.getOrNull() ?: return emptyMap()
        return BuiltinsReader.shapesFrom(bytes).mapValues { (_, shape) -> listOf(shape) }
    }
}
