package com.familyguard.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.familyguard.admin.databinding.ItemAppLimitBinding

/** 应用限时卡片列表适配器（借鉴 KidSafe 卡片列表设计）。 */
class RuleAdapter(
    private val onPickApp: (AppLimitRowData) -> Unit,
    private val onRemove: (Int) -> Unit,
) : RecyclerView.Adapter<RuleAdapter.VH>() {

    val rows = mutableListOf<AppLimitRowData>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAppLimitBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(rows[position])
    }

    override fun getItemCount(): Int = rows.size

    inner class VH(private val binding: ItemAppLimitBinding) : RecyclerView.ViewHolder(binding.root) {
        private var currentRow: AppLimitRowData? = null

        init {
            val pick = View.OnClickListener { currentRow?.let(onPickApp) }
            binding.layoutPkg.setOnClickListener(pick)
            binding.etPkg.setOnClickListener(pick)
            binding.tvPkgHint.setOnClickListener(pick)
            binding.etMinutes.doAfterTextChanged { currentRow?.updateMinutes(it) }
            binding.spCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    currentRow?.category = AppLimitRowData.categoryAt(position)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
            binding.btnRemove.setOnClickListener {
                @Suppress("DEPRECATION")
                val position = adapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnClickListener
                rows.removeAt(position)
                notifyItemRemoved(position)
                onRemove(position)
            }
        }

        fun bind(data: AppLimitRowData) {
            currentRow = data
            binding.etPkg.setText(data.appName)
            binding.etMinutes.setText(if (data.minutes > 0) data.minutes.toString() else "")
            binding.spCategory.setSelection(AppLimitRowData.positionOf(data.category))
            binding.tvPkgHint.setText(
                if (data.appName.isNotBlank()) R.string.rules_pick_again else R.string.rules_pkg_tap,
            )
            binding.tvAppName.visibility = View.GONE
        }
    }

}
