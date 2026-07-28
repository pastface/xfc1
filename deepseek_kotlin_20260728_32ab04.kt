package com.example.floatingimage

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.floatingimage.databinding.ActivityFolderImagesBinding
import java.io.File

class FolderImagesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFolderImagesBinding
    private lateinit var folder: File
    private lateinit var adapter: ImageAdapter
    private val imageExtensions = setOf("jpg", "jpeg", "png", "bmp", "gif", "webp")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFolderImagesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val folderPath = intent.getStringExtra("folder_path") ?: return finish()
        folder = File(folderPath)
        title = folder.name

        adapter = ImageAdapter()
        binding.rvImages.layoutManager = GridLayoutManager(this, 3)
        binding.rvImages.adapter = adapter

        binding.btnAddImage.setOnClickListener {
            openImagePicker()
        }
    }

    override fun onResume() {
        super.onResume()
        loadImages()
    }

    private fun loadImages() {
        val images = folder.listFiles()?.filter {
            it.isFile && it.extension.lowercase() in imageExtensions
        }?.sortedBy { it.name } ?: emptyList()
        adapter.submitList(images)
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { saveImageToFolder(it) }
    }

    private fun openImagePicker() {
        pickImageLauncher.launch("image/*")
    }

    private fun saveImageToFolder(uri: Uri) {
        try {
            val fileName = "img_${System.currentTimeMillis()}.jpg"
            val destFile = File(folder, fileName)
            contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            loadImages()
            Toast.makeText(this, "图片已导入", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showImageOptions(imageFile: File) {
        val options = arrayOf("设为悬浮窗", "删除图片")
        AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startFloatingWindow(imageFile)
                    1 -> deleteImage(imageFile)
                }
            }
            .show()
    }

    private fun startFloatingWindow(imageFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_LONG).show()
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            startActivity(intent)
            return
        }
        val intent = Intent(this, FloatingWindowService::class.java).apply {
            putExtra("action", "show")
            putExtra("image_path", imageFile.absolutePath)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "悬浮窗已开启，可锁定穿透", Toast.LENGTH_SHORT).show()
    }

    private fun deleteImage(file: File) {
        AlertDialog.Builder(this)
            .setTitle("删除图片")
            .setMessage("确定删除 \"${file.name}\"？")
            .setPositiveButton("删除") { _, _ ->
                file.delete()
                loadImages()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    inner class ImageAdapter : RecyclerView.Adapter<ImageAdapter.VH>() {
        private var images = listOf<File>()

        fun submitList(list: List<File>) {
            images = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val image = images[position]
            Glide.with(holder.itemView.context).load(image).into(holder.iv)
            holder.itemView.setOnLongClickListener {
                showImageOptions(image)
                true
            }
        }

        override fun getItemCount() = images.size

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val iv: ImageView = view.findViewById(R.id.ivThumb)
        }
    }
}