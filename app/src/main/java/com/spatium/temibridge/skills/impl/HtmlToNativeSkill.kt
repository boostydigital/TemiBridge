package com.spatium.deamon.db.temi.skills.impl

import android.content.Context
import android.util.Log
import com.spatium.deamon.db.temi.skills.base.BaseTemiSkill
import com.spatium.deamon.db.temi.skills.base.SkillCategory
import com.spatium.deamon.db.temi.skills.base.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Skill: HtmlToNative
 *
 * Encapsula las reglas y convenciones para convertir pantallas HTML/Tailwind
 * a layouts Android nativos para el robot Temi.
 *
 * Parámetros:
 *  - htmlDescription (String): descripción del HTML o nombre de la pantalla a convertir
 *  - targetActivity (String): nombre de la Activity destino (ej. "MenuActivity")
 *
 * Convenciones aplicadas:
 *  1. Fondo: FrameLayout con ImageView (centerCrop) + View overlay oscuro
 *  2. Glassmorphism: shapes XML con solid semi-transparente + stroke blanco
 *  3. Grid: LinearLayout anidados con layout_weight (reemplaza CSS grid)
 *  4. Tiles clickables: FrameLayout con background drawable + LinearLayout interior
 *  5. Animación de tap: scale 0.95→1.0 con DecelerateInterpolator
 *  6. Colores: definidos en colors.xml (no hardcodeados en layouts)
 *  7. Navegación bottom: LinearLayout horizontal con bg_bottom_nav (rounded pill)
 *  8. Texto: fontFamily sans-serif-black para títulos, sans-serif-medium para subtítulos
 *  9. Iconos: emojis Unicode o drawables vectoriales (no Material Icons web font)
 * 10. Fullscreen: WindowInsetsControllerCompat.hide(systemBars) en setupFullscreen()
 */
