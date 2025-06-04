package com.boltgame.brickbreakerroguelite.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.boltgame.brickbreakerroguelite.R
import com.boltgame.brickbreakerroguelite.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class HomeFragment : Fragment() {
    
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: HomeViewModel by viewModel()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupButtons()
        observeViewModel()
        
        // Initialize the user session if needed
        viewModel.checkUserSession()
    }
    
    private fun setupButtons() {
        binding.startGameButton.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_gameFragment)
        }
        
        binding.upgradesButton.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_upgradeFragment)
        }
        
        binding.shopButton.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_shopFragment)
        }
        
        binding.settingsButton.setOnClickListener {
            // Navigate to settings in a real app
        }
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.gameProgress.collect { progress ->
                        binding.softCurrencyText.text = "${progress.softCurrency}"
                        binding.hardCurrencyText.text = "${progress.hardCurrency}"
                        binding.highScoreText.text = "High Score: ${progress.highScore}"
                    }
                }
                
                launch {
                    viewModel.user.collect { user ->
                        binding.userNameText.text = user.displayName.ifEmpty { "Guest" }
                    }
                }
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}