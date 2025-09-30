package com.spatium.temibridge.core

import android.app.Activity
import android.util.Log
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import kotlin.math.hypot

object TemiController {
    private const val TAG = "TemiController"

    // One-time arrival callback handling
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

    /**
     * Registra un callback que se ejecuta UNA sola vez cuando Temi reporta estado COMPLETE
     * tras un goTo. Implementado por reflexiÃ³n para no acoplar en compilaciÃ³n.
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

    // --- Locations & nearest selection ---
    private data class Pose(val x: Double, val y: Double)

    private fun getCurrentPose(): Pose? {
        val robot = robotInstance() ?: return null
        return try {
            // Try common signatures: getCurrentPosition() or getPosition()
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

    private data class LocationInfo(val name: String, val x: Double?, val y: Double?)

    private fun getSavedLocations(): List<LocationInfo> {
        val robot = robotInstance() ?: return emptyList()
        return try {
            // First, get list of names
            val namesAny = runCatching { robot.javaClass.getMethod("getAllLocations").invoke(robot) }.getOrNull()
                ?: runCatching { robot.javaClass.getMethod("getLocations").invoke(robot) }.getOrNull()
            val names = (namesAny as? List<*>)?.mapNotNull { it?.toString()?.trim() }?.filter { it.isNotEmpty() }
                ?: emptyList()

            if (names.isEmpty()) return emptyList()

            // Try to obtain coordinates per name via getLocationPosition(name) if available
            val posMethod = runCatching { robot.javaClass.getMethod("getLocationPosition", String::class.java) }.getOrNull()
            if (posMethod != null) {
                names.map { n ->
                    val pos = runCatching { posMethod.invoke(robot, n) }.getOrNull()
                    val x = safeInvokeDouble(pos, "getX")
                    val y = safeInvokeDouble(pos, "getY")
                    LocationInfo(n, x, y)
                }
            } else {
                // Coordinates not available via SDK; return names only
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
                is java.lang.Float -> v.toDouble()
                is Number -> v.toDouble()
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Intenta determinar la ubicaciÃ³n guardada mÃ¡s cercana al robot.
     * Requiere permiso de navegaciÃ³n implÃ­cito y que el SDK exponga posiciÃ³n/ubicaciones.
     * Si no hay coordenadas disponibles, devuelve null.
     */
    fun getNearestSavedLocationName(): String? {
        val pose = getCurrentPose() ?: run {
            Log.w(TAG, "getNearestSavedLocationName: sin pose actual")
            return null
        }
        val locs = getSavedLocations()
        if (locs.isEmpty()) return null

        var bestName: String? = null
        var bestDist = Double.MAX_VALUE
        for (loc in locs) {
            val lx = loc.x
            val ly = loc.y
            if (lx != null && ly != null) {
                val d = hypot(lx - pose.x, ly - pose.y)
                if (d < bestDist) {
                    bestDist = d
                    bestName = loc.name
                }
            }
        }
        if (bestName == null) {
            Log.w(TAG, "No hay coordenadas de ubicaciones; no se puede calcular la mÃ¡s cercana")
        } else {
            Log.d(TAG, "UbicaciÃ³n mÃ¡s cercana: $bestName (dist=${bestDist})")
        }
        return bestName
    }

