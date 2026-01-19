package fyi.goodbye.fridgy.models

/**
 * Standard units of measurement for items.
 */
enum class SizeUnit(val displayName: String, val pluralName: String) {
    // Volume - US
    GALLON("Gallon", "Gallons"),
    QUART("Quart", "Quarts"),
    PINT("Pint", "Pints"),
    CUP("Cup", "Cups"),
    FLUID_OUNCE("Fluid Ounce", "Fluid Ounces"),
    
    // Volume - Metric
    LITER("Liter", "Liters"),
    MILLILITER("Milliliter", "Milliliters"),
    
    // Weight - US
    POUND("Pound", "Pounds"),
    OUNCE("Ounce", "Ounces"),
    
    // Weight - Metric
    KILOGRAM("Kilogram", "Kilograms"),
    GRAM("Gram", "Grams"),
    
    // Count
    PIECE("Piece", "Pieces"),
    DOZEN("Dozen", "Dozen"),
    PACK("Pack", "Packs"),
    BOX("Box", "Boxes"),
    BAG("Bag", "Bags"),
    BOTTLE("Bottle", "Bottles"),
    CAN("Can", "Cans"),
    
    // Other
    OTHER("Other", "Other");
    
    /**
     * Returns the appropriate singular or plural form based on the quantity.
     */
    fun getDisplayName(quantity: Double?): String {
        return if (quantity == null || quantity == 1.0) displayName else pluralName
    }
    
    companion object {
        fun fromString(value: String?): SizeUnit? {
            if (value == null) return null
            return entries.find { it.name.equals(value, ignoreCase = true) }
        }
        
        /**
         * Gets the appropriate display text for a size and unit combination.
         */
        fun formatSizeUnit(size: Double?, unit: String?): String? {
            if (size == null || unit == null) return null
            val sizeUnit = fromString(unit)
            val unitDisplay = sizeUnit?.getDisplayName(size) ?: unit
            return "$size $unitDisplay"
        }
    }
}
