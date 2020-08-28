package com.github.konevdmitry.dynamictypereferencesforpythonplugin.services

import com.intellij.openapi.project.Project
import com.github.konevdmitry.dynamictypereferencesforpythonplugin.MyBundle

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