class HtmlToNativeSkill :
    BaseTemiSkill(
        skillId = "html_to_native",
        skillName = "HTML to Native",
        description = "Convierte diseños HTML/Tailwind a layouts Android nativos para Temi",
        category = SkillCategory.INTERACTION,
    ) {

    companion object {
        /**
         * Mapa de clases CSS Tailwind → equivalentes Android XML
         */
        val TAILWIND_TO_ANDROID = mapOf(
            // Layout
            "flex flex-col" to "orientation=\"vertical\"",
            "flex flex-row" to "orientation=\"horizontal\"",
            "items-center" to "gravity=\"center_vertical\"",
            "justify-center" to "gravity=\"center\"",
            "justify-between" to "gravity=\"center_vertical\" (con espaciadores View peso)",
            "grid grid-cols-4" to "LinearLayout horizontal con 4 hijos layout_weight=\"1\"",
            "gap-4" to "layout_margin=\"8dp\" en cada hijo",
            "col-span-2" to "layout_weight=\"2\" en LinearLayout",
            "row-span-2" to "LinearLayout vertical con layout_weight anidado",

            // Fondo / Colores
            "bg-slate-900" to "background=\"@color/menu_bg_dark\" (#0F172A)",
            "bg-white/10" to "background con solid #1AFFFFFF",
            "backdrop-blur" to "bg_glass_panel.xml (solid semi-transparente + stroke)",
            "rounded-2xl" to "corners android:radius=\"20dp\"",
            "rounded-full" to "corners android:radius=\"100dp\"",
            "shadow-xl" to "elevation=\"8dp\" en el View",

            // Texto
            "font-black" to "fontFamily=\"sans-serif-black\"",
            "font-bold" to "textStyle=\"bold\"",
            "uppercase" to "textAllCaps=\"true\" o android:text en mayúsculas",
            "italic" to "textStyle=\"italic\"",
            "tracking-widest" to "letterSpacing=\"0.15\"",
            "text-4xl" to "textSize=\"36sp\"",
            "text-2xl" to "textSize=\"22sp\"",
            "text-white/70" to "textColor=\"#B3FFFFFF\"",
            "text-white/60" to "textColor=\"#99FFFFFF\"",

            // Interactividad
            "cursor-pointer" to "clickable=\"true\" focusable=\"true\"",
            "hover:scale" to "animate().scaleX(0.95f) en setOnClickListener",
            "transition-all" to "animate().setDuration(150)",

            // Componentes especiales
            "glass-panel" to "background=\"@drawable/bg_glass_panel\"",
            "glass-button" to "background=\"@drawable/bg_glass_button\"",
            "bottom nav rounded-full" to "background=\"@drawable/bg_bottom_nav\"",
            "gradient indigo→purple" to "background=\"@drawable/bg_btn_confirm\"",
            "gradient blue→purple" to "background=\"@drawable/tile_opinar\"",
        )

        /**
         * Checklist de conversión HTML → Android nativo
         */
        val CONVERSION_CHECKLIST = listOf(
            "✅ Crear layout XML con FrameLayout raíz",
            "✅ Agregar ImageView de fondo (centerCrop) + View overlay oscuro",
            "✅ Traducir grid CSS a LinearLayouts con layout_weight",
            "✅ Crear drawables shape XML para glassmorphism",
            "✅ Agregar colores en colors.xml (no hardcodear en layouts)",
            "✅ IDs de Views: camelCase (tileCheckin, btnConfirmar, etc.)",
            "✅ Usar FrameLayout para tiles con contenido centrado",
            "✅ Bottom nav: LinearLayout horizontal con bg_bottom_nav",
            "✅ Íconos: emojis Unicode en TextView o drawables vectoriales",
            "✅ Configurar fullscreen en onCreate() con WindowInsetsController",
            "✅ Mantener IDs existentes al actualizar pantallas ya implementadas",
            "✅ Animación de tap en setOnClickListener de cada tile",
            "✅ Registrar Activity en AndroidManifest.xml",
            "✅ Compilar y verificar con gradlew assembleDebug",
        )

        /**
         * Estructura base de una Activity Kotlin para pantalla de tiles
         */
        val ACTIVITY_TEMPLATE = """
            class NombreActivity : AppCompatActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContentView(R.layout.activity_nombre)
                    setupFullscreen()
                    setupTiles()
                }
                
                private fun setupFullscreen() {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                    val controller = WindowInsetsControllerCompat(window, window.decorView)
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                }
                
                private fun setupTiles() {
                    findViewById<FrameLayout>(R.id.tileNombre).setOnClickListener { 
                        showTileAnimation(it)
                        // acción
                    }
                }
                
                private fun showTileAnimation(view: View) {
                    view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100)
                        .withEndAction {
                            view.animate().scaleX(1f).scaleY(1f)
                                .setDuration(150).setInterpolator(DecelerateInterpolator()).start()
                        }.start()
                }
            }
        """.trimIndent()
    }

    override suspend fun executeSkill(context: Context, params: Map<String, Any>): SkillResult {
        val htmlDescription = params["htmlDescription"] as? String ?: ""
        val targetActivity = params["targetActivity"] as? String ?: ""

        return withContext(Dispatchers.IO) {
            logInfo("HtmlToNativeSkill: convirtiendo '$htmlDescription' → '$targetActivity'")

            Log.d("HtmlToNativeSkill", "=== GUÍA DE CONVERSIÓN HTML → ANDROID NATIVO ===")
            Log.d("HtmlToNativeSkill", "Pantalla: $htmlDescription | Activity: $targetActivity")
            Log.d("HtmlToNativeSkill", "Checklist: ${CONVERSION_CHECKLIST.joinToString("\n")}")
            Log.d("HtmlToNativeSkill", "Mappings Tailwind→Android: ${TAILWIND_TO_ANDROID.size} reglas")

            SkillResult.PartialSuccess(
                "HtmlToNative: '$htmlDescription' → '$targetActivity' | " +
                    "${CONVERSION_CHECKLIST.size} pasos | ${TAILWIND_TO_ANDROID.size} mappings Tailwind→Android",
            )
        }
    }

    override suspend fun canExecute(context: Context): Boolean = true
    override fun getRequiredPermissions(): List<String> = emptyList()
}
