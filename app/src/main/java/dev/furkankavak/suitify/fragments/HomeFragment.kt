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

class HomeFragment : Fragment() {

    private lateinit var _binding : FragmentHomeBinding
    private val binding get() = _binding

    private lateinit var galleryLauncher: ActivityResultLauncher<Intent>
    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionGalleryLauncher: ActivityResultLauncher<String>
    private lateinit var permissionCameraLauncher: ActivityResultLauncher<String>

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


        permissionGalleryLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) openGallery() else Toast.makeText(
                    requireContext(),
                    "Galeri izni reddedildi",
                    Toast.LENGTH_SHORT
                ).show()
        }
        permissionCameraLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) openCamera() else Toast.makeText(
                    requireContext(),
                    "Kamera izni reddedildi",
                    Toast.LENGTH_SHORT
                ).show()
            }
        galleryLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                    val uri = result.data!!.data
                    uri?.let {
                        showSelectedImage(it)
                    }
                }
            }
        cameraLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                    val bitmap = result.data!!.extras?.get("data") as? Bitmap
                    bitmap?.let {
                        val uri = saveBitmapToGallery(it)
                        uri?.let { showSelectedImage(it) }
                    }
                }
            }

        binding.btnPickGallery.setOnClickListener {
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED -> openGallery()

                else -> permissionGalleryLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
            }
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

            val action = R.id.action_homeFragment_to_photoUploadFragment

            findNavController().navigate(action)
        }
    }


    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        galleryLauncher.launch(intent)
    }


    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraLauncher.launch(intent)
    }


    private fun showSelectedImage(imageUri: Uri) {
        binding.ivSelectedImage.setImageURI(imageUri)
        binding.ivSelectedImage.visibility = View.VISIBLE
        binding.btnContinue.visibility = View.VISIBLE
        selectedImageUri = imageUri
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