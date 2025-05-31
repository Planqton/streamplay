package at.plankt0n.streamplay

import android.os.Bundle
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
