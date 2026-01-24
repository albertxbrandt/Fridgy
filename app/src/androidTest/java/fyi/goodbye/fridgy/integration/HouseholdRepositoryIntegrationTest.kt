package fyi.goodbye.fridgy.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import fyi.goodbye.fridgy.FirebaseTestUtils
import fyi.goodbye.fridgy.repositories.HouseholdRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.tasks.await

/**
 * Integration tests for HouseholdRepository using Firebase Local Emulator Suite.
 * Tests household management, invite codes, and member operations.
 */
@RunWith(AndroidJUnit4::class)
class HouseholdRepositoryIntegrationTest {

    private lateinit var repository: HouseholdRepository
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var testUserId: String
    private lateinit var testEmail: String
    private val testPassword = "password123"
    private val testHouseholds = mutableListOf<String>()
    private val testInviteCodes = mutableListOf<String>()

    @Before
    fun setup() = runTest {
        // Configure Firebase emulators
        FirebaseTestUtils.useEmulators()
        
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        repository = HouseholdRepository(firestore, auth)

        // Create test user or re-authenticate if already created
        if (!this@HouseholdRepositoryIntegrationTest::testEmail.isInitialized) {
            testEmail = "householdtest${System.currentTimeMillis()}@test.com"
            val result = auth.createUserWithEmailAndPassword(testEmail, testPassword).await()
            testUserId = result.user?.uid ?: throw IllegalStateException("Failed to create test user")
        } else {
            // Re-authenticate if previous test signed out
            if (auth.currentUser == null) {
                val result = auth.signInWithEmailAndPassword(testEmail, testPassword).await()
                testUserId = result.user?.uid ?: throw IllegalStateException("Failed to sign in test user")
            } else {
                // Already signed in, just update testUserId
                testUserId = auth.currentUser?.uid ?: throw IllegalStateException("Auth current user is null")
            }
        }
    }

