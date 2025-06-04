package com.boltgame.brickbreakerroguelite.ui.game

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.boltgame.brickbreakerroguelite.databinding.FragmentGameBinding
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class GameFragment : Fragment() {
    
    private var _binding: FragmentGameBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: GameViewModel by viewModel()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGameBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupGameView()
        setupButtons()
        observeViewModel()
        
        // Start the game when the view is created
        viewModel.startGame()
    }
    
    private fun setupGameView() {
        binding.gameView.setOnPaddleMovedListener { x ->
            viewModel.updatePaddlePosition(x)
        }
    }
    
    private fun setupButtons() {
        binding.pauseButton.setOnClickListener {
            viewModel.togglePause()
        }
        
        binding.menuButton.setOnClickListener {
            // Show confirmation dialog before exiting
            showExitConfirmationDialog()
        }
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.gameState.collect { gameState ->
                        binding.gameView.updateGameState(gameState)
                        
                        // Update UI based on game state
                        binding.pauseButton.text = if (gameState.isPaused) "Resume" else "Pause"
                        
                        // Show/hide game over UI
                        binding.gameOverLayout.visibility = if (!gameState.isRunning && viewModel.isGameOver.value) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }
                    }
                }
                
                launch {
                    viewModel.isGameOver.collect { isGameOver ->
                        if (isGameOver) {
                            binding.gameOverLayout.visibility = View.VISIBLE
                            binding.finalScoreText.text = "Score: ${viewModel.finalScore.value}"
                        } else {
                            binding.gameOverLayout.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }
    
    private fun showExitConfirmationDialog() {
        // In a real app, show a dialog asking if the user wants to exit
        // For simplicity, we'll just exit directly
        viewModel.endGame()
        requireActivity().onBackPressed()
    }
    
    override fun onPause() {
        super.onPause()
        viewModel.pauseGame()
    }
    
    override fun onResume() {
        super.onResume()
        // Don't auto-resume, let the user press the resume button
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}