package com.github.konevdmitry.dynamictypereferencesforpythonplugin.services

import com.github.konevdmitry.dynamictypereferencesforpythonplugin.MyBundle
import com.intellij.openapi.project.Project

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
