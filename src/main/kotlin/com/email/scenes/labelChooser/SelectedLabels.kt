package com.email.scenes.labelChooser

import com.email.scenes.labelChooser.data.LabelThread
import com.email.utils.file.removeWithDiscrimination
import java.util.*

/**
 * Created by sebas on 2/2/18.
 */
class SelectedLabels() {
    private val selectedItems: LinkedList<LabelThread> = LinkedList()

    fun addMultipleSelected(items: List<LabelThread>) {
        selectedItems.addAll(items)
        items.forEach {
            if(!it.isSelected)
                it.isSelected  = true
        }
    }

    fun add(item: LabelThread) {
        selectedItems.add(item)
        if(!item.isSelected)
            item.isSelected  = true
    }

    fun remove(item: LabelThread) {
        item.isSelected = false
        selectedItems.removeWithDiscrimination { it.id.equals(item.id) }
    }

    fun clear() {
        selectedItems.forEach { it.isSelected = false }
        selectedItems.clear()
    }

    fun length(): Int = selectedItems.size

    fun isEmpty(): Boolean = selectedItems.isEmpty()

    fun toIDs(): List<Int> =
            selectedItems.map { it.id }

    fun toList() = selectedItems.toList()

}
