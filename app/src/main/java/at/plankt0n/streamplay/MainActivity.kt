package at.plankt0n.streamplay

import android.os.Bundle
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import at.plankt0n.streamplay.ui.PlayerFragment

class MainActivity : AppCompatActivity() {

    private var playerFragment: PlayerFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, PlayerFragment(), "playerFragment")
                .commit()

        } else {
            playerFragment =
                supportFragmentManager.findFragmentById(R.id.fragment_container) as? PlayerFragment
        }
    }

    override fun onStart() {
        super.onStart()
        sendRefreshMetadataCommand()
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing) {
            sendRefreshMetadataCommand()
        }
    }

    private fun sendRefreshMetadataCommand() {
        val intent = Intent(this, StreamingService::class.java).apply {
            action = StreamingService.ACTION_REFRESH_METADATA
        }
        startService(intent)
    }

    fun showPlayerFragment() {
        supportFragmentManager.beginTransaction()
            .show(playerFragment!!)
            .commit()
    }

    fun hidePlayerFragment() {
        supportFragmentManager.beginTransaction()
            .hide(playerFragment!!)
            .commit()
    }
}
