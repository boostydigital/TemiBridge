package com.spatium.temibridge.core

import android.util.Log
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

object TemiController {
    private const val TAG = "TemiController"

    // One-time arrival callback handling
    private var pendingArrival: (() -> Unit)? = null
    private var goToListenerProxy: Any? = null
    private var lastTarget: String? = null

    private fun robotInstance(): Any? = try {
        val cls = Class.forName("com.robotemi.sdk.Robot")
        val method = cls.getMethod("getInstance")
        method.invoke(null)
    } catch (t: Throwable) {
        Log.w(TAG, "Robot SDK no disponible: ${t.message}")
        null
    }

    /**
     * Registra un callback que se ejecuta UNA sola vez cuando Temi reporta estado COMPLETE
     * tras un goTo. Implementado por reflexión para no acoplar en compilación.
     */
    fun setArrivalCallbackOnce(callback: () -> Unit) {
        Log.d(TAG, "setArrivalCallbackOnce llamado")
        pendingArrival = callback
        val ok = ensureGoToListener()
        Log.d(TAG, "ensureGoToListener resultado=$ok")
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
                            if (method.name == "onGoToLocationStatusChanged" && args != null && args.size >= 2) {
                                val location = args[0]
                                val status = args[1]
                                val desc = if (args.size >= 3) args[2] else null
                                val statusStr = status?.toString()
                                Log.d(TAG, "onGoToLocationStatusChanged loc=$location status=$statusStr desc=$desc target=$lastTarget pending=${pendingArrival != null}")
                                if (statusStr?.equals("COMPLETE", ignoreCase = true) == true) {
                                    if (pendingArrival != null) {
                                        Log.d(TAG, "Invocando pendingArrival por COMPLETE")
                                        pendingArrival?.invoke()
                                        pendingArrival = null
                                    } else {
                                        Log.d(TAG, "No hay pendingArrival al completar")
                                    }
                                }
                            }
                        } catch (_: Throwable) {}
                        null
                    }
                )
                val addMethod = robot.javaClass.getMethod("addOnGoToLocationStatusChangedListener", listenerCls)
                addMethod.invoke(robot, goToListenerProxy)
                Log.d(TAG, "Listener de goTo registrado")
            }
            true
        } catch (t: Throwable) {
            Log.w(TAG, "No pude registrar listener goTo: ${t.message}")
            false
        }
    }

    // --- Sequences (temi Center) ---
    fun requestSequencePermission(): Boolean {
        val robot = robotInstance() ?: return false
        return try {
            val permClass = Class.forName("com.robotemi.sdk.Permission")
            val seqField = permClass.getField("SEQUENCE")
            val seqValue = seqField.get(null)
            val permsArray = java.lang.reflect.Array.newInstance(permClass, 1)
            java.lang.reflect.Array.set(permsArray, 0, seqValue)
            // Obtener la clase de array de Permission adecuadamente para la firma vararg
            val permArrayClass = java.lang.reflect.Array.newInstance(permClass, 0).javaClass
            val request = robot.javaClass.getMethod("requestPermissions", permArrayClass)
            request.invoke(robot, permsArray)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "requestSequencePermission fallo: ${t.message}")
            false
        }
    }

    fun listSequenceNames(): List<String> {
        val robot = robotInstance() ?: return emptyList()
        // Intenta solicitar permiso antes de leer
        requestSequencePermission()
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
        val robot = robotInstance() ?: return false
        return try {
            // Asegura permisos antes de consultar
            requestSequencePermission()
            val getAllSequences = robot.javaClass.getMethod("getAllSequences")
            val sequences = getAllSequences.invoke(robot) as? List<*>
            if (sequences.isNullOrEmpty()) {
                Log.w(TAG, "getAllSequences vacío o null")
                return false
            }
            var matchedId: String? = null
            for (s in sequences) {
                val sName = safeInvokeString(s, "getName")
                if (sName != null && sName.equals(name, ignoreCase = true)) {
                    matchedId = safeInvokeString(s, "getId")
                    break
                }
            }
            if (matchedId.isNullOrBlank()) {
                Log.w(TAG, "Sequence no encontrada por nombre: $name")
                return false
            }
            playSequenceById(matchedId)
        } catch (t: Throwable) {
            Log.w(TAG, "playSequenceByName fallo: ${t.message}")
            false
        }
    }

    fun controlSequence(action: String): Boolean {
        val robot = robotInstance() ?: return false
        return try {
            requestSequencePermission()
            val enumCls = Class.forName("com.robotemi.sdk.sequence.SequenceCommand")
            val values = enumCls.getMethod("values").invoke(null) as Array<*>
            val target = values.firstOrNull { it.toString().equals(action, ignoreCase = true) }
                ?: run {
                    Log.w(TAG, "SequenceCommand no válido: $action")
                    return false
                }
            val method = robot.javaClass.getMethod("controlSequence", enumCls)
            method.invoke(robot, target)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "controlSequence fallo: ${t.message}")
            false
        }
    }

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
            val ok = ensureGoToListener()
            Log.d(TAG, "goTo llamado place=$place listenerOk=$ok")
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

    // --- Tours (temi Center) ---
    // SDK docs expose: getAllTours(): List<TourModel> and playTour(tourId: String): Int (0 ok)
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
            if (tours.isNullOrEmpty()) {
                Log.w(TAG, "getAllTours vacío o null")
                return false
            }
            var matchedId: String? = null
            for (t in tours) {
                val tName = safeInvokeString(t, "getName")
                if (tName != null && tName.equals(name, ignoreCase = true)) {
                    matchedId = safeInvokeString(t, "getId")
                    break
                }
            }
            if (matchedId.isNullOrBlank()) {
                Log.w(TAG, "Tour no encontrado por nombre: $name")
                return false
            }
            playTourById(matchedId)
        } catch (t: Throwable) {
            Log.w(TAG, "playTourByName fallo: ${t.message}")
            false
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
}
