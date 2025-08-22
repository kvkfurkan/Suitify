package dev.furkankavak.suitify.fragments

import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import dev.furkankavak.suitify.utils.PhotoManager
import dev.furkankavak.suitify.api.FalKontextApi
import dev.furkankavak.suitify.databinding.FragmentPhotoUploadBinding
import androidx.core.net.toUri

class PhotoUploadFragment : Fragment() {
    
    private var _binding: FragmentPhotoUploadBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var photoManager: PhotoManager
    private var selectedImageUri: Uri? = null
    private var optimizedImageData: ByteArray? = null
    
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPhotoUploadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // PhotoManager'ı başlat
        photoManager = PhotoManager(requireContext())
        
        // UI'yi ayarla
        setupUI()
        
        // Gelen fotoğrafı yükle
        loadSelectedImage()
    }

    private fun setupUI() {
        // Edit button click listener
        binding.btnEditPhoto.setOnClickListener {
            if (isImageReadyForApi()) {
                // Sabit prompt kullan - profesyonel vesikalık fotoğraf için
                editPhotoWithAI()
            } else {
                Toast.makeText(requireContext(), "Fotoğraf henüz hazır değil", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Initially hide progress bar
        binding.progressBar.visibility = View.GONE
    }

    private fun loadSelectedImage() {
        val selectedImageUriString = arguments?.getString("selectedImageUri")
        
        if (!selectedImageUriString.isNullOrEmpty()) {
            selectedImageUri = selectedImageUriString.toUri()
            
            // Prepare image for API
            prepareImageForApi(selectedImageUri!!)
            
            // Load image with Glide
            val requestOptions = RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .override(com.bumptech.glide.request.target.Target.SIZE_ORIGINAL)
                .transform(RoundedCorners(32)) // Yuvarlak köşeler ekle
            
            Glide.with(this)
                .load(selectedImageUri)
                .apply(requestOptions)
                .into(binding.ivUploadedPhoto)
                
            binding.ivUploadedPhoto.visibility = View.VISIBLE
        } else {
            binding.ivUploadedPhoto.visibility = View.GONE
            Toast.makeText(requireContext(), "Fotoğraf yüklenemedi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun prepareImageForApi(uri: Uri) {
        lifecycleScope.launch {
            try {
                when (val result = photoManager.optimizeForApiSafe(uri)) {
                    is PhotoManager.PhotoResult.Success -> {
                        optimizedImageData = result.data
                        val sizeKB = photoManager.getImageSizeInKB(result.data)
                        Toast.makeText(
                            requireContext(), 
                            "Fotoğraf hazırlandı (${String.format("%.1f", sizeKB)} KB)", 
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is PhotoManager.PhotoResult.Error -> {
                        optimizedImageData = null
                        Toast.makeText(requireContext(), "Hata: ${result.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                optimizedImageData = null
                Toast.makeText(requireContext(), "Fotoğraf işlenirken hata oluştu: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun isImageReadyForApi(): Boolean {
        return optimizedImageData != null
    }

    private fun editPhotoWithAI() {
        optimizedImageData?.let { imageData ->
            // UI'yi güncelle
            binding.progressBar.visibility = View.VISIBLE
            binding.btnEditPhoto.isEnabled = false
            binding.btnEditPhoto.text = "İşleniyor..."
            
            lifecycleScope.launch {
                try {
                    // Optimize edilmiş vesikalık fotoğraf promptu kullan
                    val prompt = FalKontextApi.PASSPORT_PHOTO_PROMPT
                    
                    val result = FalKontextApi.processImage(imageData, prompt)
                    
                    result.onSuccess { kontextResult ->
                        handleEditSuccess(kontextResult)
                    }.onFailure { exception ->
                        handleEditError(exception.message ?: "Bilinmeyen hata")
                    }
                } catch (e: NumberFormatException) {
                    handleEditError("Sayı formatı hatası: ${e.message}")
                } catch (e: com.google.gson.JsonSyntaxException) {
                    handleEditError("JSON parse hatası: ${e.message}")
                } catch (e: Exception) {
                    handleEditError("API çağrısında hata: ${e.javaClass.simpleName} - ${e.message}")
                }
            }
        } ?: run {
            Toast.makeText(requireContext(), "Fotoğraf henüz hazır değil", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleEditSuccess(kontextResult: dev.furkankavak.suitify.api.KontextResult) {
        // UI'yi sıfırla
        binding.progressBar.visibility = View.GONE
        binding.btnEditPhoto.isEnabled = true
        binding.btnEditPhoto.text = "🎨  Profesyonel Vesikalık Oluştur"
        
        if (kontextResult.images.isNotEmpty()) {
            val editedImageUrl = kontextResult.images.first().url
            
            // Düzenlenmiş fotoğrafı göster
            Glide.with(this)
                .load(editedImageUrl)
                .transform(RoundedCorners(32)) // Yuvarlak köşeler ekle
                .into(binding.ivUploadedPhoto)
            
            Toast.makeText(requireContext(), "Fotoğraf başarıyla düzenlendi!", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(requireContext(), "Düzenlenmiş fotoğraf alınamadı", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleEditError(errorMessage: String) {
        // UI'yi sıfırla
        binding.progressBar.visibility = View.GONE
        binding.btnEditPhoto.isEnabled = true
        binding.btnEditPhoto.text = "🎨  Profesyonel Vesikalık Oluştur"
        
        Toast.makeText(requireContext(), "Hata: $errorMessage", Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}