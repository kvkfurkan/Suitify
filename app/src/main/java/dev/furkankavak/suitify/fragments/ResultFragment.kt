package dev.furkankavak.suitify.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dev.furkankavak.suitify.R

class ResultFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Doğru layout dosyasını kullan
        return inflater.inflate(R.layout.fragment_result, container, false)
    }
}