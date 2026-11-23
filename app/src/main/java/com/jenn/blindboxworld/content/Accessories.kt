package com.jenn.blindboxworld.content

import com.jenn.blindboxworld.R

enum class AccessorySlot { HEAD, EYES, MOUTH, NECK }

/** Stable inventory IDs also name each unit-width, Y-up wearable in assets/accessories. */
data class Accessory(val id: String, val name: String, val slot: AccessorySlot, val thumbnail: Int) {
    val modelPath get() = "accessories/$id.glb"
}

val ACCESSORIES = listOf(
    Accessory("daisy_bucket", "Daisy Bucket Hat", AccessorySlot.HEAD, R.drawable.accessory_daisy_bucket),
    Accessory("cocoa_curl", "Cocoa Curl Mustache", AccessorySlot.MOUTH, R.drawable.accessory_cocoa_curl),
    Accessory("melon_shades", "Melon Soda Shades", AccessorySlot.EYES, R.drawable.accessory_melon_shades),
    Accessory("berry_beret", "Strawberry Beret", AccessorySlot.HEAD, R.drawable.accessory_berry_beret),
    Accessory("moon_monocle", "Moonbeam Monocle", AccessorySlot.EYES, R.drawable.accessory_moon_monocle),
    Accessory("sardine_scarf", "Sardine Scarf", AccessorySlot.NECK, R.drawable.accessory_sardine_scarf),
    Accessory("frog_phones", "Frog Frequency", AccessorySlot.HEAD, R.drawable.accessory_frog_phones),
    Accessory("cherry_bow", "Cherry Ribbon", AccessorySlot.HEAD, R.drawable.accessory_cherry_bow),
    Accessory("pixel_specs", "8-Bit Specs", AccessorySlot.EYES, R.drawable.accessory_pixel_specs),
    Accessory("peach_bubble", "Peach Bubblegum", AccessorySlot.MOUTH, R.drawable.accessory_peach_bubble),
    Accessory("ramen_crown", "Midnight Ramen", AccessorySlot.HEAD, R.drawable.accessory_ramen_crown),
    Accessory("shell_ruff", "Pearl Scallop Ruff", AccessorySlot.NECK, R.drawable.accessory_shell_ruff),
    Accessory("butter_toast", "Butter Bite", AccessorySlot.MOUTH, R.drawable.accessory_butter_toast),
    Accessory("cloud_visor", "Cloud Nine Visor", AccessorySlot.EYES, R.drawable.accessory_cloud_visor),
    Accessory("matcha_sprout", "Matcha Sprout", AccessorySlot.HEAD, R.drawable.accessory_matcha_sprout),
    Accessory("honey_tie", "Honeybee Bow Tie", AccessorySlot.NECK, R.drawable.accessory_honey_tie),
    Accessory("comet_band", "Comet Antennae", AccessorySlot.HEAD, R.drawable.accessory_comet_band),
    Accessory("love_letters", "Love Letter Lenses", AccessorySlot.EYES, R.drawable.accessory_love_letters),
    Accessory("leaf_whistle", "Leaf Whistle", AccessorySlot.MOUTH, R.drawable.accessory_leaf_whistle),
    Accessory("koi_kerchief", "Koi Kerchief", AccessorySlot.NECK, R.drawable.accessory_koi_kerchief),
    Accessory("blue_bonnet", "Blueberry Bonnet", AccessorySlot.HEAD, R.drawable.accessory_blue_bonnet),
    Accessory("pocket_camera", "Pocket Paparazzi", AccessorySlot.NECK, R.drawable.accessory_pocket_camera),
    Accessory("croissant_band", "Croissant Curls", AccessorySlot.HEAD, R.drawable.accessory_croissant_band),
    Accessory("soda_swirl", "Soda Swirl Pop", AccessorySlot.MOUTH, R.drawable.accessory_soda_swirl),
    Accessory("disco_stars", "Disco Starlets", AccessorySlot.EYES, R.drawable.accessory_disco_stars),
    Accessory("milk_charm", "Strawberry Milk Charm", AccessorySlot.NECK, R.drawable.accessory_milk_charm),
    Accessory("lilac_rodeo", "Lilac Rodeo Hat", AccessorySlot.HEAD, R.drawable.accessory_lilac_rodeo),
    Accessory("daisy_stem", "One Little Daisy", AccessorySlot.MOUTH, R.drawable.accessory_daisy_stem),
    Accessory("rainbow_chain", "After Rain Necklace", AccessorySlot.NECK, R.drawable.accessory_rainbow_chain),
    Accessory("plum_fangs", "Plum Vampire Fangs", AccessorySlot.MOUTH, R.drawable.accessory_plum_fangs),
)
