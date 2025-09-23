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
                            if (method.name == "onGoToLocationStatusChanged" && args != null && args.size >= 3) {
                                val location = args[0]
                                val statusStr = args[1]?.toString() ?: ""
                                val descId = when (val v = args[2]) {
                                    is Int -> v
                                    is java.lang.Integer -> v.toInt()
                                    else -> -1
                                }
                                Log.d(TAG, "goTo status: ${'$'}statusStr (descId=${'$'}descId) at ${'$'}location target=${'$'}lastTarget")
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
                Log.d(TAG, "Listener de goTo registrado")
            }
            true
        } catch (t: Throwable) {
            Log.w(TAG, "No pude registrar listener goTo: ${t.message}")
            false
        }
    }

    // --- Sequences (temi Center) ---
    /**
     * Verifica si el permiso SEQUENCE está concedido para nuestra app.
     */
    fun hasSequencePermission(): Boolean {
        // Intentar vía SDK directo
        try {
            val robotCls = Class.forName("com.robotemi.sdk.Robot")
            val getInst = robotCls.getMethod("getInstance")
            val robot = getInst.invoke(null)
            val permCls = Class.forName("com.robotemi.sdk.Permission")
            val seq = permCls.getField("SEQUENCE").get(null)
            val check = robot.javaClass.getMethod("checkSelfPermission", permCls)
            val res = check.invoke(robot, seq)
            val isGranted = when (res) {
                is java.lang.Boolean -> res.booleanValue()
                is java.lang.Integer -> res.toInt() != 0
                else -> {
                    // Enum GrantStatus comparison
                    val grantStatusCls = Class.forName("com.robotemi.sdk.Permission\$GrantStatus")
                    val granted = grantStatusCls.getField("GRANTED").get(null)
                    res == granted
                }
            }
            Log.d(TAG, "hasSequencePermission(direct)=$isGranted")
            return isGranted
        } catch (_: Throwable) { /* fallback abajo */ }

        val robot = robotInstance() ?: return false
        return try {
            val permClass = Class.forName("com.robotemi.sdk.Permission")
            val seqField = permClass.getField("SEQUENCE")
            val seqValue = seqField.get(null)
            val checkMethod = robot.javaClass.getMethod("checkSelfPermission", permClass)
            val grantStatusClass = Class.forName("com.robotemi.sdk.Permission\$GrantStatus")
            val grantedConst = grantStatusClass.getField("GRANTED").get(null)
            val res = checkMethod.invoke(robot, seqValue)
            val granted = when (res) {
                is java.lang.Boolean -> res.booleanValue()
                is java.lang.Integer -> res.toInt() != 0
                else -> (res == grantedConst)
            }
            Log.d(TAG, "hasSequencePermission(reflection)=$granted")
            granted
        } catch (t: Throwable) {
            Log.w(TAG, "hasSequencePermission fallo: ${t.message}")
            false
        }
    }

    fun requestSequencePermission(): Boolean {
        // Si ya está concedido, no solicitar de nuevo
        if (hasSequencePermission()) {
            Log.d(TAG, "requestSequencePermission: ya concedido")
            return true
        }
        // Intento directo SDK
        try {
            val robotCls = Class.forName("com.robotemi.sdk.Robot")
            val getInst = robotCls.getMethod("getInstance")
            val robot = getInst.invoke(null)
            val permCls = Class.forName("com.robotemi.sdk.Permission")
            val seq = permCls.getField("SEQUENCE").get(null)
            val arr = java.lang.reflect.Array.newInstance(permCls, 1)
            java.lang.reflect.Array.set(arr, 0, seq)
            val request = robot.javaClass.getMethod("requestPermissions", arr.javaClass, Int::class.javaPrimitiveType)
            request.invoke(robot, arr, 1001)
            Log.d(TAG, "requestSequencePermission enviado (direct)")
            return true
        } catch (_: Throwable) { /* fallback abajo */ }

        val robot = robotInstance() ?: return false
        return try {
            val permClass = Class.forName("com.robotemi.sdk.Permission")
            val seqField = permClass.getField("SEQUENCE")
            val seqValue = seqField.get(null)
            val permsArray = java.lang.reflect.Array.newInstance(permClass, 1)
            java.lang.reflect.Array.set(permsArray, 0, seqValue)
            // Obtener la clase de array de Permission adecuadamente para la firma vararg
            val request = robot.javaClass.getMethod("requestPermissions", permsArray.javaClass, Int::class.javaPrimitiveType)
            request.invoke(robot, permsArray, 1001)
            Log.d(TAG, "requestSequencePermission enviado (reflection)")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "requestSequencePermission fallo: ${t.message}")
            false
        }
    }

    fun listSequenceNames(): List<String> {
        if (!hasSequencePermission()) {
            Log.w(TAG, "listSequenceNames: permiso SEQUENCE no concedido")
            return emptyList()
        }
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
        val robot = robotInstance() ?: return false
        if (!hasSequencePermission()) {
            Log.w(TAG, "playSequenceByName: permiso SEQUENCE no concedido")
            return false
        }
        return try {
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
        if (!hasSequencePermission()) {
            Log.w(TAG, "controlSequence: permiso SEQUENCE no concedido")
            return false
        }
        return try {
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

    /**
     * Devuelve la lista de nombres de todas las sequences disponibles en el robot.
     * Intenta tanto la firma sin parámetros como la firma con lista vacía (según versión SDK).
     */
    fun getAllSequenceNames(): List<String> {
        val robot = robotInstance() ?: return emptyList()
        return try {
            // Intentar método sin parámetros: getAllSequences()
            val mNoArgs = runCatching { robot.javaClass.getMethod("getAllSequences") }.getOrNull()
            val sequencesAny: Any? = if (mNoArgs != null) {
                mNoArgs.invoke(robot)
            } else {
                // Intentar método con un parámetro List (filtros vacíos)
                val listCls = List::class.java
                val mWithList = robot.javaClass.getMethod("getAllSequences", listCls)
                mWithList.invoke(robot, emptyList<Any>())
            }
            val list = sequencesAny as? List<*> ?: return emptyList()
            // Mapear a nombre mediante reflexión (propiedad 'name')
            list.mapNotNull { item ->
                try {
                    val nameField = item?.javaClass?.getMethod("getName")
                    (nameField?.invoke(item) as? String)?.trim()?.takeIf { it.isNotEmpty() }
                } catch (_: Throwable) {
                    null
                }
            }
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
