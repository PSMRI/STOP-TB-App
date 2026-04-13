package org.piramalswasthya.stoptb.utils

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.databinding.DialogFileViewerBinding
import org.piramalswasthya.stoptb.databinding.ItemFileBinding
import timber.log.Timber

class FileViewerDialog : BottomSheetDialogFragment() {

    private var _binding: DialogFileViewerBinding? = null
    private val binding get() = _binding!!

    private lateinit var title: String
    private var uploadedFiles: MutableList<String> = mutableListOf()
    private var isSubmitted: Boolean = false

    companion object {
        private const val ARG_TITLE = "arg_title"
        private const val ARG_FILES = "arg_files"
        private const val ARG_SUBMITTED = "arg_submitted"
        const val REQUEST_KEY = "file_viewer_request"
        const val RESULT_DELETED_URI = "deleted_file_uri"

        fun newInstance(
            title: String,
            files: List<String>,
            isSubmitted: Boolean = false
        ): FileViewerDialog {
            return FileViewerDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putStringArrayList(ARG_FILES, ArrayList(files))
                    putBoolean(ARG_SUBMITTED, isSubmitted)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            title = it.getString(ARG_TITLE, "Files")
            uploadedFiles = it.getStringArrayList(ARG_FILES)?.toMutableList() ?: mutableListOf()
            isSubmitted = it.getBoolean(ARG_SUBMITTED, false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogFileViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        binding.tvTitle.text = title
        binding.tvFileCount.text = "${uploadedFiles.size} file(s)"

        setupRecyclerView()

        binding.btnClose.setOnClickListener { dismiss() }
    }

    private fun setupRecyclerView() {
        val adapter = FileListAdapter(
            files = uploadedFiles,
            isSubmitted = isSubmitted,
            onFileClick = { openFile(it) },
            onDeleteClick = { showDeleteConfirmation(it) }
        )
        binding.rvFiles.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }
    }

    private fun openFile(fileUri: String) {
        try {
            val parsedUri = Uri.parse(fileUri)
            val contentUri = if (parsedUri.scheme == "file") {
                val file = java.io.File(parsedUri.path!!)
                androidx.core.content.FileProvider.getUriForFile(
                    requireContext(), "${requireContext().packageName}.provider", file
                )
            } else parsedUri

            val mimeType = requireContext().contentResolver.getType(contentUri)
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    MimeTypeMap.getFileExtensionFromUrl(fileUri)
                ) ?: "*/*"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Open file with"))
        } catch (e: Exception) {
            Timber.e(e, "Error opening file: $fileUri")
            Snackbar.make(binding.root, "Unable to open file", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteConfirmation(fileUri: String) {
        if (isSubmitted) {
            Snackbar.make(binding.root, "Cannot delete files after submission", Snackbar.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete File")
            .setMessage("Are you sure you want to delete this file?")
            .setPositiveButton("Delete") { dialog, _ -> deleteFile(fileUri); dialog.dismiss() }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun deleteFile(fileUri: String) {
        setFragmentResult(REQUEST_KEY, bundleOf(RESULT_DELETED_URI to fileUri))
        uploadedFiles.remove(fileUri)
        binding.tvFileCount.text = "${uploadedFiles.size} file(s)"
        setupRecyclerView()
        Snackbar.make(binding.root, "File deleted", Snackbar.LENGTH_SHORT).show()
        if (uploadedFiles.isEmpty()) dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class FileListAdapter(
        private val files: List<String>,
        private val isSubmitted: Boolean,
        private val onFileClick: (String) -> Unit,
        private val onDeleteClick: (String) -> Unit
    ) : RecyclerView.Adapter<FileListAdapter.FileViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
            val binding = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return FileViewHolder(binding)
        }

        override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
            holder.bind(files[position], isSubmitted, onFileClick, onDeleteClick)
        }

        override fun getItemCount() = files.size

        class FileViewHolder(private val binding: ItemFileBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(fileUri: String, isSubmitted: Boolean, onFileClick: (String) -> Unit, onDeleteClick: (String) -> Unit) {
                val fileName = Uri.parse(fileUri).lastPathSegment ?: "Unknown file"
                binding.tvFileName.text = fileName
                binding.ivFileIcon.setImageResource(getFileIcon(fileName.substringAfterLast('.', "")))
                binding.root.setOnClickListener { onFileClick(fileUri) }
                binding.btnDelete.visibility = if (isSubmitted) View.GONE else View.VISIBLE
                if (!isSubmitted) binding.btnDelete.setOnClickListener { onDeleteClick(fileUri) }
            }

            private fun getFileIcon(extension: String): Int {
                return when (extension.lowercase()) {
                    "pdf" -> R.drawable.ic_doc_upload
                    "jpg", "jpeg", "png", "gif", "webp" -> R.drawable.ic_image
                    "doc", "docx" -> R.drawable.ic_word
                    "xls", "xlsx" -> R.drawable.ic_excel
                    else -> R.drawable.ic_doc_upload
                }
            }
        }
    }
}