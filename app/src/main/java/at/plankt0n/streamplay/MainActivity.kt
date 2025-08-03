package at.plankt0n.streamplay

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import at.plankt0n.streamplay.helper.GitHubUpdateChecker
import at.plankt0n.streamplay.ui.MainPagerFragment
import at.plankt0n.streamplay.ui.OverlayService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var mainPagerFragment: MainPagerFragment? = null

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            startService(Intent(this, OverlayService::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lifecycleScope.launch {
            GitHubUpdateChecker(this@MainActivity).silentCheckForUpdate()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                startService(Intent(this, OverlayService::class.java))
            } else {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher.launch(intent)
            }
        } else {
            startService(Intent(this, OverlayService::class.java))
        }

        if (savedInstanceState == null) {
            mainPagerFragment = MainPagerFragment()
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, mainPagerFragment!!, "mainPager")
                .commit()

        } else {
            mainPagerFragment =
                supportFragmentManager.findFragmentById(R.id.fragment_container) as? MainPagerFragment
        }
    }

    fun showPlayerPage() {
        mainPagerFragment?.showPlayer()
    }

    fun showStationsPage() {
        mainPagerFragment?.showStations()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(this, OverlayService::class.java))
    }
}
