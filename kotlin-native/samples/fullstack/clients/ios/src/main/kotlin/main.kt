/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import kotlinx.cinterop.*
import platform.CoreGraphics.*
import platform.CoreMotion.*
import platform.EAGL.*
import platform.Foundation.*
import platform.GameKit.*
import platform.GLKit.*
import platform.UIKit.*

fun main(args: Array<String>) {
    memScoped {
        val argc = args.size + 1
        val argv = (arrayOf("konan") + args).toCStringArray(memScope)

        autoreleasepool {
            UIApplicationMain(argc, argv, null, NSStringFromClass(AppDelegate))
        }
    }
}

class AppDelegate @OverrideInit constructor() : UIResponder(), UIApplicationDelegateProtocol {
    companion object : UIResponderMeta(), UIApplicationDelegateProtocolMeta {}

    private var _window: UIWindow? = null
    override fun window() = _window
    override fun setWindow(window: UIWindow?) { _window = window }
}

@ExportObjCClass
class ViewController : GLKViewController, GKGameCenterControllerDelegateProtocol {

    @OverrideInit constructor(coder: NSCoder) : super(coder)

    private lateinit var context: EAGLContext
    private lateinit var motionManager: CMMotionManager

    private val statsFetcher = StatsFetcherImpl().also {
        it.asyncFetch()
    }

    private val gameState = GameState(
            SceneState(), statsFetcher,
            SoundPlayerImpl("swish.wav").also {
                it.initialize()
                it.enable(true)
            }
    )
    private val touchControl = TouchControl(gameState)

    private lateinit var gameRenderer: GameRenderer

    override fun viewDidLoad() {
        setupGameCenterAuthentication()

        gameCenterButton.apply {
            layer.borderWidth = 1.0
            layer.borderColor = UIColor.colorWithRed(0x47/255.0, 0x43/255.0, 0x70/255.0, 1.0).CGColor
            layer.masksToBounds = true
        }

        this.context = EAGLContext(kEAGLRenderingAPIOpenGLES3)

        val view = this.view as GLKView
        view.context = this.context
        view.drawableDepthFormat = GLKViewDrawableDepthFormat24
        view.drawableMultisample = GLKViewDrawableMultisample4X

        EAGLContext.setCurrentContext(this.context)

        gameRenderer = GameRenderer()

        motionManager = CMMotionManager().also {
            println(this.framesPerSecond)
            it.deviceMotionUpdateInterval = 1.0 / this.framesPerSecond
            it.startDeviceMotionUpdates()
        }
    }

    private var panGestureBeganDate: NSDate? = null

    @ObjCAction
    fun handlePanGesture(sender: UIPanGestureRecognizer) {
        val screen = this.view.bounds.useContents { Vector2(size.width.toFloat(), size.height.toFloat()) }
        val total = sender.translationInView(this.view).useContents {
            Vector2(
                    x.toFloat() / screen.length,
                    -y.toFloat() / screen.length // TouchControl uses the opposite `y` axis direction.
            )
        }

        when (sender.state) {
            UIGestureRecognizerStateBegan -> {
                panGestureBeganDate = NSDate.date()
                touchControl.down()
            }

            UIGestureRecognizerStateChanged -> touchControl.move(total)

            UIGestureRecognizerStateEnded -> touchControl.up(
                    total,
                    -panGestureBeganDate!!.timeIntervalSinceNow().toFloat()
            )
        }
    }

    @ObjCAction
    fun update() {
        gameState.update(this.timeSinceLastUpdate.toFloat())
        getUserAcceleration()?.let {
            touchControl.shake(it)
        }
    }

