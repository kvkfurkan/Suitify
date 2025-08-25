package dev.furkankavak.suitify.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.gson.Gson
import dev.furkankavak.suitify.R
import dev.furkankavak.suitify.api.KontextResult
import dev.furkankavak.suitify.databinding.FragmentResultBinding
import dev.furkankavak.suitify.utils.ImageDownloader
import kotlinx.coroutines.launch

class ResultFragment : Fragment() {
    
    private var _binding: FragmentResultBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var imageDownloader: ImageDownloader
    private var kontextResult: KontextResult? = null
    private var imageUrl: String? = null
    
    // Storage permission launcher
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            downloadImage()
        } else {
            Toast.makeText(requireContext(), "Depolama izni gerekli", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResultBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        

        imageDownloader = ImageDownloader(requireContext())
        

        loadResultData()
        

        setupClickListeners()
    }
    
    private fun loadResultData() {
        val kontextResultJson = arguments?.getString("kontextResult")
        
        if (!kontextResultJson.isNullOrEmpty()) {
            try {
                val gson = Gson()
                kontextResult = gson.fromJson(kontextResultJson, KontextResult::class.java)
                
                kontextResult?.let { result ->
                    if (result.images.isNotEmpty()) {
                        imageUrl = result.images.first().url
                        loadResultImage(imageUrl!!)
                        enableButtons()
                    } else {
                        showError("Resim bulunamadı")
                    }
                }
            } catch (e: Exception) {
                showError("Veri okuma hatası: ${e.message}")
            }
        } else {
            showError("Sonuç verisi bulunamadı")
        }
    }
    
    private fun loadResultImage(url: String) {
        binding.progressBarImage.visibility = View.VISIBLE
        
        Glide.with(this)
            .load(url)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(binding.ivResultPhoto)
        
        binding.progressBarImage.visibility = View.GONE
    }
    
    private fun enableButtons() {
        binding.btnDownloadPhoto.isEnabled = true
    }
    
    private fun setupClickListeners() {
        binding.btnDownloadPhoto.setOnClickListener {
            checkPermissionAndDownload()
        }

        
        binding.btnNewPhoto.setOnClickListener {
            navigateToHome()
        }
    }
    
    private fun checkPermissionAndDownload() {
        when {
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q -> {
                downloadImage()
            }
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED -> {
                downloadImage()
            }
            else -> {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }
    
    private fun downloadImage() {
        val url = imageUrl
        if (url.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "İndirilecek resim bulunamadı", Toast.LENGTH_SHORT).show()
            return
        }
        
        binding.btnDownloadPhoto.isEnabled = false
        binding.btnDownloadPhoto.text = "İndiriliyor..."
        
        lifecycleScope.launch {
            imageDownloader.downloadImageFromUrl(
                imageUrl = url,
                onSuccess = {
                    lifecycleScope.launch {
                        binding.btnDownloadPhoto.isEnabled = true
                        binding.btnDownloadPhoto.text = "Fotoğrafı İndir"
                        Toast.makeText(
                            requireContext(),
                            "Fotoğraf Galeri'ye kaydedildi!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                onError = { error ->
                    lifecycleScope.launch {
                        binding.btnDownloadPhoto.isEnabled = true
                        binding.btnDownloadPhoto.text = "Fotoğrafı İndir"
                        Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
    }
    
    private fun navigateToHome() {
        findNavController().navigate(R.id.action_resultFragment_to_homeFragment)
    }
    
    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        binding.btnNewPhoto.visibility = View.VISIBLE
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}