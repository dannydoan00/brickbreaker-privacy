package com.boltgame.brickbreakerroguelite.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boltgame.brickbreakerroguelite.data.model.GameProgress
import com.boltgame.brickbreakerroguelite.data.model.User
import com.boltgame.brickbreakerroguelite.data.repository.GameProgressRepository
import com.boltgame.brickbreakerroguelite.data.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HomeViewModel(
    private val userRepository: UserRepository,
    private val gameProgressRepository: GameProgressRepository
) : ViewModel() {
    
    val user: Flow<User> = userRepository.getCurrentUser()
    val gameProgress: Flow<GameProgress> = gameProgressRepository.getGameProgress()
    
    fun checkUserSession() {
        viewModelScope.launch {
            val currentUser = user.first()
            
            // Create a guest user if no user exists
            if (currentUser.id.isEmpty()) {
                userRepository.createGuestUser()
            }
        }
    }
}