    private fun getUserAcceleration(): Vector3? {
        val deviceMotion = this.motionManager.deviceMotion ?: return null
        val userAcceleration = deviceMotion.userAcceleration.toVector3() *
                -1.0f // TODO: for some reason Core Motion seems to report the acceleration inverted.

        // Detect the interface orientation and then transform the acceleration according to it:
        val screen = this.view.window!!.screen
        val screenCorner = screen.coordinateSpace.convertPoint(
                CGPointMake(0.0, 0.0),
                toCoordinateSpace = screen.fixedCoordinateSpace
        ).toVector2()

        val orientedAccelerationProjection = if (screenCorner.x == 0.0f) {
            if (screenCorner.y == 0.0f) {
                // portrait
                Vector2(userAcceleration.x, userAcceleration.y)
            } else {
                // landscape right
                Vector2(userAcceleration.y, -userAcceleration.x)
            }
        } else {
            if (screenCorner.y == 0.0f) {
                // landscape left
                Vector2(-userAcceleration.y, userAcceleration.x)
            } else {
                // portrait upside-down
                Vector2(-userAcceleration.x, -userAcceleration.y)
            }
        }

        return Vector3(orientedAccelerationProjection, userAcceleration.z)
    }

    override fun glkView(view: GLKView, drawInRect: CValue<CGRect>) {
        val (screenWidth, screenHeight) = this.view.bounds.useContents {
            size.width to size.height
        }

        gameRenderer.render(gameState.sceneState, screenWidth.toFloat(), screenHeight.toFloat())
    }

    private fun showErrorAlert(title: String, message: String) {
        val alert = UIAlertController.alertControllerWithTitle(title, message, UIAlertControllerStyleAlert)
        val ok = UIAlertAction.actionWithTitle("OK", style = UIAlertActionStyleDefault, handler = {
            alert.dismissViewControllerAnimated(true, completion = null)
        })
        alert.addAction(ok)
        this.presentViewController(alert, animated = true, completion = null)
    }

    // Game Center integration:

    private fun setupGameCenterAuthentication() {
        GKLocalPlayer.localPlayer().authenticateHandler = { viewController, error ->
            val localPlayer = GKLocalPlayer.localPlayer()
            if (viewController != null) {
                this.gameCenterAuthenticateViewController = viewController
            } else if (localPlayer.isAuthenticated()) {
                gameState.sceneState.stats?.reportToGameCenter()
                if (requestedGameCenter) {
                    requestedGameCenter = false
                    showGameCenter()
                }
            } else {
                if (requestedGameCenter) {
                    requestedGameCenter = false
                    showErrorAlert(
                            "Game Center error",
                            "Ensure that Game Center is enabled in Settings and that the credentials are correct"
                    )
                }
            }
        }
    }

    private var gameCenterAuthenticateViewController: UIViewController? = null

    var requestedGameCenter = false

    @ObjCAction
    fun gameCenterButtonPressed(sender: UIButton) {
        if (GKLocalPlayer.localPlayer().isAuthenticated()) {
            showGameCenter()
        } else {
            val authenticate = this.gameCenterAuthenticateViewController
            if (authenticate != null) {
                requestedGameCenter = true
                this.presentViewController(authenticate, animated = true, completion = null)
            } else {
                showErrorAlert(
                        "Game Center error",
                        "Ensure that Game Center is enabled in Settings and try to restart the application"
                )
            }
        }
    }

    @ObjCOutlet
    lateinit var gameCenterButton: UIButton

    override fun gameCenterViewControllerDidFinish(gameCenterViewController: GKGameCenterViewController) {
        gameCenterViewController.dismissViewControllerAnimated(true, completion = null)
    }

    private fun showGameCenter() {
        val gkViewController = GKGameCenterViewController().also {
            it.gameCenterDelegate = this
            it.viewState = GKGameCenterViewControllerStateLeaderboards
            it.leaderboardTimeScope = GKLeaderboardTimeScopeToday
            it.leaderboardCategory = "main"
        }

        this.presentViewController(gkViewController, animated = true, completion = null)
    }

}

private fun CValue<CMAcceleration>.toVector3() = this.useContents { Vector3(x.toFloat(), y.toFloat(), z.toFloat()) }

private fun CValue<CGPoint>.toVector2() = this.useContents { Vector2(x.toFloat(), y.toFloat()) }
