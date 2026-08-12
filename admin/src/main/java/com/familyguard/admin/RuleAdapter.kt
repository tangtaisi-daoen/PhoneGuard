package com.familyguard.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.familyguard.admin.databinding.ItemAppLimitBinding
import com.familyguard.core.categories.AppCategory

/** 规则页单行数据。 */
data class AppLimitRowData(
    var packageName: String = "",
    var appName: String = "",
    var minutes: Int = 0,
    var category: AppCategory = AppCategory.OTHER,
)

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
        holder.bind(rows[position], position)
    }

    override fun getItemCount(): Int = rows.size

    inner class VH(private val binding: ItemAppLimitBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(data: AppLimitRowData, position: Int) {
            binding.etPkg.setText(data.packageName)
            binding.etMinutes.setText(if (data.minutes > 0) data.minutes.toString() else "")
            binding.spCategory.setSelection(
                when (data.category) {
                    AppCategory.GAME -> 1
                    AppCategory.SHORT_VIDEO -> 2
                    AppCategory.SOCIAL -> 3
                    else -> 0
                },
            )
            binding.etPkg.setOnClickListener { onPickApp(data) }
            binding.tvPkgHint.text = if (data.appName.isNotBlank()) data.appName else binding.root.context.getString(R.string.rules_pkg_tap)
            binding.tvAppName.visibility = View.GONE
            binding.btnRemove.setOnClickListener {
                rows.removeAt(position)
                notifyItemRemoved(position)
                onRemove(position)
            }
        }
    }

    /** 当前所有行数据（保存用）。 */
    fun collect(): List<AppLimitRowData> = rows.toList()
}
