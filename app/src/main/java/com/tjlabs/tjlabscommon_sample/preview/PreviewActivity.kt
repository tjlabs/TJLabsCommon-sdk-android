package com.tjlabs.tjlabscommon_sample.preview

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.tjlabs.tjlabscommon_sample.R

class PreviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SECTOR_ID = "extra_sector_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_preview)
        supportActionBar?.title = "Saved Test Sets"

        if (savedInstanceState == null) {
            val sectorId = intent.getIntExtra(EXTRA_SECTOR_ID, -1)
            supportFragmentManager.beginTransaction()
                .replace(R.id.previewContainer, PreviewFragment.newInstance(sectorId))
                .commit()
        }
    }
}
