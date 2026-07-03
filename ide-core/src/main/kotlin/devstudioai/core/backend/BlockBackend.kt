package devstudioai.core.backend

import devstudioai.block.BlockEdit
import devstudioai.block.BlockId
import devstudioai.block.BlockNode
import devstudioai.block.BlockPart
import devstudioai.block.BlockRef
import devstudioai.block.BlockTemplate
import devstudioai.block.Delete
import devstudioai.block.InsertTemplate
import devstudioai.block.Move
import devstudioai.block.ReplaceWithText
import devstudioai.block.SetField
import devstudioai.block.SlotCategory
import devstudioai.block.SlotRef
import devstudioai.block.Wrap
import devstudioai.core.BackendContext
import devstudioai.ui.backend.BlockService
import devstudioai.ui.backend.UiBlockEdit
import devstudioai.ui.backend.UiBlockNode
import devstudioai.ui.backend.UiBlockPart
import devstudioai.ui.backend.UiTextEdit
import java.nio.file.Paths
import kotlinx.coroutines.withContext

/** [BlockService] over the engine's projectional editor: project the buffer into a block tree and compile a
 *  block edit back to surgical text edits. Runs on the serialized engine dispatcher. */
internal class BlockBackend(private val ctx: BackendContext) : BlockService {

    override suspend fun projectBlocks(path: String, text: String): UiBlockNode? =
        withContext(ctx.engineDispatcher) {
            ctx.services.blocks.projectBlocks(
                Paths.get(path), text
            )
        }?.let { toUiBlock(it.root) }

    override suspend fun applyBlockEdit(
        path: String, text: String, edit: UiBlockEdit
    ): List<UiTextEdit> {
        val blockEdit: BlockEdit = when (edit) {
            is UiBlockEdit.SetField -> SetField(
                BlockRef(BlockId(edit.blockId)), edit.role, edit.text
            )

            is UiBlockEdit.ReplaceSlot -> ReplaceWithText(
                SlotRef(
                    BlockId(edit.blockId), edit.slotIndex
                ), edit.text
            )

            is UiBlockEdit.DeleteBlock -> Delete(BlockRef(BlockId(edit.blockId)))
            is UiBlockEdit.InsertTemplate -> InsertTemplate(
                SlotRef(BlockId(edit.ownerBlockId), edit.slotIndex, edit.index),
                BlockTemplate(
                    label = "insert", category = SlotCategory.STATEMENT, defaultText = edit.text
                ),
            )

            is UiBlockEdit.WrapInIf -> Wrap(
                BlockRef(BlockId(edit.blockId)),
                BlockTemplate(
                    label = "if",
                    category = SlotCategory.STATEMENT,
                    defaultText = "if (true) {\n${BlockTemplate.PLACEHOLDER}\n}"
                ),
            )

            is UiBlockEdit.MoveBlock -> Move(
                BlockRef(BlockId(edit.blockId)),
                SlotRef(BlockId(edit.toOwnerBlockId), edit.toSlotIndex, edit.toIndex),
            )
        }
        return withContext(ctx.engineDispatcher) {
            ctx.services.blocks.computeBlockEdit(
                Paths.get(path), text, blockEdit
            )
        }.map { UiTextEdit(it.offset, it.offset + it.oldLength, it.newText.toString()) }
    }

    /** Map a framework [BlockNode] subtree onto the UI's neutral [UiBlockNode] DTO. */
    private fun toUiBlock(b: BlockNode): UiBlockNode = UiBlockNode(
        id = b.id.value,
        kind = b.kind.id,
        label = b.label,
        category = b.kind.id,
        start = b.range.start,
        end = b.range.end,
        parts = b.parts.map { part ->
            when (part) {
                is BlockPart.Field -> UiBlockPart.Field(
                    role = part.field.role,
                    text = part.field.text,
                    editable = part.field.editable,
                    start = part.field.range?.start ?: -1,
                    end = part.field.range?.end ?: -1,
                )

                is BlockPart.Slot -> UiBlockPart.Slot(
                    category = part.slot.category.name,
                    multiple = part.slot.multiple,
                    start = part.slot.range.start,
                    end = part.slot.range.end,
                    children = part.slot.children.map(::toUiBlock),
                    valueKind = part.slot.valueKind.name.lowercase(),
                )
            }
        },
        valueKind = b.valueKind.name.lowercase(),
    )
}