    // --- Sequences (temi Center) ---
    /**
     * Verifica si el permiso SEQUENCE estÃ¡ concedido para nuestra app.
     */
    fun hasSequencePermission(): Boolean {
        // Intentar vÃ­a SDK directo
        try {
            val robotCls = Class.forName("com.robotemi.sdk.Robot")
            val getInst = robotCls.getMethod("getInstance")
            val robot = getInst.invoke(null)
            val permCls = Class.forName("com.robotemi.sdk.Permission")
            val seq = permCls.getField("SEQUENCE").get(null) as Any
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
            val seqValue = seqField.get(null) as Any
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
        // Si ya estÃ¡ concedido, no solicitar de nuevo
        if (hasSequencePermission()) {
            Log.d(TAG, "requestSequencePermission: ya concedido")
            return true
        }
        // Intento directo SDK (List<Permission>, con/sin requestCode)
        try {
            val robotCls = Class.forName("com.robotemi.sdk.Robot")
            val getInst = robotCls.getMethod("getInstance")
            val robot = getInst.invoke(null)
            val permCls = Class.forName("com.robotemi.sdk.Permission")
            val seq = permCls.getField("SEQUENCE").get(null)
            val list = java.util.ArrayList<Any>()
            list.add(seq)
            // Variante con requestCode
            val reqWithCode = runCatching { robot.javaClass.getMethod("requestPermissions", java.util.List::class.java, Int::class.javaPrimitiveType) }.getOrNull()
            if (reqWithCode != null) {
                reqWithCode.invoke(robot, list, 1001)
                Log.d(TAG, "requestSequencePermission enviado (List, con requestCode)")
                return true
            }
            // Variante sin requestCode
            val reqNoCode = runCatching { robot.javaClass.getMethod("requestPermissions", java.util.List::class.java) }.getOrNull()
            if (reqNoCode != null) {
                reqNoCode.invoke(robot, list)
                Log.d(TAG, "requestSequencePermission enviado (List, sin requestCode)")
                return true
            }
        } catch (_: Throwable) { /* fallback abajo */ }

        val robot = robotInstance() ?: return false
        return try {
            val permClass = Class.forName("com.robotemi.sdk.Permission")
            val seqField = permClass.getField("SEQUENCE")
            val seqValue = seqField.get(null)
            // Intentar con List en diferentes variantes mediante reflexión
            val list = java.util.ArrayList<Any>()
            list.add(seqValue)
            val reqWithCode = runCatching { robot.javaClass.getMethod("requestPermissions", java.util.List::class.java, Int::class.javaPrimitiveType) }.getOrNull()
            if (reqWithCode != null) {
                reqWithCode.invoke(robot, list, 1001)
                Log.d(TAG, "requestSequencePermission enviado (reflection List+code)")
                return true
            }
            val reqNoCode = runCatching { robot.javaClass.getMethod("requestPermissions", java.util.List::class.java) }.getOrNull()
            if (reqNoCode != null) {
                reqNoCode.invoke(robot, list)
                Log.d(TAG, "requestSequencePermission enviado (reflection List)")
                return true
            }
            // Último fallback: probar firma con array
            val permsArray = java.lang.reflect.Array.newInstance(permClass, 1)
            java.lang.reflect.Array.set(permsArray, 0, seqValue as Any)
            val reqArr = runCatching { robot.javaClass.getMethod("requestPermissions", permsArray.javaClass, Int::class.javaPrimitiveType) }.getOrNull()
            if (reqArr != null) {
                reqArr.invoke(robot, permsArray, 1001)
                Log.d(TAG, "requestSequencePermission enviado (reflection Array+code)")
                return true
            }
            false
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
                Log.w(TAG, "getAllSequences vacÃ­o o null")
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
                    Log.w(TAG, "SequenceCommand no vÃ¡lido: $action")
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
     * Intenta tanto la firma sin parÃ¡metros como la firma con lista vacÃ­a (segÃºn versiÃ³n SDK).
     */
    fun getAllSequenceNames(): List<String> {
        val robot = robotInstance() ?: return emptyList()
        return try {
            // Intentar mÃ©todo sin parÃ¡metros: getAllSequences()
            val mNoArgs = runCatching { robot.javaClass.getMethod("getAllSequences") }.getOrNull()
            val sequencesAny: Any? = if (mNoArgs != null) {
                mNoArgs.invoke(robot)
            } else {
                // Intentar mÃ©todo con un parÃ¡metro List (filtros vacÃ­os)
                val listCls = List::class.java
                val mWithList = robot.javaClass.getMethod("getAllSequences", listCls)
                mWithList.invoke(robot, emptyList<Any>())
            }
            val list = sequencesAny as? List<*> ?: return emptyList()
            // Mapear a nombre mediante reflexiÃ³n (propiedad 'name')
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
                Log.w(TAG, "getAllTours vacÃ­o o null")
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

    // Overload que acepta una Activity para mostrar correctamente el diÃ¡logo de permisos en Temi
    fun requestSequencePermission(activity: Activity): Boolean {
        if (hasSequencePermission()) {
            Log.d(TAG, "requestSequencePermission(activity): ya concedido")
            return true
        }
        val robot = robotInstance() ?: return false
        return try {
            val permClass = Class.forName("com.robotemi.sdk.Permission")
            val seqField = permClass.getField("SEQUENCE")
            val seqValue = seqField.get(null)

            // Construir argumentos auxiliares
            val permsArray = java.lang.reflect.Array.newInstance(permClass, 1)
            java.lang.reflect.Array.set(permsArray, 0, seqValue)

            // Buscamos cualquier mÃ©todo cuyo nombre empiece por requestPermission y que acepte
            // Activity opcionalmente y Permission (singular) o Permission[] y opcional requestCode / listener.
            val methods = robot.javaClass.methods.filter { it.name.startsWith("requestPermission") }
                .ifEmpty { robot.javaClass.methods.filter { it.name.startsWith("requestPermissions") } }

            for (m in methods) {
                try {
                    val params = m.parameterTypes
                    if (params.isEmpty()) continue
                    val args = arrayOfNulls<Any>(params.size)
                    var filled = true
                    for ((idx, p) in params.withIndex()) {
                        when {
                            Activity::class.java.isAssignableFrom(p) -> args[idx] = activity
                            p.isArray && p.componentType?.name?.contains("Permission") == true -> args[idx] = permsArray
                            p.name.contains("Permission") -> args[idx] = seqValue
                            (p == Int::class.javaPrimitiveType) || (p == Integer::class.java) -> args[idx] = 1001
                            p.name.contains("Listener", ignoreCase = true) || p.name.contains("Callback", ignoreCase = true) -> {
                                // Crear un proxy vacÃ­o que simplemente loguea
                                val proxy = java.lang.reflect.Proxy.newProxyInstance(
                                    p.classLoader,
                                    arrayOf(p)
                                ) { _, _, _ -> null }
                                args[idx] = proxy
                            }
                            else -> {
                                filled = false
                                break
                            }
                        }
                    }
                    if (!filled) continue
                    m.isAccessible = true
                    m.invoke(robot, *args)
                    Log.d(TAG, "requestSequencePermission enviado mediante ${m.name} con firma ${params.joinToString { it.simpleName }}")
                    return true
                } catch (_: Throwable) {
                    // probar siguiente
                }
            }

            // Fallback a firmas conocidas especÃ­ficas
            runCatching {
                val known = robot.javaClass.getMethod("requestPermissions", Activity::class.java, permsArray.javaClass)
                known.invoke(robot, activity, permsArray)
                Log.d(TAG, "requestSequencePermission enviado (fallback Activity+Array)")
                return true
            }

            Log.w(TAG, "No se encontrÃ³ mÃ©todo requestPermission(s) compatible")
            false
        } catch (t: Throwable) {
            Log.w(TAG, "requestSequencePermission(activity) fallo: ${t.message}")
            false
        }
    }

    // Compat check for SEQUENCE permission using various SDK signatures.
    fun isSequencePermissionGranted(): Boolean {
        return try {
            val robot = robotInstance() ?: return false
            val permCls = Class.forName("com.robotemi.sdk.Permission")
            val seq = permCls.getField("SEQUENCE").get(null)
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
            val getGranted = robot.javaClass.methods.firstOrNull { it.name.contains("getGranted", true) && it.parameterTypes.isEmpty() }
            if (getGranted != null) {
                val any = getGranted.invoke(robot)
                if (any is Collection<*>) {
                    return any.any { it?.toString()?.contains("SEQUENCE", true) == true || it?.toString()?.contains("sequence", true) == true }
                }
            }
            false
        } catch (_: Throwable) { false }
    }

    // ---- Extra: listener y variantes explícitas de solicitud de permisos ----
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
                                val perm = args?.getOrNull(0)
                                val grantRes = args?.getOrNull(1)
                                val reqCode = args?.getOrNull(2)
                                Log.d(TAG, "[PermResult] perm=${'$'}perm grant=${'$'}grantRes req=${'$'}reqCode")
                            }
                        } catch (_: Throwable) {}
                        null
                    }
                )
                val add = robot.javaClass.getMethod("addOnRequestPermissionResultListener", listenerCls)
                add.invoke(robot, permListenerProxy)
                Log.d(TAG, "Permission result listener registrado")
            }
            true
        } catch (t: Throwable) {
            Log.w(TAG, "No pude registrar OnRequestPermissionResultListener: ${t.message}")
            false
        }
    }

    fun hasSequencePermissionDirect(): Boolean {
        val robot = robotInstance() ?: return false
        return try {
            val permCls = Class.forName("com.robotemi.sdk.Permission")
            val seq = permCls.getField("SEQUENCE").get(null)
            val check = robot.javaClass.getMethod("checkSelfPermission", permCls)
            val res = check.invoke(robot, seq)
            when (res) {
                is java.lang.Boolean -> res.booleanValue()
                is java.lang.Integer -> res.toInt() != 0
                else -> {
                    val grantStatusCls = Class.forName("com.robotemi.sdk.Permission\$GrantStatus")
                    val granted = grantStatusCls.getField("GRANTED").get(null)
                    res == granted
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "hasSequencePermissionDirect fallo: ${t.message}")
            false
        }
    }

    fun requestSeqPerm_ListWithCode(): Boolean {
        val robot = robotInstance() ?: return false
        return try {
            ensurePermissionListener()
            val permCls = Class.forName("com.robotemi.sdk.Permission")
            val seq = permCls.getField("SEQUENCE").get(null)
            val list = java.util.ArrayList<Any>()
            list.add(seq)
            val m = robot.javaClass.getMethod("requestPermissions", java.util.List::class.java, Int::class.javaPrimitiveType)
            m.invoke(robot, list, 1001)
            Log.d(TAG, "requestSeqPerm_ListWithCode OK")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "requestSeqPerm_ListWithCode fallo: ${t.message}")
            false
        }
    }

    fun requestSeqPerm_List(): Boolean {
        val robot = robotInstance() ?: return false
        return try {
            ensurePermissionListener()
            val permCls = Class.forName("com.robotemi.sdk.Permission")
            val seq = permCls.getField("SEQUENCE").get(null)
            val list = java.util.ArrayList<Any>()
            list.add(seq)
            val m = robot.javaClass.getMethod("requestPermissions", java.util.List::class.java)
            m.invoke(robot, list)
            Log.d(TAG, "requestSeqPerm_List OK")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "requestSeqPerm_List fallo: ${t.message}")
            false
        }
    }

    fun requestSeqPerm_ArrayWithCode(): Boolean {
        val robot = robotInstance() ?: return false
        return try {
            ensurePermissionListener()
            val permCls = Class.forName("com.robotemi.sdk.Permission")
            val seq = permCls.getField("SEQUENCE").get(null)
            val arr = java.lang.reflect.Array.newInstance(permCls, 1)
            java.lang.reflect.Array.set(arr, 0, seq)
            val m = robot.javaClass.getMethod("requestPermissions", arr.javaClass, Int::class.javaPrimitiveType)
            m.invoke(robot, arr, 1001)
            Log.d(TAG, "requestSeqPerm_ArrayWithCode OK")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "requestSeqPerm_ArrayWithCode fallo: ${t.message}")
            false
        }
    }

    fun requestSeqPerm_ActivityListWithCode(activity: Activity): Boolean {
        val robot = robotInstance() ?: return false
        return try {
            ensurePermissionListener()
            val permCls = Class.forName("com.robotemi.sdk.Permission")
            val seq = permCls.getField("SEQUENCE").get(null)
            val list = java.util.ArrayList<Any>()
            list.add(seq)
            val m = robot.javaClass.getMethod("requestPermissions", Activity::class.java, java.util.List::class.java, Int::class.javaPrimitiveType)
            m.invoke(robot, activity, list, 1001)
            Log.d(TAG, "requestSeqPerm_ActivityListWithCode OK")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "requestSeqPerm_ActivityListWithCode fallo: ${t.message}")
            false
        }
    }

    fun requestSeqPerm_ActivityList(activity: Activity): Boolean {
        val robot = robotInstance() ?: return false
        return try {
            ensurePermissionListener()
            val permCls = Class.forName("com.robotemi.sdk.Permission")
            val seq = permCls.getField("SEQUENCE").get(null)
            val list = java.util.ArrayList<Any>()
            list.add(seq)
            val m = robot.javaClass.getMethod("requestPermissions", Activity::class.java, java.util.List::class.java)
            m.invoke(robot, activity, list)
            Log.d(TAG, "requestSeqPerm_ActivityList OK")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "requestSeqPerm_ActivityList fallo: ${t.message}")
            false
        }
    }

    fun requestSeqPerm_ActivityArrayWithCode(activity: Activity): Boolean {
        val robot = robotInstance() ?: return false
        return try {
            ensurePermissionListener()
            val permCls = Class.forName("com.robotemi.sdk.Permission")
            val seq = permCls.getField("SEQUENCE").get(null)
            val arr = java.lang.reflect.Array.newInstance(permCls, 1)
            java.lang.reflect.Array.set(arr, 0, seq)
            val m = robot.javaClass.getMethod("requestPermissions", Activity::class.java, arr.javaClass, Int::class.javaPrimitiveType)
            m.invoke(robot, activity, arr, 1001)
            Log.d(TAG, "requestSeqPerm_ActivityArrayWithCode OK")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "requestSeqPerm_ActivityArrayWithCode fallo: ${t.message}")
            false
        }
    }
}

