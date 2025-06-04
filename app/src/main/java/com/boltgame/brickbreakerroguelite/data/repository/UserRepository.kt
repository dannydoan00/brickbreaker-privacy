package com.boltgame.brickbreakerroguelite.data.repository

import android.content.SharedPreferences
import com.boltgame.brickbreakerroguelite.data.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

interface UserRepository {
    fun getCurrentUser(): Flow<User>
    suspend fun createGuestUser()
    suspend fun signOut()
}

class UserRepositoryImpl(
    private val sharedPreferences: SharedPreferences
) : UserRepository {
    
    private val _currentUser = MutableStateFlow<User>(loadUserFromPrefs())
    
    override fun getCurrentUser(): Flow<User> = _currentUser.asStateFlow()
    
    override suspend fun createGuestUser() {
        val guestId = UUID.randomUUID().toString()
        val guestUser = User(
            id = guestId,
            displayName = "Guest",
            isGuest = true
        )
        
        saveUserToPrefs(guestUser)
        _currentUser.value = guestUser
    }
    
    override suspend fun signOut() {
        val guestUser = User(
            id = "",
            displayName = "",
            isGuest = true
        )
        
        sharedPreferences.edit().clear().apply()
        _currentUser.value = guestUser
    }
    
    private fun loadUserFromPrefs(): User {
        val userId = sharedPreferences.getString("user_id", "") ?: ""
        
        return if (userId.isNotEmpty()) {
            User(
                id = userId,
                displayName = sharedPreferences.getString("display_name", "") ?: "",
                email = sharedPreferences.getString("email", "") ?: "",
                isGuest = sharedPreferences.getBoolean("is_guest", true)
            )
        } else {
            User()
        }
    }
    
    private fun saveUserToPrefs(user: User) {
        sharedPreferences.edit().apply {
            putString("user_id", user.id)
            putString("display_name", user.displayName)
            putString("email", user.email)
            putBoolean("is_guest", user.isGuest)
            apply()
        }
    }
}