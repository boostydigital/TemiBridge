package com.spatium.temibridge.core

import android.app.Activity
import android.util.Log
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import kotlin.math.hypot

object TemiController {
    private const val TAG = "TemiController"

    private var pendingArrival: (() -> Unit)? = null
    private var goToListenerProxy: Any? = null
    private var permListenerProxy: Any? = null
    private var lastTarget: String? = null

    private fun robotInstance(): Any? = try {
        val cls = Class.forName("com.robotemi.sdk.Robot")
        val method = cls.getMethod("getInstance")
        method.invoke(null)
    } catch (t: Throwable) {
        Log.w(TAG, "Robot SDK no disponible: ${t.message}")
        null
    }

    fun setArrivalCallbackOnce(callback: () -> Unit) {
        pendingArrival = callback
        ensureGoToListener()
    }

    fun clearArrivalCallback() {
        pendingArrival = null
    }

    private fun ensureGoToListener(): Boolean {
        val robot = robotInstance() ?: return false
        return try {
            val listenerCls = Class.forName("com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener")
            if (goToListenerProxy == null) {
                goToListenerProxy = Proxy.newProxyInstance(
                    listenerCls.classLoader,
                    arrayOf(listenerCls),
                    InvocationHandler { _, method, args ->
                        try {
                            if (method.name == "onGoToLocationStatusChanged" && args != null && args.size >= 3) {
                                val statusStr = args[1]?.toString() ?: ""
                                val descId = when (val v = args[2]) {
                                    is Int -> v
                                    is java.lang.Integer -> v.toInt()
                                    else -> -1
                                }
                                val isComplete = descId == 500 || statusStr.equals("Complete", ignoreCase = true)
                                if (isComplete) {
                                    pendingArrival?.invoke()
                                    pendingArrival = null
                                }
                            }
                        } catch (_: Throwable) {}
                        null
                    }
                )
                val addMethod = robot.javaClass.getMethod("addOnGoToLocationStatusChangedListener", listenerCls)
                addMethod.invoke(robot, goToListenerProxy)
            }
            true
        } catch (t: Throwable) {
            Log.w(TAG, "No pude registrar listener goTo: ${t.message}")
            false
        }
    }

    // --- Utils pose/locations ---
    private data class Pose(val x: Double, val y: Double)
    private data class LocationInfo(val name: String, val x: Double?, val y: Double?)

    private fun getCurrentPose(): Pose? {
        val robot = robotInstance() ?: return null
        return try {
            val m = runCatching { robot.javaClass.getMethod("getCurrentPosition") }.getOrNull()
                ?: runCatching { robot.javaClass.getMethod("getPosition") }.getOrNull()
            val pos = m?.invoke(robot) ?: return null
            val x = safeInvokeDouble(pos, "getX") ?: return null
            val y = safeInvokeDouble(pos, "getY") ?: return null
            Pose(x, y)
        } catch (t: Throwable) {
            Log.w(TAG, "getCurrentPose fallo: ${t.message}")
            null
        }
    }

    private fun getSavedLocations(): List<LocationInfo> {
        val robot = robotInstance() ?: return emptyList()
        return try {
            val namesAny = runCatching { robot.javaClass.getMethod("getAllLocations").invoke(robot) }.getOrNull()
                ?: runCatching { robot.javaClass.getMethod("getLocations").invoke(robot) }.getOrNull()
            val names = (namesAny as? List<*>)?.mapNotNull { it?.toString()?.trim() }?.filter { it.isNotEmpty() }
                ?: emptyList()

            if (names.isEmpty()) return emptyList()

            val posMethod = runCatching { robot.javaClass.getMethod("getLocationPosition", String::class.java) }.getOrNull()
            if (posMethod != null) {
                names.map { n ->
                    val pos = runCatching { posMethod.invoke(robot, n) }.getOrNull()
                    val x = safeInvokeDouble(pos, "getX")
                    val y = safeInvokeDouble(pos, "getY")
                    LocationInfo(n, x, y)
                }
            } else {
                names.map { n -> LocationInfo(n, null, null) }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "getSavedLocations fallo: ${t.message}")
            emptyList()
        }
    }

