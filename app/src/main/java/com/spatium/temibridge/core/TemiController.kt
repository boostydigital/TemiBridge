package com.spatium.deamon.db.temi.core

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
        Log.d(TAG, "[CALLBACK] Configurando nuevo callback de llegada")
        pendingArrival = callback
        ensureGoToListener()
        Log.d(TAG, "[CALLBACK] pendingArrival ahora está SET")
    }

    fun clearArrivalCallback() {
        pendingArrival = null
    }

    private fun ensureGoToListener(): Boolean {
        val robot = robotInstance() ?: return false
        return try {
            val listenerCls = Class.forName("com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener")
            if (goToListenerProxy == null) {
                Log.d(TAG, "[LISTENER] Registrando OnGoToLocationStatusChangedListener...")
                goToListenerProxy = Proxy.newProxyInstance(
                    listenerCls.classLoader,
                    arrayOf(listenerCls),
                    InvocationHandler { _, method, args ->
                        try {
                            // Log de TODOS los métodos que llegan al proxy
                            Log.d(TAG, "[LISTENER] Método llamado: ${method.name}, args=${args?.contentToString()}")
                            
                            if (method.name == "onGoToLocationStatusChanged" && args != null && args.size >= 2) {
                                val location = args[0]?.toString() ?: ""
                                val statusStr = args[1]?.toString()?.uppercase() ?: ""
                                val descId = if (args.size >= 3) {
                                    when (val v = args[2]) {
                                        is Int -> v
                                        is java.lang.Integer -> v.toInt()
                                        else -> -1
                                    }
                                } else -1
                                
                                Log.d(TAG, "[GOTO] location=$location, status=$statusStr, descId=$descId, pending=${if (pendingArrival != null) "YES" else "NO"}")
                                
                                // Detectar llegada: COMPLETE o descId 500
                                // El SDK de Temi usa "COMPLETE" cuando llega al destino
                                val isComplete = statusStr == "COMPLETE" || descId == 500
                                
                                if (isComplete && pendingArrival != null) {
                                    Log.d(TAG, "[GOTO] *** LLEGADA DETECTADA *** Ejecutando arrivalCallback...")
                                    val callback = pendingArrival
                                    pendingArrival = null // Limpiar ANTES de ejecutar para evitar doble ejecución
                                    callback?.invoke()
                                }
                            }
                        } catch (e: Throwable) {
                            Log.e(TAG, "[LISTENER] Error en callback: ${e.message}")
                        }
                        null
                    }
                )
                val addMethod = robot.javaClass.getMethod("addOnGoToLocationStatusChangedListener", listenerCls)
                addMethod.invoke(robot, goToListenerProxy)
                Log.d(TAG, "[LISTENER] Listener registrado exitosamente")
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
            Log.d(TAG, "getSavedLocations raw: $namesAny")
            val names = (namesAny as? List<*>)?.mapNotNull { it?.toString()?.trim() }?.filter { it.isNotEmpty() }
                ?: emptyList()
            Log.d(TAG, "getSavedLocations parsed: $names")

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
            val permCls = runCatching { Class.forName("com.robotemi.sdk.permission.Permission") }.getOrNull()
                ?: Class.forName("com.robotemi.sdk.Permission")
            val seq = permCls.getField("SEQUENCE").get(null) as Any
            val check = runCatching { robot.javaClass.getMethod("checkSelfPermission", permCls) }.getOrNull()
                ?: runCatching { robot.javaClass.getMethod("hasPermission", permCls) }.getOrNull()
            if (check != null) {
                val res = check.invoke(robot, seq)
                return when (res) {
                    is java.lang.Boolean -> res.booleanValue()
                    is java.lang.Integer -> res.toInt() != 0
                    else -> {
                        val grantStatusCls =
                            runCatching { Class.forName("com.robotemi.sdk.permission.Permission\$GrantStatus") }.getOrNull()
                                ?: runCatching { Class.forName("com.robotemi.sdk.Permission\$GrantStatus") }.getOrNull()
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
            val permCls = runCatching { Class.forName("com.robotemi.sdk.permission.Permission") }.getOrNull()
                ?: Class.forName("com.robotemi.sdk.Permission")
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
            val permClass = runCatching { Class.forName("com.robotemi.sdk.permission.Permission") }.getOrNull()
                ?: Class.forName("com.robotemi.sdk.Permission")
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
            val permClass = runCatching { Class.forName("com.robotemi.sdk.permission.Permission") }.getOrNull()
                ?: Class.forName("com.robotemi.sdk.Permission")
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

    fun playSequenceById(
        sequenceId: String,
        withPlayer: Boolean = true,
        repeat: Int = 0,
        startFromStep: Int = 1
    ): Boolean {
        Log.d(TAG, "=== playSequenceById INICIADO === ID: $sequenceId, withPlayer=$withPlayer, repeat=$repeat, startFromStep=$startFromStep")
        
        val robot = robotInstance()
        if (robot == null) {
            Log.e(TAG, "❌ Robot instance es null, no se puede ejecutar secuencia")
            return false
        }
        
        Log.d(TAG, "Robot instance obtenido: $robot")
        
        return try {
            Log.d(TAG, "Buscando método playSequence con parámetros...")
            val mWithParams = runCatching {
                robot.javaClass.getMethod(
                    "playSequence",
                    String::class.java,
                    Boolean::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
            }.getOrNull()

            if (mWithParams != null) {
                Log.d(TAG, "✓ Método playSequence con 4 parámetros encontrado")
                Log.d(TAG, "Invocando: playSequence($sequenceId, $withPlayer, $repeat, $startFromStep)")
                val result = mWithParams.invoke(robot, sequenceId, withPlayer, repeat, startFromStep)
                Log.d(TAG, "Resultado de invocación: $result (tipo: ${result?.javaClass?.simpleName})")
                
                val ok = (result as? Int)?.let { it == 0 } ?: true
                if (!ok) {
                    Log.w(TAG, "⚠ playSequence retornó código no-cero: $result para id=$sequenceId")
                } else {
                    Log.d(TAG, "✓ playSequence ejecutado exitosamente")
                }
                ok
            } else {
                Log.d(TAG, "Método con 4 parámetros no encontrado, intentando método legacy...")
                val legacy = robot.javaClass.getMethod("playSequence", String::class.java)
                Log.d(TAG, "✓ Método playSequence(String) encontrado")
                Log.d(TAG, "Invocando: playSequence($sequenceId)")
                val result = legacy.invoke(robot, sequenceId) as? Int
                Log.d(TAG, "Resultado de invocación: $result")
                
                val ok = (result == 0)
                if (!ok) {
                    Log.w(TAG, "⚠ playSequence retornó código no-cero: $result para id=$sequenceId")
                } else {
                    Log.d(TAG, "✓ playSequence ejecutado exitosamente")
                }
                ok
            }
        } catch (t: Throwable) {
            Log.e(TAG, "❌ playSequenceById fallo con excepción: ${t.message}", t)
            Log.e(TAG, "Stack trace completo:")
            t.printStackTrace()
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

    // --- Face Tracking Permissions ---
    fun hasFaceRecognitionPermission(): Boolean {
        try {
            val robotCls = Class.forName("com.robotemi.sdk.Robot")
            val robot = robotCls.getMethod("getInstance").invoke(null)
            val permCls = runCatching { Class.forName("com.robotemi.sdk.permission.Permission") }.getOrNull()
                ?: Class.forName("com.robotemi.sdk.Permission")
            
            // Intentar obtener el permiso FACE_RECOGNITION
            val faceRecognition = runCatching { permCls.getField("FACE_RECOGNITION").get(null) }.getOrNull()
                ?: runCatching { permCls.getField("FACE").get(null) }.getOrNull()
            
            if (faceRecognition != null) {
                val check = runCatching { robot.javaClass.getMethod("checkSelfPermission", permCls) }.getOrNull()
                    ?: runCatching { robot.javaClass.getMethod("hasPermission", permCls) }.getOrNull()
                
                if (check != null) {
                    val res = check.invoke(robot, faceRecognition)
                    return when (res) {
                        is java.lang.Boolean -> res.booleanValue()
                        is java.lang.Integer -> res.toInt() != 0
                        else -> {
                            val grantStatusCls = runCatching { Class.forName("com.robotemi.sdk.permission.Permission\$GrantStatus") }.getOrNull()
                                ?: runCatching { Class.forName("com.robotemi.sdk.Permission\$GrantStatus") }.getOrNull()
                            val granted = runCatching { grantStatusCls?.getField("GRANTED")?.get(null) }.getOrNull()
                            granted != null && res == granted
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "hasFaceRecognitionPermission fallo: ${t.message}")
        }
        return false
    }

    fun requestFaceRecognitionPermission(): Boolean {
        if (hasFaceRecognitionPermission()) return true
        val robot = robotInstance() ?: return false
        return try {
            Log.d(TAG, "Solicitando permiso de reconocimiento facial...")
            val permClass = runCatching { Class.forName("com.robotemi.sdk.permission.Permission") }.getOrNull()
                ?: Class.forName("com.robotemi.sdk.Permission")
            
            val faceRecognition = runCatching { permClass.getField("FACE_RECOGNITION").get(null) }.getOrNull()
                ?: runCatching { permClass.getField("FACE").get(null) }.getOrNull()
            
            if (faceRecognition != null) {
                val list = java.util.ArrayList<Any>()
                list.add(faceRecognition)
                
                val reqWithCode = runCatching { robot.javaClass.getMethod("requestPermissions", java.util.List::class.java, Int::class.javaPrimitiveType) }.getOrNull()
                if (reqWithCode != null) {
                    reqWithCode.invoke(robot, list, 1002)
                    Log.d(TAG, "✓ Permiso de reconocimiento facial solicitado")
                    return true
                }
                
                val reqNoCode = runCatching { robot.javaClass.getMethod("requestPermissions", java.util.List::class.java) }.getOrNull()
                if (reqNoCode != null) {
                    reqNoCode.invoke(robot, list)
                    Log.d(TAG, "✓ Permiso de reconocimiento facial solicitado")
                    return true
                }
            }
            Log.w(TAG, "No se pudo solicitar permiso de reconocimiento facial")
            false
        } catch (t: Throwable) {
            Log.e(TAG, "requestFaceRecognitionPermission fallo: ${t.message}", t)
            false
        }
    }

    // --- Face Tracking & Head Orientation ---
    fun enableFaceTracking(): Boolean {
        val robot = robotInstance() ?: return false
        return try {
            Log.d(TAG, "=== HABILITANDO FACE TRACKING CON constraintBeWith ===")
            Log.d(TAG, "Robot instance: $robot")
            Log.d(TAG, "Robot class: ${robot.javaClass.name}")
            
            // constraintBeWith() - robot gira y tilta en su eje hacia el usuario sin moverse
            // No requiere permisos especiales. Disponible desde SDK 0.10.53
            Log.d(TAG, "Buscando método constraintBeWith()...")
            val constraintBeWithMethod = robot.javaClass.getMethod("constraintBeWith")
            Log.d(TAG, "Método encontrado: $constraintBeWithMethod")
            
            Log.d(TAG, "Invocando constraintBeWith()...")
            constraintBeWithMethod.invoke(robot)
            Log.d(TAG, "✓ constraintBeWith() ejecutado - Robot se orienta hacia el usuario")
            
            Log.d(TAG, "✓✓✓ FACE TRACKING HABILITADO COMPLETAMENTE ✓✓✓")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "❌ enableFaceTracking fallo: ${t.message}", t)
            Log.e(TAG, "Stack trace:", t)
            t.printStackTrace()
            false
        }
    }

    fun disableFaceTracking(): Boolean {
        val robot = robotInstance() ?: return false
        return try {
            Log.d(TAG, "Deshabilitando face tracking con stopMovement...")
            
            // stopMovement() detiene constraintBeWith y cualquier movimiento activo
            val stopMovementMethod = robot.javaClass.getMethod("stopMovement")
            stopMovementMethod.invoke(robot)
            Log.d(TAG, "✓ stopMovement() ejecutado")
            
            // 2. Detener reconocimiento facial
            val stopFaceRecognitionMethod = robot.javaClass.getMethod("stopFaceRecognition")
            stopFaceRecognitionMethod.invoke(robot)
            Log.d(TAG, "✓ stopFaceRecognition() ejecutado")
            
            Log.d(TAG, "✓ Face tracking deshabilitado completamente")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "❌ disableFaceTracking fallo: ${t.message}", t)
            false
        }
    }

    fun tiltHead(angle: Float): Boolean {
        val robot = robotInstance() ?: return false
        return try {
            Log.d(TAG, "Intentando inclinar cabeza: $angle grados...")
            
            // Intentar con Float primero, luego con Double
            var method = runCatching { robot.javaClass.getMethod("tiltHead", Float::class.javaPrimitiveType) }.getOrNull()
            if (method == null) {
                method = runCatching { robot.javaClass.getMethod("tiltHead", Double::class.javaPrimitiveType) }.getOrNull()
            }
            
            if (method != null) {
                method.invoke(robot, angle)
                Log.d(TAG, "✓ Cabeza inclinada: $angle grados")
                true
            } else {
                Log.w(TAG, "⚠ No se encontró método para inclinar cabeza")
                logAvailableMethods(robot)
                false
            }
        } catch (t: Throwable) {
            Log.e(TAG, "❌ tiltHead fallo: ${t.message}", t)
            false
        }
    }

    fun turnHead(angle: Float): Boolean {
        val robot = robotInstance() ?: return false
        return try {
            Log.d(TAG, "Intentando girar cabeza: $angle grados...")
            
            var method = runCatching { robot.javaClass.getMethod("turnHead", Float::class.javaPrimitiveType) }.getOrNull()
            if (method == null) {
                method = runCatching { robot.javaClass.getMethod("turnHead", Double::class.javaPrimitiveType) }.getOrNull()
            }
            
            if (method != null) {
                method.invoke(robot, angle)
                Log.d(TAG, "✓ Cabeza girada: $angle grados")
                true
            } else {
                Log.w(TAG, "⚠ No se encontró método para girar cabeza")
                false
            }
        } catch (t: Throwable) {
            Log.e(TAG, "❌ turnHead fallo: ${t.message}", t)
            false
        }
    }

    fun turnByAngle(angle: Float): Boolean {
        val robot = robotInstance() ?: return false
        return try {
            Log.d(TAG, "Intentando girar robot: $angle grados...")
            
            var method = runCatching { robot.javaClass.getMethod("turnByAngle", Float::class.javaPrimitiveType) }.getOrNull()
            if (method == null) {
                method = runCatching { robot.javaClass.getMethod("turnByAngle", Double::class.javaPrimitiveType) }.getOrNull()
            }
            
            if (method != null) {
                method.invoke(robot, angle)
                Log.d(TAG, "✓ Robot girado: $angle grados")
                true
            } else {
                Log.w(TAG, "⚠ No se encontró método para girar robot")
                false
            }
        } catch (t: Throwable) {
            Log.e(TAG, "❌ turnByAngle fallo: ${t.message}", t)
            false
        }
    }

    private fun logAvailableMethods(robot: Any) {
        try {
            val methods = robot.javaClass.methods
            Log.d(TAG, "=== MÉTODOS DISPONIBLES EN ROBOT ===")
            methods.filter { it.name.contains("track", ignoreCase = true) || 
                            it.name.contains("face", ignoreCase = true) ||
                            it.name.contains("head", ignoreCase = true) ||
                            it.name.contains("turn", ignoreCase = true) ||
                            it.name.contains("tilt", ignoreCase = true) }
                .forEach { method ->
                    Log.d(TAG, "  - ${method.name}(${method.parameterTypes.joinToString { it.simpleName }})")
                }
        } catch (t: Throwable) {
            Log.w(TAG, "No se pudieron listar métodos: ${t.message}")
        }
    }

    // --- Patrol Mode (SDK 1.129.1+) ---
    
    /**
     * Inicia modo patrullaje visitando las ubicaciones en loop.
     * @param locations Lista de waypoints (mínimo 3)
     * @param nonstop Si true, no espera en cada ubicación
     * @param times Número de repeticiones (0 = infinito)
     * @param waiting Segundos de espera en cada ubicación (3-60)
     * @return true si el patrullaje inició correctamente
     */
    fun patrol(
        locations: List<String>,
        nonstop: Boolean = false,
        times: Int = 0,
        waiting: Int = 10
    ): Boolean {
        val robot = robotInstance() ?: return false
        return try {
            Log.d(TAG, "=== INICIANDO PATROL ===")
            Log.d(TAG, "Locations: ${locations.joinToString()}")
            Log.d(TAG, "nonstop=$nonstop, times=$times, waiting=$waiting")
            
            ensureGoToListener()
            
            val patrolMethod = robot.javaClass.getMethod(
                "patrol",
                java.util.List::class.java,
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            
            val result = patrolMethod.invoke(robot, locations, nonstop, times, waiting)
            val success = (result as? Boolean) ?: true
            
            if (success) {
                Log.d(TAG, "✓ Patrol iniciado exitosamente")
            } else {
                Log.w(TAG, "⚠ Patrol retornó false")
            }
            success
        } catch (t: Throwable) {
            Log.e(TAG, "❌ patrol fallo: ${t.message}", t)
            false
        }
    }

    /**
     * Detiene cualquier movimiento activo (goTo, patrol, follow, etc.)
     */
    fun stopMovement(): Boolean {
        val robot = robotInstance() ?: return false
        return try {
            Log.d(TAG, "Deteniendo movimiento...")
            val stopMethod = robot.javaClass.getMethod("stopMovement")
            stopMethod.invoke(robot)
            Log.d(TAG, "✓ Movimiento detenido")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "❌ stopMovement fallo: ${t.message}", t)
            false
        }
    }

    // --- Speed Control (SDK 0.10.70+) ---
    
    /**
     * Enum para niveles de velocidad de navegación.
     * VERY_HIGH y VERY_SLOW disponibles desde SDK 1.137.1
     */
    enum class SpeedLevel {
        VERY_HIGH, HIGH, MEDIUM, SLOW, VERY_SLOW
    }

    /**
     * Obtiene el nivel de velocidad actual de navegación.
     * @return SpeedLevel actual o null si falla
     */
    fun getGoToSpeed(): SpeedLevel? {
        val robot = robotInstance() ?: return null
        return try {
            val getMethod = robot.javaClass.getMethod("getGoToSpeed")
            val result = getMethod.invoke(robot)
            val speedName = result?.toString()?.uppercase() ?: return null
            SpeedLevel.values().find { it.name == speedName }
        } catch (t: Throwable) {
            Log.w(TAG, "getGoToSpeed fallo: ${t.message}")
            null
        }
    }

    /**
     * Configura el nivel de velocidad de navegación.
     * Requiere permiso SETTINGS.
     * @param level Nivel de velocidad deseado
     * @return true si se configuró correctamente
     */
    fun setGoToSpeed(level: SpeedLevel): Boolean {
        val robot = robotInstance() ?: return false
        return try {
            Log.d(TAG, "Configurando velocidad: $level")
            
            val speedLevelCls = Class.forName("com.robotemi.sdk.navigation.model.SpeedLevel")
            val speedValue = speedLevelCls.getField(level.name).get(null)
            
            val setMethod = robot.javaClass.getMethod("setGoToSpeed", speedLevelCls)
            setMethod.invoke(robot, speedValue)
            
            Log.d(TAG, "✓ Velocidad configurada: $level")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "❌ setGoToSpeed fallo: ${t.message}", t)
            false
        }
    }

    // --- Settings Permission ---
    
    fun hasSettingsPermission(): Boolean {
        try {
            val robotCls = Class.forName("com.robotemi.sdk.Robot")
            val robot = robotCls.getMethod("getInstance").invoke(null)
            val permCls = runCatching { Class.forName("com.robotemi.sdk.permission.Permission") }.getOrNull()
                ?: Class.forName("com.robotemi.sdk.Permission")
            
            val settings = runCatching { permCls.getField("SETTINGS").get(null) }.getOrNull()
            
            if (settings != null) {
                val check = runCatching { robot.javaClass.getMethod("checkSelfPermission", permCls) }.getOrNull()
                    ?: runCatching { robot.javaClass.getMethod("hasPermission", permCls) }.getOrNull()
                
                if (check != null) {
                    val res = check.invoke(robot, settings)
                    return when (res) {
                        is java.lang.Boolean -> res.booleanValue()
                        is java.lang.Integer -> res.toInt() != 0
                        else -> {
                            val grantStatusCls = runCatching { Class.forName("com.robotemi.sdk.permission.Permission\$GrantStatus") }.getOrNull()
                                ?: runCatching { Class.forName("com.robotemi.sdk.Permission\$GrantStatus") }.getOrNull()
                            val granted = runCatching { grantStatusCls?.getField("GRANTED")?.get(null) }.getOrNull()
                            granted != null && res == granted
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "hasSettingsPermission fallo: ${t.message}")
        }
        return false
    }

    fun requestSettingsPermission(): Boolean {
        if (hasSettingsPermission()) return true
        val robot = robotInstance() ?: return false
        return try {
            Log.d(TAG, "Solicitando permiso SETTINGS...")
            val permClass = runCatching { Class.forName("com.robotemi.sdk.permission.Permission") }.getOrNull()
                ?: Class.forName("com.robotemi.sdk.Permission")
            
            val settings = runCatching { permClass.getField("SETTINGS").get(null) }.getOrNull()
            
            if (settings != null) {
                val list = java.util.ArrayList<Any>()
                list.add(settings)
                
                val reqWithCode = runCatching { robot.javaClass.getMethod("requestPermissions", java.util.List::class.java, Int::class.javaPrimitiveType) }.getOrNull()
                if (reqWithCode != null) {
                    reqWithCode.invoke(robot, list, 1003)
                    Log.d(TAG, "✓ Permiso SETTINGS solicitado")
                    return true
                }
                
                val reqNoCode = runCatching { robot.javaClass.getMethod("requestPermissions", java.util.List::class.java) }.getOrNull()
                if (reqNoCode != null) {
                    reqNoCode.invoke(robot, list)
                    Log.d(TAG, "✓ Permiso SETTINGS solicitado")
                    return true
                }
            }
            Log.w(TAG, "No se pudo solicitar permiso SETTINGS")
            false
        } catch (t: Throwable) {
            Log.e(TAG, "requestSettingsPermission fallo: ${t.message}", t)
            false
        }
    }

    /**
     * Obtiene la lista de ubicaciones guardadas en el robot.
     * Útil para validar waypoints antes de iniciar patrol.
     */
    fun getLocations(): List<String> {
        return getSavedLocations().map { it.name }
    }

    // --- UI Control ---
    
    /**
     * Oculta la barra superior de Temi.
     */
    fun hideTopBar(): Boolean {
        val robot = robotInstance() ?: return false
        return try {
            val method = robot.javaClass.getMethod("hideTopBar")
            method.invoke(robot)
            Log.d(TAG, "✓ TopBar ocultada")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "hideTopBar fallo: ${t.message}")
            false
        }
    }

    /**
     * Muestra la barra superior de Temi.
     */
    fun showTopBar(): Boolean {
        val robot = robotInstance() ?: return false
        return try {
            val method = robot.javaClass.getMethod("showTopBar")
            method.invoke(robot)
            Log.d(TAG, "✓ TopBar mostrada")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "showTopBar fallo: ${t.message}")
            false
        }
    }

    /**
     * Configura si se muestra el billboard de navegación ("Yendo a...").
     * @param hide true para ocultar, false para mostrar
     */
    fun setGoToBillboardDisabled(hide: Boolean): Boolean {
        val robot = robotInstance() ?: return false
        return try {
            // Método: setGoToBillboardDisabled(boolean)
            val method = robot.javaClass.getMethod("setGoToBillboardDisabled", Boolean::class.javaPrimitiveType)
            method.invoke(robot, hide)
            Log.d(TAG, "✓ GoToBillboard disabled: $hide")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "setGoToBillboardDisabled fallo: ${t.message}")
            false
        }
    }

    /**
     * Activa el modo kiosk que oculta elementos de UI de Temi.
     * Requiere que la app esté configurada como Kiosk en la configuración del robot.
     */
    fun setKioskModeOn(on: Boolean): Boolean {
        val robot = robotInstance() ?: return false
        return try {
            val method = robot.javaClass.getMethod("setKioskModeOn", Boolean::class.javaPrimitiveType)
            method.invoke(robot, on)
            Log.d(TAG, "✓ Kiosk mode: $on")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "setKioskModeOn fallo: ${t.message}")
            false
        }
    }

    /**
     * Verifica si la app está configurada como Kiosk.
     */
    fun isKioskModeOn(): Boolean {
        val robot = robotInstance() ?: return false
        return try {
            val method = robot.javaClass.getMethod("isKioskModeOn")
            val result = method.invoke(robot)
            (result as? Boolean) ?: false
        } catch (t: Throwable) {
            Log.w(TAG, "isKioskModeOn fallo: ${t.message}")
            false
        }
    }

    /**
     * Configura la visibilidad del billboard de navegación.
     * @param disabled true para ocultar "Yendo a...", false para mostrar
     */
    fun toggleNavigationBillboard(disabled: Boolean): Boolean {
        val robot = robotInstance() ?: return false
        return try {
            // Intentar con toggleNavigationBillboard primero
            val method = robot.javaClass.getMethod("toggleNavigationBillboard", Boolean::class.javaPrimitiveType)
            method.invoke(robot, disabled)
            Log.d(TAG, "✓ Navigation billboard disabled: $disabled")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "toggleNavigationBillboard fallo: ${t.message}")
            false
        }
    }

    // --- Volume Control ---
    
    /**
     * Obtiene el volumen actual del robot (0-10).
     */
    fun getVolume(): Int? {
        val robot = robotInstance() ?: return null
        return try {
            val getMethod = robot.javaClass.getMethod("getVolume")
            val result = getMethod.invoke(robot)
            (result as? Number)?.toInt()
        } catch (t: Throwable) {
            Log.w(TAG, "getVolume fallo: ${t.message}")
            null
        }
    }

    /**
     * Configura el volumen del robot (0-10).
     * @param level Nivel de volumen (0-10)
     * @return true si se configuró correctamente
     */
    fun setVolume(level: Int): Boolean {
        val robot = robotInstance() ?: return false
        val clampedLevel = level.coerceIn(0, 10)
        return try {
            Log.d(TAG, "Configurando volumen: $clampedLevel")
            val setMethod = robot.javaClass.getMethod("setVolume", Int::class.javaPrimitiveType)
            setMethod.invoke(robot, clampedLevel)
            Log.d(TAG, "✓ Volumen configurado: $clampedLevel")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "❌ setVolume fallo: ${t.message}", t)
            false
        }
    }
}
