package com.bytedance.mathlab

import com.bytedance.mathlab.content.LabVolume
import com.bytedance.mathlab.lab.DemoRegistry
import com.bytedance.mathlab.lab.LabHost
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        LabHost.register(DemoRegistry.all)
        DefaultWindowContainer {
            PicoTheme {
                LabVolume(firstDemo = DemoRegistry.FIRST)
            }
        }
    }
