package fyi.goodbye.fridgy.constants

/**
 * Centralized constants for Firestore collection names.
 * Single source of truth to prevent typos and enable refactoring.
 */
object FirestoreCollections {
    const val ADMINS = "admins"
    const val CATEGORIES = "categories"
    const val FCM_TOKENS = "fcmTokens"
    const val FRIDGES = "fridges"
    const val HOUSEHOLDS = "households"
    const val INVITE_CODES = "inviteCodes"
    const val NOTIFICATIONS = "notifications"
    const val PRODUCTS = "products"
    const val USERS = "users"
    const val USER_PROFILES = "userProfiles"

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
    const val EMAIL = "email"
    const val LAST_SEEN = "lastSeen"
    const val LAST_UPDATED = "lastUpdated"
    const val LAST_UPDATED_AT = "lastUpdatedAt"
    const val LAST_UPDATED_BY = "lastUpdatedBy"
    const val NAME = "name"
    const val TYPE = "type"

    // User fields
    const val UID = "uid"
    const val USER_ID = "userId"
    const val USERNAME = "username"

    // Admin fields
    const val GRANTED_AT = "grantedAt"
    const val GRANTED_BY = "grantedBy"

    // Category fields
    const val ORDER = "order"

    // FCM Token fields
    const val TOKEN = "token"
    const val UPDATED_AT = "updatedAt"

    // Product fields
    const val BRAND = "brand"
    const val CATEGORY = "category"
    const val IMAGE_URL = "imageUrl"
    const val SEARCH_TOKENS = "searchTokens"
    const val SIZE = "size"
    const val UNIT = "unit"
    const val UPC = "upc"

    // Household fields
    const val MEMBER_ROLES = "memberRoles"
    const val MEMBERS = "members"

    // Fridge fields
    const val HOUSEHOLD_ID = "householdId"
    const val LOCATION = "location"

    // Item fields
    const val ADDED_AT = "addedAt"
    const val ADDED_BY = "addedBy"
    const val EXPIRATION_DATE = "expirationDate"

    // Shopping list fields
    const val CHECKED = "checked"
    const val CUSTOM_NAME = "customName"
    const val OBTAINED = "obtained"
    const val OBTAINED_BY = "obtainedBy"
    const val OBTAINED_QUANTITY = "obtainedQuantity"
    const val QUANTITY = "quantity"
    const val STORE = "store"
    const val TARGET_FRIDGE_ID = "targetFridgeId"

    // Invite code fields
    const val ACTIVE = "active"
    const val EXPIRED = "expired"
    const val EXPIRES_AT = "expiresAt"
    const val HOUSEHOLD_NAME = "householdName"
    const val USED_AT = "usedAt"
    const val USED_BY = "usedBy"
    const val VALID = "valid"

    // Notification fields
    const val BODY = "body"
    const val IS_READ = "read"  // Note: Kotlin property 'isRead' serializes to 'read' in Firestore
    const val RELATED_FRIDGE_ID = "relatedFridgeId"
    const val RELATED_ITEM_ID = "relatedItemId"
    const val TITLE = "title"
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
