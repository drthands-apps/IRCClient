package com.personal.ircclient.core.utils

import java.util.Locale

object NickGenerator {
    private val data = mapOf(
        "en" to Pair(
            listOf("User", "Bird", "Cloud", "Storm", "Fire", "Wave", "Shadow", "Light", "Fox", "Wolf"),
            listOf("Happy", "Fast", "Quiet", "Bright", "Dark", "Cold", "Warm", "Wild", "Brave", "Swift")
        ),
        "es" to Pair(
            listOf("Usuario", "Pajaro", "Nube", "Tormenta", "Fuego", "Ola", "Sombra", "Luz", "Zorro", "Lobo"),
            listOf("Feliz", "Rapido", "Quieto", "Claro", "Oscuro", "Frio", "Calido", "Salvaje", "Valiente", "Veloz")
        ),
        "fr" to Pair(
            listOf("Ami", "Oiseau", "Nuage", "Orage", "Feu", "Vague", "Ombre", "Lumiere", "Renard", "Loup"),
            listOf("Heureux", "Rapide", "Calme", "Brillant", "Sombre", "Froid", "Chaud", "Sauvage", "Fier", "Vif")
        ),
        "de" to Pair(
            listOf("Nutzer", "Vogel", "Wolke", "Sturm", "Feuer", "Welle", "Schatten", "Licht", "Fuchs", "Wolf"),
            listOf("Glücklich", "Schnell", "Leise", "Hell", "Dunkel", "Kalt", "Warm", "Wild", "Mutig", "Flink")
        ),
        "pt" to Pair(
            listOf("Usuario", "Passaro", "Nuvem", "Tempestade", "Fogo", "Onda", "Sombra", "Luz", "Raposa", "Lobo"),
            listOf("Feliz", "Rapido", "Quieto", "Brilhante", "Escuro", "Frio", "Quente", "Selvagem", "Valente", "Veloz")
        ),
        "zh" to Pair(
            listOf("Yonghu", "Niao", "Yun", "Fengbao", "Huo", "Lang", "Ying", "Guang", "Hu", "Lang"),
            listOf("Kuaile", "Kuai", "Anjing", "Mingliang", "An", "Leng", "Nuan", "Yexing", "Yonggan", "Minjie")
        )
    )

    fun generate(): String {
        val lang = Locale.getDefault().language
        val pair = data[lang] ?: data["en"]!!
        val noun = pair.first.random()
        val adj = pair.second.random()
        val num = (100..999).random()
        return "$adj$noun$num"
    }
}
