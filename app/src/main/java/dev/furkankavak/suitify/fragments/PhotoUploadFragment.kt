package dev.furkankavak.suitify.fragments

import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import dev.furkankavak.suitify.R

class PhotoUploadFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_photo_upload, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Retrieve selected image URI and display with Glide
        val selectedImageUri = arguments?.getString("selectedImageUri")
        val imageView = view.findViewById<ImageView>(R.id.ivUploadedPhoto)
        if (!selectedImageUri.isNullOrEmpty()) {
            val requestOptions = RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .dontTransform()
                .override(com.bumptech.glide.request.target.Target.SIZE_ORIGINAL)
            
            Glide.with(this)
                .load(Uri.parse(selectedImageUri))
                .apply(requestOptions)
                .into(imageView)
            imageView.visibility = View.VISIBLE
        } else {
            imageView.visibility = View.GONE
        }
    }
}