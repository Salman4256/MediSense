package com.medisense.app.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.medisense.app.domain.support.MediSenseSupportConstants

/**
 * Helper object providing safe external intent launching and accessible fallbacks
 * for MediSense support communication and official web presence.
 */
object SupportIntentHelper {

    /**
     * Launches the user's installed email application with pre-addressed support recipient and subject.
     * Never sends automatically. If no email client is available, displays a graceful dialog
     * allowing the user to copy the support email to clipboard.
     */
    fun openSupportEmail(context: Context, onInitiated: (() -> Unit)? = null) {
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${MediSenseSupportConstants.MEDISENSE_SUPPORT_EMAIL}")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(MediSenseSupportConstants.MEDISENSE_SUPPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, MediSenseSupportConstants.SUPPORT_EMAIL_SUBJECT)
        }

        try {
            context.startActivity(emailIntent)
            onInitiated?.invoke()
        } catch (e: Exception) {
            showNoEmailAppDialog(context)
        }
    }

    /**
     * Launches the user's default web browser with the official MediSense policy website URL.
     * If no browser is installed or available, displays a graceful dialog allowing the user
     * to copy the website URL to clipboard.
     */
    fun openOfficialWebsite(context: Context, onOpened: (() -> Unit)? = null) {
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(MediSenseSupportConstants.MEDISENSE_WEBSITE_URL))

        try {
            context.startActivity(webIntent)
            onOpened?.invoke()
        } catch (e: Exception) {
            showNoBrowserDialog(context)
        }
    }

    private fun showNoEmailAppDialog(context: Context) {
        MaterialAlertDialogBuilder(context)
            .setTitle("Contact Support")
            .setMessage(
                "No compatible email application was found on this device.\n\n" +
                "You can reach our official support team at:\n${MediSenseSupportConstants.MEDISENSE_SUPPORT_EMAIL}"
            )
            .setPositiveButton("Copy Email") { _, _ ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = ClipData.newPlainText("MediSense Support Email", MediSenseSupportConstants.MEDISENSE_SUPPORT_EMAIL)
                clipboard?.setPrimaryClip(clip)
                Toast.makeText(context, "Support email copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showNoBrowserDialog(context: Context) {
        MaterialAlertDialogBuilder(context)
            .setTitle("Official MediSense Website")
            .setMessage(
                "No web browser was found on this device to open the link.\n\n" +
                "You can visit our official website at:\n${MediSenseSupportConstants.MEDISENSE_WEBSITE_URL}"
            )
            .setPositiveButton("Copy Link") { _, _ ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = ClipData.newPlainText("MediSense Official Website", MediSenseSupportConstants.MEDISENSE_WEBSITE_URL)
                clipboard?.setPrimaryClip(clip)
                Toast.makeText(context, "Website link copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }
}
