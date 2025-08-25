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
import androidx.navigation.fragment.findNavController
import com.google.gson.Gson

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

        photoManager = PhotoManager(requireContext())

        setupUI()

        loadSelectedImage()
    }

    private fun setupUI() {

        binding.btnEditPhoto.setOnClickListener {
            if (isImageReadyForApi()) {
                editPhotoWithAI()
            } else {
                Toast.makeText(requireContext(), "Fotoğraf henüz hazır değil", Toast.LENGTH_SHORT).show()
            }
        }

        binding.progressBar.visibility = View.GONE
    }

    private fun loadSelectedImage() {
        val selectedImageUriString = arguments?.getString("selectedImageUri")
        
        if (!selectedImageUriString.isNullOrEmpty()) {
            selectedImageUri = selectedImageUriString.toUri()

            prepareImageForApi(selectedImageUri!!)

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

            binding.progressBar.visibility = View.VISIBLE
            binding.btnEditPhoto.isEnabled = false
            binding.btnEditPhoto.text = "İşleniyor..."
            
            lifecycleScope.launch {
                try {
                    val prompt = FalKontextApi.DEFAULT_PROMPT
                    
                    val result = FalKontextApi.processImage(imageData, prompt)
                    
                    result.onSuccess { kontextResult ->
                        if (kontextResult.images.isEmpty()) {
                            handleEditError("API başarılı ama resim üretilemedi. Lütfen tekrar deneyin.")
                        } else {
                            handleEditSuccess(kontextResult)
                        }
                    }.onFailure { exception ->
                        val errorMessage = when (exception) {
                            is NumberFormatException -> 
                                "Sunucu yanıtında sayısal veri hatası. Lütfen tekrar deneyin."
                            is com.google.gson.JsonSyntaxException -> 
                                "Sunucu yanıtı işlenirken hata oluştu. Lütfen tekrar deneyin."
                            is java.net.SocketTimeoutException -> 
                                "İstek zaman aşımına uğradı. İnternet bağlantınızı kontrol edin."
                            is java.net.UnknownHostException -> 
                                "İnternet bağlantısı bulunamadı. Bağlantınızı kontrol edin."
                            is retrofit2.HttpException -> {
                                when (exception.code()) {
                                    401 -> "API anahtarı geçersiz. Uygulama yöneticisi ile iletişime geçin."
                                    429 -> "Çok fazla istek gönderildi. Lütfen biraz bekleyin."
                                    500, 502, 503 -> "Sunucu hatası. Lütfen daha sonra tekrar deneyin."
                                    else -> "Sunucu hatası (${exception.code()}). Lütfen tekrar deneyin."
                                }
                            }
                            else -> exception.message ?: "Bilinmeyen hata oluştu. Lütfen tekrar deneyin."
                        }
                        handleEditError(errorMessage)
                    }
                } catch (e: NumberFormatException) {
                    handleEditError("Veri formatı hatası. Lütfen tekrar deneyin.")
                } catch (e: com.google.gson.JsonSyntaxException) {
                    handleEditError("Sunucu yanıtı işlenirken hata. Lütfen tekrar deneyin.")
                } catch (e: OutOfMemoryError) {
                    handleEditError("Bellek yetersiz. Lütfen uygulamayı yeniden başlatın.")
                } catch (e: Exception) {
                    handleEditError("Beklenmeyen hata: ${e.javaClass.simpleName}. Lütfen tekrar deneyin.")
                }
            }
        } ?: run {
            Toast.makeText(requireContext(), "Fotoğraf henüz hazır değil", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleEditSuccess(kontextResult: dev.furkankavak.suitify.api.KontextResult) {
        binding.progressBar.visibility = View.GONE
        binding.btnEditPhoto.isEnabled = true
        binding.btnEditPhoto.text = "Profesyonel Vesikalık Oluştur"
        
        if (kontextResult.images.isNotEmpty()) {
            Toast.makeText(requireContext(), "Fotoğraf başarıyla düzenlendi!", Toast.LENGTH_LONG).show()

            val bundle = Bundle().apply {
                val gson = Gson()
                putString("kontextResult", gson.toJson(kontextResult))
            }
            
            findNavController().navigate(
                dev.furkankavak.suitify.R.id.action_photoUploadFragment_to_resultFragment,
                bundle
            )
        } else {
            Toast.makeText(requireContext(), "Düzenlenmiş fotoğraf alınamadı", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleEditError(errorMessage: String) {
        binding.progressBar.visibility = View.GONE
        binding.btnEditPhoto.isEnabled = true
        binding.btnEditPhoto.text = "Profesyonel Vesikalık Oluştur"
        
        Toast.makeText(requireContext(), "Hata: $errorMessage", Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}