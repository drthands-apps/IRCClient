package com.personal.ircclient.core.utils

/**
 * Universal Nickname Generator for FenixIRC.
 * Optimized to use English components to avoid gender/grammar mismatches across languages.
 */
object NickGenerator {
    private val nouns = listOf(
        "User", "Bird", "Cloud", "Storm", "Fire", "Wave", "Shadow", "Light", "Fox", "Wolf",
        "Falcon", "Tiger", "Bear", "Panda", "Eagle", "Lion", "Shark", "Dragon", "Phoenix", "Titan",
        "Ghost", "Specter", "Ranger", "Hunter", "Pilot", "Captain", "Seeker", "Sage", "Knight", "Mage",
        "Star", "Moon", "Galaxy", "Nebula", "Comet", "Planet", "Orbit", "Zenith", "Apex", "Nova",
        "River", "Mountain", "Forest", "Desert", "Ocean", "Valley", "Cliff", "Stone", "Ice", "Flame",
        "Cyber", "Pixel", "Byte", "Code", "Link", "Data", "Node", "Vector", "Matrix", "Echo"
    )

    private val adjectives = listOf(
        "Happy", "Fast", "Quiet", "Bright", "Dark", "Cold", "Warm", "Wild", "Brave", "Swift",
        "Silver", "Golden", "Iron", "Steel", "Crimson", "Azure", "Emerald", "Obsidian", "Amber", "Violet",
        "Loyal", "Smart", "Cunning", "Strong", "Gentle", "Fierce", "Calm", "Proud", "Noble", "Ancient",
        "Hidden", "Secret", "Lost", "Found", "New", "Old", "Young", "Free", "Bound", "First",
        "Sonic", "Mega", "Ultra", "Hyper", "Super", "Alpha", "Omega", "Prime", "Delta", "Core",
        "Solid", "Liquid", "Static", "Dynamic", "Global", "Local", "Inner", "Outer", "True", "False"
    )

    fun generate(): String {
        val noun = nouns.random()
        val adj = adjectives.random()
        val num = (100..999).random()
        return "$adj$noun$num"
    }
}
