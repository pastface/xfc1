package com.example.floatingimage

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.floatingimage.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val foldersDir: File by lazy { File(filesDir, "folders").apply { if (!exists()) mkdirs() } }
    private lateinit var adapter: FolderAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = FolderAdapter()
        binding.rvFolders.layoutManager = LinearLayoutManager(this)
        binding.rvFolders.adapter = adapter

        binding.fabAddFolder.setOnClickListener {
            showCreateFolderDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        loadFolders()
    }

    private fun loadFolders() {
        val folders = foldersDir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
        adapter.submitList(folders)
        binding.tvEmpty.visibility = if (folders.isEmpty()) View.VISIBLE else View.GONE
        binding.rvFolders.visibility = if (folders.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showCreateFolderDialog() {
        val editText = EditText(this)
        editText.hint = "文件夹名称"
        AlertDialog.Builder(this)
            .setTitle("新建文件夹")
            .setView(editText)
            .setPositiveButton("创建") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    val folder = File(foldersDir, name)
                    if (folder.exists()) {
                        Toast.makeText(this, "文件夹已存在", Toast.LENGTH_SHORT).show()
                    } else {
                        folder.mkdirs()
                        loadFolders()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteFolder(folder: File) {
        AlertDialog.Builder(this)
            .setTitle("删除文件夹")
            .setMessage("确定删除 \"${folder.name}\" 及其所有图片？")
            .setPositiveButton("删除") { _, _ ->
                folder.deleteRecursively()
                loadFolders()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    inner class FolderAdapter : RecyclerView.Adapter<FolderAdapter.VH>() {
        private var folders = listOf<File>()

        fun submitList(list: List<File>) {
            folders = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_folder, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val folder = folders[position]
            holder.tvName.text = folder.name
            holder.itemView.setOnClickListener {
                val intent = Intent(this@MainActivity, FolderImagesActivity::class.java)
                intent.putExtra("folder_path", folder.absolutePath)
                startActivity(intent)
            }
            holder.btnDelete.setOnClickListener {
                deleteFolder(folder)
            }
        }

        override fun getItemCount() = folders.size

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvFolderName)
            val btnDelete: View = view.findViewById(R.id.btnDeleteFolder)
        }
    }
}