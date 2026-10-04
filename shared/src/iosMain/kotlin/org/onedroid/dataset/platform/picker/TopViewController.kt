package org.onedroid.dataset.platform.picker

import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

internal fun topViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) controller = controller.presentedViewController
    return controller
}
