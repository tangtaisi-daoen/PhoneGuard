package com.familyguard.kid.protect

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.familyguard.kid.R

/** Setup Wizard 的策略合规回调；基线成功后才允许完成 Fully Managed 配置。 */
class AdminPolicyComplianceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = KidDevicePolicyController(this).applyBaseline()
        if (result.isSuccess) {
            setResult(RESULT_OK)
        } else {
            Toast.makeText(this, R.string.device_owner_setup_failed, Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELED)
        }
        finish()
    }
}
