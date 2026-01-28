package fyi.goodbye.fridgy.models.entities

/**
 * Enum representing the role a user has within a household.
 *
 * Roles define what actions a user can perform:
 * - **Owner**: Can edit user roles, manage managers/members, create/delete fridges, manage invite codes
 * - **Manager**: Can create/delete fridges, create/remove invite codes, remove members (but not owners or other managers)
 * - **Member**: Can view fridges, add/remove items, use shopping list
 *
 * Permission hierarchy: Owner > Manager > Member
 * Higher roles inherit all permissions from lower roles.
 */
enum class HouseholdRole {
    OWNER,
    MANAGER,
    MEMBER;

    companion object {
        fun fromString(value: String?): HouseholdRole {
            return when (value?.uppercase()) {
                "OWNER" -> OWNER
                "MANAGER" -> MANAGER
                "MEMBER" -> MEMBER
                else -> MEMBER // Default to member for backwards compatibility
            }
        }
    }
}

/**
 * Extension functions for checking permissions based on role.
 */
fun HouseholdRole.canEditRoles(): Boolean = this == HouseholdRole.OWNER

fun HouseholdRole.canManageFridges(): Boolean = this in listOf(HouseholdRole.OWNER, HouseholdRole.MANAGER)

fun HouseholdRole.canManageInviteCodes(): Boolean = this in listOf(HouseholdRole.OWNER, HouseholdRole.MANAGER)

fun HouseholdRole.canRemoveMembers(): Boolean = this in listOf(HouseholdRole.OWNER, HouseholdRole.MANAGER)

fun HouseholdRole.canDeleteHousehold(): Boolean = this == HouseholdRole.OWNER

fun HouseholdRole.canViewAndEditItems(): Boolean = true // All roles can view and edit items

/**
 * Checks if this role can modify another user's role or remove them.
 * Only owners can modify managers or other owners.
 * Managers can only modify members.
 */
fun HouseholdRole.canModifyUser(targetRole: HouseholdRole): Boolean {
    return when (this) {
        HouseholdRole.OWNER -> true // Owners can modify anyone
        HouseholdRole.MANAGER -> targetRole == HouseholdRole.MEMBER // Managers can only modify members
        HouseholdRole.MEMBER -> false // Members cannot modify anyone
    }
}
