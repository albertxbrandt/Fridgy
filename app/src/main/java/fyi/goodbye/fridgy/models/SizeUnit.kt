package fyi.goodbye.fridgy.models

/**
 * Standard units of measurement for items.
 */
enum class SizeUnit(val displayName: String) {
    // Volume - US
    GALLON("Gallon"),
    QUART("Quart"),
    PINT("Pint"),
    CUP("Cup"),
    FLUID_OUNCE("Fluid Ounce"),
    
    // Volume - Metric
    LITER("Liter"),
    MILLILITER("Milliliter"),
    
    // Weight - US
    POUND("Pound"),
    OUNCE("Ounce"),
    
    // Weight - Metric
    KILOGRAM("Kilogram"),
    GRAM("Gram"),
    
    // Count
    PIECE("Piece"),
    DOZEN("Dozen"),
    PACK("Pack"),
    BOX("Box"),
    BAG("Bag"),
    BOTTLE("Bottle"),
    CAN("Can"),
    
    // Other
    OTHER("Other");
    
    companion object {
        fun fromString(value: String?): SizeUnit? {
            if (value == null) return null
            return entries.find { it.name.equals(value, ignoreCase = true) }
        }
    }
}
