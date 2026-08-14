package com.familyguard.admin

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.familyguard.admin.databinding.ActivityAboutBinding

/** 关于页：许可证（AGPL-3.0）、源码获取方式、隐私政策与第三方声明入口。 */
class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tvVersion.text = getString(R.string.about_version, BuildConfig.VERSION_NAME)
    }
}
