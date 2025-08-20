package dev.furkankavak.suitify.fragments

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
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

class HomeFragment : Fragment() {

    private lateinit var _binding : FragmentHomeBinding
    private val binding get() = _binding

    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionCameraLauncher: ActivityResultLauncher<String>
    private lateinit var photoPickerLauncher: ActivityResultLauncher<PickVisualMediaRequest>

    private var selectedImageUri: Uri? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        photoPickerLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let {
                // Yüksek kaliteli URI'yi direkt kullan
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
                if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                    val bitmap = result.data!!.extras?.get("data") as? Bitmap
                    bitmap?.let {
                        val uri = saveBitmapToGallery(it)
                        uri?.let { 
                            selectedImageUri = it
                            showSelectedImage(it)
                        }
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
        binding.btnContinue.setOnClickListener {
            selectedImageUri?.let { uri ->
                val bundle = Bundle()
                bundle.putString("selectedImageUri", uri.toString())
                val action = R.id.action_homeFragment_to_photoUploadFragment
                findNavController().navigate(action, bundle)
            }
        }
    }


    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraLauncher.launch(intent)
    }


    private fun showSelectedImage(imageUri: Uri) {
        binding.continueCardView.visibility = View.VISIBLE
    }


    private fun saveBitmapToGallery(bitmap: Bitmap): Uri? {
        val path = MediaStore.Images.Media.insertImage(
            requireContext().contentResolver,
            bitmap,
            "SuitifyCamera",
            "Taken from camera"
        )
        return path?.let { Uri.parse(it) }
    }
}