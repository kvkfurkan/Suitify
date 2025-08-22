package dev.furkankavak.suitify.fragments

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import dev.furkankavak.suitify.R
import dev.furkankavak.suitify.databinding.FragmentHomeBinding
import androidx.activity.result.PickVisualMediaRequest
import dev.furkankavak.suitify.utils.PhotoManager
import java.io.File

class HomeFragment : Fragment() {

    private lateinit var _binding : FragmentHomeBinding
    private val binding get() = _binding

    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionCameraLauncher: ActivityResultLauncher<String>
    private lateinit var photoPickerLauncher: ActivityResultLauncher<PickVisualMediaRequest>
    private lateinit var photoManager: PhotoManager

    private var selectedImageUri: Uri? = null
    private var currentPhotoFile: File? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        

        photoManager = PhotoManager(requireContext())

        photoPickerLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let {
                selectedImageUri = it
                showSelectedImage(it)
            }
        }
        
        permissionCameraLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) openCamera() else Toast.makeText(
                    requireContext(),
                    "Kamera izni reddedildi",
                    Toast.LENGTH_SHORT
                ).show()
            }
        cameraLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    currentPhotoFile?.let { file ->
                        selectedImageUri = photoManager.getUriForFile(file)
                        showSelectedImage(selectedImageUri!!)
                    }
                }
            }

        binding.btnPickGallery.setOnClickListener {
            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.btnOpenCamera.setOnClickListener {
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED -> openCamera()

                else -> permissionCameraLauncher.launch(Manifest.permission.CAMERA)
            }
        }

    }


    private fun openCamera() {
        try {
            currentPhotoFile = photoManager.createImageFile()
            currentPhotoFile?.let { file ->
                val photoURI = photoManager.getUriForFile(file)
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                }
                cameraLauncher.launch(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Kamera açılırken hata oluştu: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    private fun showSelectedImage(imageUri: Uri) {
        // Fotoğraf seçilir seçilmez direkt PhotoUploadFragment'a geç
        val bundle = Bundle()
        bundle.putString("selectedImageUri", imageUri.toString())
        val action = R.id.action_homeFragment_to_photoUploadFragment
        findNavController().navigate(action, bundle)
    }


    override fun onDestroy() {
        super.onDestroy()
        photoManager.cleanupTempFiles()
    }
}