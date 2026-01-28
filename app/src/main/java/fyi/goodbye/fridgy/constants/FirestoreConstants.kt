package fyi.goodbye.fridgy.constants

/**
 * Centralized constants for Firestore collection names.
 * Single source of truth to prevent typos and enable refactoring.
 */
object FirestoreCollections {
    const val USERS = "users"
    const val USER_PROFILES = "userProfiles"
    const val PRODUCTS = "products"
    const val FRIDGES = "fridges"
    const val HOUSEHOLDS = "households"
    const val ADMINS = "admins"
    const val INVITE_CODES = "inviteCodes"
    const val CATEGORIES = "categories"
    const val FCM_TOKENS = "fcmTokens"

    // Subcollections
    const val ITEMS = "items"
    const val SHOPPING_LIST = "shoppingList"
    const val SHOPPING_LIST_PRESENCE = "shoppingListPresence"
}

/**
 * Centralized constants for Firestore field names.
 * Single source of truth to prevent typos and enable refactoring.
 */
object FirestoreFields {
    // Common fields
    const val CREATED_AT = "createdAt"
    const val CREATED_BY = "createdBy"
    const val LAST_UPDATED = "lastUpdated"
    const val LAST_UPDATED_BY = "lastUpdatedBy"
    const val LAST_SEEN = "lastSeen"
    const val NAME = "name"
    const val EMAIL = "email"
    const val ORDER = "order"
    const val IS_READ = "isRead"

    // User fields
    const val USERNAME = "username"
    const val UID = "uid"
    const val USER_ID = "userId"

    // Product fields
    const val UPC = "upc"
    const val BRAND = "brand"
    const val CATEGORY = "category"
    const val IMAGE_URL = "imageUrl"
    const val SIZE = "size"
    const val UNIT = "unit"
    const val SEARCH_TOKENS = "searchTokens"

    // Household fields
    const val MEMBERS = "members"
    const val MEMBER_ROLES = "memberRoles"
    const val PENDING_INVITES = "pendingInvites"

    // Fridge fields
    const val HOUSEHOLD_ID = "householdId"
    const val TYPE = "type"

    // Item fields
    const val ADDED_AT = "addedAt"
    const val ADDED_BY = "addedBy"
    const val QUANTITY = "quantity"
    const val EXPIRATION_DATE = "expirationDate"

    // Shopping list fields
    const val OBTAINED = "obtained"
    const val OBTAINED_BY = "obtainedBy"
    const val OBTAINED_QUANTITY = "obtainedQuantity"
    const val CHECKED = "checked"
    const val SHOPPING_LIST = "shoppingList"
    const val CUSTOM_NAME = "customName"
    const val TARGET_FRIDGE_ID = "targetFridgeId"
    const val LAST_UPDATED_AT = "lastUpdatedAt"

    // Invite code fields
    const val CODE = "code"
    const val USED_BY = "usedBy"
    const val USED_AT = "usedAt"
    const val EXPIRES_AT = "expiresAt"
    const val MAX_USES = "maxUses"
    const val CURRENT_USES = "currentUses"
    const val REVOKED = "revoked"
    const val ACTIVE = "active"
}

/**
 * Centralized constants for Firebase Storage paths.
 * Single source of truth to prevent typos and enable refactoring.
 */
object StoragePaths {
    /**
     * Product images directory.
     * Usage: "${StoragePaths.PRODUCTS_DIR}/$upc.jpg"
     */
    const val PRODUCTS_DIR = "products"
    
    /**
     * Gets the full path for a product image.
     * @param upc The product UPC code
     * @return Full storage path (e.g., "products/123456789.jpg")
     */
    fun productImage(upc: String) = "$PRODUCTS_DIR/$upc.jpg"
}