    private fun safeInvokeDouble(instance: Any?, methodName: String): Double? {
        if (instance == null) return null
        return try {
            val m = instance.javaClass.getMethod(methodName)
            when (val v = m.invoke(instance)) {
                is Double -> v
                is java.lang.Double -> v.toDouble()
                is Float -> v.toDouble()
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun safeInvokeString(instance: Any?, methodName: String): String? {
        if (instance == null) return null
        return try {
            val m = instance.javaClass.getMethod(methodName)
            (m.invoke(instance) as? CharSequence)?.toString()
        } catch (_: Throwable) {
            null
        }
    }

    // --- Speech / Movement ---
    private fun ttsRequest(text: String): Any? = try {
        val cls = Class.forName("com.robotemi.sdk.TtsRequest")
        val create = cls.getMethod("create", String::class.java, Boolean::class.javaPrimitiveType)
        create.invoke(null, text, false)
    } catch (t: Throwable) {
        Log.w(TAG, "TtsRequest no disponible: ${t.message}")
        null
    }

    fun speak(text: String) {
        val robot = robotInstance() ?: return
        val req = ttsRequest(text) ?: return
        try {
            val speak = robot.javaClass.getMethod("speak", req.javaClass)
            speak.invoke(robot, req)
        } catch (t: Throwable) {
            Log.w(TAG, "speak fallo: ${t.message}")
        }
    }

    fun goTo(place: String) {
        val robot = robotInstance() ?: return
        try {
            ensureGoToListener()
            lastTarget = place
            val goTo = robot.javaClass.getMethod("goTo", String::class.java)
            goTo.invoke(robot, place)
        } catch (t: Throwable) {
            Log.w(TAG, "goTo fallo: ${t.message}")
        }
    }

    fun startDefaultNlu(identifier: String) {
        val robot = robotInstance() ?: return
        try {
            val start = robot.javaClass.getMethod("startDefaultNlu", String::class.java)
            start.invoke(robot, identifier)
        } catch (t: Throwable) {
            Log.w(TAG, "startDefaultNlu fallo: ${t.message}")
        }
    }

    // --- Tours / Sequences ---
    fun getAllSequenceNames(): List<String> {
        val robot = robotInstance() ?: return emptyList()
        return try {
            val mNoArgs = runCatching { robot.javaClass.getMethod("getAllSequences") }.getOrNull()
            val sequencesAny: Any? = if (mNoArgs != null) {
                mNoArgs.invoke(robot)
            } else {
                val listCls = List::class.java
                val mWithList = robot.javaClass.getMethod("getAllSequences", listCls)
                mWithList.invoke(robot, emptyList<Any>())
            }
            val list = sequencesAny as? List<*> ?: return emptyList()
            list.mapNotNull { item -> safeInvokeString(item, "getName")?.trim()?.takeIf { it.isNotEmpty() } }
        } catch (t: Throwable) {
            Log.w(TAG, "getAllSequenceNames fallo: ${t.message}")
            emptyList()
        }
    }

    fun logAndSpeakSequenceNames() {
        val names = getAllSequenceNames()
        Log.d(TAG, "Sequences disponibles: ${names.joinToString()}")
        speak("Sequences disponibles: ${names.joinToString()}")
    }

    fun playTourById(tourId: String): Boolean {
        val robot = robotInstance() ?: return false
        return try {
            val playTour = robot.javaClass.getMethod("playTour", String::class.java)
            val result = playTour.invoke(robot, tourId) as? Int
            val ok = (result == 0)
            if (!ok) Log.w(TAG, "playTour result: $result for id=$tourId")
            ok
        } catch (t: Throwable) {
            Log.w(TAG, "playTourById fallo: ${t.message}")
            false
        }
    }

    fun playTourByName(name: String): Boolean {
        val robot = robotInstance() ?: return false
        return try {
            val getAllTours = robot.javaClass.getMethod("getAllTours")
            val tours = getAllTours.invoke(robot) as? List<*>
            if (tours.isNullOrEmpty()) return false
            var matchedId: String? = null
            for (t in tours) {
                val tName = safeInvokeString(t, "getName")
                if (tName != null && tName.equals(name, ignoreCase = true)) {
                    matchedId = safeInvokeString(t, "getId")
                    break
                }
            }
            if (matchedId.isNullOrBlank()) return false
            playTourById(matchedId)
        } catch (t: Throwable) {
            Log.w(TAG, "playTourByName fallo: ${t.message}")
            false
        }
    }

    // --- Sequences permission helpers ---
    fun hasSequencePermission(): Boolean {
        try {
            val robotCls = Class.forName("com.robotemi.sdk.Robot")
            val robot = robotCls.getMethod("getInstance").invoke(null)
            val permCls = Class.forName("com.robotemi.sdk.Permission")
            val seq = permCls.getField("SEQUENCE").get(null) as Any
            val check = runCatching { robot.javaClass.getMethod("checkSelfPermission", permCls) }.getOrNull()
                ?: runCatching { robot.javaClass.getMethod("hasPermission", permCls) }.getOrNull()
            if (check != null) {
                val res = check.invoke(robot, seq)
                return when (res) {
                    is java.lang.Boolean -> res.booleanValue()
                    is java.lang.Integer -> res.toInt() != 0
                    else -> {
                        val grantStatusCls = runCatching { Class.forName("com.robotemi.sdk.Permission\$GrantStatus") }.getOrNull()
                        val granted = runCatching { grantStatusCls?.getField("GRANTED")?.get(null) }.getOrNull()
                        granted != null && res == granted
                    }
                }
            }
        } catch (_: Throwable) { }
        return false
    }

    fun isSequencePermissionGranted(): Boolean = hasSequencePermission()

    // --- Backward-compat wrappers used by MainActivity test buttons ---
    // Keep these method names to avoid unresolved references in UI code.
    fun hasSequencePermissionDirect(): Boolean = hasSequencePermission()

    fun requestSeqPerm_ListWithCode(): Boolean = requestSequencePermission()
    fun requestSeqPerm_List(): Boolean = requestSequencePermission()
    fun requestSeqPerm_ArrayWithCode(): Boolean = requestSequencePermission()
    fun requestSeqPerm_ActivityListWithCode(activity: Activity): Boolean = requestSequencePermission(activity)
    fun requestSeqPerm_ActivityList(activity: Activity): Boolean = requestSequencePermission(activity)
    fun requestSeqPerm_ActivityArrayWithCode(activity: Activity): Boolean = requestSequencePermission(activity)

    fun requestSequencePermission(): Boolean {
        if (hasSequencePermission()) return true
        try {
            val robotCls = Class.forName("com.robotemi.sdk.Robot")
            val robot = robotCls.getMethod("getInstance").invoke(null)
            val permCls = Class.forName("com.robotemi.sdk.Permission")
            val seq = permCls.getField("SEQUENCE").get(null) as Any
            val list = java.util.ArrayList<Any>()
            list.add(seq)
            val reqWithCode = runCatching { robot.javaClass.getMethod("requestPermissions", java.util.List::class.java, Int::class.javaPrimitiveType) }.getOrNull()
            if (reqWithCode != null) {
                reqWithCode.invoke(robot, list, 1001)
                return true
            }
            val reqNoCode = runCatching { robot.javaClass.getMethod("requestPermissions", java.util.List::class.java) }.getOrNull()
            if (reqNoCode != null) {
                reqNoCode.invoke(robot, list)
                return true
            }
        } catch (_: Throwable) { }

        val robot = robotInstance() ?: return false
        return try {
            val permClass = Class.forName("com.robotemi.sdk.Permission")
            val seqValue = permClass.getField("SEQUENCE").get(null) as Any
            val list = java.util.ArrayList<Any>()
            list.add(seqValue)
            val reqWithCode = runCatching { robot.javaClass.getMethod("requestPermissions", java.util.List::class.java, Int::class.javaPrimitiveType) }.getOrNull()
            if (reqWithCode != null) {
                reqWithCode.invoke(robot, list, 1001)
                return true
            }
            val reqNoCode = runCatching { robot.javaClass.getMethod("requestPermissions", java.util.List::class.java) }.getOrNull()
            if (reqNoCode != null) {
                reqNoCode.invoke(robot, list)
                return true
            }
            val permsArray = java.lang.reflect.Array.newInstance(permClass, 1)
            java.lang.reflect.Array.set(permsArray, 0, seqValue as Any)
            val reqArr = runCatching { robot.javaClass.getMethod("requestPermissions", permsArray.javaClass, Int::class.javaPrimitiveType) }.getOrNull()
            if (reqArr != null) {
                reqArr.invoke(robot, permsArray, 1001)
                return true
            }
            false
        } catch (t: Throwable) {
            Log.w(TAG, "requestSequencePermission fallo: ${t.message}")
            false
        }
    }

    fun requestSequencePermission(activity: Activity): Boolean {
        if (hasSequencePermission()) return true
        val robot = robotInstance() ?: return false
        return try {
            ensurePermissionListener()
            val permClass = Class.forName("com.robotemi.sdk.Permission")
            val seqValue = permClass.getField("SEQUENCE").get(null) as Any
            val list = java.util.ArrayList<Any>()
            list.add(seqValue)
            val m1 = runCatching { robot.javaClass.getMethod("requestPermissions", Activity::class.java, java.util.List::class.java, Int::class.javaPrimitiveType) }.getOrNull()
            if (m1 != null) {
                m1.invoke(robot, activity, list, 1001)
                return true
            }
            val m2 = runCatching { robot.javaClass.getMethod("requestPermissions", Activity::class.java, java.util.List::class.java) }.getOrNull()
            if (m2 != null) {
                m2.invoke(robot, activity, list)
                return true
            }
            val arr = java.lang.reflect.Array.newInstance(permClass, 1)
            java.lang.reflect.Array.set(arr, 0, seqValue as Any)
            val m3 = runCatching { robot.javaClass.getMethod("requestPermissions", Activity::class.java, arr.javaClass, Int::class.javaPrimitiveType) }.getOrNull()
            if (m3 != null) {
                m3.invoke(robot, activity, arr, 1001)
                return true
            }
            false
        } catch (t: Throwable) {
            Log.w(TAG, "requestSequencePermission(activity) fallo: ${t.message}")
            false
        }
    }

    fun listSequenceNames(): List<String> {
        if (!hasSequencePermission()) return emptyList()
        val robot = robotInstance() ?: return emptyList()
        return try {
            val getAllSequences = robot.javaClass.getMethod("getAllSequences")
            val sequences = getAllSequences.invoke(robot) as? List<*>
            if (sequences.isNullOrEmpty()) return emptyList()
            sequences.mapNotNull { s -> safeInvokeString(s, "getName") }
        } catch (t: Throwable) {
            Log.w(TAG, "listSequenceNames fallo: ${t.message}")
            emptyList()
        }
    }

    fun playSequenceById(sequenceId: String): Boolean {
        val robot = robotInstance() ?: return false
        return try {
            val playSequence = robot.javaClass.getMethod("playSequence", String::class.java)
            val result = playSequence.invoke(robot, sequenceId) as? Int
            val ok = (result == 0)
            if (!ok) Log.w(TAG, "playSequence result: $result for id=$sequenceId")
            ok
        } catch (t: Throwable) {
            Log.w(TAG, "playSequenceById fallo: ${t.message}")
            false
        }
    }

    fun playSequenceByName(name: String): Boolean {
        if (!hasSequencePermission()) return false
        val robot = robotInstance() ?: return false
        return try {
            val getAllSequences = robot.javaClass.getMethod("getAllSequences")
            val sequences = getAllSequences.invoke(robot) as? List<*>
            if (sequences.isNullOrEmpty()) return false
            var matchedId: String? = null
            for (s in sequences) {
                val sName = safeInvokeString(s, "getName")
                if (sName != null && sName.equals(name, ignoreCase = true)) {
                    matchedId = safeInvokeString(s, "getId")
                    break
                }
            }
            if (matchedId.isNullOrBlank()) return false
            playSequenceById(matchedId)
        } catch (t: Throwable) {
            Log.w(TAG, "playSequenceByName fallo: ${t.message}")
            false
        }
    }

    fun controlSequence(action: String): Boolean {
        if (!hasSequencePermission()) return false
        val robot = robotInstance() ?: return false
        return try {
            val enumCls = Class.forName("com.robotemi.sdk.sequence.SequenceCommand")
            val values = enumCls.getMethod("values").invoke(null) as Array<*>
            val target = values.firstOrNull { it.toString().equals(action, ignoreCase = true) } ?: return false
            val method = robot.javaClass.getMethod("controlSequence", enumCls)
            method.invoke(robot, target)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "controlSequence fallo: ${t.message}")
            false
        }
    }

    // Optional: permission result listener for logs
    fun ensurePermissionListener(): Boolean {
        val robot = robotInstance() ?: return false
        return try {
            val listenerCls = Class.forName("com.robotemi.sdk.permission.OnRequestPermissionResultListener")
            if (permListenerProxy == null) {
                permListenerProxy = Proxy.newProxyInstance(
                    listenerCls.classLoader,
                    arrayOf(listenerCls),
                    InvocationHandler { _, method, args ->
                        try {
                            if (method.name.equals("onRequestPermissionResult", true)) {
                                val perm = args?.get(0)
                                val grantRes = args?.get(1)
                                val reqCode = args?.get(2)
                                Log.d(TAG, "[PermResult] perm=$perm grant=$grantRes req=$reqCode")
                            }
                        } catch (_: Throwable) {}
                        null
                    }
                )
                val add = robot.javaClass.getMethod("addOnRequestPermissionResultListener", listenerCls)
                add.invoke(robot, permListenerProxy)
            }
            true
        } catch (t: Throwable) {
            Log.w(TAG, "No pude registrar OnRequestPermissionResultListener: ${t.message}")
            false
        }
    }
}
