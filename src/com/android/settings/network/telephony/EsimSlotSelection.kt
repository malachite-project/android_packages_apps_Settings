/* SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.network.telephony

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.UserManager
import android.telephony.TelephonyManager
import android.telephony.euicc.EuiccManager
import com.android.settings.R
import com.android.settings.Utils

/** Entry points for the native Settings UI around the phone-UID slot switch. */
object EsimSlotSelection {
    const val PHYSICAL_SLOT_PREFERENCE_KEY = "physical_sim_slot_2"

    fun isSupported(context: Context): Boolean =
        context.resources.getBoolean(R.bool.config_mtk_euicc_slot_switch) &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_EUICC) &&
            context.getSystemService(UserManager::class.java)?.isAdminUser == true

    fun addSimIntent(context: Context): Intent =
        SharedSimSlotDialogActivity.createIntent(context, true, standardAddSimIntent())

    fun physicalSlotIntent(context: Context): Intent =
        SharedSimSlotDialogActivity.createIntent(context, false, null)

    fun isEmbeddedSlotSelected(context: Context): Boolean =
        context.getSystemService(TelephonyManager::class.java)?.uiccCardsInfo?.any {
            it.physicalSlotIndex == 1 && it.isEuicc && it.ports.any { port -> port.isActive }
        } == true

    fun standardAddSimIntent(): Intent =
        Intent(EuiccManager.ACTION_PROVISION_EMBEDDED_SUBSCRIPTION).apply {
            setPackage(Utils.PHONE_PACKAGE_NAME)
            putExtra(EuiccManager.EXTRA_FORCE_PROVISION, true)
        }
}