    @After
    fun teardown() = runTest {
        // Clean up test households
        testHouseholds.forEach { householdId ->
            try {
                firestore.collection("households").document(householdId).delete().await()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }

        // Clean up test invite codes
        testInviteCodes.forEach { code ->
            try {
                firestore.collection("inviteCodes").document(code).delete().await()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }

        // Clean up test user (keep auth signed in for next test)
        testUserId.let { uid ->
            try {
                firestore.collection("users").document(uid).delete().await()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }

    @Test
    fun createHousehold_successfullyCreatesHousehold() = runTest {
        val householdName = "Test Household ${System.currentTimeMillis()}"
        
        val household = repository.createHousehold(householdName)
        assertNotNull("Household should not be null", household)
        testHouseholds.add(household.id)

        // Verify household was created in Firestore
        val doc = firestore.collection("households").document(household.id).get().await()
        assertTrue("Household document should exist", doc.exists())
        assertEquals(householdName, doc.getString("name"))
        assertEquals(testUserId, doc.getString("createdBy"))
        
        val members = doc.get("members") as? List<*>
        assertTrue("Owner should be in members", members?.contains(testUserId) == true)
    }

    @Test
    fun getHouseholdsForCurrentUser_returnsUserHouseholds() = runTest {
        // Create two households
        val household1 = repository.createHousehold("Household 1")
        testHouseholds.add(household1.id)
        val household2 = repository.createHousehold("Household 2")
        testHouseholds.add(household2.id)

        // Get user's households
        val households = repository.getHouseholdsForCurrentUser().first()
        
        assertTrue("Should return at least 2 households", households.size >= 2)
        assertTrue("Should contain household 1", 
            households.any { it.id == household1.id })
        assertTrue("Should contain household 2", 
            households.any { it.id == household2.id })
    }

    @Test
    fun getHouseholdById_returnsCorrectHousehold() = runTest {
        val householdName = "Specific Household ${System.currentTimeMillis()}"
        val household = repository.createHousehold(householdName)
        testHouseholds.add(household.id)

        val retrieved = repository.getHouseholdById(household.id)
        
        assertNotNull("Household should not be null", retrieved)
        assertEquals(household.id, retrieved?.id)
        assertEquals(householdName, retrieved?.name)
        assertEquals(testUserId, retrieved?.createdBy)
    }

    @Test
    fun getHouseholdById_returnsNullForNonexistent() = runTest {
        val household = repository.getHouseholdById("nonexistent-household-id")
        assertNull("Should return null for nonexistent household", household)
    }

    @Test
    fun updateHouseholdName_successfullyUpdatesName() = runTest {
        val household = repository.createHousehold("Old Name")
        testHouseholds.add(household.id)

        repository.updateHouseholdName(household.id, "New Name")

        val updated = repository.getHouseholdById(household.id)
        assertEquals("New Name", updated?.name)
    }

    @Test
    fun deleteHousehold_removesHouseholdAndRelatedData() = runTest {
        val household = repository.createHousehold("Household to Delete")
        val householdId = household.id
        testHouseholds.add(householdId)

        repository.deleteHousehold(householdId)

        // Verify household document is deleted
        val doc = firestore.collection("households").document(householdId).get().await()
        assertFalse("Household document should not exist", doc.exists())
    }

    @Test
    fun createInviteCode_successfullyCreatesCode() = runTest {
        val household = repository.createHousehold("Household with Invite")
        testHouseholds.add(household.id)

        val inviteCode = repository.createInviteCode(household.id)
        assertNotNull("Invite code should not be null", inviteCode)
        testInviteCodes.add(inviteCode.code)

        // Verify code was created in Firestore
        val doc = firestore.collection("inviteCodes").document(inviteCode.code).get().await()
        assertTrue("Invite code document should exist", doc.exists())
        assertEquals(household.id, doc.getString("householdId"))
        assertEquals(testUserId, doc.getString("createdBy"))
        assertEquals(6, inviteCode.code.length)
    }

    @Test
    fun validateInviteCode_returnsValidCode() = runTest {
        val household = repository.createHousehold("Household for Validation")
        testHouseholds.add(household.id)
        
        val inviteCode = repository.createInviteCode(household.id)
        testInviteCodes.add(inviteCode.code)

        val validated = repository.validateInviteCode(inviteCode.code)
        
        assertNotNull("Validated code should not be null", validated)
        assertEquals(inviteCode.code, validated?.code)
        assertEquals(household.id, validated?.householdId)
    }

    @Test
    fun validateInviteCode_returnsNullForInvalidCode() = runTest {
        val validated = repository.validateInviteCode("INVALID")
        assertNull("Should return null for invalid code", validated)
    }

    @Test
    fun redeemInviteCode_successfullyAddsUserToHousehold() = runTest {
        // Create household with first user
        val household = repository.createHousehold("Household for Redemption")
        testHouseholds.add(household.id)
        val inviteCode = repository.createInviteCode(household.id)
        testInviteCodes.add(inviteCode.code)

        // Create second user
        val email2 = "newmember${System.currentTimeMillis()}@test.com"
        auth.signOut()
        val result2 = auth.createUserWithEmailAndPassword(email2, "password123").await()
        val newUserId = result2.user?.uid!!

        // Create UserRepository to ensure user document exists
        val userRepo = fyi.goodbye.fridgy.repositories.UserRepository(firestore, auth)
        userRepo.createUserDocuments(newUserId, email2, "NewMember")

        // Redeem invite code
        val householdId = repository.redeemInviteCode(inviteCode.code)
        assertEquals(household.id, householdId)

        // Verify user is now a member
        val updatedHousehold = repository.getHouseholdById(household.id)
        assertTrue("New user should be in members", 
            updatedHousehold?.members?.contains(newUserId) == true)

        // Cleanup second user and re-authenticate main user
        auth.signOut()
        firestore.collection("users").document(newUserId).delete().await()
        auth.signInWithEmailAndPassword(testEmail, testPassword).await()
    }

    @Test
    fun getInviteCodesForHousehold_returnsAllCodes() = runTest {
        val household = repository.createHousehold("Household with Multiple Codes")
        testHouseholds.add(household.id)
        
        val code1 = repository.createInviteCode(household.id)
        testInviteCodes.add(code1.code)
        val code2 = repository.createInviteCode(household.id)
        testInviteCodes.add(code2.code)

        val codes = repository.getInviteCodesForHousehold(household.id)
        
        assertTrue("Should have at least 2 codes", codes.size >= 2)
        assertTrue("Should contain code 1", codes.any { it.code == code1.code })
        assertTrue("Should contain code 2", codes.any { it.code == code2.code })
    }

    @Test
    fun revokeInviteCode_removesCode() = runTest {
        val household = repository.createHousehold("Household for Revocation")
        testHouseholds.add(household.id)
        
        val inviteCode = repository.createInviteCode(household.id)
        testInviteCodes.add(inviteCode.code)

        // Verify code exists
        var doc = firestore.collection("inviteCodes").document(inviteCode.code).get().await()
        assertTrue("Code should exist before revocation", doc.exists())

        // Revoke code
        repository.revokeInviteCode(inviteCode.code)

        // Verify code is marked as inactive (not deleted)
        doc = firestore.collection("inviteCodes").document(inviteCode.code).get().await()
        assertTrue("Code document should still exist", doc.exists())
        assertFalse("Code should be inactive after revocation", doc.getBoolean("active") == true)
    }

    @Test
    fun isUserMemberOfHousehold_returnsTrueForMember() = runTest {
        val household = repository.createHousehold("Household for Membership Check")
        testHouseholds.add(household.id)

        val isMember = repository.isUserMemberOfHousehold(household.id)
        assertTrue("User should be member of created household", isMember)
    }

    @Test
    fun isUserMemberOfHousehold_returnsFalseForNonMember() = runTest {
        val isMember = repository.isUserMemberOfHousehold("nonexistent-household")
        assertFalse("User should not be member of nonexistent household", isMember)
    }

    @Test
    fun leaveHousehold_removesUserFromMembers() = runTest {
        // Create household
        val household = repository.createHousehold("Household to Leave")
        testHouseholds.add(household.id)
        val inviteCode = repository.createInviteCode(household.id)
        testInviteCodes.add(inviteCode.code)

        // Create second user and join
        val email2 = "leaver${System.currentTimeMillis()}@test.com"
        auth.signOut()
        val result2 = auth.createUserWithEmailAndPassword(email2, "password123").await()
        val leaverId = result2.user?.uid!!
        
        val userRepo = fyi.goodbye.fridgy.repositories.UserRepository(firestore, auth)
        userRepo.createUserDocuments(leaverId, email2, "Leaver")
        repository.redeemInviteCode(inviteCode.code)

        // Leave household
        repository.leaveHousehold(household.id)

        // Verify user is no longer a member
        val updatedHousehold = repository.getHouseholdById(household.id)
        assertFalse("User should not be in members after leaving", 
            updatedHousehold?.members?.contains(leaverId) == true)

        // Cleanup: delete user document BEFORE signing out, then re-authenticate main user
        firestore.collection("users").document(leaverId).delete().await()
        auth.signOut()
        auth.signInWithEmailAndPassword(testEmail, testPassword).await()
    }

    @Test
    fun addShoppingListItem_successfullyAddsItem() = runTest {
        val household = repository.createHousehold("Household with Shopping List")
        testHouseholds.add(household.id)

        repository.addShoppingListItem(
            householdId = household.id,
            upc = "123456789012",
            quantity = 2
        )

        // Verify item was added
        val items = repository.getShoppingListItems(household.id).first()
        assertEquals(1, items.size)
        assertEquals("123456789012", items[0].upc)
        assertEquals(2, items[0].quantity)
    }

    @Test
    fun removeShoppingListItem_successfullyRemovesItem() = runTest {
        val household = repository.createHousehold("Household for Item Removal")
        testHouseholds.add(household.id)
        
        repository.addShoppingListItem(household.id, "111111111111", 1)
        repository.removeShoppingListItem(household.id, "111111111111")

        val items = repository.getShoppingListItems(household.id).first()
        assertEquals(0, items.size)
    }

    @Test
    fun getShoppingListItems_returnsAllItems() = runTest {
        val household = repository.createHousehold("Household with Multiple Items")
        testHouseholds.add(household.id)
        
        repository.addShoppingListItem(household.id, "100000000001", 1)
        repository.addShoppingListItem(household.id, "100000000002", 2)
        repository.addShoppingListItem(household.id, "100000000003", 3)

        val items = repository.getShoppingListItems(household.id).first()
        
        assertEquals(3, items.size)
        assertTrue(items.any { it.upc == "100000000001" && it.quantity == 1 })
        assertTrue(items.any { it.upc == "100000000002" && it.quantity == 2 })
        assertTrue(items.any { it.upc == "100000000003" && it.quantity == 3 })
    }
}
