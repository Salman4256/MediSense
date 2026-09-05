package com.medisense.app.ui.appointment.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medisense.app.R
import com.medisense.app.data.local.entity.AppointmentEntity
import com.medisense.app.databinding.ItemAppointmentBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppointmentAdapter(
    private val onCompleteClick: (AppointmentEntity) -> Unit,
    private val onEditClick: (AppointmentEntity) -> Unit,
    private val onCancelClick: (AppointmentEntity) -> Unit,
    private val onDeleteClick: (AppointmentEntity) -> Unit
) : ListAdapter<AppointmentEntity, AppointmentAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppointmentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemAppointmentBinding) : RecyclerView.ViewHolder(binding.root) {
        private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        fun bind(item: AppointmentEntity) {
            binding.tvDoctorName.text = item.doctorName
            binding.tvAppointmentType.text = item.appointmentType
            binding.tvClinicName.text = item.clinicName

            val dateStr = if (item.appointmentDate.isNotBlank()) item.appointmentDate else dateFormat.format(Date(item.appointmentTimestamp))
            binding.tvDateTime.text = "$dateStr • ${item.appointmentTime}"

            if (item.reminderMinutesBefore > 0) {
                binding.tvReminderIndicator.visibility = View.VISIBLE
                binding.tvReminderIndicator.text = "⏰ ${item.reminderMinutesBefore}m before"
            } else {
                binding.tvReminderIndicator.visibility = View.GONE
            }

            if (!item.notes.isNullOrBlank()) {
                binding.tvNotes.visibility = View.VISIBLE
                binding.tvNotes.text = "Note: ${item.notes}"
            } else {
                binding.tvNotes.visibility = View.GONE
            }

            val status = item.status.uppercase()
            binding.chipStatus.text = status
            when (status) {
                "COMPLETED" -> {
                    binding.chipStatus.setTextColor(Color.parseColor("#2E7D32"))
                    binding.chipStatus.setChipBackgroundColorResource(android.R.color.transparent)
                }
                "CANCELLED" -> {
                    binding.chipStatus.setTextColor(Color.parseColor("#C62828"))
                    binding.chipStatus.setChipBackgroundColorResource(android.R.color.transparent)
                }
                else -> {
                    binding.chipStatus.setTextColor(Color.parseColor("#1565C0"))
                    binding.chipStatus.setChipBackgroundColorResource(android.R.color.transparent)
                }
            }

            binding.btnMenu.setOnClickListener { view ->
                val popup = PopupMenu(view.context, view)
                popup.inflate(R.menu.menu_appointment_item)
                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.action_complete -> {
                            onCompleteClick(item)
                            true
                        }
                        R.id.action_edit -> {
                            onEditClick(item)
                            true
                        }
                        R.id.action_cancel_appointment -> {
                            onCancelClick(item)
                            true
                        }
                        R.id.action_delete -> {
                            onDeleteClick(item)
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AppointmentEntity>() {
        override fun areItemsTheSame(oldItem: AppointmentEntity, newItem: AppointmentEntity): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: AppointmentEntity, newItem: AppointmentEntity): Boolean =
            oldItem == newItem
    }
}
