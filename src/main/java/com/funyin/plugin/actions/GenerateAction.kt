package com.funyin.plugin.actions

import com.funyin.plugin.utils.FileGenerator
import com.funyin.plugin.utils.FileHelperNew.shouldActivateFor
import com.funyin.plugin.utils.PluginUtils.showNotify
import com.funyin.plugin.utils.message
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys

class GenerateAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(PlatformDataKeys.PROJECT)
        if (shouldActivateFor(project!!)) {
            FileGenerator(project).generateAll()
        } else {
            showNotify(message("notKotlinProject"))
        }
    }
}
